package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PlaybackMath
import com.example.ui.theme.AmberGold
import com.example.ui.theme.MonospaceMetric
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightweight pre-allocated holder used inside draw scope to interpolate playback progress cleanly
 * at 60 FPS without creating per-frame allocations or triggering Compose recompositions.
 */
private class ScrubberFrameSync {
  var lastEngineProgress: Float = 0f
  var lastSyncTimeMs: Long = 0L
}

/**
 * Reusable high-performance audio scrubber overlay wrapping any waveform canvas.
 *
 * - Reads [progressProvider] strictly inside [drawWithCache] / [onDrawBehind] to avoid recomposition storms.
 * - Syncs playhead needle movement at 60 FPS during active playback.
 * - Suspends engine polling updates during drag gestures (`onScrubStart` -> `onScrubPreview` -> `onScrubEnd`)
 *   and renders a floating `mm:ss.ms` time readout tracking the playhead while preserving play/pause state.
 * - Handles empty audio (`durationMs <= 0L`), clamping outside `0f..1f`, loop wraparound, and TalkBack `Role.Slider`.
 */
@Composable
fun WaveformScrubberOverlay(
  durationMs: Long,
  isPlaying: Boolean,
  isLooping: Boolean,
  isScrubbing: Boolean,
  progressProvider: () -> Float,
  onScrubStart: (Float) -> Unit,
  onScrubPreview: (Float) -> Unit,
  onScrubEnd: (Float) -> Unit,
  onTapSeek: (Float) -> Unit,
  enabled: Boolean = durationMs > 0L,
  accentColor: Color = AmberGold,
  compact: Boolean = false,
  inspectionXState: MutableState<Float?>? = null,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  val isScrubActive = enabled && durationMs > 0L
  val dragFractionState = remember { mutableStateOf<Float?>(null) }
  val dragActive = isScrubActive && (dragFractionState.value != null || isScrubbing)
  val frameTickState = remember { mutableLongStateOf(0L) }
  val frameSync = remember { ScrubberFrameSync() }
  val density = LocalDensity.current

  // Ensure stray drag state clears cleanly if source becomes disabled or unmounted
  LaunchedEffect(isScrubActive) {
    if (!isScrubActive) {
      dragFractionState.value = null
    }
  }

  // Drive 60 FPS vsync ticks ONLY while actively playing & not paused for drag
  LaunchedEffect(isScrubActive, isPlaying, isScrubbing, durationMs) {
    if (isScrubActive && isPlaying && !isScrubbing) {
      while (true) {
        val frameTimeNanos = awaitFrame()
        frameTickState.longValue = frameTimeNanos
      }
    }
  }

  val semanticsModifier = if (isScrubActive) {
    Modifier.semantics {
      val activeFraction = PlaybackMath.clampFraction(dragFractionState.value ?: progressProvider())
      val posMs = PlaybackMath.computeSeekTargetMs(activeFraction, durationMs)
      progressBarRangeInfo = ProgressBarRangeInfo(current = activeFraction, range = 0f..1f)
      stateDescription = "Position ${PlaybackMath.formatDurationMmSsMs(posMs)} of ${PlaybackMath.formatDurationMmSsMs(durationMs)}"
      contentDescription = "Audio playback waveform scrubber slider"
      setProgress { targetValue ->
        val clamped = PlaybackMath.clampFraction(targetValue)
        onTapSeek(clamped)
        true
      }
    }
  } else {
    Modifier.semantics {
      contentDescription = "Audio waveform display"
    }
  }

  val gestureModifier = if (isScrubActive) {
    Modifier
      .pointerInput(isScrubActive, durationMs, onTapSeek, onScrubStart) {
        detectTapGestures(
          onTap = { offset ->
            if (size.width > 0) {
              inspectionXState?.value = offset.x
              val frac = PlaybackMath.clampFraction(offset.x / size.width.toFloat())
              onTapSeek(frac)
            }
          }
        )
      }
      .pointerInput(isScrubActive, durationMs, onScrubStart, onScrubPreview, onScrubEnd) {
        var currentDragFrac = 0f
        detectHorizontalDragGestures(
          onDragStart = { startOffset ->
            if (size.width > 0) {
              val frac = PlaybackMath.clampFraction(startOffset.x / size.width.toFloat())
              currentDragFrac = frac
              dragFractionState.value = frac
              inspectionXState?.value = startOffset.x
              onScrubStart(frac)
            }
          },
          onHorizontalDrag = { change, _ ->
            change.consume()
            if (size.width > 0) {
              val frac = PlaybackMath.clampFraction(change.position.x / size.width.toFloat())
              currentDragFrac = frac
              dragFractionState.value = frac
              inspectionXState?.value = change.position.x
              onScrubPreview(frac)
            }
          },
          onDragEnd = {
            dragFractionState.value = null
            onScrubEnd(currentDragFrac)
          },
          onDragCancel = {
            dragFractionState.value = null
            onScrubEnd(currentDragFrac)
          }
        )
      }
  } else {
    Modifier
  }

  BoxWithConstraints(
    modifier = modifier
      .then(semanticsModifier)
      .then(gestureModifier)
      .drawWithCache {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f

        val playheadGlowColor = Color.White.copy(alpha = 0.28f)
        val playheadLineColor = Color.White
        val thumbHaloColor = accentColor.copy(alpha = 0.32f)
        val thumbCoreColor = accentColor
        val inspectDash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))

        onDrawWithContent {
          drawContent()

          // Observe frame tick strictly inside draw phase
          val frameNanos = frameTickState.longValue
          val dragFrac = dragFractionState.value

          if (isScrubActive) {
            val resolvedFraction = if (dragFrac != null) {
              PlaybackMath.clampFraction(dragFrac)
            } else {
              val engineFrac = PlaybackMath.clampFraction(progressProvider())
              val nowMs = if (frameNanos > 0L) frameNanos / 1_000_000L else System.currentTimeMillis()
              if (abs(engineFrac - frameSync.lastEngineProgress) > 0.0005f || !isPlaying || isScrubbing) {
                frameSync.lastEngineProgress = engineFrac
                frameSync.lastSyncTimeMs = nowMs
                engineFrac
              } else if (isPlaying && durationMs > 0L) {
                // Smooth monotonic interpolation (capped <= 32ms lead) between polling syncs at 60 FPS
                val deltaMs = (nowMs - frameSync.lastSyncTimeMs).coerceIn(0L, 32L)
                val baseMs = PlaybackMath.computeSeekTargetMs(frameSync.lastEngineProgress, durationMs)
                val nextMs = PlaybackMath.advanceLoopPositionMs(baseMs + deltaMs, durationMs, isLooping)
                PlaybackMath.computeNormalizedProgress(nextMs, durationMs)
              } else {
                engineFrac
              }
            }

            val playX = (resolvedFraction * canvasWidth).coerceIn(0f, canvasWidth)
            drawLine(
              color = playheadGlowColor,
              start = Offset(playX, 0f),
              end = Offset(playX, canvasHeight),
              strokeWidth = if (compact) 4.5f else 6f
            )
            drawLine(
              color = playheadLineColor,
              start = Offset(playX, 0f),
              end = Offset(playX, canvasHeight),
              strokeWidth = if (compact) 1.6f else 2f
            )
            drawCircle(
              color = thumbHaloColor,
              radius = if (dragFrac != null) 8.5f else if (compact) 5.5f else 7f,
              center = Offset(playX, centerY)
            )
            drawCircle(
              color = thumbCoreColor,
              radius = if (dragFrac != null) 5f else if (compact) 3.2f else 4.2f,
              center = Offset(playX, centerY)
            )
          } else {
            val tapX = inspectionXState?.value
            if (tapX != null) {
              val clampedX = tapX.coerceIn(0f, canvasWidth)
              drawLine(
                color = Color.White,
                start = Offset(clampedX, 0f),
                end = Offset(clampedX, canvasHeight),
                strokeWidth = 1.5f,
                pathEffect = inspectDash
              )
              drawCircle(
                color = accentColor,
                radius = 4f,
                center = Offset(clampedX, centerY)
              )
            }
          }
        }
      }
      .testTag("waveform_scrubber_overlay")
  ) {
    content()

    // Floating mm:ss.ms time pill tracking the active drag playhead
    val activeDrag = dragFractionState.value
    AnimatedVisibility(
      visible = dragActive && activeDrag != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopStart)
    ) {
      val frac = PlaybackMath.clampFraction(activeDrag ?: progressProvider())
      val previewMs = PlaybackMath.computeSeekTargetMs(frac, durationMs)
      val formattedTime = PlaybackMath.formatDurationMmSsMs(previewMs)

      val maxWidthPx = with(density) { maxWidth.toPx() }
      val approxPillWidthPx = with(density) { 86.dp.toPx() }
      val minEdgePx = with(density) { 4.dp.toPx() }
      val targetXPx = (frac * maxWidthPx - approxPillWidthPx / 2f)
        .coerceIn(minEdgePx, (maxWidthPx - approxPillWidthPx - minEdgePx).coerceAtLeast(minEdgePx))

      Surface(
        color = StudioCardElevated.copy(alpha = 0.96f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
          .offset { IntOffset(targetXPx.roundToInt(), 4) }
          .border(1.dp, AmberGold, RoundedCornerShape(6.dp))
          .testTag("scrubber_floating_time_pill")
      ) {
        Text(
          text = formattedTime,
          style = MonospaceMetric.copy(
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold
          ),
          color = StudioCyan,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
      }
    }
  }
}
