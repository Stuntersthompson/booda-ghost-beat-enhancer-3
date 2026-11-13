package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.StudioPreferencesRepository
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

/**
 * Navigation drawer content sheet hosting global studio display preferences,
 * stepped sensitivity slider, peak-hold marker toggle, live preview strip, and DSP telemetry.
 */
@Composable
fun StudioSettingsDrawerSheet(
  viewModel: MainViewModel,
  onClose: () -> Unit
) {
  val sensitivityState = viewModel.visualizerSensitivity.collectAsStateWithLifecycle()
  val peakHoldState = viewModel.peakHoldEnabled.collectAsStateWithLifecycle()
  val liveWaveformState = viewModel.liveWaveform.collectAsStateWithLifecycle()
  val analysisState = viewModel.audioAnalysis.collectAsStateWithLifecycle()
  val isRecordingState = viewModel.isRecording.collectAsStateWithLifecycle()
  val activeLane by viewModel.activeLane.collectAsStateWithLifecycle()

  val sensitivity = sensitivityState.value
  val peakHoldEnabled = peakHoldState.value

  val idlePreviewContour = remember {
    floatArrayOf(
      0.10f, 0.22f, 0.45f, 0.85f, 0.60f, 0.32f, 0.18f, 0.50f,
      0.72f, 0.95f, 0.40f, 0.25f, 0.15f, 0.62f, 0.78f, 0.30f,
      0.12f, 0.35f, 0.58f, 0.90f, 0.44f, 0.20f, 0.10f, 0.48f,
      0.65f, 0.82f, 0.38f, 0.22f, 0.16f, 0.55f, 0.70f, 0.28f
    )
  }

  ModalDrawerSheet(
    drawerContainerColor = StudioSurface,
    drawerContentColor = TextPrimary,
    modifier = Modifier
      .widthIn(max = 340.dp)
      .fillMaxHeight()
      .border(0.5.dp, StudioBorder)
      .testTag("studio_settings_drawer")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .verticalScroll(rememberScrollState())
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(34.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(AmberGold),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = null,
              tint = Color.Black,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "STUDIO PREFERENCES",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Waveform & DSP Configuration",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("action_close_settings")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close Settings Drawer", tint = TextSecondary)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = StudioBorder)
      Spacer(modifier = Modifier.height(16.dp))

      // Section 1: VISUALIZER & DISPLAY
      Text(
        text = "VISUALIZER & DISPLAY",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = AmberGold
      )
      Spacer(modifier = Modifier.height(8.dp))

      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          // a) Visualizer Sensitivity Header & Live Multiplier Readout
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = StudioCyan,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Visualizer Sensitivity",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
              )
            }

            Surface(
              color = StudioCyan.copy(alpha = 0.15f),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.border(0.5.dp, StudioCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            ) {
              Text(
                text = "%.2fx".format(sensitivity),
                style = MonospaceMetric.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = StudioCyan,
                modifier = Modifier
                  .padding(horizontal = 8.dp, vertical = 3.dp)
                  .testTag("sensitivity_numeric_readout")
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Stepped 0.05x Sensitivity Slider (0.25x to 4.00x)
          Slider(
            value = sensitivity,
            onValueChange = { rawVal ->
              viewModel.setVisualizerSensitivity(
                StudioPreferencesRepository.snapSensitivityStep(rawVal)
              )
            },
            valueRange = StudioPreferencesRepository.MIN_SENSITIVITY..StudioPreferencesRepository.MAX_SENSITIVITY,
            steps = StudioPreferencesRepository.SENSITIVITY_SLIDER_STEPS,
            colors = SliderDefaults.colors(
              thumbColor = StudioCyan,
              activeTrackColor = StudioCyan,
              inactiveTrackColor = StudioCardElevated,
              activeTickColor = Color.Transparent,
              inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("visualizer_sensitivity_slider")
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "0.25x",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted
            )

            OutlinedButton(
              onClick = { viewModel.resetVisualizerSensitivity() },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
              border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f)),
              modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .testTag("action_reset_sensitivity")
            ) {
              Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Reset to 1.0x", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
            }

            Text(
              text = "4.0x",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted
            )
          }

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = StudioBorder.copy(alpha = 0.6f))
          Spacer(modifier = Modifier.height(12.dp))

          // b) Peak-Hold Markers Toggle (Switch)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
              Text(
                text = "Peak-Hold Markers",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
              )
              Text(
                text = "Holds kick & 808 transients for 500 ms with smooth gravity falloff.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }

            Switch(
              checked = peakHoldEnabled,
              onCheckedChange = { viewModel.setPeakHoldEnabled(it) },
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = StudioCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = StudioCardElevated,
                uncheckedBorderColor = StudioBorder
              ),
              modifier = Modifier.testTag("switch_peak_hold_markers")
            )
          }

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = StudioBorder.copy(alpha = 0.6f))
          Spacer(modifier = Modifier.height(12.dp))

          // c) Live Preview Strip using CompactWaveformMeter
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "LIVE PREVIEW STRIP",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
              color = TextMuted
            )
            Text(
              text = activeLane,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = StudioCyan
            )
          }
          Spacer(modifier = Modifier.height(6.dp))

          CompactWaveformMeter(
            amplitudesProvider = {
              if (isRecordingState.value) {
                liveWaveformState.value
              } else {
                val analyzed = analysisState.value
                if (analyzed != null && analyzed.waveformPeaks.isNotEmpty()) {
                  analyzed.waveformPeaksArray
                } else {
                  idlePreviewContour
                }
              }
            },
            sensitivityProvider = { sensitivityState.value },
            peakHoldEnabledProvider = { peakHoldState.value },
            activeLane = activeLane,
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("settings_preview_meter")
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Section 2: DSP AUDIO ENGINE STATUS
      Text(
        text = "DSP AUDIO ENGINE STATUS",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = AmberGold
      )
      Spacer(modifier = Modifier.height(8.dp))

      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SettingInfoRow(title = "PCM Streaming Rate", value = "44.1 kHz · 16-bit")
          SettingInfoRow(
            title = "Peak-Hold Decay",
            value = if (peakHoldEnabled) "500 ms Hold · Gravity" else "Disabled"
          )
          SettingInfoRow(title = "Lane Palette", value = activeLane)
          SettingInfoRow(title = "Draw Pipeline", value = "Zero-Alloc · 60 FPS")
        }
      }
    }
  }
}

@Composable
private fun SettingInfoRow(title: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(title, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    Text(value, style = MonospaceMetric.copy(fontSize = 11.sp), color = TextPrimary)
  }
}
