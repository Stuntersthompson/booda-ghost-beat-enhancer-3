package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiConsultationUiState
import com.example.ai.GeminiBeatService
import com.example.ai.TranscriptionTarget
import com.example.ai.TranscriptionUiState
import com.example.audio.AudioAnalysisResult
import com.example.ui.components.AudioMeterCard
import com.example.ui.components.RealtimeWaveformVisualizer
import com.example.ui.components.WaveformTransportBar
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyzeScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val analysis by viewModel.audioAnalysis.collectAsStateWithLifecycle()
  val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
  val recordingDb by viewModel.recordingDb.collectAsStateWithLifecycle()
  val recordingPeak by viewModel.recordingPeak.collectAsStateWithLifecycle()
  val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
  val aiConsultation by viewModel.aiConsultation.collectAsStateWithLifecycle()
  val aiConsultationState by viewModel.aiConsultationState.collectAsStateWithLifecycle()
  val micTranscriptionState by viewModel.micTranscriptionState.collectAsStateWithLifecycle()
  val activeLane by viewModel.activeLane.collectAsStateWithLifecycle()
  val audioError by viewModel.audioError.collectAsStateWithLifecycle()
  val playbackError by viewModel.playbackError.collectAsStateWithLifecycle()
  val micPermissionDenied by viewModel.micPermissionDenied.collectAsStateWithLifecycle()
  val combinedAudioError = audioError ?: playbackError

  // Draw-scoped state holders (read inside Canvas/drawWithCache to avoid recomposition storms)
  val liveWaveformState = viewModel.liveWaveform.collectAsStateWithLifecycle()
  val sensitivityState = viewModel.visualizerSensitivity.collectAsStateWithLifecycle()
  val peakHoldState = viewModel.peakHoldEnabled.collectAsStateWithLifecycle()
  val playbackProgressState = viewModel.playbackProgress.collectAsStateWithLifecycle()

  val isPlayingAudio by viewModel.isPlayingAudio.collectAsStateWithLifecycle()
  val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
  val playbackDurationMs by viewModel.playbackDurationMs.collectAsStateWithLifecycle()
  val hasPlayableAudio by viewModel.hasPlayableAudio.collectAsStateWithLifecycle()
  val isLoopingAudio by viewModel.isLoopingAudio.collectAsStateWithLifecycle()
  val isScrubbingAudio by viewModel.isScrubbingAudio.collectAsStateWithLifecycle()

  var showWordsDialog by rememberSaveable { mutableStateOf(false) }
  var wordsInput by rememberSaveable(analysis?.fileName, analysis?.sampleWordsText) {
    mutableStateOf(analysis?.sampleWordsText ?: "")
  }
  var wordsKind by rememberSaveable(analysis?.fileName, analysis?.sampleWordsKind) {
    mutableStateOf(analysis?.sampleWordsKind ?: "Heard clearly")
  }

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
    viewModel.onMicrophonePermissionResult(isGranted)
    if (isGranted) {
      viewModel.startRecording()
    } else {
      Toast.makeText(context, "Microphone permission is required for live recording", Toast.LENGTH_SHORT).show()
    }
  }

  // Permission Launcher for Microphone Speech/Hook Transcription (gemini-3.5-transcribe)
  val transcribePermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    viewModel.onMicrophonePermissionResult(isGranted)
    if (isGranted) {
      viewModel.startMicTranscription(TranscriptionTarget.ANALYZE_SAMPLE_WORDS)
    } else {
      Toast.makeText(context, "Microphone permission is required to transcribe audio", Toast.LENGTH_SHORT).show()
    }
  }

  val openAppSettingsAction: () -> Unit = {
    try {
      val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
      ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      context.startActivity(intent)
    } catch (_: Exception) {
      Toast.makeText(context, "Open device Settings > Apps to allow Microphone access.", Toast.LENGTH_SHORT).show()
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioBg)
  ) {
    val isWideLayout = maxWidth >= 760.dp

    if (isWideLayout && analysis != null) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Left Pane: Ingestion + Synced Waveform + Transport + Telemetry
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          if (combinedAudioError != null) {
            item {
              AudioErrorNoticeBanner(
                message = combinedAudioError,
                onDismiss = { viewModel.dismissAudioError() }
              )
            }
          }
          if (micPermissionDenied) {
            item {
              MicPermissionDeniedBanner(
                onOpenSettings = openAppSettingsAction,
                onDismiss = { viewModel.dismissMicPermissionDenied() }
              )
            }
          }
          item {
            IngestionHeroCard(
              isRecording = isRecording,
              isAnalyzing = isAnalyzing,
              hasAnalysis = true,
              recordingDb = recordingDb,
              recordingPeak = recordingPeak,
              activeLane = activeLane,
              liveWaveformState = liveWaveformState,
              sensitivityState = sensitivityState,
              peakHoldState = peakHoldState,
              onPickAudio = { audioPickerLauncher.launch("audio/*") },
              onToggleRecord = {
                if (isRecording) viewModel.stopRecording()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            )
          }

          item {
            AnalyzedWaveformSection(
              analysis = analysis!!,
              activeLane = activeLane,
              isRecording = isRecording,
              hasPlayableAudio = hasPlayableAudio,
              isPlayingAudio = isPlayingAudio,
              isLoopingAudio = isLoopingAudio,
              isScrubbingAudio = isScrubbingAudio,
              playbackPositionMs = playbackPositionMs,
              playbackDurationMs = playbackDurationMs,
              playbackProgressState = playbackProgressState,
              sensitivityState = sensitivityState,
              peakHoldState = peakHoldState,
              onSeek = { viewModel.seekAudioToFraction(it) },
              onScrubStart = { viewModel.beginAudioScrub(it) },
              onScrubPreview = { viewModel.previewAudioScrub(it) },
              onScrubEnd = { viewModel.endAudioScrub(it) },
              onTogglePlayPause = { viewModel.togglePlayPauseAudio() },
              onStop = { viewModel.stopAudioPlayback() },
              onToggleLoop = { viewModel.toggleAudioLoop() }
            )
          }

          item {
            AudioMeterCard(
              analysis = analysis!!,
              onBpmMultiplier = { viewModel.adjustBpmMultiplier(it) }
            )
          }
          if (analysis!!.analysisNote != null || !analysis!!.isAudioMeasured) {
            item {
              AnalysisHonestNoticeBanner(
                note = analysis!!.analysisNote ?: "Audio signal metrics could not be reliably measured; fields show Unread or Estimate."
              )
            }
          }
        }

        // Right Pane: Honesty Words + Gemini Analyst + Handoff Actions
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            SampleHonestyCard(
              analysis = analysis!!,
              heardVocalText = aiConsultation?.heardVocalText,
              transcriptionState = micTranscriptionState,
              onToggleTranscribeMic = {
                if (micTranscriptionState is TranscriptionUiState.Recording) {
                  viewModel.stopMicTranscription()
                } else {
                  transcribePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              },
              onRetryTranscribe = { viewModel.retryLastTranscription() },
              onClearWords = { viewModel.setSampleWords(null) },
              onOpenEditWords = {
                wordsInput = analysis?.sampleWordsText ?: ""
                wordsKind = analysis?.sampleWordsKind ?: "Heard clearly"
                showWordsDialog = true
              }
            )
          }

          item {
            GeminiAnalystCard(
              analysis = analysis!!,
              aiConsultation = aiConsultation,
              aiState = aiConsultationState,
              onRetryAi = { viewModel.retryAiConsultation() },
              onCopyPrompt = { title, prompt ->
                viewModel.copyToClipboard(context, title, prompt)
                viewModel.recordGeneratedPrompt(
                  prompt = prompt,
                  source = "Analyze",
                  lane = analysis?.suggestedLane ?: activeLane,
                  bpm = analysis?.bpm?.toInt()
                )
              },
              onSendPromptToBuild = { prompt ->
                viewModel.updateStylePrompt(prompt)
                viewModel.recordGeneratedPrompt(
                  prompt = prompt,
                  source = "Send-to-Build",
                  lane = analysis?.suggestedLane ?: activeLane,
                  bpm = analysis?.bpm?.toInt()
                )
                viewModel.setTab(StudioTab.BUILD)
              }
            )
          }

          item {
            HandoffActionsColumn(
              analysis = analysis!!,
              heardVocalText = aiConsultation?.heardVocalText,
              onFullPreserve = { viewModel.loadFullPreservePackageFromAnalysis() },
              onSendToBuild = { viewModel.sendAnalysisToBuild() },
              onSendToSimple = { viewModel.sendAnalysisToSimple() },
              onCopyReport = { report -> viewModel.copyToClipboard(context, "Beat Analysis Report", report) }
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        if (combinedAudioError != null) {
          item {
            AudioErrorNoticeBanner(
              message = combinedAudioError,
              onDismiss = { viewModel.dismissAudioError() }
            )
          }
        }
        if (micPermissionDenied) {
          item {
            MicPermissionDeniedBanner(
              onOpenSettings = openAppSettingsAction,
              onDismiss = { viewModel.dismissMicPermissionDenied() }
            )
          }
        }
        item {
          IngestionHeroCard(
            isRecording = isRecording,
            isAnalyzing = isAnalyzing,
            hasAnalysis = analysis != null,
            recordingDb = recordingDb,
            recordingPeak = recordingPeak,
            activeLane = activeLane,
            liveWaveformState = liveWaveformState,
            sensitivityState = sensitivityState,
            peakHoldState = peakHoldState,
            onPickAudio = { audioPickerLauncher.launch("audio/*") },
            onToggleRecord = {
              if (isRecording) viewModel.stopRecording()
              else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          )
        }

        if (analysis != null) {
          item {
            AnalyzedWaveformSection(
              analysis = analysis!!,
              activeLane = activeLane,
              isRecording = isRecording,
              hasPlayableAudio = hasPlayableAudio,
              isPlayingAudio = isPlayingAudio,
              isLoopingAudio = isLoopingAudio,
              isScrubbingAudio = isScrubbingAudio,
              playbackPositionMs = playbackPositionMs,
              playbackDurationMs = playbackDurationMs,
              playbackProgressState = playbackProgressState,
              sensitivityState = sensitivityState,
              peakHoldState = peakHoldState,
              onSeek = { viewModel.seekAudioToFraction(it) },
              onScrubStart = { viewModel.beginAudioScrub(it) },
              onScrubPreview = { viewModel.previewAudioScrub(it) },
              onScrubEnd = { viewModel.endAudioScrub(it) },
              onTogglePlayPause = { viewModel.togglePlayPauseAudio() },
              onStop = { viewModel.stopAudioPlayback() },
              onToggleLoop = { viewModel.toggleAudioLoop() }
            )
          }

          item {
            AudioMeterCard(
              analysis = analysis!!,
              onBpmMultiplier = { viewModel.adjustBpmMultiplier(it) }
            )
          }
          if (analysis!!.analysisNote != null || !analysis!!.isAudioMeasured) {
            item {
              AnalysisHonestNoticeBanner(
                note = analysis!!.analysisNote ?: "Audio signal metrics could not be reliably measured; fields show Unread or Estimate."
              )
            }
          }

          item {
            SampleHonestyCard(
              analysis = analysis!!,
              heardVocalText = aiConsultation?.heardVocalText,
              transcriptionState = micTranscriptionState,
              onToggleTranscribeMic = {
                if (micTranscriptionState is TranscriptionUiState.Recording) {
                  viewModel.stopMicTranscription()
                } else {
                  transcribePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              },
              onRetryTranscribe = { viewModel.retryLastTranscription() },
              onClearWords = { viewModel.setSampleWords(null) },
              onOpenEditWords = {
                wordsInput = analysis?.sampleWordsText ?: ""
                wordsKind = analysis?.sampleWordsKind ?: "Heard clearly"
                showWordsDialog = true
              }
            )
          }

          item {
            GeminiAnalystCard(
              analysis = analysis!!,
              aiConsultation = aiConsultation,
              aiState = aiConsultationState,
              onRetryAi = { viewModel.retryAiConsultation() },
              onCopyPrompt = { title, prompt ->
                viewModel.copyToClipboard(context, title, prompt)
                viewModel.recordGeneratedPrompt(
                  prompt = prompt,
                  source = "Analyze",
                  lane = analysis?.suggestedLane ?: activeLane,
                  bpm = analysis?.bpm?.toInt()
                )
              },
              onSendPromptToBuild = { prompt ->
                viewModel.updateStylePrompt(prompt)
                viewModel.recordGeneratedPrompt(
                  prompt = prompt,
                  source = "Send-to-Build",
                  lane = analysis?.suggestedLane ?: activeLane,
                  bpm = analysis?.bpm?.toInt()
                )
                viewModel.setTab(StudioTab.BUILD)
              }
            )
          }

          item {
            HandoffActionsColumn(
              analysis = analysis!!,
              heardVocalText = aiConsultation?.heardVocalText,
              onFullPreserve = { viewModel.loadFullPreservePackageFromAnalysis() },
              onSendToBuild = { viewModel.sendAnalysisToBuild() },
              onSendToSimple = { viewModel.sendAnalysisToSimple() },
              onCopyReport = { report -> viewModel.copyToClipboard(context, "Beat Analysis Report", report) }
            )
          }
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
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { wordsKind = kind }
                  .border(0.5.dp, StudioBorder, RoundedCornerShape(12.dp))
              ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                  Text(
                    text = kind,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) Color.Black else TextPrimary
                  )
                }
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
          colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Text("Save Words", color = Color.Black)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showWordsDialog = false },
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioCardElevated
    )
  }
}

