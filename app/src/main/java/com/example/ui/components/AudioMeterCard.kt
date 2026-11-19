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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioAnalysisResult
import com.example.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioMeterCard(
  analysis: AudioAnalysisResult,
  onBpmMultiplier: (Double) -> Unit
) {
  val hasMeasuredAudio = analysis.isAudioMeasured
  val bpmConfPct = (analysis.bpmConfidenceScore * 100.0).roundToInt().coerceIn(0, 100)
  val keyConfPct = (analysis.keyConfidenceScore * 100.0).roundToInt().coerceIn(0, 100)
  val hasKeyData = hasMeasuredAudio && (analysis.key != null || analysis.keyName != null)
  val displayKey = if (!hasMeasuredAudio) {
    "Unread"
  } else {
    analysis.key ?: analysis.keyName?.substringBefore(" ") ?: "Unread"
  }
  val displayMode = if (!hasMeasuredAudio || (!hasKeyData && analysis.mode == null)) {
    "Unread"
  } else {
    analysis.mode ?: analysis.keyName?.substringAfter(" ", "")?.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase() else it.toString()
    }?.ifBlank { null } ?: "Unread"
  }

  val isTempoEstimate = hasMeasuredAudio &&
    (analysis.isTempoEstimate || analysis.bpmConfidence.equals("estimate", ignoreCase = true))
  val bpmConfColor = when {
    !hasMeasuredAudio || analysis.bpm == null -> TextMuted
    isTempoEstimate -> AmberGold
    bpmConfPct >= 70 -> StudioWinner
    bpmConfPct >= 40 -> AmberGold
    else -> StudioMiss
  }
  val keyConfColor = when {
    !hasKeyData -> TextMuted
    analysis.keyClear || keyConfPct >= 65 -> StudioWinner
    else -> AmberGold
  }
  val isPeakClipping = hasMeasuredAudio && analysis.peakDb > -1.0

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
            color = StudioCyan,
            maxLines = 1,
            softWrap = false
          )
        }
        Text(
          text = analysis.fileName,
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.End,
          modifier = Modifier
            .weight(1f)
            .padding(start = 8.dp)
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Main Meters Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // BPM Box with Numeric % Confidence & Octave Half / Double
        val hasMeasuredBpm = hasMeasuredAudio && analysis.bpm != null
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
              Text(
                text = "TEMPO",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                softWrap = false
              )
              Surface(
                color = bpmConfColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "$bpmConfPct%",
                  style = MonospaceMetric.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                  color = bpmConfColor,
                  maxLines = 1,
                  softWrap = false,
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
              text = if (hasMeasuredBpm) "${analysis.bpm!!.toInt()}" else "UNREAD",
              style = MonospaceMetric.copy(fontSize = if (hasMeasuredBpm) 24.sp else 16.sp),
              color = if (hasMeasuredBpm) AmberGold else TextMuted,
              maxLines = 1,
              softWrap = false
            )
            Text(
              text = when {
                !hasMeasuredBpm -> "UNREAD"
                isTempoEstimate -> "ESTIMATE"
                else -> "${analysis.bpmConfidence.uppercase()} CONFIDENCE"
              },
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              color = bpmConfColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Button(
                onClick = { onBpmMultiplier(0.5) },
                enabled = hasMeasuredBpm,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = StudioBg,
                  disabledContainerColor = StudioBg.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                  .weight(1f)
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .semantics { contentDescription = "Halve BPM" }
                  .testTag("action_bpm_half")
              ) {
                Text("x0.5", style = MaterialTheme.typography.labelSmall, color = if (hasMeasuredBpm) StudioCyan else TextMuted)
              }
              Button(
                onClick = { onBpmMultiplier(2.0) },
                enabled = hasMeasuredBpm,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = StudioBg,
                  disabledContainerColor = StudioBg.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                  .weight(1f)
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .semantics { contentDescription = "Double BPM" }
                  .testTag("action_bpm_double")
              ) {
                Text("x2.0", style = MaterialTheme.typography.labelSmall, color = if (hasMeasuredBpm) StudioCyan else TextMuted)
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
              Text(
                text = "KEY",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                softWrap = false
              )
              Surface(
                color = keyConfColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "$keyConfPct%",
                  style = MonospaceMetric.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                  color = keyConfColor,
                  maxLines = 1,
                  softWrap = false,
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
              text = if (hasKeyData) (analysis.keyName ?: displayKey) else "Unread",
              style = MonospaceMetric.copy(fontSize = if (hasKeyData) 16.sp else 15.sp),
              color = keyConfColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
              color = when {
                !hasKeyData -> StudioBg
                analysis.keyClear -> Color(0x3310B981)
                else -> Color(0x33F5A524)
              },
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = "${displayMode.uppercase()} · $keyConfPct%",
                style = MonospaceMetric.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = keyConfColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        val hasMeasuredSpectrum = hasMeasuredAudio && analysis.isSpectralMeasured
        Surface(
          color = StudioCardElevated,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(0.95f)
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Text(
              text = "LOW-END",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted,
              maxLines = 1,
              softWrap = false
            )
            Text(
              text = if (hasMeasuredSpectrum) "${(analysis.lowShare * 100).toInt()}%" else "Unread",
              style = MonospaceMetric.copy(fontSize = if (hasMeasuredSpectrum) 20.sp else 15.sp),
              color = if (hasMeasuredSpectrum) StudioCyan else TextMuted,
              maxLines = 1,
              softWrap = false
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = when {
                !hasMeasuredSpectrum -> "Spectrum Unread"
                analysis.lowShare > 0.4 -> "Sub Heavy (<160Hz)"
                else -> "Mid/Top Focus"
              },
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = TextSecondary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Signal Readouts: Peak dB, RMS dB, and Spectral Centroid
      FlowRow(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(6.dp))
          .background(StudioBg)
          .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PEAK: ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
              text = if (hasMeasuredAudio) "${analysis.peakDb.toInt()} dBFS" else "Unread",
              style = MonospaceMetric.copy(fontSize = 12.sp),
              color = if (isPeakClipping) StudioMiss else if (hasMeasuredAudio) TextPrimary else TextMuted
            )
            if (isPeakClipping) {
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                color = Color(0x33EF4444),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "CLIP",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                  color = StudioMiss,
                  modifier = Modifier
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .testTag("meter_clip_badge")
                )
              }
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RMS: ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
              text = if (hasMeasuredAudio) "${analysis.rmsDb.toInt()} dBFS" else "Unread",
              style = MonospaceMetric.copy(fontSize = 12.sp),
              color = if (hasMeasuredAudio) TextPrimary else TextMuted
            )
          }
        }
        Text(
          text = if (hasMeasuredAudio && analysis.isSpectralMeasured) {
            "Centroid: ${analysis.centroidHz.toInt()} Hz"
          } else {
            "Centroid: Unread"
          },
          style = MonospaceMetric.copy(fontSize = 11.sp),
          color = if (hasMeasuredAudio && analysis.isSpectralMeasured) TextSecondary else TextMuted,
          maxLines = 1,
          softWrap = false
        )
      }
    }
  }
}
