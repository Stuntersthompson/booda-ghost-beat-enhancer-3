package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.TranscriptionTarget
import com.example.ai.TranscriptionUiState
import com.example.audio.PlaybackMath
import com.example.data.model.*
import com.example.data.repository.HistoryImportMode
import com.example.ui.components.BeatIntensityCanvasMeter
import com.example.ui.components.CompactWaveformMeter
import com.example.ui.components.PromptBox
import com.example.ui.components.QuickTemplatesBar
import com.example.ui.components.RecentPromptsHistoryCard
import com.example.ui.components.SaveTemplateDialog
import com.example.ui.components.SettingsCardView
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab

@Composable
fun BuildScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val jobType by viewModel.jobType.collectAsStateWithLifecycle()
  val activeLane by viewModel.activeLane.collectAsStateWithLifecycle()
  val stylePrompt by viewModel.stylePrompt.collectAsStateWithLifecycle()
  val lyricsMap by viewModel.lyricsMap.collectAsStateWithLifecycle()
  val excludeText by viewModel.excludeText.collectAsStateWithLifecycle()

  val variety by viewModel.variety.collectAsStateWithLifecycle()
  val weirdness by viewModel.weirdness.collectAsStateWithLifecycle()
  val styleInfluence by viewModel.styleInfluence.collectAsStateWithLifecycle()
  val audioInfluence by viewModel.audioInfluence.collectAsStateWithLifecycle()

  val audioAnalysisState = viewModel.audioAnalysis.collectAsStateWithLifecycle()
  val liveWaveformState = viewModel.liveWaveform.collectAsStateWithLifecycle()
  val sensitivityState = viewModel.visualizerSensitivity.collectAsStateWithLifecycle()
  val peakHoldState = viewModel.peakHoldEnabled.collectAsStateWithLifecycle()
  val playbackProgressState = viewModel.playbackProgress.collectAsStateWithLifecycle()

  val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
  val hasPlayableAudio by viewModel.hasPlayableAudio.collectAsStateWithLifecycle()
  val isPlayingAudio by viewModel.isPlayingAudio.collectAsStateWithLifecycle()
  val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
  val playbackDurationMs by viewModel.playbackDurationMs.collectAsStateWithLifecycle()
  val isLoopingAudio by viewModel.isLoopingAudio.collectAsStateWithLifecycle()
  val isScrubbingAudio by viewModel.isScrubbingAudio.collectAsStateWithLifecycle()
  val handoffStepsDone by viewModel.handoffStepsDone.collectAsStateWithLifecycle()
  val recentHistory by viewModel.recentPromptHistory.collectAsStateWithLifecycle()
  val promptTemplates by viewModel.promptTemplates.collectAsStateWithLifecycle()
  val beatMeterSnapshot by viewModel.beatMeterSnapshot.collectAsStateWithLifecycle()
  val micTranscriptionState by viewModel.micTranscriptionState.collectAsStateWithLifecycle()
  val micPermissionDenied by viewModel.micPermissionDenied.collectAsStateWithLifecycle()

  var showSaveModal by rememberSaveable { mutableStateOf(false) }
  var showSaveTemplateModal by rememberSaveable { mutableStateOf(false) }
  var takeNameInput by rememberSaveable { mutableStateOf("") }
  var beatTitleInput by rememberSaveable { mutableStateOf("") }
  var showExcludeDropdown by remember { mutableStateOf(false) }
  var pendingTranscriptionTarget by rememberSaveable { mutableStateOf(TranscriptionTarget.BUILD_LYRICS) }
  var pendingExportIncludeSnapshot by rememberSaveable { mutableStateOf(true) }
  var pendingImportMode by rememberSaveable { mutableStateOf(HistoryImportMode.MERGE) }
  var pendingImportRestoreSnapshot by rememberSaveable { mutableStateOf(true) }

  val exportHistoryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.exportPromptHistoryToUri(context, uri, includeSnapshot = pendingExportIncludeSnapshot)
    }
  }

  val importHistoryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.importPromptHistoryFromUri(
        context = context,
        uri = uri,
        restoreSnapshot = pendingImportRestoreSnapshot,
        mode = pendingImportMode
      )
    }
  }

  val dictatePermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    viewModel.onMicrophonePermissionResult(isGranted)
    if (isGranted) {
      viewModel.startMicTranscription(pendingTranscriptionTarget)
    } else {
      Toast.makeText(context, "Microphone permission is required for voice dictation", Toast.LENGTH_SHORT).show()
    }
  }

  val isPreserve = jobType == JobType.PRESERVE
  val hasLockLine = stylePrompt.contains(LOCK_LINE)
  val hasEndTag = lyricsMap.trim().endsWith("[End]")
  val isStyleOk = stylePrompt.length <= STYLE_LIMIT
  val isLyricsOk = lyricsMap.length <= LYRICS_LIMIT
  val isExcludeOk = excludeText.length <= EXCLUDE_LIMIT

  val allClear = (!isPreserve || hasLockLine) && hasEndTag && isStyleOk && isLyricsOk && isExcludeOk
  val audioAnalysis = audioAnalysisState.value

  val monitorCard: @Composable () -> Unit = {
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        .testTag("build_groove_monitor")
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Equalizer,
              contentDescription = null,
              tint = StudioCyan,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "GROOVE PREVIEW MONITOR",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = StudioCyan
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            if (hasPlayableAudio && !isRecording) {
              Text(
                text = PlaybackMath.formatDurationMmSs(playbackPositionMs),
                style = MonospaceMetric.copy(fontSize = 10.sp),
                color = StudioCyan
              )
              FilledIconButton(
                onClick = { viewModel.togglePlayPauseAudio() },
                colors = IconButtonDefaults.filledIconButtonColors(
                  containerColor = if (isPlayingAudio) AmberGold else StudioCyan,
                  contentColor = Color.Black
                ),
                modifier = Modifier
                  .size(48.dp)
                  .testTag("build_monitor_play_pause")
              ) {
                Icon(
                  imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                  contentDescription = if (isPlayingAudio) "Pause groove preview" else "Play groove preview",
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            Surface(
              color = if (audioAnalysis != null) AmberGold.copy(alpha = 0.15f) else StudioCardElevated,
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = if (audioAnalysis != null) {
                  "%.1f BPM • %s".format(audioAnalysis.bpm ?: 130.0, audioAnalysis.keyName ?: "Key")
                } else if (isRecording) {
                  "LIVE MIC"
                } else {
                  "GROOVE CONTOUR"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (audioAnalysis != null) AmberGold else TextSecondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        CompactWaveformMeter(
          amplitudesProvider = {
            val analyzed = audioAnalysisState.value
            if (analyzed != null && analyzed.waveformPeaks.isNotEmpty() && !isRecording) {
              analyzed.waveformPeaksArray
            } else {
              liveWaveformState.value
            }
          },
          sensitivityProvider = { sensitivityState.value },
          peakHoldEnabledProvider = { peakHoldState.value },
          playheadProgressProvider = if (hasPlayableAudio && !isRecording) {
            { playbackProgressState.value }
          } else null,
          onSeek = if (hasPlayableAudio && !isRecording) {
            { fraction -> viewModel.seekAudioToFraction(fraction) }
          } else null,
          playbackDurationMs = if (hasPlayableAudio && !isRecording) playbackDurationMs else 0L,
          isPlayingAudio = isPlayingAudio,
          isLoopingAudio = isLoopingAudio,
          isScrubbingAudio = isScrubbingAudio,
          onScrubStart = if (hasPlayableAudio && !isRecording) {
            { viewModel.beginAudioScrub(it) }
          } else null,
          onScrubPreview = if (hasPlayableAudio && !isRecording) {
            { viewModel.previewAudioScrub(it) }
          } else null,
          onScrubEnd = if (hasPlayableAudio && !isRecording) {
            { viewModel.endAudioScrub(it) }
          } else null,
          activeLane = activeLane,
          beatResetKey = audioAnalysis?.fileName ?: activeLane,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
        )

        if (hasPlayableAudio && !isRecording && playbackDurationMs > 0L) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Tap or drag waveform to scrub (${PlaybackMath.formatDurationMmSs(playbackPositionMs)} / ${PlaybackMath.formatDurationMmSs(playbackDurationMs)})",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextMuted
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        BeatIntensityCanvasMeter(
          snapshot = beatMeterSnapshot,
          sensitivity = sensitivityState.value,
          peakHoldEnabled = peakHoldState.value,
          isLiveAudio = isPlayingAudio || isRecording
        )
      }
    }
  }

  val quickTemplatesSection: @Composable () -> Unit = {
    QuickTemplatesBar(
      templates = promptTemplates,
      onApplyTemplate = { template ->
        viewModel.applyPromptTemplate(template, context = context, switchToBuild = false)
      },
      onSaveCurrentClick = { showSaveTemplateModal = true },
      onBrowseAllClick = { viewModel.setTab(StudioTab.LIBRARY) }
    )
  }

  val quickActionsRow: @Composable () -> Unit = {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { viewModel.injectSlotTemplate() },
        colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minHeight = 48.dp)
          .testTag("action_inject_slot")
      ) {
        Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Slot Inject", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
      }

      Button(
        onClick = { viewModel.randomizeAll() },
        colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minHeight = 48.dp)
          .testTag("action_randomize_all")
      ) {
        Icon(Icons.Default.Shuffle, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Randomize", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
      }
    }
  }

  val voiceDictationCard: @Composable () -> Unit = {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (micPermissionDenied) {
        Surface(
          color = StudioCardElevated,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AmberGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .testTag("mic_permission_denied_banner")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Microphone access denied. Enable it in App Settings to use voice dictation.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
              modifier = Modifier.weight(1f)
            )
            TextButton(
              onClick = {
                try {
                  context.startActivity(
                    Intent(
                      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      Uri.fromParts("package", context.packageName, null)
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                  )
                } catch (_: Exception) {
                  Toast.makeText(context, "Open Settings > Apps to grant Microphone permission.", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
              Text("Settings", color = StudioCyan, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
            IconButton(
              onClick = { viewModel.dismissMicPermissionDenied() },
              modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("action_dismiss_mic_denied")
            ) {
              Icon(Icons.Default.Close, contentDescription = "Dismiss microphone permission notice", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
      BuildVoiceTranscriptionCard(
        state = micTranscriptionState,
        onToggleDictate = { target ->
          if (micTranscriptionState is TranscriptionUiState.Recording) {
            viewModel.stopMicTranscription()
          } else {
            pendingTranscriptionTarget = target
            dictatePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        },
        onApplyToLyrics = { text -> viewModel.applyTranscriptToLyrics(text, context) },
        onApplyToStyle = { text -> viewModel.applyTranscriptToStyle(text, context) },
        onRetry = { viewModel.retryLastTranscription() },
        onDismiss = { viewModel.dismissTranscriptionState() }
      )
    }
  }

  val bundleActionButtons: @Composable () -> Unit = {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Button(
        onClick = { viewModel.copyFullBundle(context) },
        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1.3f)
          .defaultMinSize(minHeight = 48.dp)
          .testTag("action_copy_bundle")
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Copy Bundle", style = MaterialTheme.typography.titleMedium, color = Color.Black)
      }

      Button(
        onClick = {
          takeNameInput = "$activeLane Take"
          beatTitleInput = audioAnalysis?.fileName ?: ""
          showSaveModal = true
        },
        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minHeight = 48.dp)
          .testTag("action_save_take")
      ) {
        Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.Black)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Save Take", style = MaterialTheme.typography.titleMedium, color = Color.Black)
      }
    }
  }

  val handoffCard: @Composable () -> Unit = {
    val doneCount = handoffStepsDone.size
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        .testTag("suno_handoff_card")
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("SUNO V6 HANDOFF CHECKLIST", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)
          }
          Surface(
            color = if (doneCount == 5) Color(0x3310B981) else StudioCardElevated,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "$doneCount OF 5 READY",
              style = MonospaceMetric.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
              color = if (doneCount == 5) StudioWinner else StudioCyan,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        HandoffStepItem(
          title = if (isPreserve) "Route: Extend (Audio Influence $audioInfluence)" else "Route: Create (Standard)",
          sub = if (isPreserve) "Locks uploaded audio backbone" else "Fresh generative beat",
          isDone = handoffStepsDone.contains(1),
          onToggle = { viewModel.toggleHandoffStep(1) },
          onCopy = { viewModel.copyHandoffStep(context, 1) }
        )

        HandoffStepItem(
          title = "Style of Music Box (${stylePrompt.length}/1000c)",
          sub = if (isPreserve && hasLockLine) "Preserve lock line included" else "Base style + lane sound design",
          isDone = handoffStepsDone.contains(2),
          onToggle = { viewModel.toggleHandoffStep(2) },
          onCopy = { viewModel.copyHandoffStep(context, 2) }
        )

        HandoffStepItem(
          title = "Exclude Styles Box (${excludeText.length}/1000c)",
          sub = "Anti-hallucination & clean textures",
          isDone = handoffStepsDone.contains(3),
          onToggle = { viewModel.toggleHandoffStep(3) },
          onCopy = { viewModel.copyHandoffStep(context, 3) }
        )

        HandoffStepItem(
          title = "Lyrics Box Structure (${lyricsMap.length}/5000c)",
          sub = if (hasEndTag) "16-section map ending with [End]" else "Structure map",
          isDone = handoffStepsDone.contains(4),
          onToggle = { viewModel.toggleHandoffStep(4) },
          onCopy = { viewModel.copyHandoffStep(context, 4) }
        )

        HandoffStepItem(
          title = "Tested Parameter Sliders",
          sub = "Variety $variety · Weirdness $weirdness · Style $styleInfluence · Audio $audioInfluence",
          isDone = handoffStepsDone.contains(5),
          onToggle = { viewModel.toggleHandoffStep(5) },
          onCopy = { viewModel.copyHandoffStep(context, 5) }
        )
      }
    }
  }

  val preFlightAuditCard: @Composable () -> Unit = {
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
          Text("PRE-FLIGHT READINESS AUDIT", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
          Surface(
            color = if (allClear) Color(0x3310B981) else Color(0x33EF4444),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = if (allClear) "READY FOR SUNO" else "NEEDS ATTENTION",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = if (allClear) StudioWinner else StudioMiss,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        ChecklistItem(
          title = if (isPreserve) "Preserve Lock Line Present" else "Original Route (No Lock Line Required)",
          isOk = !isPreserve || hasLockLine,
          note = if (isPreserve && !hasLockLine) "Required lock line missing from Style Box" else "Verified"
        )

        ChecklistItem(
          title = "Lyrics Box Closes With [End]",
          isOk = hasEndTag,
          note = if (!hasEndTag) "Missing closing [End] tag" else "Verified"
        )

        ChecklistItem(
          title = "Character Caps Under Limit",
          isOk = isStyleOk && isLyricsOk && isExcludeOk,
          note = "Style: ${stylePrompt.length}/1000 · Lyrics: ${lyricsMap.length}/5000 · Exclude: ${excludeText.length}/1000"
        )

        if (variety > 0) {
          ChecklistItem(
            title = "Variety Slider Advisory",
            isOk = false,
            note = "Variety is $variety (>0 triggers Suno's prompt rewriter; set to 0 for verbatim prompt adherence)"
          )
        }
      }
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioBg)
  ) {
    val isWideLayout = maxWidth >= 760.dp

    if (isWideLayout) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Left Pane: Monitor + Actions + Engine Controls + Checklists
        LazyColumn(
          modifier = Modifier.weight(0.95f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item { monitorCard() }
          item { quickTemplatesSection() }
          item { quickActionsRow() }
          item {
            SettingsCardView(
              jobType = jobType,
              variety = variety,
              onVarietyChange = { viewModel.setVariety(it) },
              weirdness = weirdness,
              onWeirdnessChange = { viewModel.setWeirdness(it) },
              styleInfluence = styleInfluence,
              onStyleInfluenceChange = { viewModel.setStyleInfluence(it) },
              audioInfluence = audioInfluence,
              onAudioInfluenceChange = { viewModel.setAudioInfluence(it) },
              onResetToDefaults = { viewModel.resetToPreserveDefaults() }
            )
          }
          item { handoffCard() }
          item { preFlightAuditCard() }
        }

        // Right Pane: Recent 20 Recall + Prompt Boxes + Copy/Save Bundle
        LazyColumn(
          modifier = Modifier.weight(1.05f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item { voiceDictationCard() }
          item {
            RecentPromptsHistoryCard(
              history = recentHistory,
              onRecallVariation = { entry ->
                viewModel.recallPromptVariation(entry, navigateToBuild = false, context = context)
              },
              onCopyPrompt = { entry ->
                viewModel.copyToClipboard(context, "Suno Prompt", entry.prompt)
              },
              onDeleteEntry = { id -> viewModel.deletePromptHistoryEntry(id) },
              onClearAll = { viewModel.clearPromptHistory() },
              onExportBackupFile = { includeSnap ->
                pendingExportIncludeSnapshot = includeSnap
                exportHistoryLauncher.launch("booda_prompt_history.json")
              },
              onCopyBackupJson = { includeSnap ->
                viewModel.copyPromptHistoryToClipboard(context, includeSnapshot = includeSnap)
              },
              onImportBackupFile = { mode, restoreSnap ->
                pendingImportMode = mode
                pendingImportRestoreSnapshot = restoreSnap
                importHistoryLauncher.launch(arrayOf("application/json", "text/plain"))
              },
              onImportBackupJson = { json, mode, restore ->
                viewModel.importPromptHistoryFromJson(context, json, restoreSnapshot = restore, mode = mode)
              },
              validateSchema = viewModel::validateHistoryBackupSchema
            )
          }
          item {
            PromptBox(
              title = "Style of Music",
              text = stylePrompt,
              onTextChanged = { viewModel.updateStylePrompt(it) },
              limit = STYLE_LIMIT,
              minLines = 3,
              isPreserve = isPreserve,
              onCopy = { viewModel.copyToClipboard(context, "Style Prompt", stylePrompt) }
            )
          }
          item {
            PromptBox(
              title = "Exclude Styles (Negatives)",
              text = excludeText,
              onTextChanged = { viewModel.updateExcludeText(it) },
              limit = EXCLUDE_LIMIT,
              minLines = 2,
              extraHeaderAction = {
                Box {
                  TextButton(
                    onClick = { showExcludeDropdown = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Text("Banks ▾", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
                  }
                  DropdownMenu(
                    expanded = showExcludeDropdown,
                    onDismissRequest = { showExcludeDropdown = false }
                  ) {
                    viewModel.bankRepo.excludeBanks.forEach { eb ->
                      DropdownMenuItem(
                        text = { Text(eb.name) },
                        onClick = {
                          viewModel.selectExcludeBank(eb.name)
                          showExcludeDropdown = false
                        }
                      )
                    }
                  }
                }
              },
              onCopy = { viewModel.copyToClipboard(context, "Exclude Styles", excludeText) }
            )
          }
          item {
            PromptBox(
              title = "Lyrics Box (Structure Map)",
              text = lyricsMap,
              onTextChanged = { viewModel.updateLyricsMap(it) },
              limit = LYRICS_LIMIT,
              minLines = 6,
              extraHeaderAction = {
                TextButton(
                  onClick = { viewModel.updateLyricsMap(viewModel.bankRepo.buildV6LyricsMap(activeLane, jobType)) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Text("Regen Map", style = MaterialTheme.typography.labelSmall, color = AmberGold)
                }
              },
              onCopy = { viewModel.copyToClipboard(context, "Lyrics Map", lyricsMap) }
            )
          }
          item { bundleActionButtons() }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp)
          .testTag("build_list"),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item { monitorCard() }
        item { quickTemplatesSection() }
        item { quickActionsRow() }
        item { voiceDictationCard() }
        item {
          RecentPromptsHistoryCard(
            history = recentHistory,
            onRecallVariation = { entry ->
              viewModel.recallPromptVariation(entry, navigateToBuild = false, context = context)
            },
            onCopyPrompt = { entry ->
              viewModel.copyToClipboard(context, "Suno Prompt", entry.prompt)
            },
            onDeleteEntry = { id -> viewModel.deletePromptHistoryEntry(id) },
            onClearAll = { viewModel.clearPromptHistory() },
            onExportBackupFile = { includeSnap ->
              pendingExportIncludeSnapshot = includeSnap
              exportHistoryLauncher.launch("booda_prompt_history.json")
            },
            onCopyBackupJson = { includeSnap ->
              viewModel.copyPromptHistoryToClipboard(context, includeSnapshot = includeSnap)
            },
            onImportBackupFile = { mode, restoreSnap ->
              pendingImportMode = mode
              pendingImportRestoreSnapshot = restoreSnap
              importHistoryLauncher.launch(arrayOf("application/json", "text/plain"))
            },
            onImportBackupJson = { json, mode, restore ->
              viewModel.importPromptHistoryFromJson(context, json, restoreSnapshot = restore, mode = mode)
            },
            validateSchema = viewModel::validateHistoryBackupSchema
          )
        }
        item {
          PromptBox(
            title = "Style of Music",
            text = stylePrompt,
            onTextChanged = { viewModel.updateStylePrompt(it) },
            limit = STYLE_LIMIT,
            minLines = 3,
            isPreserve = isPreserve,
            onCopy = { viewModel.copyToClipboard(context, "Style Prompt", stylePrompt) }
          )
        }
        item {
          PromptBox(
            title = "Exclude Styles (Negatives)",
            text = excludeText,
            onTextChanged = { viewModel.updateExcludeText(it) },
            limit = EXCLUDE_LIMIT,
            minLines = 2,
            extraHeaderAction = {
              Box {
                TextButton(
                  onClick = { showExcludeDropdown = true },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Text("Banks ▾", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
                }
                DropdownMenu(
                  expanded = showExcludeDropdown,
                  onDismissRequest = { showExcludeDropdown = false }
                ) {
                  viewModel.bankRepo.excludeBanks.forEach { eb ->
                    DropdownMenuItem(
                      text = { Text(eb.name) },
                      onClick = {
                        viewModel.selectExcludeBank(eb.name)
                        showExcludeDropdown = false
                      }
                    )
                  }
                }
              }
            },
            onCopy = { viewModel.copyToClipboard(context, "Exclude Styles", excludeText) }
          )
        }
        item {
          PromptBox(
            title = "Lyrics Box (Structure Map)",
            text = lyricsMap,
            onTextChanged = { viewModel.updateLyricsMap(it) },
            limit = LYRICS_LIMIT,
            minLines = 6,
            extraHeaderAction = {
              TextButton(
                onClick = { viewModel.updateLyricsMap(viewModel.bankRepo.buildV6LyricsMap(activeLane, jobType)) },
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Text("Regen Map", style = MaterialTheme.typography.labelSmall, color = AmberGold)
              }
            },
            onCopy = { viewModel.copyToClipboard(context, "Lyrics Map", lyricsMap) }
          )
        }
        item {
          SettingsCardView(
            jobType = jobType,
            variety = variety,
            onVarietyChange = { viewModel.setVariety(it) },
            weirdness = weirdness,
            onWeirdnessChange = { viewModel.setWeirdness(it) },
            styleInfluence = styleInfluence,
            onStyleInfluenceChange = { viewModel.setStyleInfluence(it) },
            audioInfluence = audioInfluence,
            onAudioInfluenceChange = { viewModel.setAudioInfluence(it) },
            onResetToDefaults = { viewModel.resetToPreserveDefaults() }
          )
        }
        item { handoffCard() }
        item { preFlightAuditCard() }
        item { bundleActionButtons() }
      }
    }
  }

  // Save Take Modal
  if (showSaveModal) {
    AlertDialog(
      onDismissRequest = { showSaveModal = false },
      title = { Text("Save Take to Logbook", color = TextPrimary) },
      text = {
        Column {
          Text("Assign a name to log this Suno package and track its generation outcome (max 120 chars):", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = takeNameInput,
            onValueChange = { if (it.length <= 120) takeNameInput = it },
            label = { Text("Take Name (${takeNameInput.length}/120)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_save_take_name")
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = beatTitleInput,
            onValueChange = { if (it.length <= 120) beatTitleInput = it },
            label = { Text("Beat Title / Project (${beatTitleInput.length}/120)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_save_beat_title")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.saveCurrentTake(
              takeName = takeNameInput,
              beatTitle = beatTitleInput,
              context = context
            )
            showSaveModal = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Text("Save Take", color = Color.Black)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showSaveModal = false },
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioCardElevated
    )
  }

  if (showSaveTemplateModal) {
    val defaultBpm = audioAnalysis?.bpm?.let { "${it.toInt()} BPM" } ?: ""
    val defaultCat = when {
      activeLane.contains("lo-fi", ignoreCase = true) || activeLane.contains("jazz", ignoreCase = true) -> "Lo-Fi Hip Hop"
      activeLane.contains("dark", ignoreCase = true) || activeLane.contains("phonk", ignoreCase = true) -> "Dark Synthwave"
      activeLane.contains("drill", ignoreCase = true) -> "UK Drill"
      activeLane.contains("boom bap", ignoreCase = true) -> "Boom Bap"
      activeLane.contains("afro", ignoreCase = true) -> "Afro-Trap"
      activeLane.contains("trap", ignoreCase = true) -> "Trap"
      else -> "Custom"
    }
    SaveTemplateDialog(
      defaultTitle = "$activeLane Preset",
      defaultCategory = defaultCat,
      defaultBpmHint = defaultBpm,
      onDismiss = { showSaveTemplateModal = false },
      onSave = { title, category, bpmHint ->
        viewModel.saveCurrentAsTemplate(title = title, category = category, bpmHint = bpmHint, context = context)
        showSaveTemplateModal = false
      }
    )
  }
}

@Composable
private fun HandoffStepItem(
  title: String,
  sub: String,
  isDone: Boolean,
  onToggle: () -> Unit,
  onCopy: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f)
    ) {
      IconButton(
        onClick = onToggle,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Icon(
          imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
          contentDescription = if (isDone) "Mark $title incomplete" else "Mark $title complete",
          tint = if (isDone) StudioWinner else TextMuted,
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.width(4.dp))
      Column {
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
      }
    }
    IconButton(
      onClick = onCopy,
      modifier = Modifier.minimumInteractiveComponentSize()
    ) {
      Icon(Icons.Default.ContentCopy, contentDescription = "Copy $title", tint = StudioCyan, modifier = Modifier.size(18.dp))
    }
  }
}

@Composable
private fun ChecklistItem(title: String, isOk: Boolean, note: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
      contentDescription = if (isOk) "$title passed" else "$title needs attention",
      tint = if (isOk) StudioWinner else StudioMiss,
      modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
      Text(note, style = MaterialTheme.typography.labelSmall, color = if (isOk) TextSecondary else StudioMiss)
    }
  }
}

@Composable
private fun BuildVoiceTranscriptionCard(
  state: TranscriptionUiState,
  onToggleDictate: (TranscriptionTarget) -> Unit,
  onApplyToLyrics: (String) -> Unit,
  onApplyToStyle: (String) -> Unit,
  onRetry: () -> Unit,
  onDismiss: () -> Unit
) {
  val isRecording = state is TranscriptionUiState.Recording
  val isTranscribing = state is TranscriptionUiState.Transcribing

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
      .testTag("build_mic_transcribe_card")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Microphone dictation",
            tint = AmberGold,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "VOICE HOOK & PROMPT DICTATION",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
        }
        Surface(
          color = StudioCardElevated,
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "GEMINI-3.5-TRANSCRIBE",
            style = MonospaceMetric.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = StudioCyan,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val recordingLyrics = state is TranscriptionUiState.Recording && state.target == TranscriptionTarget.BUILD_LYRICS
        val recordingStyle = state is TranscriptionUiState.Recording && state.target == TranscriptionTarget.BUILD_STYLE

        Button(
          onClick = { onToggleDictate(TranscriptionTarget.BUILD_LYRICS) },
          enabled = !isTranscribing && (!isRecording || recordingLyrics),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (recordingLyrics) StudioMiss else StudioCardElevated
          ),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
              contentDescription = if (recordingLyrics) "Stop dictating lyrics hook" else "Dictate lyrics hook with microphone"
            }
            .testTag("action_build_dictate_lyrics")
        ) {
          Icon(
            imageVector = if (recordingLyrics) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
            contentDescription = null,
            tint = if (recordingLyrics) Color.White else AmberGold,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (recordingLyrics) "Stop (${state.dbLevel.toInt()}dB)" else "Dictate Lyrics",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (recordingLyrics) Color.White else TextPrimary
          )
        }

        Button(
          onClick = { onToggleDictate(TranscriptionTarget.BUILD_STYLE) },
          enabled = !isTranscribing && (!isRecording || recordingStyle),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (recordingStyle) StudioMiss else StudioCardElevated
          ),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
              contentDescription = if (recordingStyle) "Stop dictating style cue" else "Dictate style cue with microphone"
            }
            .testTag("action_build_dictate_style")
        ) {
          Icon(
            imageVector = if (recordingStyle) Icons.Default.Stop else Icons.Default.GraphicEq,
            contentDescription = null,
            tint = if (recordingStyle) Color.White else StudioCyan,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (recordingStyle) "Stop (${state.dbLevel.toInt()}dB)" else "Dictate Style",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (recordingStyle) Color.White else TextPrimary
          )
        }
      }

      when (state) {
        is TranscriptionUiState.Transcribing -> {
          Spacer(modifier = Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = StudioCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Transcribing microphone audio via gemini-3.5-transcribe...",
              style = MaterialTheme.typography.labelSmall,
              color = StudioCyan
            )
          }
        }

        is TranscriptionUiState.Error -> {
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = state.message,
              style = MaterialTheme.typography.labelSmall,
              color = StudioMiss,
              modifier = Modifier.weight(1f)
            )
            if (state.canRetry) {
              TextButton(
                onClick = onRetry,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              ) {
                Text("Retry", color = AmberGold, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
              }
            }
          }
        }

        is TranscriptionUiState.Success -> {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            color = StudioBg,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("build_transcript_result")
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("TRANSCRIBED AUDIO", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = StudioWinner)
                IconButton(
                  onClick = onDismiss,
                  modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Dismiss transcript", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
              }
              Text(
                text = "\"${state.text}\"",
                style = MonospacePrompt.copy(fontSize = 12.sp),
                color = TextPrimary
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedButton(
                  onClick = { onApplyToLyrics(state.text) },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("action_apply_transcript_lyrics")
                ) {
                  Text("Insert to Lyrics", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberGold)
                }
                OutlinedButton(
                  onClick = { onApplyToStyle(state.text) },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("action_apply_transcript_style")
                ) {
                  Text("Add to Style", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = StudioCyan)
                }
              }
            }
          }
        }

        else -> Unit
      }
    }
  }
}