@Composable
private fun AudioErrorNoticeBanner(
  message: String,
  onDismiss: () -> Unit
) {
  Surface(
    color = Color(0x22EF4444),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioMiss.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
      .testTag("analyze_audio_error_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = "Audio error", tint = StudioMiss, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = TextPrimary
        )
      }
      IconButton(
        onClick = onDismiss,
        modifier = Modifier
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .testTag("action_dismiss_audio_error")
      ) {
        Icon(Icons.Default.Close, contentDescription = "Dismiss audio error", tint = TextSecondary, modifier = Modifier.size(18.dp))
      }
    }
  }
}

@Composable
private fun AnalysisHonestNoticeBanner(note: String) {
  Surface(
    color = Color(0x1FF5A524),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, AmberGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
      .testTag("analyze_honest_dsp_notice")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(Icons.Default.Info, contentDescription = "DSP measurement notice", tint = AmberGold, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = TextPrimary
      )
    }
  }
}

@Composable
private fun MicPermissionDeniedBanner(
  onOpenSettings: () -> Unit,
  onDismiss: () -> Unit
) {
  Surface(
    color = StudioCardElevated,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, AmberGold.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
      .testTag("mic_permission_denied_banner")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.MicOff, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Microphone Access Denied",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("action_dismiss_mic_denied")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Dismiss microphone permission notice", tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
      }
      Text(
        text = "You can still import audio files and edit all Suno prompts offline. To enable live mic recording or dictation, allow Microphone in App Settings.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(
        onClick = onOpenSettings,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      ) {
        Text("Open App Settings", color = StudioCyan, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
      }
    }
  }
}

