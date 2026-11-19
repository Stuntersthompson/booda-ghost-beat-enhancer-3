package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.model.LOCK_LINE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.math.*

data class AudioAnalysisResult(
  val fileName: String,
  val durationSec: Double,
  val bpm: Double?,
  val bpmConfidence: String, // "high", "medium", "low", "estimate", "unread"
  val keyName: String?,
  val keyClear: Boolean,
  val rmsDb: Double,
  val peakDb: Double,
  val lowShare: Double, // 0..1 of spectral energy under ~160 Hz
  val centroidHz: Double,
  val onsetRate: Double,
  val suggestedLane: String,
  val pocketLabel: String,
  val promptPreserve: String,
  val promptOriginal: String,
  val sampleWordsText: String? = null,
  val sampleWordsKind: String? = null,
  val waveformPeaks: List<Float> = emptyList(),
  val key: String? = null,
  val mode: String? = null,
  val bpmConfidenceScore: Double = 0.0,
  val keyConfidenceScore: Double = 0.0,
  val isAudioMeasured: Boolean = true,
  val isSpectralMeasured: Boolean = true,
  val isTempoEstimate: Boolean = false,
  val measuredSpectrumBands: List<Float> = emptyList(),
  val analysisNote: String? = null
) {
  val waveformPeaksArray: FloatArray by lazy(LazyThreadSafetyMode.NONE) {
    waveformPeaks.toFloatArray()
  }
}

data class AnalyzedClipOutput(
  val result: AudioAnalysisResult,
  val multimodalWavBytes: ByteArray?
)

internal data class SpectralFeatures(
  val lowShare: Double,
  val centroidHz: Double,
  val isMeasured: Boolean,
  val spectrumBands12: List<Float>
)

internal data class TempoEstimate(
  val bpm: Double?,
  val confidenceLabel: String,
  val confidenceScore: Double,
  val onsetRate: Double,
  val isEstimate: Boolean = false
)

internal data class KeyEstimate(
  val key: String?,
  val mode: String?,
  val keyName: String?,
  val clear: Boolean,
  val confidenceScore: Double
)

/**
 * Production-grade multi-frame DSP engine with pre-allocated FFT/window workspace buffers.
 * Computes container-accurate mono downmix, 24-frame Hann-windowed chromagram key/mode estimation,
 * and 3-sub-band spectral energy-flux lag autocorrelation (60–176 BPM) with parabolic peak interpolation.
 */
class AudioAnalyzer(private val context: Context) {

  private data class DecodedAudio(
    val samples: FloatArray,
    val sampleRate: Int
  )

  private companion object {
    const val SPECTRAL_FFT_SIZE = 2048
    const val CHROMA_FFT_SIZE = 4096
    const val TEMPO_FFT_SIZE = 1024
    const val TEMPO_HOP_SIZE = 512
    const val SUB_BAND_COUNT = 3
    const val TARGET_ANALYSIS_FRAMES = 24
  }

  private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
  private val majorProfile = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
  private val minorProfile = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

  // Pre-allocated DSP workspace buffers (protected by synchronized access during CPU analysis)
  private val spectralHammingWindow = DoubleArray(SPECTRAL_FFT_SIZE) { i ->
    0.54 - 0.46 * cos(2.0 * PI * i / (SPECTRAL_FFT_SIZE - 1))
  }
  private val spectralReal = DoubleArray(SPECTRAL_FFT_SIZE)
  private val spectralImag = DoubleArray(SPECTRAL_FFT_SIZE)

  private val chromaHannWindow = DoubleArray(CHROMA_FFT_SIZE) { i ->
    0.5 * (1.0 - cos(2.0 * PI * i / (CHROMA_FFT_SIZE - 1)))
  }
  private val chromaReal = DoubleArray(CHROMA_FFT_SIZE)
  private val chromaImag = DoubleArray(CHROMA_FFT_SIZE)
  private val chromaAccumulator = DoubleArray(12)
  private val normalizedChroma = DoubleArray(12)

  private val tempoHannWindow = DoubleArray(TEMPO_FFT_SIZE) { i ->
    0.5 * (1.0 - cos(2.0 * PI * i / (TEMPO_FFT_SIZE - 1)))
  }
  private val tempoReal = DoubleArray(TEMPO_FFT_SIZE)
  private val tempoImag = DoubleArray(TEMPO_FFT_SIZE)
  private val bandCurrentEnergy = DoubleArray(SUB_BAND_COUNT)
  private val bandPrevEnergy = DoubleArray(SUB_BAND_COUNT)

  suspend fun analyzeAudioUri(uri: Uri, fileName: String): AudioAnalysisResult =
    analyzeAudioUriWithClip(uri, fileName).result

  suspend fun analyzeAudioUriWithClip(uri: Uri, fileName: String): AnalyzedClipOutput =
    withContext(Dispatchers.IO) {
      val decoded = decodeAudioUri(uri)
      val analysis = analyzeFloatSamples(fileName, decoded.samples, decoded.sampleRate)
      val wavBytes = if (decoded.samples.isNotEmpty()) {
        encodeWav16KhzMono(decoded.samples, decoded.sampleRate, maxSeconds = 15)
      } else null
      AnalyzedClipOutput(analysis, wavBytes)
    }

