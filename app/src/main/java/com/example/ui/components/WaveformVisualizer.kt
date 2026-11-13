package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PlaybackMath
import com.example.ui.theme.*
import kotlinx.coroutines.delay

// Top-level named tuning & boundary constants
const val WAVEFORM_MIN_AMPLITUDE = 0.04f
const val WAVEFORM_MAX_AMPLITUDE = 1.0f
const val WAVEFORM_CLIP_THRESHOLD = 0.88f
const val WAVEFORM_HIGH_THRESHOLD = 0.60f
const val WAVEFORM_MIN_SENSITIVITY = 0.25f
const val WAVEFORM_MAX_SENSITIVITY = 4.0f
const val WAVEFORM_SENSITIVITY_STEP = 0.05f
const val WAVEFORM_DEFAULT_SENSITIVITY = 1.0f

const val PEAK_HOLD_DURATION_MS = 500L
const val PEAK_GRAVITY_ACCELERATION = 0.000006f
const val PEAK_INITIAL_FALL_VELOCITY = 0.00025f
const val PEAK_MIN_VISIBLE_DELTA = 0.015f

const val MAX_WAVEFORM_BARS = 128
const val BRUSH_HEIGHT_BUCKETS = 24
const val LANE_TINT_BLEND_FACTOR = 0.22f

enum class WaveformStyle {
  MIRRORED_GLOW,
  BARS,
  OSCILLOSCOPE
}

/**
 * Tuning parameters for trailing peak-hold markers and gravity decay.
 */
object PeakHoldConstants {
  const val DEFAULT_HOLD_TIME_MS: Long = PEAK_HOLD_DURATION_MS
  const val GRAVITY_ACCELERATION: Float = PEAK_GRAVITY_ACCELERATION
  const val INITIAL_FALL_VELOCITY: Float = PEAK_INITIAL_FALL_VELOCITY
  const val MIN_PEAK_THRESHOLD: Float = WAVEFORM_MIN_AMPLITUDE
}

/**
 * Resolves a subtle accent tint for any of the 41 studio subgenre lanes.
 * Falls back to null (pure StudioCyan-to-AmberGold default) when lane is empty or unrecognized.
 */
fun resolveLaneAccentColor(lane: String): Color? {
  if (lane.isBlank()) return null
  val lower = lane.lowercase()
  return when {
    lower.contains("gospel") || lower.contains("soul") -> Color(0xFFFDE047) // Sanctuary Sunburst
    lower.contains("drill") || lower.contains("grime") -> Color(0xFF38BDF8) // Ice Cyan
    lower.contains("phonk") || lower.contains("rage") || lower.contains("horror") ||
      lower.contains("digicore") || lower.contains("tread") -> Color(0xFFFB923C) // Electric Coral-Amber
    lower.contains("boom") || lower.contains("jazz") || lower.contains("east coast") -> Color(0xFFFBBF24) // Vintage Honey
    lower.contains("cloud") || lower.contains("plugg") || lower.contains("west coast") ||
      lower.contains("g-funk") || lower.contains("trap soul") -> Color(0xFF2DD4BF) // Seafoam Cyan
    lower.contains("afro") || lower.contains("latin") || lower.contains("amapiano") ||
      lower.contains("bounce") || lower.contains("dmv") || lower.contains("jerk") -> Color(0xFF22D3EE) // Tropical Cyan
    lower.contains("trap") || lower.contains("south") || lower.contains("detroit") ||
      lower.contains("florida") || lower.contains("texas") || lower.contains("milwaukee") ||
      lower.contains("jersey") || lower.contains("chopped") -> Color(0xFFFFB74D) // Warm Studio Amber
    else -> null
  }
}

/**
 * Zero-allocation tracking engine for trailing peak-hold markers.
 * Maintains state in pre-allocated primitive arrays for 60 FPS performance without GC pauses.
 */
class PeakHoldTracker(val maxCapacity: Int = MAX_WAVEFORM_BARS) {
  val peakAmplitudes = FloatArray(maxCapacity)
  val peakHoldUntilMs = LongArray(maxCapacity)
  val peakVelocities = FloatArray(maxCapacity)
  var lastUpdateTimestamp: Long = 0L
  private var activeCount: Int = 0
  private var elevatedFlag: Boolean = false

  fun hasActivePeaks(): Boolean = activeCount > 0 || lastUpdateTimestamp != 0L

  fun hasElevatedPeaks(): Boolean = elevatedFlag

  fun beginFrame(currentTimeMs: Long = System.currentTimeMillis()): Long {
    val dt = if (lastUpdateTimestamp > 0L) (currentTimeMs - lastUpdateTimestamp).coerceIn(1L, 100L) else 16L
    lastUpdateTimestamp = currentTimeMs
    elevatedFlag = false
    return dt
  }

