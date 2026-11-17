package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.BeatIntensityCalculator
import com.example.audio.BeatMeterSnapshot
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldLight
import com.example.ui.theme.MonospaceMetric
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioMiss
import com.example.ui.theme.StudioWinner
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Real-time Canvas DSP spectrum & beat parameter meter displaying multi-band energy,
 * peak-hold caps, complexity %, and intensity % feedback inside the Groove Preview Monitor.
 */
@Composable
fun BeatIntensityCanvasMeter(
  snapshot: BeatMeterSnapshot,
  sensitivity: Float = 1f,
  peakHoldEnabled: Boolean = true,
  isLiveAudio: Boolean = false,
  modifier: Modifier = Modifier
) {
  val bandCount = snapshot.bandTargets.size.coerceAtLeast(1)
  val smoothedBars = remember(bandCount) {
    FloatArray(bandCount) { idx -> snapshot.bandTargets.getOrElse(idx) { 0.08f } }
  }
  val peakCaps = remember(bandCount) {
    FloatArray(bandCount) { idx -> snapshot.bandTargets.getOrElse(idx) { 0.12f } }
  }
  val modulatedBands = remember(bandCount) { FloatArray(bandCount) { 0.08f } }
  val transitionTick = remember { mutableLongStateOf(0L) }

  val currentSnapshot by rememberUpdatedState(snapshot)
  val currentSensitivity by rememberUpdatedState(sensitivity)
  val currentPeakHold by rememberUpdatedState(peakHoldEnabled)

  // Compose-native infinite transition for BPM-paced rhythmic pulse (idling-resource safe in UI tests)
  val infiniteTransition = rememberInfiniteTransition(label = "beat_meter_groove")
  val grooveTimeSecState = infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 8f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 8000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "groove_time_sec"
  )

  // Bounded smoothing transition whenever beat parameters or audio analysis change
  LaunchedEffect(snapshot, sensitivity, peakHoldEnabled, isLiveAudio) {
    var frameCount = 0
    val maxTransitionFrames = if (isLiveAudio) 120 else 28
    while (frameCount < maxTransitionFrames) {
      var settled = true
      withFrameMillis { nowMs ->
        frameCount++
        for (i in 0 until bandCount) {
          val target = (currentSnapshot.bandTargets[i] * currentSensitivity.coerceIn(0.5f, 2.0f))
            .coerceIn(0.05f, 1f)
          val prev = smoothedBars[i]
          val delta = target - prev
          val next = if (abs(delta) < 0.004f) target else prev + delta * 0.28f
          if (abs(target - next) >= 0.004f) settled = false
          smoothedBars[i] = next.coerceIn(0.05f, 1f)

          if (currentPeakHold) {
            if (smoothedBars[i] >= peakCaps[i]) {
              peakCaps[i] = smoothedBars[i]
            } else {
              val decayedPeak = max(smoothedBars[i], peakCaps[i] - 0.015f)
              if (abs(decayedPeak - smoothedBars[i]) >= 0.004f) settled = false
              peakCaps[i] = decayedPeak
            }
          } else {
            peakCaps[i] = smoothedBars[i]
          }
        }
        transitionTick.longValue = nowMs
      }
      if (settled && !isLiveAudio) break
    }
  }

  val tierColor = when (snapshot.tierLabel) {
    "MAXIMAL" -> StudioMiss
    "DENSE" -> AmberGold
    "TIGHT" -> StudioCyan
    else -> StudioWinner
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(StudioBg)
      .border(0.5.dp, StudioBorder, RoundedCornerShape(8.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp)
      .semantics(mergeDescendants = true) {
        contentDescription = "Beat complexity and intensity Canvas meter. ${snapshot.summaryText}"
      }
      .testTag("beat_intensity_canvas_meter")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          color = if (snapshot.audioDriven) Color(0x2210B981) else Color(0x2200D2FF),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = if (snapshot.audioDriven) "DSP AUDIO + PARAMS" else "PARAM SPECTRUM",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = if (snapshot.audioDriven) StudioWinner else StudioCyan,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
        Surface(
          color = StudioCardElevated,
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = snapshot.tierLabel,
            style = MonospaceMetric.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = tierColor,
            modifier = Modifier
              .padding(horizontal = 6.dp, vertical = 2.dp)
              .testTag("beat_meter_tier_badge")
          )
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "INT ${snapshot.intensityScore}%",
          style = MonospaceMetric.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
          color = AmberGoldLight,
          modifier = Modifier.testTag("beat_meter_intensity_value")
        )
        Text(
          text = "CPLX ${snapshot.complexityScore}%",
          style = MonospaceMetric.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
          color = StudioCyan,
          modifier = Modifier.testTag("beat_meter_complexity_value")
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    val cyanTint = StudioCyan
    val amberTint = AmberGold
    val peakHotTint = StudioMiss
    val gridTint = StudioBorder.copy(alpha = 0.55f)
    val trackBgTint = StudioCardElevated.copy(alpha = 0.5f)
    val segmentCount = 10

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(68.dp)
    ) {
      @Suppress("UNUSED_VARIABLE")
      val tick = transitionTick.longValue
      val timeSec = grooveTimeSecState.value

      val w = size.width
      val h = size.height
      if (w <= 0f || h <= 0f) return@Canvas

      BeatIntensityCalculator.sampleAnimatedBands(
        snapshot = currentSnapshot,
        timeSec = timeSec,
        sensitivity = currentSensitivity,
        isPlayingOrLive = isLiveAudio,
        outBands = modulatedBands
      )

      // Reference dB lines (-24dB, -12dB, -3dB)
      val gridLines = 3
      for (g in 1..gridLines) {
        val gy = h * (g.toFloat() / (gridLines + 1))
        drawLine(
          color = gridTint,
          start = Offset(0f, gy),
          end = Offset(w, gy),
          strokeWidth = 1f
        )
      }

      val gapPx = 4.dp.toPx()
      val barWidth = ((w - gapPx * (bandCount - 1)) / bandCount).coerceAtLeast(3f)
      val segGap = 1.5.dp.toPx()
      val segHeight = ((h - segGap * (segmentCount - 1)) / segmentCount).coerceAtLeast(2f)
      val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())

      for (band in 0 until bandCount) {
        val x = band * (barWidth + gapPx)
        val level = ((smoothedBars[band] + modulatedBands[band]) * 0.5f).coerceIn(0.05f, 1f)
        val peak = max(level, peakCaps[band]).coerceIn(0.05f, 1f)
        val activeSegments = (level * segmentCount).roundToInt().coerceIn(1, segmentCount)

        for (seg in 0 until segmentCount) {
          val y = h - (seg + 1) * segHeight - seg * segGap
          val isLit = seg < activeSegments
          val color = if (!isLit) {
            trackBgTint
          } else {
            val segRatio = (seg + 1).toFloat() / segmentCount
            when {
              segRatio >= 0.85f -> peakHotTint
              segRatio >= 0.55f -> amberTint
              else -> cyanTint
            }
          }
          drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(barWidth, segHeight),
            cornerRadius = corner
          )
        }

        if (currentPeakHold) {
          val peakY = (h - peak * h).coerceIn(1f, h - 2.dp.toPx())
          drawRoundRect(
            brush = Brush.horizontalGradient(listOf(AmberGoldLight, TextPrimary)),
            topLeft = Offset(x, peakY),
            size = Size(barWidth, 2.dp.toPx()),
            cornerRadius = corner
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      listOf("32Hz", "125", "500", "2k", "6k", "16k").forEach { label ->
        Text(
          text = label,
          style = MonospaceMetric.copy(fontSize = 8.sp),
          color = TextMuted
        )
      }
    }
  }
}