  suspend fun analyzeRecordedSamples(
    fileName: String,
    samples: FloatArray,
    sampleRate: Int = 44100
  ): AudioAnalysisResult = withContext(Dispatchers.Default) {
    analyzeFloatSamples(fileName, samples, sampleRate)
  }

  /**
   * Synchronous thread-safe DSP entry point for unit tests and audio buffers.
   */
  @Synchronized
  fun analyzeFloatSamples(fileName: String, samples: FloatArray, sampleRate: Int): AudioAnalysisResult {
    if (samples.isEmpty() || sampleRate <= 0) {
      return unreadResult(fileName, "Could not decode readable PCM samples from this audio file.")
    }

    val durationSec = samples.size.toDouble() / sampleRate
    var sumSq = 0.0
    var peak = 0.0
    for (s in samples) {
      val absVal = abs(s.toDouble())
      if (absVal > peak) peak = absVal
      sumSq += (s * s)
    }
    val rms = sqrt(sumSq / samples.size)
    val rmsDb = if (rms > 1e-5) 20.0 * log10(rms) else -80.0
    val peakDb = if (peak > 1e-5) 20.0 * log10(peak) else -80.0

    if (peak <= 1e-5) {
      return unreadResult(
        fileName = fileName,
        reason = "Audio stream appears completely silent (peak <= -80 dBFS).",
        durationSec = durationSec
      )
    }

    val spectral = calculateSpectralFeaturesDetailed(samples, sampleRate)
    val lowShare = spectral.lowShare
    val centroidHz = spectral.centroidHz
    val keyEstimate = estimateMusicalKeyDetailed(samples, sampleRate)
    val tempoEstimate = estimateTempoDetailed(samples, sampleRate)

    val numBuckets = 100
    val bucketSize = max(1, samples.size / numBuckets)
    val peaks = ArrayList<Float>(numBuckets)
    for (b in 0 until numBuckets) {
      var bPeak = 0.04f
      val start = b * bucketSize
      val end = if (b == numBuckets - 1) samples.size else min(samples.size, start + bucketSize)
      for (i in start until end) {
        val a = abs(samples[i])
        if (a > bPeak) bPeak = a
      }
      peaks.add(bPeak.coerceIn(0.04f, 1.0f))
    }

    val suggestedLane = rankBestLane(tempoEstimate.bpm, centroidHz, lowShare, tempoEstimate.onsetRate, spectral.isMeasured)
    val bpmTag = tempoEstimate.bpm?.let {
      if (tempoEstimate.isEstimate) "${it.roundToInt()} BPM (Est.)" else "${it.roundToInt()} BPM"
    } ?: "BPM Unread"
    val toneTag = if (!spectral.isMeasured) {
      "Spectrum Unread"
    } else {
      "${if (centroidHz > 2800) "Bright" else "Dark"} · ${if (lowShare > 0.36) "Sub-heavy" else "Balanced"}"
    }
    val pocketLabel = "$bpmTag · $toneTag"

    val promptPreserve = "$LOCK_LINE. Add tight $suggestedLane drums underneath only, spice only, sample stays lead."
    val bpmClause = tempoEstimate.bpm?.let { ", ${it.roundToInt()} BPM" } ?: ""
    val promptOriginal = "$suggestedLane instrumental$bpmClause, heavy 808, crisp percussion, full arrangement."

    return AudioAnalysisResult(
      fileName = fileName,
      durationSec = durationSec,
      bpm = tempoEstimate.bpm,
      bpmConfidence = tempoEstimate.confidenceLabel,
      keyName = keyEstimate.keyName,
      keyClear = keyEstimate.clear,
      rmsDb = rmsDb,
      peakDb = peakDb,
      lowShare = lowShare,
      centroidHz = centroidHz,
      onsetRate = tempoEstimate.onsetRate,
      suggestedLane = suggestedLane,
      pocketLabel = pocketLabel,
      promptPreserve = promptPreserve,
      promptOriginal = promptOriginal,
      waveformPeaks = peaks,
      key = keyEstimate.key,
      mode = keyEstimate.mode,
      bpmConfidenceScore = tempoEstimate.confidenceScore,
      keyConfidenceScore = keyEstimate.confidenceScore,
      isAudioMeasured = true,
      isSpectralMeasured = spectral.isMeasured,
      isTempoEstimate = tempoEstimate.isEstimate,
      measuredSpectrumBands = spectral.spectrumBands12,
      analysisNote = when {
        tempoEstimate.bpm == null && keyEstimate.keyName == null ->
          "Partial DSP read: clip too short or non-tonal for full tempo and key lock."
        tempoEstimate.bpm == null ->
          "Tempo could not be locked cleanly (clip short or lacks steady transient pulse)."
        tempoEstimate.isEstimate ->
          "Tempo is a low-confidence estimate — verify with x0.5 / x2.0 if needed."
        else -> null
      }
    )
  }

