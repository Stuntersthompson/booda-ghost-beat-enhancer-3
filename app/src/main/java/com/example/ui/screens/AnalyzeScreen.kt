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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioAnalysisResult
import com.example.ui.components.AudioMeterCard
import com.example.ui.components.RealtimeWaveformVisualizer
import com.example.ui.components.WaveformTransportBar
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab

@Composable
fun AnalyzeScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val analysis by viewModel.audioAnalysis.collectAsStateWithLifecycle()
  val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
  val recordingDb by viewModel.recordingDb.collectAsStateWithLifecycle()
  val recordingPeak by viewModel.recordingPeak.collectAsStateWithLifecycle()
  val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
  val aiConsultation by viewModel.aiConsultation.collectAsStateWithLifecycle()
  val activeLane by viewModel.activeLane.collectAsStateWithLifecycle()

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
    if (isGranted) {
      viewModel.startRecording()
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
              playbackPositionMs = playbackPositionMs,
              playbackDurationMs = playbackDurationMs,
              playbackProgressState = playbackProgressState,
              sensitivityState = sensitivityState,
              peakHoldState = peakHoldState,
              onSeek = { viewModel.seekAudioToFraction(it) },
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
              onCopyPrompt = { title, prompt -> viewModel.copyToClipboard(context, title, prompt) },
              onSendPromptToBuild = { prompt ->
                viewModel.updateStylePrompt(prompt)
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
              playbackPositionMs = playbackPositionMs,
              playbackDurationMs = playbackDurationMs,
              playbackProgressState = playbackProgressState,
              sensitivityState = sensitivityState,
              peakHoldState = peakHoldState,
              onSeek = { viewModel.seekAudioToFraction(it) },
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

          item {
            SampleHonestyCard(
              analysis = analysis!!,
              heardVocalText = aiConsultation?.heardVocalText,
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
              onCopyPrompt = { title, prompt -> viewModel.copyToClipboard(context, title, prompt) },
              onSendPromptToBuild = { prompt ->
                viewModel.updateStylePrompt(prompt)
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
                  .minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { wordsKind = kind }
                  .border(0.5.dp, StudioBorder, RoundedCornerShape(12.dp))
              ) {
                Text(
                  text = kind,
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = if (selected) Color.Black else TextPrimary,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
  playbackPositionMs: Long,
  playbackDurationMs: Long,
  playbackProgressState: State<Float>,
  sensitivityState: State<Float>,
  peakHoldState: State<Boolean>,
  onSeek: (Float) -> Unit,
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

@Composable
private fun SampleHonestyCard(
  analysis: AudioAnalysisResult,
  heardVocalText: String?,
  onClearWords: () -> Unit,
  onOpenEditWords: () -> Unit
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

      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (analysis.sampleWordsText != null) {
          TextButton(
            onClick = onClearWords,
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Text("Clear Words", style = MaterialTheme.typography.labelSmall, color = StudioMiss)
          }
          Spacer(modifier = Modifier.width(4.dp))
        }
        OutlinedButton(
          onClick = onOpenEditWords,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.minimumInteractiveComponentSize().defaultMinSize(minHeight = 36.dp)
        ) {
          Icon(Icons.Default.Edit, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (analysis.sampleWordsText != null) "Edit Words" else "Type What You Heard",
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
                  Text(sug.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${sug.chars} / 200c", style = MonospaceMetric.copy(fontSize = 11.sp), color = StudioCyan)
                    IconButton(
                      onClick = { onCopyPrompt(sug.title, sug.prompt) },
                      modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                      Icon(Icons.Default.ContentCopy, contentDescription = "Copy ${sug.title} prompt", tint = TextSecondary, modifier = Modifier.size(16.dp))
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
        val report = """=== BOODA BEAT ANALYSIS REPORT ===
File: ${analysis.fileName}
Tempo: ${analysis.bpm?.toInt() ?: 0} BPM (${analysis.bpmConfidence} confidence)
Musical Key: ${analysis.keyName ?: "Unread"}
Brightness Centroid: ${analysis.centroidHz.toInt()} Hz (${analysis.pocketLabel})
Onset Density: ${analysis.onsetRate} hits/sec
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
