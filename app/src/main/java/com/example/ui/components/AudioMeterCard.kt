package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioAnalysisResult
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun AudioMeterCard(
  analysis: AudioAnalysisResult,
  onBpmMultiplier: (Double) -> Unit
) {
  val bpmConfPct = (analysis.bpmConfidenceScore * 100.0).roundToInt().coerceIn(0, 100)
  val keyConfPct = (analysis.keyConfidenceScore * 100.0).roundToInt().coerceIn(0, 100)
  val displayKey = analysis.key ?: analysis.keyName?.substringBefore(" ") ?: "Unread"
  val displayMode = analysis.mode ?: analysis.keyName?.substringAfter(" ", "")?.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase() else it.toString()
  }?.ifBlank { null } ?: "Minor"

  val bpmConfColor = when {
    bpmConfPct >= 70 -> StudioWinner
    bpmConfPct >= 40 -> AmberGold
    else -> StudioMiss
  }
  val keyConfColor = if (analysis.keyClear || keyConfPct >= 65) StudioWinner else AmberGold

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = "Audio telemetry meters",
            tint = StudioCyan,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "AUDIO TELEMETRY",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = StudioCyan
          )
        }
        Text(
          text = analysis.fileName.take(24),
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Main Meters Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // BPM Box with Numeric % Confidence & Octave Half / Double
        Surface(
          color = StudioCardElevated,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1.25f)
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("TEMPO (BPM)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
              Surface(
                color = bpmConfColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "$bpmConfPct%",
                  style = MonospaceMetric.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                  color = bpmConfColor,
                  modifier = Modifier
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .semantics {
                      contentDescription = "Tempo confidence $bpmConfPct percent, ${analysis.bpmConfidence}"
                    }
                    .testTag("meter_bpm_confidence")
                )
              }
            }
            Text(
              text = "${analysis.bpm?.toInt() ?: "--"}",
              style = MonospaceMetric.copy(fontSize = 24.sp),
              color = AmberGold
            )
            Text(
              text = "${analysis.bpmConfidence.uppercase()} CONFIDENCE",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              color = bpmConfColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Button(
                onClick = { onBpmMultiplier(0.5) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioBg),
                modifier = Modifier
                  .weight(1f)
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .semantics { contentDescription = "Halve BPM" }
                  .testTag("action_bpm_half")
              ) {
                Text("x0.5", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
              }
              Button(
                onClick = { onBpmMultiplier(2.0) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioBg),
                modifier = Modifier
                  .weight(1f)
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .semantics { contentDescription = "Double BPM" }
                  .testTag("action_bpm_double")
              ) {
                Text("x2.0", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
              }
            }
          }
        }

        // Musical Key & Mode Box with Numeric % Confidence
        Surface(
          color = StudioCardElevated,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1.05f)
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("KEY / MODE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
              Surface(
                color = keyConfColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "$keyConfPct%",
                  style = MonospaceMetric.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                  color = keyConfColor,
                  modifier = Modifier
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .semantics {
                      contentDescription = "Key and mode confidence $keyConfPct percent"
                    }
                    .testTag("meter_key_confidence")
                )
              }
            }
            Text(
              text = analysis.keyName ?: displayKey,
              style = MonospaceMetric.copy(fontSize = 17.sp),
              color = keyConfColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
              color = if (analysis.keyClear) Color(0x3310B981) else Color(0x33F5A524),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = "${displayMode.uppercase()} · $keyConfPct%",
                style = MonospaceMetric.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = keyConfColor,
                modifier = Modifier
                  .padding(horizontal = 5.dp, vertical = 2.dp)
                  .semantics {
                    contentDescription = "Mode $displayMode with $keyConfPct percent confidence"
                  }
                  .testTag("meter_mode_confidence")
              )
            }
          }
        }

        // Low-End Ratio Box
        Surface(
          color = StudioCardElevated,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(0.95f)
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Text("LOW-END", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
              text = "${(analysis.lowShare * 100).toInt()}%",
              style = MonospaceMetric.copy(fontSize = 20.sp),
              color = StudioCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (analysis.lowShare > 0.4) "Sub Heavy (<160Hz)" else "Mid/Top Focus",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = TextSecondary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Signal Readouts: Peak dB and RMS dB
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(6.dp))
          .background(StudioBg)
          .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row {
          Text("PEAK: ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
          Text("${analysis.peakDb.toInt()} dBFS", style = MonospaceMetric.copy(fontSize = 12.sp), color = TextPrimary)
          Spacer(modifier = Modifier.width(12.dp))
          Text("RMS: ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
          Text("${analysis.rmsDb.toInt()} dBFS", style = MonospaceMetric.copy(fontSize = 12.sp), color = TextPrimary)
        }
        Text(
          text = "Centroid: ${analysis.centroidHz.toInt()} Hz",
          style = MonospaceMetric.copy(fontSize = 11.sp),
          color = TextSecondary
        )
      }
    }
  }
}
