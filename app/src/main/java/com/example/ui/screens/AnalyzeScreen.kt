package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AudioMeterCard
import com.example.ui.components.RealtimeWaveformVisualizer
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun AnalyzeScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val analysis by viewModel.audioAnalysis.collectAsState()
  val isRecording by viewModel.isRecording.collectAsState()
  val recordingDb by viewModel.recordingDb.collectAsState()
  val recordingPeak by viewModel.recordingPeak.collectAsState()
  val isAnalyzing by viewModel.isAnalyzing.collectAsState()
  val aiConsultation by viewModel.aiConsultation.collectAsState()
  val liveWaveform by viewModel.liveWaveform.collectAsState()

  // SAF Audio Picker
  val audioPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      val fileName = queryFileName(context, uri) ?: "Selected Beat Audio"
      viewModel.analyzeFile(uri, fileName)
    }
  }

  // Permission Launcher for Live Recording
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      viewModel.startRecording()
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioBg)
      .padding(horizontal = 14.dp),
    contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Top Input Hero: File Upload or Mic Recording
    item {
      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "AUDIO BEAT INGESTION",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
          Text(
            text = "Import a sample audio file or record directly with the studio mic to analyze tempo, musical key, and vocal fragments.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Pick Audio File
            Button(
              onClick = { audioPickerLauncher.launch("audio/*") },
              colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("action_pick_audio")
            ) {
              Icon(Icons.Default.AudioFile, contentDescription = null, tint = StudioCyan)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Import File", color = TextPrimary)
            }

            // Live Mic Record Button
            Button(
              onClick = {
                if (isRecording) {
                  viewModel.stopRecording()
                } else {
                  permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) StudioMiss else AmberGold
              ),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("action_mic_record")
            ) {
              Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                tint = if (isRecording) Color.White else Color.Black
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isRecording) "Stop (${recordingDb.toInt()}dB)" else "Live Record",
                color = if (isRecording) Color.White else Color.Black
              )
            }
          }

          // Live Realtime Waveform when Recording
          if (isRecording) {
            Spacer(modifier = Modifier.height(12.dp))
            RealtimeWaveformVisualizer(
              amplitudes = liveWaveform,
              isLiveRecording = true,
              currentPeakDb = recordingPeak,
              currentRmsDb = recordingDb
            )
          } else if (analysis == null && !isAnalyzing) {
            // Standby Groove Monitor
            Spacer(modifier = Modifier.height(12.dp))
            RealtimeWaveformVisualizer(
              amplitudes = liveWaveform,
              isLiveRecording = false,
              bpm = null,
              durationSec = 0.0
            )
          }

          if (isAnalyzing) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(color = StudioCyan, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(10.dp))
              Text("Analyzing FFT, Chromagram, and Transient Flux...", style = MaterialTheme.typography.labelMedium, color = StudioCyan)
            }
          }
        }
      }
    }

    // DSP Analysis Results & Groove Visualizer
    if (analysis != null) {
      item {
        RealtimeWaveformVisualizer(
          amplitudes = analysis!!.waveformPeaks,
          isLiveRecording = false,
          bpm = analysis!!.bpm,
          durationSec = analysis!!.durationSec
        )
      }

      item {
        AudioMeterCard(
          analysis = analysis!!,
          onBpmMultiplier = { viewModel.adjustBpmMultiplier(it) }
        )
      }

      // "What The Sample Says" Honesty Card
      item {
        var showWordsDialog by remember { mutableStateOf(false) }
        var wordsInput by remember { mutableStateOf(analysis?.sampleWordsText ?: "") }
        var wordsKind by remember { mutableStateOf(analysis?.sampleWordsKind ?: "Heard clearly") }

        Surface(
          color = StudioCard,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("WHAT THE SAMPLE SAYS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)
              }
              Surface(
                color = Color(0x3310B981),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "HONESTY LAW: NO GUESSING",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                  color = StudioWinner,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
              color = StudioBg,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                if (analysis?.sampleWordsText != null) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                      color = StudioCardElevated,
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        text = analysis!!.sampleWordsKind ?: "Heard clearly",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = StudioCyan,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "\"${analysis!!.sampleWordsText}\"",
                      style = MonospacePrompt.copy(fontSize = 12.sp),
                      color = TextPrimary
                    )
                  }
                } else {
                  Text(
                    text = aiConsultation?.heardVocalText ?: "No words made out. (Sampled vocals remain preservation-locked)",
                    style = MonospacePrompt.copy(fontSize = 12.sp),
                    color = TextSecondary
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (analysis?.sampleWordsText != null) {
                TextButton(
                  onClick = { viewModel.setSampleWords(null) },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                  Text("Clear Words", style = MaterialTheme.typography.labelSmall, color = StudioMiss)
                }
                Spacer(modifier = Modifier.width(4.dp))
              }
              OutlinedButton(
                onClick = {
                  wordsInput = analysis?.sampleWordsText ?: ""
                  wordsKind = analysis?.sampleWordsKind ?: "Heard clearly"
                  showWordsDialog = true
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(30.dp)
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (analysis?.sampleWordsText != null) "Edit Words" else "Type What You Heard",
                  style = MaterialTheme.typography.labelSmall,
                  color = StudioCyan
                )
              }
            }
          }
        }

        if (showWordsDialog) {
          AlertDialog(
            onDismissRequest = { showWordsDialog = false },
            title = { Text("What The Sample Says", color = TextPrimary) },
            text = {
              Column {
                Text(
                  text = "Honesty rule: Only enter words you actually heard. Max 120 characters, no guessed completions.",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                  value = wordsInput,
                  onValueChange = { if (it.length <= 120) wordsInput = it },
                  placeholder = { Text("e.g. hold on to faith, I need you...") },
                  label = { Text("Identified Words (${wordsInput.length}/120)") },
                  modifier = Modifier.fillMaxWidth(),
                  singleLine = true,
                  textStyle = MonospacePrompt.copy(color = TextPrimary)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Confidence Label:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  listOf("Heard clearly", "Fragment only").forEach { kind ->
                    val selected = wordsKind == kind
                    Surface(
                      color = if (selected) AmberGold else StudioCardElevated,
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { wordsKind = kind }
                        .border(0.5.dp, StudioBorder, RoundedCornerShape(12.dp))
                    ) {
                      Text(
                        text = kind,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) Color.Black else TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                      )
                    }
                  }
                }
              }
            },
            confirmButton = {
              Button(
                onClick = {
                  viewModel.setSampleWords(wordsInput, wordsKind)
                  showWordsDialog = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
              ) {
                Text("Save Words", color = Color.Black)
              }
            },
            dismissButton = {
              TextButton(onClick = { showWordsDialog = false }) {
                Text("Cancel", color = TextSecondary)
              }
            },
            containerColor = StudioCardElevated
          )
        }
      }

      // Gemini AI Beat Intelligence Card
      item {
        Surface(
          color = StudioCard,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("GEMINI MULTIMODAL BEAT ANALYST", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = StudioCyan)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = aiConsultation?.tacticalAdvice ?: "Keep the uploaded sample leading at full dynamic range. Add crisp half-time snare and tuned 808 sub.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary
            )

            if (!aiConsultation?.searchGroundingSummary.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              Surface(
                color = StudioBg,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Language, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Suno v6 Grounding: ${aiConsultation?.searchGroundingSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioCyan
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Suggested Lane Match:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(analysis!!.suggestedLane, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)

            // Render 4 Suno Prompt Suggestions
            val suggestions = aiConsultation?.suggestions ?: emptyList()
            if (suggestions.isNotEmpty()) {
              Spacer(modifier = Modifier.height(12.dp))
              Text("SUGGESTED SUNO V6 PROMPTS (≤ 200 CHARS):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberGold)
              Spacer(modifier = Modifier.height(6.dp))
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { sug ->
                  Surface(
                    color = StudioCardElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, StudioBorder, RoundedCornerShape(8.dp))
                  ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(sug.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text("${sug.chars} / 200c", style = MonospaceMetric.copy(fontSize = 11.sp), color = StudioCyan)
                          Spacer(modifier = Modifier.width(6.dp))
                          IconButton(
                            onClick = { viewModel.copyToClipboard(context, sug.title, sug.prompt) },
                            modifier = Modifier.size(26.dp)
                          ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(13.dp))
                          }
                          IconButton(
                            onClick = {
                              viewModel.updateStylePrompt(sug.prompt)
                              viewModel.setTab(com.example.viewmodel.StudioTab.BUILD)
                            },
                            modifier = Modifier.size(26.dp)
                          ) {
                            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send, contentDescription = "Send to Build", tint = AmberGold, modifier = Modifier.size(13.dp))
                          }
                        }
                      }
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(sug.prompt, style = MonospacePrompt.copy(fontSize = 12.sp), color = TextPrimary)
                      Spacer(modifier = Modifier.height(2.dp))
                      Text("• ${sug.focusNote}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                  }
                }
              }
            }
          }
        }
      }

      // One-Tap Package & Handoff Actions
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(
            onClick = { viewModel.loadFullPreservePackageFromAnalysis() },
            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("action_load_full_preserve")
          ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Build Full Preserve Package", style = MaterialTheme.typography.titleMedium, color = Color.Black)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = { viewModel.sendAnalysisToBuild() },
              colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("action_send_to_build")
            ) {
              Icon(Icons.Default.Build, contentDescription = null, tint = AmberGold)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Send to Build", color = TextPrimary)
            }

            Button(
              onClick = { viewModel.sendAnalysisToSimple() },
              colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("action_send_to_simple")
            ) {
              Icon(Icons.Default.Tune, contentDescription = null, tint = StudioCyan)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Send to Simple", color = TextPrimary)
            }
          }

          OutlinedButton(
            onClick = {
              val a = analysis ?: return@OutlinedButton
              val report = """=== BOODA BEAT ANALYSIS REPORT ===
File: ${a.fileName}
Tempo: ${a.bpm?.toInt() ?: 0} BPM (${a.bpmConfidence} confidence)
Musical Key: ${a.keyName ?: "Unread"}
Brightness Centroid: ${a.centroidHz.toInt()} Hz (${a.pocketLabel})
Onset Density: ${a.onsetRate} hits/sec
Suggested Lane: ${a.suggestedLane}
Sample Vocals: ${aiConsultation?.heardVocalText ?: (a.sampleWordsText ?: "No clear words isolated")}

[SUGGESTED SUNO PRESERVE PROMPT]
${a.promptPreserve}

[SUGGESTED SUNO ORIGINAL PROMPT]
${a.promptOriginal}
===================================""".trimIndent()
              viewModel.copyToClipboard(context, "Beat Analysis Report", report)
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("action_copy_analysis_report")
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Full Analysis Report", style = MaterialTheme.typography.labelLarge, color = StudioCyan)
          }
        }
      }
    }
  }
}

private fun queryFileName(context: Context, uri: Uri): String? {
  var name: String? = null
  val cursor = context.contentResolver.query(uri, null, null, null, null)
  cursor?.use {
    if (it.moveToFirst()) {
      val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (index >= 0) {
        name = it.getString(index)
      }
    }
  }
  return name
}
