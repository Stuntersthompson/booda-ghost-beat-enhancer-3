package com.example.audio

import com.example.data.model.JobType
import com.example.data.model.STYLE_LIMIT
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

data class BeatMeterSnapshot(
  val intensityScore: Int,
  val complexityScore: Int,
  val tierLabel: String,
  val audioDriven: Boolean,
  val effectiveBpm: Double,
  val bandTargets: List<Float>,
  val bandLabels: List<String> = DEFAULT_BAND_LABELS
) {
  val summaryText: String
    get() {
      val source = if (audioDriven) "driven by analyzed audio and beat parameters" else "simulated from beat parameters at ${effectiveBpm.roundToInt()} BPM"
      return "Intensity $intensityScore percent, Complexity $complexityScore percent, $tierLabel profile, $source"
    }

  companion object {
    val DEFAULT_BAND_LABELS = listOf("32", "63", "125", "250", "500", "1k", "2k", "4k", "6k", "8k", "12k", "16k")
  }
}

/**
 * Pure-Kotlin DSP meter calculator mapping generated Suno beat parameters
 * (sliders, prompt complexity, genre lane) and optional AudioAnalysisResult telemetry
 * into normalized 12-band spectrum targets plus complexity & intensity indices.
 */
object BeatIntensityCalculator {

  private const val BAND_COUNT = 12
  private val sectionTagRegex = Regex("""\[[^\]]+]""")
  private val wordSplitRegex = Regex("""[\s,;]+""")

  fun compute(
    variety: Int,
    weirdness: Int,
    styleInfluence: Int,
    audioInfluence: Int,
    stylePrompt: String,
    lyricsMap: String,
    excludeText: String,
    activeLane: String,
    jobType: JobType,
    audioAnalysis: AudioAnalysisResult?
  ): BeatMeterSnapshot {
    val normVariety = (variety.coerceIn(0, 100)) / 100f
    val normWeirdness = (weirdness.coerceIn(0, 100)) / 100f
    val normStyleInf = (styleInfluence.coerceIn(0, 100)) / 100f
    val normAudioInf = if (jobType == JobType.PRESERVE) (audioInfluence.coerceIn(0, 100)) / 100f else 0.15f

    // Complexity: structure sections + style lexical density + exclude constraints + weirdness/variety
    val sectionCount = sectionTagRegex.findAll(lyricsMap).count()
    val lyricsLineCount = lyricsMap.lineSequence().count { it.isNotBlank() }
    val styleWordCount = stylePrompt.trim().split(wordSplitRegex).count { it.isNotBlank() }
    val excludeTokenCount = excludeText.trim().split(Regex("""[,;\n]+""")).count { it.isNotBlank() }

    val lyricsComplexity = ((sectionCount / 8f) * 0.65f + (lyricsLineCount / 24f).coerceAtMost(1f) * 0.35f).coerceIn(0f, 1f)
    val styleComplexity = ((styleWordCount / 22f).coerceAtMost(1f) * 0.65f + (stylePrompt.length.toFloat() / (STYLE_LIMIT * 0.35f)).coerceIn(0f, 1f) * 0.35f).coerceIn(0f, 1f)
    val excludeComplexity = (excludeTokenCount / 8f).coerceIn(0f, 1f)
    val sliderEntropy = (normWeirdness * 0.55f + normVariety * 0.45f).coerceIn(0f, 1f)

    val rawComplexity = (
      lyricsComplexity * 0.32f +
        styleComplexity * 0.28f +
        excludeComplexity * 0.14f +
        sliderEntropy * 0.26f
      ).coerceIn(0f, 1f)

    // Lane baseline energy (0..1)
    val laneProfile = resolveLaneSpectralBias(activeLane)
    val effectiveBpm = audioAnalysis?.bpm ?: laneProfile.defaultBpm

    // Intensity: style drive + sub/audio push + lane energy + optional audio analysis dynamics
    val paramIntensity = (
      normStyleInf * 0.38f +
        normAudioInf * 0.26f +
        normWeirdness * 0.14f +
        normVariety * 0.10f +
        laneProfile.energyBias * 0.12f
      ).coerceIn(0f, 1f)

    val audioDriven = audioAnalysis != null
    val audioEnergy = if (audioAnalysis != null) {
      val rmsNorm = ((audioAnalysis.rmsDb + 48.0) / 44.0).toFloat().coerceIn(0.08f, 1f)
      val onsetNorm = (audioAnalysis.onsetRate / 6.0).toFloat().coerceIn(0.05f, 1f)
      (rmsNorm * 0.65f + onsetNorm * 0.35f).coerceIn(0.08f, 1f)
    } else {
      0f
    }

    val rawIntensity = if (audioDriven) {
      (paramIntensity * 0.45f + audioEnergy * 0.55f).coerceIn(0.05f, 1f)
    } else {
      paramIntensity
    }

    val intensityScore = (rawIntensity * 100f).roundToInt().coerceIn(0, 100)
    val complexityScore = (rawComplexity * 100f).roundToInt().coerceIn(0, 100)
    val composite = (intensityScore * 0.55f + complexityScore * 0.45f).roundToInt()

    val tierLabel = when {
      composite >= 75 -> "MAXIMAL"
      composite >= 52 -> "DENSE"
      composite >= 28 -> "TIGHT"
      else -> "MINIMAL"
    }

    // Build 12 spectral band targets (32Hz -> 16kHz)
    val lowBoost = if (audioAnalysis != null) {
      (audioAnalysis.lowShare.toFloat().coerceIn(0.08f, 0.85f) * 1.15f + normAudioInf * 0.35f) / 1.35f
    } else {
      (laneProfile.subWeight * 0.55f + normAudioInf * 0.45f)
    }.coerceIn(0.08f, 1f)

    val centroidTilt = if (audioAnalysis != null) {
      ((audioAnalysis.centroidHz.toFloat() - 600f) / 4200f).coerceIn(0.1f, 1f)
    } else {
      (laneProfile.airWeight * 0.5f + normStyleInf * 0.3f + normWeirdness * 0.2f).coerceIn(0.1f, 1f)
    }

    val onsetBoost = if (audioAnalysis != null) {
      (audioAnalysis.onsetRate.toFloat() / 6.5f).coerceIn(0.1f, 1f)
    } else {
      (normVariety * 0.5f + styleComplexity * 0.5f).coerceIn(0.1f, 1f)
    }

    val targets = FloatArray(BAND_COUNT) { idx ->
      val bandNorm = idx.toFloat() / (BAND_COUNT - 1)
      val baseCurve = when {
        idx <= 2 -> {
          // Sub & Bass (32, 63, 125 Hz)
          val falloff = 1f - idx * 0.06f
          (lowBoost * 0.72f + rawIntensity * 0.28f) * falloff
        }
        idx <= 6 -> {
          // Low-Mid & Mid (250, 500, 1k, 2k Hz)
          val midFocus = 0.5f * styleComplexity + 0.3f * normStyleInf + 0.2f * lyricsComplexity
          val centerShape = 1f - abs(bandNorm - 0.38f) * 0.45f
          (midFocus * 0.68f + rawIntensity * 0.32f) * centerShape
        }
        else -> {
          // Presence & Air (4k, 6k, 8k, 12k, 16k Hz)
          val airEnergy = centroidTilt * 0.45f + onsetBoost * 0.30f + normWeirdness * 0.25f
          val taper = 1f - ((idx - 7) * 0.08f)
          (airEnergy * 0.75f + rawComplexity * 0.25f) * taper
        }
      }
      val minFloor = if (stylePrompt.isBlank() && lyricsMap.isBlank() && audioAnalysis == null) 0.06f else 0.10f
      max(minFloor, baseCurve.coerceIn(0.06f, 0.98f))
    }

    return BeatMeterSnapshot(
      intensityScore = intensityScore,
      complexityScore = complexityScore,
      tierLabel = tierLabel,
      audioDriven = audioDriven,
      effectiveBpm = effectiveBpm,
      bandTargets = targets.toList()
    )
  }