  /**
   * Zero-allocation per-bar peak update invoked directly inside the Canvas draw loop.
   */
  fun updateSingleBar(
    index: Int,
    scaledAmp: Float,
    currentTimeMs: Long,
    dt: Long,
    holdDurationMs: Long = PEAK_HOLD_DURATION_MS
  ) {
    if (index < 0 || index >= maxCapacity) return
    if (index + 1 > activeCount) activeCount = index + 1

    val clampedAmp = scaledAmp.coerceIn(WAVEFORM_MIN_AMPLITUDE, WAVEFORM_MAX_AMPLITUDE)
    val currentPeak = peakAmplitudes[index]

    if (clampedAmp >= currentPeak) {
      peakAmplitudes[index] = clampedAmp
      peakHoldUntilMs[index] = currentTimeMs + holdDurationMs
      peakVelocities[index] = 0f
    } else {
      if (currentTimeMs >= peakHoldUntilMs[index]) {
        val nextVel = if (peakVelocities[index] == 0f) {
          PEAK_INITIAL_FALL_VELOCITY + (PEAK_GRAVITY_ACCELERATION * dt)
        } else {
          peakVelocities[index] + (PEAK_GRAVITY_ACCELERATION * dt)
        }
        peakVelocities[index] = nextVel
        val decayedPeak = peakAmplitudes[index] - (nextVel * dt)
        peakAmplitudes[index] = decayedPeak.coerceAtLeast(clampedAmp)
      }
      if (peakAmplitudes[index] > clampedAmp + PEAK_MIN_VISIBLE_DELTA) {
        elevatedFlag = true
      }
    }
  }

  /**
   * Batch FloatArray update with peakHoldEnabled flag check.
   */
  fun update(
    amplitudes: FloatArray,
    sensitivity: Float,
    peakHoldEnabled: Boolean = true,
    currentTimeMs: Long = System.currentTimeMillis(),
    holdDurationMs: Long = PEAK_HOLD_DURATION_MS
  ) {
    if (!peakHoldEnabled) {
      if (hasActivePeaks()) reset()
      return
    }
    val count = amplitudes.size.coerceAtMost(maxCapacity)
    val dt = beginFrame(currentTimeMs)
    activeCount = count
    for (i in 0 until count) {
      val raw = amplitudes[i]
      val safeRaw = if (raw.isNaN()) WAVEFORM_MIN_AMPLITUDE else raw
      val scaledAmp = (safeRaw * sensitivity).coerceIn(WAVEFORM_MIN_AMPLITUDE, WAVEFORM_MAX_AMPLITUDE)
      updateSingleBar(i, scaledAmp, currentTimeMs, dt, holdDurationMs)
    }
  }

  /**
   * Convenience batch List<Float> update (used by unit tests).
   */
  fun update(
    amplitudes: List<Float>,
    sensitivity: Float,
    peakHoldEnabled: Boolean = true,
    currentTimeMs: Long = System.currentTimeMillis(),
    holdDurationMs: Long = PEAK_HOLD_DURATION_MS
  ) {
    if (!peakHoldEnabled) {
      if (hasActivePeaks()) reset()
      return
    }
    val count = amplitudes.size.coerceAtMost(maxCapacity)
    val dt = beginFrame(currentTimeMs)
    activeCount = count
    for (i in 0 until count) {
      val raw = amplitudes[i]
      val safeRaw = if (raw.isNaN()) WAVEFORM_MIN_AMPLITUDE else raw
      val scaledAmp = (safeRaw * sensitivity).coerceIn(WAVEFORM_MIN_AMPLITUDE, WAVEFORM_MAX_AMPLITUDE)
      updateSingleBar(i, scaledAmp, currentTimeMs, dt, holdDurationMs)
    }
  }

  fun reset() {
    peakAmplitudes.fill(0f)
    peakHoldUntilMs.fill(0L)
    peakVelocities.fill(0f)
    lastUpdateTimestamp = 0L
    activeCount = 0
    elevatedFlag = false
  }
}

/**
 * Computes the max-pooled amplitude for adaptive bar index [barIndex] out of [barCount] from [amplitudes]
 * without allocating any intermediate lists or arrays.
 */
private fun poolBarAmplitude(amplitudes: FloatArray, barIndex: Int, barCount: Int): Float {
  val size = amplitudes.size
  if (size == 0) return WAVEFORM_MIN_AMPLITUDE
  val start = (barIndex * size) / barCount
  val end = (((barIndex + 1) * size) / barCount).coerceAtLeast(start + 1).coerceAtMost(size)
  var peak = 0f
  for (idx in start until end) {
    val v = amplitudes[idx]
    if (!v.isNaN() && v > peak) peak = v
  }
  return peak
}

/**
 * High-Performance, Draw-Scoped Real-time Audio Waveform Visualizer.
 * Reads amplitudes, sensitivity, and peak-hold states strictly inside draw phase to guarantee
 * zero recomposition storms and zero per-frame allocations at 60 FPS.
 */