  /**
   * Downsamples/normalizes float audio to compact 16 kHz mono 16-bit WAV
   * well inside Firebase AI inline payload limits (< 20 MB).
   */
  fun encodeWav16KhzMono(
    samples: FloatArray,
    sourceSampleRate: Int,
    maxSeconds: Int = 15
  ): ByteArray {
    if (samples.isEmpty() || sourceSampleRate <= 0) return ByteArray(0)
    val targetRate = 16000
    val step = sourceSampleRate.toDouble() / targetRate
    val maxTargetSamples = targetRate * maxSeconds
    val estimatedOutSamples = min(maxTargetSamples, (samples.size / step).toInt().coerceAtLeast(1))
    val dataBytes = estimatedOutSamples * 2
    val out = ByteArrayOutputStream(44 + dataBytes)

    fun writeAscii(s: String) {
      for (ch in s) out.write(ch.code)
    }
    fun writeIntLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v shr 8) and 0xFF)
      out.write((v shr 16) and 0xFF)
      out.write((v shr 24) and 0xFF)
    }
    fun writeShortLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v shr 8) and 0xFF)
    }

    writeAscii("RIFF")
    writeIntLE(36 + dataBytes)
    writeAscii("WAVE")
    writeAscii("fmt ")
    writeIntLE(16)
    writeShortLE(1)
    writeShortLE(1)
    writeIntLE(targetRate)
    writeIntLE(targetRate * 2)
    writeShortLE(2)
    writeShortLE(16)
    writeAscii("data")
    writeIntLE(dataBytes)

    for (i in 0 until estimatedOutSamples) {
      val srcPos = i * step
      val idx0 = srcPos.toInt().coerceIn(0, samples.lastIndex)
      val idx1 = (idx0 + 1).coerceAtMost(samples.lastIndex)
      val frac = (srcPos - idx0).toFloat()
      val sample = (samples[idx0] * (1f - frac) + samples[idx1] * frac).coerceIn(-1f, 1f)
      val pcm16 = (sample * 32767f).roundToInt().coerceIn(-32768, 32767)
      out.write(pcm16 and 0xFF)
      out.write((pcm16 shr 8) and 0xFF)
    }
    return out.toByteArray()
  }

  /**
   * Multi-frame Hamming-windowed FFT spectral analysis across up to 24 evenly spaced active frames
   * using pre-allocated FFT workspace buffers. Computes real 12-band spectrum magnitudes alongside
   * honest unread state on silent or sub-window clips.
   */
  internal fun calculateSpectralFeaturesDetailed(samples: FloatArray, sampleRate: Int): SpectralFeatures {
    if (samples.size < SPECTRAL_FFT_SIZE || sampleRate <= 0) {
      return SpectralFeatures(lowShare = 0.0, centroidHz = 0.0, isMeasured = false, spectrumBands12 = emptyList())
    }

    val maxStart = samples.size - SPECTRAL_FFT_SIZE
    val frameStep = max(SPECTRAL_FFT_SIZE / 2, maxStart / max(1, TARGET_ANALYSIS_FRAMES - 1))
    val binWidth = sampleRate.toDouble() / SPECTRAL_FFT_SIZE

    val bandCenters = doubleArrayOf(32.0, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 6000.0, 8000.0, 12000.0, 16000.0)
    val bandEdges = doubleArrayOf(20.0, 45.0, 90.0, 180.0, 355.0, 710.0, 1400.0, 2800.0, 5000.0, 7000.0, 10000.0, 14000.0, 20000.0)
    val bandEnergyAccum = DoubleArray(bandCenters.size)
    val bandBinCounts = IntArray(bandCenters.size)

    var lowEnergyTotal = 0.0
    var totalEnergyTotal = 0.0
    var weightedFreqTotal = 0.0
    var activeFrames = 0

    var offset = 0
    while (offset <= maxStart && activeFrames < TARGET_ANALYSIS_FRAMES) {
      var frameSumSq = 0.0
      for (i in 0 until SPECTRAL_FFT_SIZE) {
        val s = samples[offset + i].toDouble()
        frameSumSq += s * s
        spectralReal[i] = s * spectralHammingWindow[i]
        spectralImag[i] = 0.0
      }

      val frameRms = sqrt(frameSumSq / SPECTRAL_FFT_SIZE)
      if (frameRms > 1e-4) {
        fftRadix2(spectralReal, spectralImag)
        for (k in 1 until SPECTRAL_FFT_SIZE / 2) {
          val mag = hypot(spectralReal[k], spectralImag[k])
          val freq = k * binWidth
          totalEnergyTotal += mag
          weightedFreqTotal += (mag * freq)
          if (freq <= 160.0) {
            lowEnergyTotal += mag
          }
          for (b in bandCenters.indices) {
            if (freq >= bandEdges[b] && freq < bandEdges[b + 1]) {
              bandEnergyAccum[b] += mag
              if (activeFrames == 0) bandBinCounts[b]++
              break
            }
          }
        }
        activeFrames++
      }
      offset += frameStep
    }

    if (totalEnergyTotal <= 1e-9 || activeFrames == 0) {
      return SpectralFeatures(lowShare = 0.0, centroidHz = 0.0, isMeasured = false, spectrumBands12 = emptyList())
    }

    val lowShare = (lowEnergyTotal / totalEnergyTotal).coerceIn(0.0, 1.0)
    val centroid = (weightedFreqTotal / totalEnergyTotal).coerceIn(40.0, (sampleRate / 2.0).coerceAtLeast(300.0))

    // Normalize 12 frequency bands using log-scaled mean bin energy per band relative to peak band
    val meanBandVals = DoubleArray(bandCenters.size) { b ->
      val avg = bandEnergyAccum[b] / activeFrames.toDouble()
      val widthNorm = sqrt(max(1, bandBinCounts[b]).toDouble())
      ln(1.0 + (avg / widthNorm) * 0.45)
    }
    val maxBand = meanBandVals.maxOrNull()?.coerceAtLeast(1e-6) ?: 1.0
    val bands12 = meanBandVals.map { v ->
      (v / maxBand).toFloat().coerceIn(0.05f, 1.0f)
    }

    return SpectralFeatures(
      lowShare = lowShare,
      centroidHz = centroid,
      isMeasured = true,
      spectrumBands12 = bands12
    )
  }

  internal fun calculateSpectralFeatures(samples: FloatArray, sampleRate: Int): Pair<Double, Double> {
    val features = calculateSpectralFeaturesDetailed(samples, sampleRate)
    return Pair(features.lowShare, features.centroidHz)
  }

  /**
   * Hann-windowed chromagram key and mode detector analyzing up to 24 evenly spaced active frames
   * and correlating pitch-class energy against major and minor profiles.
   */
  internal fun estimateMusicalKeyDetailed(samples: FloatArray, sampleRate: Int): KeyEstimate {
    if (samples.size < CHROMA_FFT_SIZE || sampleRate <= 0) {
      return KeyEstimate(key = null, mode = null, keyName = null, clear = false, confidenceScore = 0.0)
    }

    chromaAccumulator.fill(0.0)

    val maxStart = samples.size - CHROMA_FFT_SIZE
    val activeOffsets = collectActiveFrameOffsets(
      samples = samples,
      windowSize = CHROMA_FFT_SIZE,
      targetFrames = TARGET_ANALYSIS_FRAMES,
      minEnergyThreshold = 1e-6
    )

    val binWidth = sampleRate.toDouble() / CHROMA_FFT_SIZE
    val effectiveOffsets = if (activeOffsets.isNotEmpty()) {
      activeOffsets
    } else {
      val fallbackStep = max(CHROMA_FFT_SIZE / 2, maxStart / max(1, TARGET_ANALYSIS_FRAMES - 1))
      buildList {
        var off = 0
        while (off <= maxStart && size < TARGET_ANALYSIS_FRAMES) {
          add(off)
          off += fallbackStep
        }
      }
    }

    for (offset in effectiveOffsets) {
      for (i in 0 until CHROMA_FFT_SIZE) {
        chromaReal[i] = samples[offset + i].toDouble() * chromaHannWindow[i]
        chromaImag[i] = 0.0
      }

      fftRadix2(chromaReal, chromaImag)
      for (k in 1 until CHROMA_FFT_SIZE / 2) {
        val freq = k * binWidth
        if (freq in 65.0..2000.0) {
          val mag = hypot(chromaReal[k], chromaImag[k])
          val midi = (12.0 * log2(freq / 440.0) + 69.0).roundToInt()
          val pitchClass = (midi % 12 + 12) % 12
          chromaAccumulator[pitchClass] += mag * mag
        }
      }
    }

    var sum = 0.0
    for (v in chromaAccumulator) sum += v
    if (sum <= 1e-12) {
      return KeyEstimate(key = null, mode = null, keyName = null, clear = false, confidenceScore = 0.0)
    }
    for (i in 0 until 12) {
      normalizedChroma[i] = chromaAccumulator[i] / sum
    }

    var bestScore = -Double.MAX_VALUE
    var secondScore = -Double.MAX_VALUE
    var bestRoot = "C"
    var bestMode = "Minor"

    for (root in 0 until 12) {
      val majScore = correlate(normalizedChroma, majorProfile, root)
      if (majScore > bestScore) {
        secondScore = bestScore
        bestScore = majScore
        bestRoot = noteNames[root]
        bestMode = "Major"
      } else if (majScore > secondScore) {
        secondScore = majScore
      }

      val minScore = correlate(normalizedChroma, minorProfile, root)
      if (minScore > bestScore) {
        secondScore = bestScore
        bestScore = minScore
        bestRoot = noteNames[root]
        bestMode = "Minor"
      } else if (minScore > secondScore) {
        secondScore = minScore
      }
    }

    val margin = (bestScore - secondScore).coerceAtLeast(0.0)
    val confidenceScore = ((bestScore.coerceIn(0.0, 1.0) * 0.65) + ((margin / 0.30).coerceIn(0.0, 1.0) * 0.35))
      .coerceIn(0.0, 1.0)
    val clear = margin > 0.07 && bestScore > 0.50

    if (bestScore < 0.25) {
      return KeyEstimate(
        key = null,
        mode = null,
        keyName = null,
        clear = false,
        confidenceScore = round(confidenceScore * 1000.0) / 1000.0
      )
    }

    return KeyEstimate(
      key = bestRoot,
      mode = bestMode,
      keyName = "$bestRoot ${bestMode.lowercase()}",
      clear = clear,
      confidenceScore = round(confidenceScore * 1000.0) / 1000.0
    )
  }

  internal fun estimateMusicalKey(samples: FloatArray, sampleRate: Int): Pair<String?, Boolean> {
    val est = estimateMusicalKeyDetailed(samples, sampleRate)
    return Pair(est.keyName, est.clear)
  }

  private fun collectActiveFrameOffsets(
    samples: FloatArray,
    windowSize: Int,
    targetFrames: Int,
    minEnergyThreshold: Double
  ): List<Int> {
    val maxStart = samples.size - windowSize
    if (maxStart < 0) return emptyList()
    val probeStep = max(windowSize / 2, maxStart / max(1, (targetFrames * 3) - 1))
    val candidates = ArrayList<Int>(targetFrames * 3)
    var off = 0
    while (off <= maxStart) {
      var energySum = 0.0
      // Sample every 4th point for fast silence screening
      var i = 0
      while (i < windowSize) {
        val s = samples[off + i].toDouble()
        energySum += s * s
        i += 4
      }
      if (energySum / (windowSize / 4) > minEnergyThreshold) {
        candidates.add(off)
      }
      off += probeStep
    }
    if (candidates.size <= targetFrames) return candidates
    return List(targetFrames) { idx ->
      val mappedIndex = (idx.toLong() * (candidates.size - 1) / (targetFrames - 1)).toInt()
      candidates[mappedIndex]
    }
  }

  private fun correlate(chroma: DoubleArray, profile: DoubleArray, root: Int): Double {
    var meanC = 0.0
    var meanP = 0.0
    for (i in 0 until 12) {
      meanC += chroma[i]
      meanP += profile[i]
    }
    meanC /= 12.0
    meanP /= 12.0

    var num = 0.0
    var dc = 0.0
    var dp = 0.0
    for (i in 0 until 12) {
      val c = chroma[i] - meanC
      val p = profile[(i - root + 12) % 12] - meanP
      num += c * p
      dc += c * c
      dp += p * p
    }
    val den = sqrt(dc * dp)
    return if (den > 0.0) num / den else 0.0
  }

  /**
   * Sub-band spectral energy-flux lag autocorrelation over 60–176 BPM with parabolic peak interpolation.
   * Bands: Low Kick/Sub (20–180 Hz), Mid Snare/Chop (180–1400 Hz), High Hats/Transients (1400–6500 Hz).
   */
  internal fun estimateTempoDetailed(samples: FloatArray, sampleRate: Int): TempoEstimate {
    if (sampleRate <= 0) {
      return TempoEstimate(bpm = null, confidenceLabel = "unread", confidenceScore = 0.0, onsetRate = 0.0, isEstimate = false)
    }
    val numHops = (samples.size - TEMPO_FFT_SIZE) / TEMPO_HOP_SIZE + 1
    val durationSec = samples.size.toDouble() / sampleRate
    if (samples.size < TEMPO_FFT_SIZE || numHops < 24 || durationSec < 0.8) {
      return TempoEstimate(bpm = null, confidenceLabel = "unread", confidenceScore = 0.0, onsetRate = 0.0, isEstimate = false)
    }

    val binWidth = sampleRate.toDouble() / TEMPO_FFT_SIZE
    val lowMinBin = max(1, (20.0 / binWidth).toInt())
    val lowMaxBin = max(lowMinBin, (180.0 / binWidth).toInt().coerceAtMost(TEMPO_FFT_SIZE / 2 - 1))
    val midMinBin = min(TEMPO_FFT_SIZE / 2 - 1, lowMaxBin + 1)
    val midMaxBin = max(midMinBin, (1400.0 / binWidth).toInt().coerceAtMost(TEMPO_FFT_SIZE / 2 - 1))
    val highMinBin = min(TEMPO_FFT_SIZE / 2 - 1, midMaxBin + 1)
    val highMaxBin = max(highMinBin, (6500.0 / binWidth).toInt().coerceAtMost(TEMPO_FFT_SIZE / 2 - 1))

    val bandNovelty = Array(SUB_BAND_COUNT) { DoubleArray(numHops) }
    val bandMeans = DoubleArray(SUB_BAND_COUNT)
    val combinedNovelty = DoubleArray(numHops)
    bandPrevEnergy.fill(0.0)

    for (h in 0 until numHops) {
      val frameOffset = h * TEMPO_HOP_SIZE
      for (i in 0 until TEMPO_FFT_SIZE) {
        tempoReal[i] = samples[frameOffset + i].toDouble() * tempoHannWindow[i]
        tempoImag[i] = 0.0
      }

      fftRadix2(tempoReal, tempoImag)

      var lowMagSq = 0.0
      for (k in lowMinBin..lowMaxBin) {
        lowMagSq += tempoReal[k] * tempoReal[k] + tempoImag[k] * tempoImag[k]
      }
      var midMagSq = 0.0
      for (k in midMinBin..midMaxBin) {
        midMagSq += tempoReal[k] * tempoReal[k] + tempoImag[k] * tempoImag[k]
      }
      var highMagSq = 0.0
      for (k in highMinBin..highMaxBin) {
        highMagSq += tempoReal[k] * tempoReal[k] + tempoImag[k] * tempoImag[k]
      }

      // Log-compressed sub-band energies preserve kick, snare, and hi-hat transients together
      bandCurrentEnergy[0] = ln(1.0 + 12.0 * sqrt(lowMagSq / max(1, lowMaxBin - lowMinBin + 1)))
      bandCurrentEnergy[1] = ln(1.0 + 12.0 * sqrt(midMagSq / max(1, midMaxBin - midMinBin + 1)))
      bandCurrentEnergy[2] = ln(1.0 + 12.0 * sqrt(highMagSq / max(1, highMaxBin - highMinBin + 1)))

      if (h > 0) {
        var frameNovelty = 0.0
        for (b in 0 until SUB_BAND_COUNT) {
          val diff = bandCurrentEnergy[b] - bandPrevEnergy[b]
          val pos = if (diff > 0.0) diff else 0.0
          bandNovelty[b][h] = pos
          bandMeans[b] += pos
          frameNovelty += pos
        }
        combinedNovelty[h] = frameNovelty
      }

      for (b in 0 until SUB_BAND_COUNT) {
        bandPrevEnergy[b] = bandCurrentEnergy[b]
      }
    }

    val denomFrames = max(1, numHops - 1)
    for (b in 0 until SUB_BAND_COUNT) {
      bandMeans[b] /= denomFrames
    }

    var combinedMean = 0.0
    for (h in 1 until numHops) combinedMean += combinedNovelty[h]
    combinedMean /= denomFrames

    var onsets = 0
    val adaptiveThresh = max(1e-4, combinedMean * 2.05)
    var lastOnsetHop = -4
    for (h in 1 until numHops) {
      if (combinedNovelty[h] > adaptiveThresh && (h - lastOnsetHop) >= 3) {
        onsets++
        lastOnsetHop = h
      }
    }
    val onsetRate = if (durationSec > 0.0) onsets / durationSec else 0.0
    val roundedOnsetRate = round(onsetRate * 10.0) / 10.0

    val frameRate = sampleRate.toDouble() / TEMPO_HOP_SIZE
    val minBpm = 60
    val maxBpm = 176
    val minLag = max(12, floor(60.0 * frameRate / maxBpm).toInt())
    val maxLag = min(numHops / 2, ceil(60.0 * frameRate / minBpm).toInt())

    if (maxLag <= minLag) {
      return if (onsets >= 2 && onsetRate > 0.5) {
        val estBpm = (onsetRate * 60.0).coerceIn(70.0, 165.0)
        TempoEstimate(
          bpm = round(estBpm * 10.0) / 10.0,
          confidenceLabel = "estimate",
          confidenceScore = 0.25,
          onsetRate = roundedOnsetRate,
          isEstimate = true
        )
      } else {
        TempoEstimate(
          bpm = null,
          confidenceLabel = "unread",
          confidenceScore = 0.0,
          onsetRate = roundedOnsetRate,
          isEstimate = false
        )
      }
    }

    val bandZeroEnergy = DoubleArray(SUB_BAND_COUNT)
    var totalActiveBandVar = 0.0
    for (b in 0 until SUB_BAND_COUNT) {
      var sumSqZero = 0.0
      val curve = bandNovelty[b]
      val mean = bandMeans[b]
      for (i in 0 until numHops) {
        val c = curve[i] - mean
        sumSqZero += c * c
      }
      bandZeroEnergy[b] = sumSqZero
      if (sumSqZero > 1e-10) {
        totalActiveBandVar += sumSqZero
      }
    }

    if (totalActiveBandVar <= 1e-10) {
      return TempoEstimate(
        bpm = null,
        confidenceLabel = "unread",
        confidenceScore = 0.0,
        onsetRate = roundedOnsetRate,
        isEstimate = false
      )
    }

    val bandWeights = DoubleArray(SUB_BAND_COUNT)
    val bandPriors = doubleArrayOf(0.45, 0.35, 0.20)
    var weightSum = 0.0
    for (b in 0 until SUB_BAND_COUNT) {
      if (bandZeroEnergy[b] > 1e-10) {
        val w = bandPriors[b] * (0.35 + 0.65 * (bandZeroEnergy[b] / totalActiveBandVar))
        bandWeights[b] = w
        weightSum += w
      }
    }
    if (weightSum <= 1e-12) weightSum = 1.0
    for (b in 0 until SUB_BAND_COUNT) {
      bandWeights[b] /= weightSum
    }

    val combinedAcf = DoubleArray(maxLag + 2)
    var bestLag = minLag
    var bestScore = -Double.MAX_VALUE

    for (lag in minLag..maxLag) {
      val count = numHops - lag
      var combinedNorm = 0.0
      for (b in 0 until SUB_BAND_COUNT) {
        val w = bandWeights[b]
        if (w <= 0.0) continue
        val curve = bandNovelty[b]
        val mean = bandMeans[b]
        var corr = 0.0
        for (i in 0 until count) {
          corr += (curve[i] - mean) * (curve[i + lag] - mean)
        }
        val normB = (corr * numHops) / (count.toDouble() * bandZeroEnergy[b])
        combinedNorm += w * normB
      }

      combinedAcf[lag] = combinedNorm
      val candidateBpm = 60.0 * frameRate / lag
      // Gentle tempo pocket prior prevents locking onto half-tempo subharmonics
      val priorWeight = exp(-0.5 * (ln(candidateBpm / 120.0) / 0.42).pow(2.0))
      val weighted = combinedNorm * (0.75 + 0.25 * priorWeight)
      if (weighted > bestScore) {
        bestScore = weighted
        bestLag = lag
      }
    }

    val y0 = combinedAcf[(bestLag - 1).coerceAtLeast(minLag)]
    val y1 = combinedAcf[bestLag]
    val y2 = combinedAcf[(bestLag + 1).coerceAtMost(maxLag)]
    val denom = 2.0 * (y0 - 2.0 * y1 + y2)
    val delta = if (abs(denom) > 1e-9) ((y0 - y2) / denom).coerceIn(-0.5, 0.5) else 0.0
    var refinedBpm = 60.0 * frameRate / (bestLag + delta)
    if (refinedBpm.isNaN() || refinedBpm.isInfinite() || refinedBpm <= 0.0) {
      return TempoEstimate(
        bpm = null,
        confidenceLabel = "unread",
        confidenceScore = 0.0,
        onsetRate = roundedOnsetRate,
        isEstimate = false
      )
    }

    while (refinedBpm < 65.0) refinedBpm *= 2.0
    while (refinedBpm > 175.0) refinedBpm /= 2.0

    val peakNorm = y1.coerceIn(0.0, 1.0)
    val durationFactor = (durationSec / 6.0).coerceIn(0.25, 1.0)
    val confidenceScore = (peakNorm * 0.85 + durationFactor * 0.15).coerceIn(0.0, 1.0)
    val confidenceLabel = when {
      peakNorm >= 0.35 && durationSec >= 3.0 -> "high"
      peakNorm >= 0.16 -> "medium"
      else -> "estimate"
    }

    return TempoEstimate(
      bpm = round(refinedBpm * 10.0) / 10.0,
      confidenceLabel = confidenceLabel,
      confidenceScore = round(confidenceScore * 1000.0) / 1000.0,
      onsetRate = roundedOnsetRate,
      isEstimate = confidenceLabel == "estimate"
    )
  }

  internal fun estimateTempo(samples: FloatArray, sampleRate: Int): Triple<Double?, String, Double> {
    val est = estimateTempoDetailed(samples, sampleRate)
    return Triple(est.bpm, est.confidenceLabel, est.onsetRate)
  }

  private fun rankBestLane(
    bpm: Double?,
    centroid: Double,
    lowShare: Double,
    density: Double,
    spectralMeasured: Boolean
  ): String {
    if (bpm == null && !spectralMeasured) return "Trap"
    if (bpm == null) {
      return when {
        lowShare > 0.45 && centroid < 2200 -> "Memphis phonk / dark trap"
        centroid < 2000 -> "Boom bap"
        lowShare > 0.42 -> "Trap"
        centroid > 3200 -> "Detroit trap"
        else -> "Trap"
      }
    }
    return when {
      bpm in 130.0..155.0 && lowShare > 0.40 -> "Trap"
      bpm in 135.0..150.0 && centroid < 2500 -> "NY drill"
      bpm in 136.0..148.0 && density > 2.5 -> "UK drill"
      bpm in 82.0..98.0 && centroid < 2200 -> "Boom bap"
      bpm in 90.0..115.0 && centroid > 3200 -> "Milwaukee low-end"
      bpm in 95.0..118.0 && density > 2.8 -> "DMV crank"
      bpm in 85.0..105.0 -> "West Coast / G-funk"
      bpm in 120.0..145.0 && centroid < 2000 -> "Memphis phonk / dark trap"
      bpm in 140.0..170.0 -> "Detroit scam-rap pocket"
      bpm in 65.0..85.0 -> "Chopped & screwed"
      else -> "Trap"
    }
  }

  /**
   * Decodes any Android content URI audio stream using its container sample rate and channel count,
   * downmixing interleaved multi-channel PCM cleanly into mono Floats.
   */
  private fun decodeAudioUri(uri: Uri): DecodedAudio {
    if (uri == Uri.EMPTY) return DecodedAudio(FloatArray(0), 44100)
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
      extractor.setDataSource(context, uri, null)
      var audioTrackIndex = -1
      var format: MediaFormat? = null

      for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
          audioTrackIndex = i
          format = f
          break
        }
      }

      if (audioTrackIndex < 0 || format == null) return DecodedAudio(FloatArray(0), 44100)
      extractor.selectTrack(audioTrackIndex)

      val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
      var decodedSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
        format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
      } else 44100
      var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
      } else 1

      val activeCodec = MediaCodec.createDecoderByType(mime).also { codec = it }
      activeCodec.configure(format, null, null, 0)
      activeCodec.start()

      val maxMonoSamples = decodedSampleRate * 30
      val outputFloats = ArrayList<Float>(min(maxMonoSamples, 44100 * 15))
      val info = MediaCodec.BufferInfo()
      var isInputEOS = false
      var isOutputEOS = false
      var idleIterations = 0
      val timeoutUs = 5000L

      while (!isOutputEOS && outputFloats.size < maxMonoSamples && idleIterations < 400) {
        var madeProgress = false
        if (!isInputEOS) {
          val inIndex = activeCodec.dequeueInputBuffer(timeoutUs)
          if (inIndex >= 0) {
            madeProgress = true
            val buffer = activeCodec.getInputBuffer(inIndex)
            val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
            if (sampleSize < 0) {
              activeCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              isInputEOS = true
            } else {
              activeCodec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }

        val outIndex = activeCodec.dequeueOutputBuffer(info, timeoutUs)
        when {
          outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            madeProgress = true
            val outFmt = activeCodec.outputFormat
            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              decodedSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
            }
            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            }
          }
          outIndex >= 0 -> {
            madeProgress = true
            val outBuffer = activeCodec.getOutputBuffer(outIndex)
            if (outBuffer != null && info.size > 0) {
              outBuffer.position(info.offset)
              outBuffer.limit(info.offset + info.size)
              val dup = outBuffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
              val frameLimit = decodedSampleRate * 30
              while (dup.remaining() >= channelCount && outputFloats.size < frameLimit) {
                var sum = 0f
                for (ch in 0 until channelCount) {
                  sum += dup.get() / 32768.0f
                }
                outputFloats.add(sum / channelCount)
              }
            }
            activeCodec.releaseOutputBuffer(outIndex, false)
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              isOutputEOS = true
            }
          }
        }

        if (madeProgress) {
          idleIterations = 0
        } else {
          idleIterations++
        }
      }

      DecodedAudio(outputFloats.toFloatArray(), decodedSampleRate)
    } catch (e: Exception) {
      e.printStackTrace()
      DecodedAudio(FloatArray(0), 44100)
    } finally {
      try { codec?.stop() } catch (_: Exception) {}
      try { codec?.release() } catch (_: Exception) {}
      try { extractor.release() } catch (_: Exception) {}
    }
  }

  private fun fftRadix2(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 0 until n - 1) {
      if (i < j) {
        val tr = re[i]
        re[i] = re[j]
        re[j] = tr
        val ti = im[i]
        im[i] = im[j]
        im[j] = ti
      }
      var k = n shr 1
      while (k <= j) {
        j -= k
        k = k shr 1
      }
      j += k
    }

    var len = 2
    while (len <= n) {
      val ang = -2.0 * PI / len
      val wlenR = cos(ang)
      val wlenI = sin(ang)
      val half = len shr 1
      var i = 0
      while (i < n) {
        var wR = 1.0
        var wI = 0.0
        for (k in 0 until half) {
          val a = i + k
          val b = a + half
          val uR = re[a]
          val uI = im[a]
          val vR = re[b] * wR - im[b] * wI
          val vI = re[b] * wI + im[b] * wR
          re[a] = uR + vR
          im[a] = uI + vI
          re[b] = uR - vR
          im[b] = uI - vI
          val nwR = wR * wlenR - wI * wlenI
          wI = wR * wlenI + wI * wlenR
          wR = nwR
        }
        i += len
      }
      len = len shl 1
    }
  }

  private fun unreadResult(
    fileName: String,
    reason: String,
    durationSec: Double = 0.0
  ): AudioAnalysisResult {
    return AudioAnalysisResult(
      fileName = fileName,
      durationSec = durationSec,
      bpm = null,
      bpmConfidence = "unread",
      keyName = null,
      keyClear = false,
      rmsDb = -80.0,
      peakDb = -80.0,
      lowShare = 0.0,
      centroidHz = 0.0,
      onsetRate = 0.0,
      suggestedLane = "Trap",
      pocketLabel = "Unread · No decodable audio",
      promptPreserve = "$LOCK_LINE. Add drums underneath only, spice only, sample stays lead.",
      promptOriginal = "Instrumental beat, heavy 808, crisp percussion, full arrangement.",
      waveformPeaks = emptyList(),
      key = null,
      mode = null,
      bpmConfidenceScore = 0.0,
      keyConfidenceScore = 0.0,
      isAudioMeasured = false,
      isSpectralMeasured = false,
      isTempoEstimate = false,
      measuredSpectrumBands = emptyList(),
      analysisNote = reason
    )
  }
}
