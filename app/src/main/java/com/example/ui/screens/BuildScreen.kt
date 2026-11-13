package com.example.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.PlaybackMath
import com.example.data.model.*
import com.example.ui.components.CompactWaveformMeter
import com.example.ui.components.PromptBox
import com.example.ui.components.SettingsCardView
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

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
  val handoffStepsDone by viewModel.handoffStepsDone.collectAsStateWithLifecycle()

  var showSaveModal by rememberSaveable { mutableStateOf(false) }
  var takeNameInput by rememberSaveable { mutableStateOf("") }
  var beatTitleInput by rememberSaveable { mutableStateOf("") }
  var showExcludeDropdown by remember { mutableStateOf(false) }

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
                  .minimumInteractiveComponentSize()
                  .size(30.dp)
                  .testTag("build_monitor_play_pause")
              ) {
                Icon(
                  imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                  contentDescription = if (isPlayingAudio) "Pause groove preview" else "Play groove preview",
                  modifier = Modifier.size(16.dp)
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
      }
    }
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

        // Right Pane: Prompt Boxes + Copy/Save Bundle
        LazyColumn(
          modifier = Modifier.weight(1.05f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
          .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item { monitorCard() }
        item { quickActionsRow() }
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
          colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
        ) {
          Text("Save Take", color = Color.Black)
        }
      },
      dismissButton = {
        TextButton(onClick = { showSaveModal = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioCardElevated
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
      contentDescription = null,
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