@Composable
fun RealtimeWaveformVisualizer(
  amplitudesProvider: () -> FloatArray,
  isLiveRecording: Boolean,
  sensitivityProvider: () -> Float = { WAVEFORM_DEFAULT_SENSITIVITY },
  peakHoldEnabledProvider: () -> Boolean = { true },
  playheadProgressProvider: (() -> Float)? = null,
  onSeek: ((Float) -> Unit)? = null,
  playbackDurationMs: Long = 0L,
  isPlayingAudio: Boolean = false,
  isLoopingAudio: Boolean = false,
  isScrubbingAudio: Boolean = false,
  onScrubStart: ((Float) -> Unit)? = null,
  onScrubPreview: ((Float) -> Unit)? = null,
  onScrubEnd: ((Float) -> Unit)? = null,
  activeLane: String = "",
  beatResetKey: Any? = null,
  currentPeakDb: Float = -60f,
  currentRmsDb: Float = -60f,
  bpm: Double? = null,
  durationSec: Double = 0.0,
  modifier: Modifier = Modifier
) {
  var visualizerStyle by rememberSaveable { mutableStateOf(WaveformStyle.MIRRORED_GLOW) }
  val inspectPositionXState = remember { mutableStateOf<Float?>(null) }
  val peakTracker = remember { PeakHoldTracker(MAX_WAVEFORM_BARS) }
  val frameTickState = remember { mutableLongStateOf(0L) }

  // Reset peak-hold buffers when recording stops, style switches, or a new beat analysis loads
  LaunchedEffect(isLiveRecording) {
    if (!isLiveRecording) {
      peakTracker.reset()
    }
  }

  LaunchedEffect(visualizerStyle) {
    peakTracker.reset()
  }

  LaunchedEffect(bpm, durationSec, beatResetKey) {
    peakTracker.reset()
  }

  // Smooth 60 FPS decay ticker ONLY while elevated peak caps are falling; 0 CPU when idle
  LaunchedEffect(peakTracker) {
    while (true) {
      if (peakTracker.hasElevatedPeaks() && peakHoldEnabledProvider()) {
        withFrameMillis { ms ->
          frameTickState.longValue = ms
        }
      } else {
        delay(32L)
      }
    }
  }

  // Pulsing dot animation for active mic capture
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(14.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
      .testTag("waveform_visualizer_card")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Header: Mode info & Display Style Switcher
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isLiveRecording) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(StudioMiss.copy(alpha = pulseAlpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "LIVE AUDIO WAVEFORM",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = StudioMiss
            )
          } else {
            Icon(
              imageVector = Icons.Default.GraphicEq,
              contentDescription = null,
              tint = AmberGold,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "BEAT GROOVE CONTOUR",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = AmberGold
            )
          }
        }

        // Style Switcher Buttons
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StudioBg)
            .padding(2.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          IconButton(
            onClick = { visualizerStyle = WaveformStyle.MIRRORED_GLOW },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(34.dp)
              .testTag("style_mirrored_glow")
          ) {
            Icon(
              imageVector = Icons.Default.Equalizer,
              contentDescription = "Mirrored Glow waveform style",
              tint = if (visualizerStyle == WaveformStyle.MIRRORED_GLOW) AmberGold else TextMuted,
              modifier = Modifier.size(16.dp)
            )
          }
          IconButton(
            onClick = { visualizerStyle = WaveformStyle.BARS },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(34.dp)
              .testTag("style_bars")
          ) {
            Icon(
              imageVector = Icons.Default.GraphicEq,
              contentDescription = "Standard bars waveform style",
              tint = if (visualizerStyle == WaveformStyle.BARS) StudioCyan else TextMuted,
              modifier = Modifier.size(16.dp)
            )
          }
          IconButton(
            onClick = { visualizerStyle = WaveformStyle.OSCILLOSCOPE },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(34.dp)
              .testTag("style_oscilloscope")
          ) {
            Icon(
              imageVector = Icons.Default.Timeline,
              contentDescription = "Oscilloscope waveform style",
              tint = if (visualizerStyle == WaveformStyle.OSCILLOSCOPE) StudioWinner else TextMuted,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // High-Performance Draw-Scoped Waveform Container wrapped in reusable Synchronized Scrubber Overlay
      WaveformScrubberOverlay(
        durationMs = playbackDurationMs,
        isPlaying = isPlayingAudio,
        isLooping = isLoopingAudio,
        isScrubbing = isScrubbingAudio,
        progressProvider = { playheadProgressProvider?.invoke() ?: 0f },
        onScrubStart = { frac -> onScrubStart?.invoke(frac) },
        onScrubPreview = { frac -> onScrubPreview?.invoke(frac) },
        onScrubEnd = { frac -> onScrubEnd?.invoke(frac) ?: onSeek?.invoke(frac) },
        onTapSeek = { frac -> onSeek?.invoke(frac) },
        enabled = !isLiveRecording && onSeek != null && playheadProgressProvider != null && playbackDurationMs > 0L,
        accentColor = AmberGold,
        compact = false,
        inspectionXState = inspectPositionXState,
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(StudioBg)
          .border(0.5.dp, StudioBorder, RoundedCornerShape(8.dp))
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f

            // Adaptive bar count determined once per size change
            val targetSlotPx = 5.dp.toPx().coerceAtLeast(4f)
            val adaptiveBarCount = (canvasWidth / targetSlotPx).toInt().coerceIn(16, MAX_WAVEFORM_BARS)
            val totalSpacing = canvasWidth * 0.22f
            val barWidth = ((canvasWidth - totalSpacing) / adaptiveBarCount).coerceAtLeast(2f)
            val gap = totalSpacing / (adaptiveBarCount - 1).coerceAtLeast(1)

            // Resolve 41-lane subtle tint blended into the Studio Cyan-to-Amber palette
            val laneTint = resolveLaneAccentColor(activeLane)
            val centerColor = if (laneTint != null) lerp(StudioCyan, laneTint, LANE_TINT_BLEND_FACTOR * 0.7f) else StudioCyan
            val baseEdgeColor = if (laneTint != null) lerp(AmberGoldLight, laneTint, LANE_TINT_BLEND_FACTOR) else AmberGoldLight
            val highEdgeColor = if (laneTint != null) lerp(AmberGold, laneTint, LANE_TINT_BLEND_FACTOR) else AmberGold
            val clipEdgeColor = StudioMiss // Red tier strictly preserved above clip threshold

            val centerGlowColor = centerColor.copy(alpha = 0.40f)
            val baseGlowEdge = baseEdgeColor.copy(alpha = 0.30f)
            val highGlowEdge = highEdgeColor.copy(alpha = 0.30f)
            val clipGlowEdge = clipEdgeColor.copy(alpha = 0.34f)

            // Pre-allocate quantized vertical Gradient Brushes across height buckets (0 allocations in draw loop)
            val barBrushesBase = Array(BRUSH_HEIGHT_BUCKETS) { step ->
              val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
              val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(3f)
              Brush.verticalGradient(
                colorStops = arrayOf(0f to baseEdgeColor, 0.5f to centerColor, 1f to baseEdgeColor),
                startY = centerY - hh,
                endY = centerY + hh
              )
            }
            val barBrushesHigh = Array(BRUSH_HEIGHT_BUCKETS) { step ->
              val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
              val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(3f)
              Brush.verticalGradient(
                colorStops = arrayOf(0f to highEdgeColor, 0.5f to centerColor, 1f to highEdgeColor),
                startY = centerY - hh,
                endY = centerY + hh
              )
            }
            val barBrushesClip = Array(BRUSH_HEIGHT_BUCKETS) { step ->
              val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
              val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(3f)
              Brush.verticalGradient(
                colorStops = arrayOf(0f to clipEdgeColor, 0.5f to centerColor, 1f to clipEdgeColor),
                startY = centerY - hh,
                endY = centerY + hh
              )
            }

            val glowBrushesBase = Array(BRUSH_HEIGHT_BUCKETS) { step ->
              val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
              val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(3f)
              Brush.verticalGradient(
                colorStops = arrayOf(0f to baseGlowEdge, 0.5f to centerGlowColor, 1f to baseGlowEdge),
                startY = centerY - hh - 2f,
                endY = centerY + hh + 2f
              )
            }
            val glowBrushesHigh = Array(BRUSH_HEIGHT_BUCKETS) { step ->
              val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
              val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(3f)
              Brush.verticalGradient(
                colorStops = arrayOf(0f to highGlowEdge, 0.5f to centerGlowColor, 1f to highGlowEdge),
                startY = centerY - hh - 2f,
                endY = centerY + hh + 2f
              )
            }
            val glowBrushesClip = Array(BRUSH_HEIGHT_BUCKETS) { step ->
              val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
              val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(3f)
              Brush.verticalGradient(
                colorStops = arrayOf(0f to clipGlowEdge, 0.5f to centerGlowColor, 1f to clipGlowEdge),
                startY = centerY - hh - 2f,
                endY = centerY + hh + 2f
              )
            }

            // Pre-cached colors, Path, and Strokes
            val gridLineColor = StudioBorder.copy(alpha = 0.5f)
            val centerlineColor = if (isLiveRecording) StudioMiss.copy(alpha = 0.35f) else centerColor.copy(alpha = 0.25f)
            val fourBarColor = highEdgeColor.copy(alpha = 0.6f)
            val capLineColor = Color.White.copy(alpha = 0.95f)
            val capGlowCyan = centerColor.copy(alpha = 0.38f)
            val capGlowAmber = highEdgeColor.copy(alpha = 0.38f)
            val capGlowClip = clipEdgeColor.copy(alpha = 0.42f)
            val oscGlowColor = (if (isLiveRecording) StudioMiss else highEdgeColor).copy(alpha = 0.3f)
            val oscLineColor = if (isLiveRecording) StudioMiss else centerColor
            val oscPath = Path()
            val oscGlowStroke = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val oscLineStroke = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val barCornerRadius = CornerRadius(2f, 2f)
            val glowCornerRadius = CornerRadius(3f, 3f)
            val capCornerRadius = CornerRadius(1f, 1f)
            val capGlowCornerRadius = CornerRadius(1.5f, 1.5f)

            onDrawBehind {
              // Observe animation tick inside draw scope only
              @Suppress("UNUSED_VARIABLE")
              val tick = frameTickState.longValue

              val amplitudes = amplitudesProvider()
              val sensitivity = sensitivityProvider()
              val peakHoldEnabled = peakHoldEnabledProvider()

              // 1. Background grid lines
              val numGridLines = 8
              val gridSpacing = canvasWidth / numGridLines
              for (g in 1 until numGridLines) {
                val gx = g * gridSpacing
                drawLine(
                  color = gridLineColor,
                  start = Offset(gx, 0f),
                  end = Offset(gx, canvasHeight),
                  strokeWidth = 1f
                )
              }

              // Centerline (Zero-crossing)
              drawLine(
                color = centerlineColor,
                start = Offset(0f, centerY),
                end = Offset(canvasWidth, centerY),
                strokeWidth = 1.2f
              )

              // 2. Beat bar markers if BPM & Duration are known
              if (!isLiveRecording && bpm != null && bpm > 0 && durationSec > 0) {
                val totalBeats = (bpm / 60.0) * durationSec
                val totalBars = (totalBeats / 4.0).toInt()
                if (totalBars in 2..64) {
                  val barSpacing = canvasWidth / totalBars
                  for (bar in 1 until totalBars) {
                    val isFourBar = bar % 4 == 0
                    val barX = bar * barSpacing
                    drawLine(
                      color = if (isFourBar) fourBarColor else StudioBorder,
                      start = Offset(barX, if (isFourBar) 4f else centerY - 15f),
                      end = Offset(barX, if (isFourBar) canvasHeight - 4f else centerY + 15f),
                      strokeWidth = if (isFourBar) 2f else 1f
                    )
                  }
                }
              }

              if (amplitudes.isEmpty()) return@onDrawBehind

              val nowMs = System.currentTimeMillis()
              val dt = if (peakHoldEnabled) {
                peakTracker.beginFrame(nowMs)
              } else {
                if (peakTracker.hasActivePeaks()) peakTracker.reset()
                0L
              }

              // 3. Render Waveform (0 allocations in draw loop)
              when (visualizerStyle) {
                WaveformStyle.MIRRORED_GLOW -> {
                  for (i in 0 until adaptiveBarCount) {
                    val rawAmp = poolBarAmplitude(amplitudes, i, adaptiveBarCount)
                    val amp = (rawAmp * sensitivity).coerceIn(WAVEFORM_MIN_AMPLITUDE, WAVEFORM_MAX_AMPLITUDE)
                    val halfHeight = (amp * (canvasHeight * 0.44f)).coerceAtLeast(3f)
                    val x = i * (barWidth + gap)
                    val topY = centerY - halfHeight
                    val barHeight = halfHeight * 2f

                    val bucket = (((amp - WAVEFORM_MIN_AMPLITUDE) / (1f - WAVEFORM_MIN_AMPLITUDE)) * (BRUSH_HEIGHT_BUCKETS - 1))
                      .toInt()
                      .coerceIn(0, BRUSH_HEIGHT_BUCKETS - 1)

                    val glowBrush = when {
                      amp > WAVEFORM_CLIP_THRESHOLD -> glowBrushesClip[bucket]
                      amp > WAVEFORM_HIGH_THRESHOLD -> glowBrushesHigh[bucket]
                      else -> glowBrushesBase[bucket]
                    }
                    val barBrush = when {
                      amp > WAVEFORM_CLIP_THRESHOLD -> barBrushesClip[bucket]
                      amp > WAVEFORM_HIGH_THRESHOLD -> barBrushesHigh[bucket]
                      else -> barBrushesBase[bucket]
                    }

                    // Outer soft glow pass
                    drawRoundRect(
                      brush = glowBrush,
                      topLeft = Offset(x - 1f, topY - 1f),
                      size = Size(barWidth + 2f, barHeight + 2f),
                      cornerRadius = glowCornerRadius
                    )

                    // Main mirrored Cyan-to-Amber/Red bar
                    drawRoundRect(
                      brush = barBrush,
                      topLeft = Offset(x, topY),
                      size = Size(barWidth, barHeight),
                      cornerRadius = barCornerRadius
                    )

                    // Trailing Peak-Hold Caps (mirrored top & bottom) only when toggle is ON
                    if (peakHoldEnabled) {
                      peakTracker.updateSingleBar(i, amp, nowMs, dt)
                      val peakAmp = peakTracker.peakAmplitudes[i]
                      if (peakAmp > amp + PEAK_MIN_VISIBLE_DELTA) {
                        val peakHalfHeight = (peakAmp * (canvasHeight * 0.44f)).coerceAtLeast(3f)
                        val markerTopY = (centerY - peakHalfHeight - 2.5f).coerceAtLeast(1f)
                        val markerBottomY = (centerY + peakHalfHeight + 0.5f).coerceAtMost(canvasHeight - 3f)

                        val capGlowColor = when {
                          peakAmp > WAVEFORM_CLIP_THRESHOLD -> capGlowClip
                          peakAmp > WAVEFORM_HIGH_THRESHOLD -> capGlowAmber
                          else -> capGlowCyan
                        }

                        // Top Cap Glow & Thin Line
                        drawRoundRect(
                          color = capGlowColor,
                          topLeft = Offset(x - 0.5f, markerTopY - 0.5f),
                          size = Size(barWidth + 1f, 3f),
                          cornerRadius = capGlowCornerRadius
                        )
                        drawRoundRect(
                          color = capLineColor,
                          topLeft = Offset(x, markerTopY),
                          size = Size(barWidth, 2f),
                          cornerRadius = capCornerRadius
                        )

                        // Bottom Mirrored Cap Glow & Thin Line
                        drawRoundRect(
                          color = capGlowColor,
                          topLeft = Offset(x - 0.5f, markerBottomY - 0.5f),
                          size = Size(barWidth + 1f, 3f),
                          cornerRadius = capGlowCornerRadius
                        )
                        drawRoundRect(
                          color = capLineColor,
                          topLeft = Offset(x, markerBottomY),
                          size = Size(barWidth, 2f),
                          cornerRadius = capCornerRadius
                        )
                      }
                    }
                  }
                }

                WaveformStyle.BARS -> {
                  for (i in 0 until adaptiveBarCount) {
                    val rawAmp = poolBarAmplitude(amplitudes, i, adaptiveBarCount)
                    val amp = (rawAmp * sensitivity).coerceIn(WAVEFORM_MIN_AMPLITUDE, WAVEFORM_MAX_AMPLITUDE)
                    val barHeight = (amp * (canvasHeight * 0.88f)).coerceAtLeast(4f)
                    val x = i * (barWidth + gap)
                    val topY = centerY - (barHeight / 2f)

                    val barColor = when {
                      amp > WAVEFORM_CLIP_THRESHOLD -> clipEdgeColor
                      amp > WAVEFORM_HIGH_THRESHOLD -> highEdgeColor
                      amp > 0.35f -> baseEdgeColor
                      else -> centerColor
                    }

                    drawRoundRect(
                      color = barColor,
                      topLeft = Offset(x, topY),
                      size = Size(barWidth, barHeight),
                      cornerRadius = barCornerRadius
                    )

                    if (peakHoldEnabled) {
                      peakTracker.updateSingleBar(i, amp, nowMs, dt)
                      val peakAmp = peakTracker.peakAmplitudes[i]
                      if (peakAmp > amp + PEAK_MIN_VISIBLE_DELTA) {
                        val peakBarHeight = (peakAmp * (canvasHeight * 0.88f)).coerceAtLeast(4f)
                        val peakTopY = (centerY - (peakBarHeight / 2f) - 2.5f).coerceAtLeast(1f)
                        val peakBottomY = (centerY + (peakBarHeight / 2f) + 0.5f).coerceAtMost(canvasHeight - 3f)

                        drawRoundRect(
                          color = capLineColor,
                          topLeft = Offset(x, peakTopY),
                          size = Size(barWidth, 2f),
                          cornerRadius = capCornerRadius
                        )
                        drawRoundRect(
                          color = capLineColor,
                          topLeft = Offset(x, peakBottomY),
                          size = Size(barWidth, 2f),
                          cornerRadius = capCornerRadius
                        )
                      }
                    }
                  }
                }

                WaveformStyle.OSCILLOSCOPE -> {
                  oscPath.rewind()
                  val pointCount = adaptiveBarCount.coerceAtMost(amplitudes.size).coerceAtLeast(2)
                  val stepX = canvasWidth / (pointCount - 1).coerceAtLeast(1)

                  for (i in 0 until pointCount) {
                    val rawAmp = poolBarAmplitude(amplitudes, i, pointCount)
                    val amp = (rawAmp * sensitivity).coerceIn(WAVEFORM_MIN_AMPLITUDE, WAVEFORM_MAX_AMPLITUDE)
                    val polarity = if (i % 2 == 0) 1f else -1f
                    val y = centerY - (amp * (canvasHeight * 0.45f) * polarity)
                    val x = i * stepX

                    if (i == 0) {
                      oscPath.moveTo(x, y)
                    } else {
                      val prevRaw = poolBarAmplitude(amplitudes, i - 1, pointCount)
                      val prevAmp = (prevRaw * sensitivity).coerceIn(WAVEFORM_MIN_AMPLITUDE, WAVEFORM_MAX_AMPLITUDE)
                      val prevPolarity = if ((i - 1) % 2 == 0) 1f else -1f
                      val prevY = centerY - (prevAmp * (canvasHeight * 0.45f) * prevPolarity)
                      val prevX = (i - 1) * stepX
                      val cx = (prevX + x) * 0.5f
                      oscPath.cubicTo(cx, prevY, cx, y, x, y)
                    }
                  }

                  drawPath(path = oscPath, color = oscGlowColor, style = oscGlowStroke)
                  drawPath(path = oscPath, color = oscLineColor, style = oscLineStroke)
                }
              }
            }
          }
          .testTag("waveform_canvas")
        ) {}
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Footer Readouts: Real-time dB metrics or groove legend
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (isLiveRecording) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Peak: ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
              "${currentPeakDb.toInt()} dBFS",
              style = MonospaceMetric.copy(fontSize = 11.sp),
              color = if (currentPeakDb > -3f) StudioMiss else TextPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("RMS: ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
              "${currentRmsDb.toInt()} dBFS",
              style = MonospaceMetric.copy(fontSize = 11.sp),
              color = StudioCyan
            )
          }
          Text(
            text = "Streaming PCM 16-bit",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
          )
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendItem(color = AmberGold, label = "808 Transients")
            LegendItem(color = StudioCyan, label = "Groove Pocket")
            LegendItem(color = StudioMiss, label = "Peaks")
          }
          if (bpm != null && bpm > 0) {
            Text(
              text = "4-Bar Grid Active",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = AmberGoldLight
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LegendItem(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(7.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(color)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextMuted)
  }
}

/**
 * Compact Mirrored Glowing Waveform Meter for Build Groove Monitor and Settings Drawer preview strip.
 * Zero-recomposition, zero-allocation draw pipeline synchronized with sensitivity, peak-hold, and 41-lane tinting.
 */
@Composable
fun CompactWaveformMeter(
  amplitudesProvider: () -> FloatArray,
  sensitivityProvider: () -> Float = { WAVEFORM_DEFAULT_SENSITIVITY },
  peakHoldEnabledProvider: () -> Boolean = { true },
  playheadProgressProvider: (() -> Float)? = null,
  onSeek: ((Float) -> Unit)? = null,
  playbackDurationMs: Long = 0L,
  isPlayingAudio: Boolean = false,
  isLoopingAudio: Boolean = false,
  isScrubbingAudio: Boolean = false,
  onScrubStart: ((Float) -> Unit)? = null,
  onScrubPreview: ((Float) -> Unit)? = null,
  onScrubEnd: ((Float) -> Unit)? = null,
  activeLane: String = "",
  beatResetKey: Any? = null,
  modifier: Modifier = Modifier
) {
  val peakTracker = remember { PeakHoldTracker(MAX_WAVEFORM_BARS) }
  val frameTickState = remember { mutableLongStateOf(0L) }

  LaunchedEffect(beatResetKey) {
    peakTracker.reset()
  }

  LaunchedEffect(peakTracker) {
    while (true) {
      if (peakTracker.hasElevatedPeaks() && peakHoldEnabledProvider()) {
        withFrameMillis { ms ->
          frameTickState.longValue = ms
        }
      } else {
        delay(32L)
      }
    }
  }

  WaveformScrubberOverlay(
    durationMs = playbackDurationMs,
    isPlaying = isPlayingAudio,
    isLooping = isLoopingAudio,
    isScrubbing = isScrubbingAudio,
    progressProvider = { playheadProgressProvider?.invoke() ?: 0f },
    onScrubStart = { frac -> onScrubStart?.invoke(frac) },
    onScrubPreview = { frac -> onScrubPreview?.invoke(frac) },
    onScrubEnd = { frac -> onScrubEnd?.invoke(frac) ?: onSeek?.invoke(frac) },
    onTapSeek = { frac -> onSeek?.invoke(frac) },
    enabled = onSeek != null && playheadProgressProvider != null && playbackDurationMs > 0L,
    accentColor = AmberGold,
    compact = true,
    modifier = modifier
      .fillMaxWidth()
      .height(44.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(StudioBg)
      .border(0.5.dp, StudioBorder, RoundedCornerShape(6.dp))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f

        val targetSlotPx = 5.dp.toPx().coerceAtLeast(4f)
        val adaptiveBarCount = (canvasWidth / targetSlotPx).toInt().coerceIn(16, MAX_WAVEFORM_BARS)
        val totalSpacing = canvasWidth * 0.20f
        val barWidth = ((canvasWidth - totalSpacing) / adaptiveBarCount).coerceAtLeast(2f)
        val gap = totalSpacing / (adaptiveBarCount - 1).coerceAtLeast(1)

        val laneTint = resolveLaneAccentColor(activeLane)
        val centerColor = if (laneTint != null) lerp(StudioCyan, laneTint, LANE_TINT_BLEND_FACTOR * 0.7f) else StudioCyan
        val baseEdgeColor = if (laneTint != null) lerp(AmberGoldLight, laneTint, LANE_TINT_BLEND_FACTOR) else AmberGoldLight
        val highEdgeColor = if (laneTint != null) lerp(AmberGold, laneTint, LANE_TINT_BLEND_FACTOR) else AmberGold
        val clipEdgeColor = StudioMiss

        val centerGlowColor = centerColor.copy(alpha = 0.35f)
        val baseGlowEdge = baseEdgeColor.copy(alpha = 0.25f)
        val highGlowEdge = highEdgeColor.copy(alpha = 0.25f)
        val clipGlowEdge = clipEdgeColor.copy(alpha = 0.30f)

        val barBrushesBase = Array(BRUSH_HEIGHT_BUCKETS) { step ->
          val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
          val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(2.5f)
          Brush.verticalGradient(
            colorStops = arrayOf(0f to baseEdgeColor, 0.5f to centerColor, 1f to baseEdgeColor),
            startY = centerY - hh,
            endY = centerY + hh
          )
        }
        val barBrushesHigh = Array(BRUSH_HEIGHT_BUCKETS) { step ->
          val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
          val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(2.5f)
          Brush.verticalGradient(
            colorStops = arrayOf(0f to highEdgeColor, 0.5f to centerColor, 1f to highEdgeColor),
            startY = centerY - hh,
            endY = centerY + hh
          )
        }
        val barBrushesClip = Array(BRUSH_HEIGHT_BUCKETS) { step ->
          val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
          val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(2.5f)
          Brush.verticalGradient(
            colorStops = arrayOf(0f to clipEdgeColor, 0.5f to centerColor, 1f to clipEdgeColor),
            startY = centerY - hh,
            endY = centerY + hh
          )
        }

        val glowBrushesBase = Array(BRUSH_HEIGHT_BUCKETS) { step ->
          val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
          val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(2.5f)
          Brush.verticalGradient(
            colorStops = arrayOf(0f to baseGlowEdge, 0.5f to centerGlowColor, 1f to baseGlowEdge),
            startY = centerY - hh - 1f,
            endY = centerY + hh + 1f
          )
        }
        val glowBrushesHigh = Array(BRUSH_HEIGHT_BUCKETS) { step ->
          val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
          val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(2.5f)
          Brush.verticalGradient(
            colorStops = arrayOf(0f to highGlowEdge, 0.5f to centerGlowColor, 1f to highGlowEdge),
            startY = centerY - hh - 1f,
            endY = centerY + hh + 1f
          )
        }
        val glowBrushesClip = Array(BRUSH_HEIGHT_BUCKETS) { step ->
          val frac = WAVEFORM_MIN_AMPLITUDE + (step.toFloat() / (BRUSH_HEIGHT_BUCKETS - 1)) * (1f - WAVEFORM_MIN_AMPLITUDE)
          val hh = (frac * (canvasHeight * 0.44f)).coerceAtLeast(2.5f)
          Brush.verticalGradient(
            colorStops = arrayOf(0f to clipGlowEdge, 0.5f to centerGlowColor, 1f to clipGlowEdge),
            startY = centerY - hh - 1f,
            endY = centerY + hh + 1f
          )
        }

        val centerlineColor = centerColor.copy(alpha = 0.25f)
        val capLineColor = Color.White.copy(alpha = 0.92f)
        val glowCorner = CornerRadius(2f, 2f)
        val barCorner = CornerRadius(1.5f, 1.5f)
        val capCorner = CornerRadius(1f, 1f)

        onDrawBehind {
          @Suppress("UNUSED_VARIABLE")
          val tick = frameTickState.longValue

          val amplitudes = amplitudesProvider()
          val sensitivity = sensitivityProvider()
          val peakHoldEnabled = peakHoldEnabledProvider()

          drawLine(
            color = centerlineColor,
            start = Offset(0f, centerY),
            end = Offset(canvasWidth, centerY),
            strokeWidth = 1f
          )

          if (amplitudes.isEmpty()) return@onDrawBehind

          val nowMs = System.currentTimeMillis()
          val dt = if (peakHoldEnabled) {
            peakTracker.beginFrame(nowMs)
          } else {
            if (peakTracker.hasActivePeaks()) peakTracker.reset()
            0L
          }

          for (i in 0 until adaptiveBarCount) {
            val rawAmp = poolBarAmplitude(amplitudes, i, adaptiveBarCount)
            val amp = (rawAmp * sensitivity).coerceIn(WAVEFORM_MIN_AMPLITUDE, WAVEFORM_MAX_AMPLITUDE)
            val halfHeight = (amp * (canvasHeight * 0.44f)).coerceAtLeast(2.5f)
            val x = i * (barWidth + gap)
            val topY = centerY - halfHeight
            val barHeight = halfHeight * 2f

            val bucket = (((amp - WAVEFORM_MIN_AMPLITUDE) / (1f - WAVEFORM_MIN_AMPLITUDE)) * (BRUSH_HEIGHT_BUCKETS - 1))
              .toInt()
              .coerceIn(0, BRUSH_HEIGHT_BUCKETS - 1)

            val glowBrush = when {
              amp > WAVEFORM_CLIP_THRESHOLD -> glowBrushesClip[bucket]
              amp > WAVEFORM_HIGH_THRESHOLD -> glowBrushesHigh[bucket]
              else -> glowBrushesBase[bucket]
            }
            val barBrush = when {
              amp > WAVEFORM_CLIP_THRESHOLD -> barBrushesClip[bucket]
              amp > WAVEFORM_HIGH_THRESHOLD -> barBrushesHigh[bucket]
              else -> barBrushesBase[bucket]
            }

            drawRoundRect(
              brush = glowBrush,
              topLeft = Offset(x - 0.5f, topY - 0.5f),
              size = Size(barWidth + 1f, barHeight + 1f),
              cornerRadius = glowCorner
            )

            drawRoundRect(
              brush = barBrush,
              topLeft = Offset(x, topY),
              size = Size(barWidth, barHeight),
              cornerRadius = barCorner
            )

            if (peakHoldEnabled) {
              peakTracker.updateSingleBar(i, amp, nowMs, dt)
              val peakAmp = peakTracker.peakAmplitudes[i]
              if (peakAmp > amp + PEAK_MIN_VISIBLE_DELTA) {
                val peakHalfHeight = (peakAmp * (canvasHeight * 0.44f)).coerceAtLeast(2.5f)
                val markerTopY = (centerY - peakHalfHeight - 2f).coerceAtLeast(1f)
                val markerBottomY = (centerY + peakHalfHeight + 0.5f).coerceAtMost(canvasHeight - 2.5f)

                drawRoundRect(
                  color = capLineColor,
                  topLeft = Offset(x, markerTopY),
                  size = Size(barWidth, 1.5f),
                  cornerRadius = capCorner
                )
                drawRoundRect(
                  color = capLineColor,
                  topLeft = Offset(x, markerBottomY),
                  size = Size(barWidth, 1.5f),
                  cornerRadius = capCorner
                )
              }
            }
          }

        }
      }
      .testTag("compact_waveform_meter")
    ) {}
  }
}