@Composable
private fun IngestionHeroCard(
  isRecording: Boolean,
  isAnalyzing: Boolean,
  hasAnalysis: Boolean,
  recordingDb: Float,
  recordingPeak: Float,
  activeLane: String,
  liveWaveformState: State<FloatArray>,
  sensitivityState: State<Float>,
  peakHoldState: State<Boolean>,
  onPickAudio: () -> Unit,
  onToggleRecord: () -> Unit
) {
  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
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
        Button(
          onClick = onPickAudio,
          colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("action_pick_audio")
        ) {
          Icon(Icons.Default.AudioFile, contentDescription = "Import audio file", tint = StudioCyan)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Import File", color = TextPrimary)
        }

        Button(
          onClick = onToggleRecord,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecording) StudioMiss else AmberGold
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("action_mic_record")
        ) {
          Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop recording" else "Start live mic recording",
            tint = if (isRecording) Color.White else Color.Black
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isRecording) "Stop (${recordingDb.toInt()}dB)" else "Live Record",
            color = if (isRecording) Color.White else Color.Black
          )
        }
      }

      if (isRecording) {
        Spacer(modifier = Modifier.height(12.dp))
        RealtimeWaveformVisualizer(
          amplitudesProvider = { liveWaveformState.value },
          isLiveRecording = true,
          sensitivityProvider = { sensitivityState.value },
          peakHoldEnabledProvider = { peakHoldState.value },
          activeLane = activeLane,
          beatResetKey = "live_recording",
          currentPeakDb = recordingPeak,
          currentRmsDb = recordingDb
        )
      } else if (!hasAnalysis && !isAnalyzing) {
        Spacer(modifier = Modifier.height(12.dp))
        RealtimeWaveformVisualizer(
          amplitudesProvider = { liveWaveformState.value },
          isLiveRecording = false,
          sensitivityProvider = { sensitivityState.value },
          peakHoldEnabledProvider = { peakHoldState.value },
          activeLane = activeLane,
          beatResetKey = "standby",
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
          Text(
            "Analyzing FFT, Chromagram, and Transient Flux...",
            style = MaterialTheme.typography.labelMedium,
            color = StudioCyan
          )
        }
      }
    }
  }
}