  /**
   * Computes time-modulated frame amplitudes into [outBands] with zero heap allocations.
   */
  fun sampleAnimatedBands(
    snapshot: BeatMeterSnapshot,
    timeSec: Float,
    sensitivity: Float,
    isPlayingOrLive: Boolean,
    outBands: FloatArray
  ) {
    val count = minOf(outBands.size, snapshot.bandTargets.size)
    val beatsPerSec = (snapshot.effectiveBpm.toFloat().coerceIn(55f, 195f)) / 60f
    val beatPhase = timeSec * beatsPerSec * (2f * PI.toFloat())
    val motionDepth = when {
      isPlayingOrLive -> 0.24f
      snapshot.audioDriven -> 0.16f
      else -> 0.13f
    }

    for (i in 0 until count) {
      val base = snapshot.bandTargets[i] * sensitivity.coerceIn(0.5f, 2.0f)
      // Sub bands pulse on quarter/half beats; high bands shimmer on offbeats/16ths
      val wave = when {
        i <= 2 -> (0.5f + 0.5f * sin(beatPhase - i * 0.25f))
        i <= 6 -> (0.5f + 0.5f * cos(beatPhase * 1.5f + i * 0.55f))
        else -> (0.5f + 0.5f * sin(beatPhase * 2.5f + i * 0.85f))
      }
      val modulated = base * (1f - motionDepth * 0.5f + wave * motionDepth)
      outBands[i] = modulated.coerceIn(0.05f, 1.0f)
    }
  }

  private data class LaneBias(
    val defaultBpm: Double,
    val energyBias: Float,
    val subWeight: Float,
    val airWeight: Float
  )

  private fun resolveLaneSpectralBias(lane: String): LaneBias {
    val key = lane.lowercase()
    return when {
      key.contains("rage") || key.contains("tread") || key.contains("digicore") ->
        LaneBias(152.0, 0.92f, 0.85f, 0.90f)
      key.contains("drill") ->
        LaneBias(142.0, 0.86f, 0.90f, 0.72f)
      key.contains("phonk") || key.contains("dark") || key.contains("horror") || key.contains("synthwave") ->
        LaneBias(126.0, 0.82f, 0.86f, 0.68f)
      key.contains("trap") ->
        LaneBias(140.0, 0.80f, 0.88f, 0.65f)
      key.contains("lo-fi") || key.contains("lofi") || key.contains("jazz") || key.contains("cloud") ->
        LaneBias(82.0, 0.45f, 0.58f, 0.36f)
      key.contains("boom bap") || key.contains("east coast") ->
        LaneBias(92.0, 0.68f, 0.64f, 0.58f)
      key.contains("afro") || key.contains("amapiano") || key.contains("bounce") || key.contains("jersey") ->
        LaneBias(116.0, 0.78f, 0.76f, 0.74f)
      else ->
        LaneBias(120.0, 0.66f, 0.70f, 0.60f)
    }
  }
}