/**
 * Accessible Transport Bar synced with waveform playback & scrubbing.
 */
@Composable
fun WaveformTransportBar(
  isPlaying: Boolean,
  isLooping: Boolean,
  positionMs: Long,
  durationMs: Long,
  progress: Float,
  onTogglePlayPause: () -> Unit,
  onStop: () -> Unit,
  onToggleLoop: () -> Unit,
  onSeekFraction: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
      .testTag("waveform_transport_bar")
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          FilledIconButton(
            onClick = onTogglePlayPause,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = if (isPlaying) AmberGold else StudioCyan,
              contentColor = Color.Black
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(40.dp)
              .testTag("action_transport_play_pause")
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (isPlaying) "Pause audio playback" else "Play audio playback"
            )
          }

          IconButton(
            onClick = onStop,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("action_transport_stop")
          ) {
            Icon(
              imageVector = Icons.Default.Stop,
              contentDescription = "Stop playback and reset playhead",
              tint = TextSecondary
            )
          }

          IconButton(
            onClick = onToggleLoop,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("action_transport_loop")
          ) {
            Icon(
              imageVector = Icons.Default.Repeat,
              contentDescription = if (isLooping) "Disable loop playback" else "Enable loop playback",
              tint = if (isLooping) AmberGold else TextMuted
            )
          }
        }

        Surface(
          color = StudioBg,
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.border(0.5.dp, StudioBorder, RoundedCornerShape(6.dp))
        ) {
          Text(
            text = "${PlaybackMath.formatDurationMmSs(positionMs)} / ${PlaybackMath.formatDurationMmSs(durationMs)}",
            style = MonospaceMetric.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = StudioCyan,
            modifier = Modifier
              .padding(horizontal = 10.dp, vertical = 5.dp)
              .testTag("transport_time_readout")
          )
        }
      }

      Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = { onSeekFraction(it) },
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
          thumbColor = AmberGold,
          activeTrackColor = StudioCyan,
          inactiveTrackColor = StudioBg
        ),
        modifier = Modifier
          .fillMaxWidth()
          .semantics {
            contentDescription = "Audio playback scrubber. Current time ${PlaybackMath.formatDurationMmSs(positionMs)} of ${PlaybackMath.formatDurationMmSs(durationMs)}"
          }
          .testTag("transport_scrub_slider")
      )
    }
  }
}

