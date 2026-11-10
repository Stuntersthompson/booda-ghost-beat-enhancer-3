package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.sin

enum class WaveformStyle {
  BARS,
  OSCILLOSCOPE
}

/**
 * Real-time Audio Waveform Visualizer using Jetpack Compose Canvas API.
 * Displays live incoming mic amplitudes or full-track groove contours with beat grid markers.
 */
@Composable
fun RealtimeWaveformVisualizer(
  amplitudes: List<Float>,
  isLiveRecording: Boolean,
  currentPeakDb: Float = -60f,
  currentRmsDb: Float = -60f,
  bpm: Double? = null,
  durationSec: Double = 0.0,
  modifier: Modifier = Modifier
) {
  var visualizerStyle by remember { mutableStateOf(WaveformStyle.BARS) }
  var inspectPositionX by remember { mutableStateOf<Float?>(null) }

  // Pulsing animation for live recording mode
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
      // Header: Mode info and Display Style Toggle
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
            .clip(RoundedCornerShape(6.dp))
            .background(StudioBg)
            .padding(2.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          IconButton(
            onClick = { visualizerStyle = WaveformStyle.BARS },
            modifier = Modifier.size(26.dp)
          ) {
            Icon(
              imageVector = Icons.Default.GraphicEq,
              contentDescription = "Bars",
              tint = if (visualizerStyle == WaveformStyle.BARS) AmberGold else TextMuted,
              modifier = Modifier.size(14.dp)
            )
          }
          IconButton(
            onClick = { visualizerStyle = WaveformStyle.OSCILLOSCOPE },
            modifier = Modifier.size(26.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Timeline,
              contentDescription = "Oscilloscope",
              tint = if (visualizerStyle == WaveformStyle.OSCILLOSCOPE) StudioCyan else TextMuted,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Canvas Waveform Area
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(StudioBg)
          .border(0.5.dp, StudioBorder, RoundedCornerShape(8.dp))
          .pointerInput(Unit) {
            detectTapGestures(
              onTap = { offset -> inspectPositionX = offset.x },
              onPress = { inspectPositionX = it.x }
            )
          }
          .pointerInput(Unit) {
            detectDragGestures { change, _ ->
              inspectPositionX = change.position.x
            }
          }
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxSize()
            .testTag("waveform_canvas")
        ) {
          val canvasWidth = size.width
          val canvasHeight = size.height
          val centerY = canvasHeight / 2f

          // 1. Draw subtle background grid lines
          val numGridLines = 8
          val gridSpacing = canvasWidth / numGridLines
          for (g in 1 until numGridLines) {
            drawLine(
              color = StudioBorder.copy(alpha = 0.5f),
              start = Offset(g * gridSpacing, 0f),
              end = Offset(g * gridSpacing, canvasHeight),
              strokeWidth = 1f
            )
          }

          // Centerline (Zero-crossing)
          drawLine(
            color = if (isLiveRecording) StudioMiss.copy(alpha = 0.35f) else StudioCyan.copy(alpha = 0.25f),
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
                  color = if (isFourBar) AmberGold.copy(alpha = 0.6f) else StudioBorder,
                  start = Offset(barX, if (isFourBar) 4f else centerY - 15f),
                  end = Offset(barX, if (isFourBar) canvasHeight - 4f else centerY + 15f),
                  strokeWidth = if (isFourBar) 2f else 1f
                )
              }
            }
          }

          if (amplitudes.isEmpty()) return@Canvas

          // 3. Render Waveform (Bars or Continuous Oscilloscope)
          when (visualizerStyle) {
            WaveformStyle.BARS -> {
              val barCount = amplitudes.size
              val totalSpacing = canvasWidth * 0.25f
              val barWidth = ((canvasWidth - totalSpacing) / barCount).coerceAtLeast(2f)
              val gap = totalSpacing / (barCount - 1).coerceAtLeast(1)

              for (i in 0 until barCount) {
                val amp = amplitudes[i].coerceIn(0.04f, 1f)
                val barHeight = (amp * (canvasHeight * 0.88f)).coerceAtLeast(4f)
                val x = i * (barWidth + gap)
                val topY = centerY - (barHeight / 2f)

                // Color-code: Red if clipping, Amber for hard 808/drop transients, Cyan for mellow
                val barColor = when {
                  amp > 0.90f -> StudioMiss
                  amp > 0.60f -> AmberGold
                  amp > 0.35f -> AmberGoldLight
                  else -> StudioCyan
                }

                drawRoundRect(
                  color = barColor,
                  topLeft = Offset(x, topY),
                  size = Size(barWidth, barHeight),
                  cornerRadius = CornerRadius(2f, 2f)
                )

                // Peak transient dot for strong kicks/808s
                if (amp > 0.55f) {
                  drawCircle(
                    color = Color.White,
                    radius = (barWidth * 0.7f).coerceIn(1.5f, 3.5f),
                    center = Offset(x + barWidth / 2f, topY - 3f)
                  )
                }
              }
            }

            WaveformStyle.OSCILLOSCOPE -> {
              val path = Path()
              val stepX = canvasWidth / (amplitudes.size - 1).coerceAtLeast(1)

              for (i in amplitudes.indices) {
                val amp = amplitudes[i].coerceIn(0.04f, 1f)
                // Alternate polarity to create an authentic wave oscillation
                val polarity = if (i % 2 == 0) 1f else -1f
                val y = centerY - (amp * (canvasHeight * 0.45f) * polarity)
                val x = i * stepX

                if (i == 0) {
                  path.moveTo(x, y)
                } else {
                  val prevX = (i - 1) * stepX
                  val prevAmp = amplitudes[i - 1].coerceIn(0.04f, 1f)
                  val prevPolarity = if ((i - 1) % 2 == 0) 1f else -1f
                  val prevY = centerY - (prevAmp * (canvasHeight * 0.45f) * prevPolarity)

                  val cx = (prevX + x) / 2f
                  path.cubicTo(cx, prevY, cx, y, x, y)
                }
              }

              // Glow stroke
              drawPath(
                path = path,
                color = (if (isLiveRecording) StudioMiss else AmberGold).copy(alpha = 0.3f),
                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
              )
              // Crisp main line
              drawPath(
                path = path,
                color = if (isLiveRecording) StudioMiss else StudioCyan,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
              )
            }
          }

          // 4. Draw Inspection Cursor if tapped
          inspectPositionX?.let { tapX ->
            val clampedX = tapX.coerceIn(0f, canvasWidth)
            drawLine(
              color = Color.White,
              start = Offset(clampedX, 0f),
              end = Offset(clampedX, canvasHeight),
              strokeWidth = 1.5f,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )
            drawCircle(
              color = AmberGold,
              radius = 4f,
              center = Offset(clampedX, centerY)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Footer Readouts: Real-time dB readouts or groove legend
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
          // Legend
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