@Composable
private fun AnalyzedWaveformSection(
  analysis: AudioAnalysisResult,
  activeLane: String,
  isRecording: Boolean,
  hasPlayableAudio: Boolean,
  isPlayingAudio: Boolean,
  isLoopingAudio: Boolean,
  isScrubbingAudio: Boolean,
  playbackPositionMs: Long,
  playbackDurationMs: Long,
  playbackProgressState: State<Float>,
  sensitivityState: State<Float>,
  peakHoldState: State<Boolean>,
  onSeek: (Float) -> Unit,
  onScrubStart: (Float) -> Unit,
  onScrubPreview: (Float) -> Unit,
  onScrubEnd: (Float) -> Unit,
  onTogglePlayPause: () -> Unit,
  onStop: () -> Unit,
  onToggleLoop: () -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    RealtimeWaveformVisualizer(
      amplitudesProvider = { analysis.waveformPeaksArray },
      isLiveRecording = false,
      sensitivityProvider = { sensitivityState.value },
      peakHoldEnabledProvider = { peakHoldState.value },
      playheadProgressProvider = if (hasPlayableAudio && !isRecording) {
        { playbackProgressState.value }
      } else null,
      onSeek = if (hasPlayableAudio && !isRecording) onSeek else null,
      playbackDurationMs = if (hasPlayableAudio && !isRecording) playbackDurationMs else 0L,
      isPlayingAudio = isPlayingAudio,
      isLoopingAudio = isLoopingAudio,
      isScrubbingAudio = isScrubbingAudio,
      onScrubStart = if (hasPlayableAudio && !isRecording) onScrubStart else null,
      onScrubPreview = if (hasPlayableAudio && !isRecording) onScrubPreview else null,
      onScrubEnd = if (hasPlayableAudio && !isRecording) onScrubEnd else null,
      activeLane = activeLane,
      beatResetKey = analysis.fileName,
      bpm = analysis.bpm,
      durationSec = analysis.durationSec
    )

    if (hasPlayableAudio && !isRecording) {
      WaveformTransportBar(
        isPlaying = isPlayingAudio,
        isLooping = isLoopingAudio,
        positionMs = playbackPositionMs,
        durationMs = playbackDurationMs,
        progress = playbackProgressState.value,
        onTogglePlayPause = onTogglePlayPause,
        onStop = onStop,
        onToggleLoop = onToggleLoop,
        onSeekFraction = onSeek
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SampleHonestyCard(
  analysis: AudioAnalysisResult,
  heardVocalText: String?,
  transcriptionState: TranscriptionUiState,
  onToggleTranscribeMic: () -> Unit,
  onRetryTranscribe: () -> Unit,
  onClearWords: () -> Unit,
  onOpenEditWords: () -> Unit
) {
  val isRecordingMic = transcriptionState is TranscriptionUiState.Recording &&
    transcriptionState.target == TranscriptionTarget.ANALYZE_SAMPLE_WORDS
  val isTranscribingMic = transcriptionState is TranscriptionUiState.Transcribing &&
    transcriptionState.target == TranscriptionTarget.ANALYZE_SAMPLE_WORDS
  val transcribeError = (transcriptionState as? TranscriptionUiState.Error)
    ?.takeIf { it.target == TranscriptionTarget.ANALYZE_SAMPLE_WORDS }

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "WHAT THE SAMPLE SAYS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(
          color = Color(0x3310B981),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "HONESTY LAW: NO GUESSING",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = StudioWinner,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Surface(
        color = StudioBg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          if (analysis.sampleWordsText != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = analysis.sampleWordsKind ?: "Heard clearly",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                  color = StudioCyan,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "\"${analysis.sampleWordsText}\"",
                style = MonospacePrompt.copy(fontSize = 12.sp),
                color = TextPrimary
              )
            }
          } else {
            Text(
              text = heardVocalText ?: "No words made out. (Sampled vocals remain preservation-locked)",
              style = MonospacePrompt.copy(fontSize = 12.sp),
              color = TextSecondary
            )
          }
        }
      }

      if (isTranscribingMic) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(color = StudioCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Transcribing vocal hook with gemini-3.5-transcribe...",
            style = MaterialTheme.typography.labelSmall,
            color = StudioCyan
          )
        }
      } else if (transcribeError != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = transcribeError.message,
            style = MaterialTheme.typography.labelSmall,
            color = StudioMiss,
            modifier = Modifier.weight(1f)
          )
          if (transcribeError.canRetry) {
            TextButton(
              onClick = onRetryTranscribe,
              modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
              Text("Retry", color = AmberGold, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (analysis.sampleWordsText != null) {
          TextButton(
            onClick = onClearWords,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          ) {
            Text("Clear", style = MaterialTheme.typography.labelSmall, color = StudioMiss)
          }
        }
        Button(
          onClick = onToggleTranscribeMic,
          enabled = !isTranscribingMic,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecordingMic) StudioMiss else StudioCardElevated
          ),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
              contentDescription = if (isRecordingMic) "Stop microphone transcription" else "Transcribe sample hook from microphone"
            }
            .testTag("action_analyze_transcribe_mic")
        ) {
          Icon(
            imageVector = if (isRecordingMic) Icons.Default.Stop else Icons.Default.GraphicEq,
            contentDescription = null,
            tint = if (isRecordingMic) Color.White else AmberGold,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isRecordingMic) "Stop (${(transcriptionState as TranscriptionUiState.Recording).dbLevel.toInt()}dB)" else "Transcribe Mic",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isRecordingMic) Color.White else AmberGold
          )
        }
        OutlinedButton(
          onClick = onOpenEditWords,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Icon(Icons.Default.Edit, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (analysis.sampleWordsText != null) "Edit" else "Type",
            style = MaterialTheme.typography.labelSmall,
            color = StudioCyan
          )
        }
      }
    }
  }
}

@Composable
private fun GeminiAnalystCard(
  analysis: AudioAnalysisResult,
  aiConsultation: com.example.ai.AiBeatConsultation?,
  aiState: AiConsultationUiState,
  onRetryAi: () -> Unit,
  onCopyPrompt: (String, String) -> Unit,
  onSendPromptToBuild: (String) -> Unit
) {
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp)
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "GEMINI MULTIMODAL BEAT ANALYST",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = StudioCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        val statusLabel = when (aiState) {
          is AiConsultationUiState.Loading -> "LIVE"
          is AiConsultationUiState.Success -> "AI VERIFIED"
          is AiConsultationUiState.Error -> "ERROR"
          is AiConsultationUiState.Idle -> "READY"
        }
        val statusColor = when (aiState) {
          is AiConsultationUiState.Loading -> AmberGold
          is AiConsultationUiState.Success -> StudioWinner
          is AiConsultationUiState.Error -> StudioMiss
          is AiConsultationUiState.Idle -> TextMuted
        }
        Surface(
          color = statusColor.copy(alpha = 0.16f),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = statusLabel,
            style = MonospaceMetric.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = statusColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      if (aiState is AiConsultationUiState.Loading) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          color = StudioBg,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_loading_banner")
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(
              color = StudioCyan,
              strokeWidth = 2.dp,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = aiState.stage,
              style = MaterialTheme.typography.labelMedium,
              color = StudioCyan
            )
          }
        }
      } else if (aiState is AiConsultationUiState.Error) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          color = Color(0x22EF4444),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioMiss.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .testTag("ai_error_banner")
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = StudioMiss, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Cloud AI Consultation Unavailable",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = StudioMiss
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = aiState.message,
              style = MaterialTheme.typography.bodySmall,
              color = TextPrimary
            )
            if (aiState.canRetry) {
              Spacer(modifier = Modifier.height(8.dp))
              Button(
                onClick = onRetryAi,
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("action_retry_ai_analysis")
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry AI Analysis", color = Color.Black)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = aiConsultation?.tacticalAdvice ?: "Keep the uploaded sample leading at full dynamic range. Add crisp half-time snare and tuned 808 sub.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextPrimary
      )

      if (!aiConsultation?.suggestedSettings.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = aiConsultation?.suggestedSettings.orEmpty(),
          style = MonospaceMetric.copy(fontSize = 11.sp),
          color = AmberGoldLight
        )
      }

      val cardContext = LocalContext.current
      val uriHandler = LocalUriHandler.current
      if (!aiConsultation?.searchGroundingSummary.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          color = StudioBg,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("analyze_search_grounding_box")
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Language, contentDescription = "Google Search Grounding", tint = StudioCyan, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "SEARCH-GROUNDED SUNO V6 INSIGHT (GEMINI-4-HYDROGEN)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = StudioCyan
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = aiConsultation?.searchGroundingSummary.orEmpty(),
              style = MaterialTheme.typography.labelSmall,
              color = TextPrimary
            )
            val groundedSources = aiConsultation?.groundedSources.orEmpty()
            if (groundedSources.isNotEmpty()) {
              Spacer(modifier = Modifier.height(6.dp))
              groundedSources.take(3).forEach { src ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                      if (GeminiBeatService.isSafeHttpUri(src.uri)) {
                        try {
                          uriHandler.openUri(src.uri)
                        } catch (_: Exception) {
                          Toast.makeText(cardContext, "No browser app available to open link.", Toast.LENGTH_SHORT).show()
                        }
                      } else {
                        Toast.makeText(cardContext, "Invalid or unsupported link URL.", Toast.LENGTH_SHORT).show()
                      }
                    }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Open grounded source ${src.title}" },
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = src.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text("Suggested Lane Match:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
      Text(analysis.suggestedLane, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)

      val suggestions = aiConsultation?.suggestions ?: emptyList()
      if (suggestions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text("SUGGESTED SUNO V6 PROMPTS (<= 200 CHARS):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberGold)
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
                  Text(
                    text = sug.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = AmberGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                      .weight(1f)
                      .padding(end = 6.dp)
                  )
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    var sugCopiedFlash by remember { mutableStateOf(false) }
                    LaunchedEffect(sugCopiedFlash) {
                      if (sugCopiedFlash) {
                        kotlinx.coroutines.delay(1200L)
                        sugCopiedFlash = false
                      }
                    }
                    Text("${sug.chars} / 200c", style = MonospaceMetric.copy(fontSize = 11.sp), color = StudioCyan)
                    IconButton(
                      onClick = {
                        sugCopiedFlash = true
                        onCopyPrompt(sug.title, sug.prompt)
                      },
                      modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("action_copy_ai_suggestion_${sug.title.lowercase().replace(" ", "_")}")
                    ) {
                      Icon(
                        imageVector = if (sugCopiedFlash) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy Prompt: ${sug.title}",
                        tint = if (sugCopiedFlash) StudioWinner else TextSecondary,
                        modifier = Modifier.size(16.dp)
                      )
                    }
                    IconButton(
                      onClick = { onSendPromptToBuild(sug.prompt) },
                      modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                      Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send ${sug.title} to Build", tint = AmberGold, modifier = Modifier.size(16.dp))
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

@Composable
private fun HandoffActionsColumn(
  analysis: AudioAnalysisResult,
  heardVocalText: String?,
  onFullPreserve: () -> Unit,
  onSendToBuild: () -> Unit,
  onSendToSimple: () -> Unit,
  onCopyReport: (String) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Button(
      onClick = onFullPreserve,
      colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .testTag("action_load_full_preserve")
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
        onClick = onSendToBuild,
        colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minHeight = 48.dp)
          .testTag("action_send_to_build")
      ) {
        Icon(Icons.Default.Build, contentDescription = null, tint = AmberGold)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Send to Build", color = TextPrimary)
      }

      Button(
        onClick = onSendToSimple,
        colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minHeight = 48.dp)
          .testTag("action_send_to_simple")
      ) {
        Icon(Icons.Default.Tune, contentDescription = null, tint = StudioCyan)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Send to Simple", color = TextPrimary)
      }
    }

    OutlinedButton(
      onClick = {
        val tempoText = analysis.bpm?.let {
          val est = if (analysis.isTempoEstimate) " (estimate)" else ""
          "${it.toInt()} BPM$est (${analysis.bpmConfidence} confidence)"
        } ?: "Unread"
        val centroidText = if (analysis.isAudioMeasured && analysis.isSpectralMeasured) {
          "${analysis.centroidHz.toInt()} Hz (${analysis.pocketLabel})"
        } else {
          "Unread (${analysis.pocketLabel})"
        }
        val levelText = if (analysis.isAudioMeasured) {
          "Peak ${"%.1f".format(analysis.peakDb)} dBFS · RMS ${"%.1f".format(analysis.rmsDb)} dBFS"
        } else {
          "Unread"
        }
        val report = """=== BOODA BEAT ANALYSIS REPORT ===
File: ${analysis.fileName}
Tempo: $tempoText
Musical Key: ${analysis.keyName ?: "Unread"}
Levels: $levelText
Brightness Centroid: $centroidText
Onset Density: ${if (analysis.isAudioMeasured) "${analysis.onsetRate} hits/sec" else "Unread"}
Suggested Lane: ${analysis.suggestedLane}
Sample Vocals: ${heardVocalText ?: (analysis.sampleWordsText ?: "No clear words isolated")}

[SUGGESTED SUNO PRESERVE PROMPT]
${analysis.promptPreserve}

[SUGGESTED SUNO ORIGINAL PROMPT]
${analysis.promptOriginal}
===================================""".trimIndent()
        onCopyReport(report)
      },
      colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .testTag("action_copy_analysis_report")
    ) {
      Icon(Icons.Default.ContentCopy, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Copy Full Analysis Report", style = MaterialTheme.typography.labelLarge, color = StudioCyan)
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
