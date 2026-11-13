package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.data.model.*
import com.example.ui.components.CompactWaveformMeter
import com.example.ui.components.PromptBox
import com.example.ui.components.SettingsCardView
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun BuildScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val jobType by viewModel.jobType.collectAsState()
  val activeLane by viewModel.activeLane.collectAsState()
  val stylePrompt by viewModel.stylePrompt.collectAsState()
  val lyricsMap by viewModel.lyricsMap.collectAsState()
  val excludeText by viewModel.excludeText.collectAsState()

  val variety by viewModel.variety.collectAsState()
  val weirdness by viewModel.weirdness.collectAsState()
  val styleInfluence by viewModel.styleInfluence.collectAsState()
  val audioInfluence by viewModel.audioInfluence.collectAsState()

  val audioAnalysis by viewModel.audioAnalysis.collectAsState()
  val liveWaveform by viewModel.liveWaveform.collectAsState()
  val isRecording by viewModel.isRecording.collectAsState()
  val visualizerSensitivity by viewModel.visualizerSensitivity.collectAsState()

  var showSaveModal by remember { mutableStateOf(false) }
  var takeNameInput by remember { mutableStateOf("") }
  var beatTitleInput by remember { mutableStateOf("") }
  var showExcludeDropdown by remember { mutableStateOf(false) }

  val isPreserve = jobType == JobType.PRESERVE
  val hasLockLine = stylePrompt.contains(LOCK_LINE)
  val hasEndTag = lyricsMap.trim().endsWith("[End]")
  val isStyleOk = stylePrompt.length <= STYLE_LIMIT
  val isLyricsOk = lyricsMap.length <= LYRICS_LIMIT
  val isExcludeOk = excludeText.length <= EXCLUDE_LIMIT

  val allClear = (!isPreserve || hasLockLine) && hasEndTag && isStyleOk && isLyricsOk && isExcludeOk

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioBg)
      .padding(horizontal = 14.dp),
    contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Studio Groove & Audio Preview Monitor
    item {
      val displayAmps = if (audioAnalysis != null && audioAnalysis!!.waveformPeaks.isNotEmpty()) {
        audioAnalysis!!.waveformPeaks
      } else {
        liveWaveform
      }

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
            Surface(
              color = if (audioAnalysis != null) AmberGold.copy(alpha = 0.15f) else StudioCardElevated,
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = if (audioAnalysis != null) {
                  "%.1f BPM • %s".format(audioAnalysis!!.bpm, audioAnalysis!!.keyName)
                } else if (isRecording) {
                  "LIVE MIC MONITOR"
                } else {
                  "GROOVE CONTOUR"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (audioAnalysis != null) AmberGold else TextSecondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          CompactWaveformMeter(
            amplitudes = displayAmps,
            sensitivity = visualizerSensitivity,
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
          )
        }
      }
    }

    // Quick Actions Bar
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = { viewModel.injectSlotTemplate() },
          colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f).testTag("action_inject_slot")
        ) {
          Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Slot Inject", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
        }

        Button(
          onClick = { viewModel.randomizeAll() },
          colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f).testTag("action_randomize_all")
        ) {
          Icon(Icons.Default.Shuffle, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Randomize", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
        }
      }
    }

    // 1. Style Prompt Box
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

    // 2. Exclude Styles Box
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
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
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

    // 3. Lyrics Map Box
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
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text("Regen Map", style = MaterialTheme.typography.labelSmall, color = AmberGold)
          }
        },
        onCopy = { viewModel.copyToClipboard(context, "Lyrics Map", lyricsMap) }
      )
    }

    // 4. Suno v6 Settings Card
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

    // 5. Suno v6 Step-by-Step Handoff Checklist
    item {
      val handoffStepsDone by viewModel.handoffStepsDone.collectAsState()
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
              Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
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
            stepNumber = 1,
            title = if (isPreserve) "Route: Extend (Audio Influence $audioInfluence)" else "Route: Create (Standard)",
            sub = if (isPreserve) "Locks uploaded audio backbone" else "Fresh generative beat",
            isDone = handoffStepsDone.contains(1),
            onToggle = { viewModel.toggleHandoffStep(1) },
            onCopy = { viewModel.copyHandoffStep(context, 1) }
          )

          HandoffStepItem(
            stepNumber = 2,
            title = "Style of Music Box (${stylePrompt.length}/1000c)",
            sub = if (isPreserve && hasLockLine) "Preserve lock line included" else "Base style + lane sound design",
            isDone = handoffStepsDone.contains(2),
            onToggle = { viewModel.toggleHandoffStep(2) },
            onCopy = { viewModel.copyHandoffStep(context, 2) }
          )

          HandoffStepItem(
            stepNumber = 3,
            title = "Exclude Styles Box (${excludeText.length}/1000c)",
            sub = "Anti-hallucination & clean textures",
            isDone = handoffStepsDone.contains(3),
            onToggle = { viewModel.toggleHandoffStep(3) },
            onCopy = { viewModel.copyHandoffStep(context, 3) }
          )

          HandoffStepItem(
            stepNumber = 4,
            title = "Lyrics Box Structure (${lyricsMap.length}/5000c)",
            sub = if (hasEndTag) "16-section map ending with [End]" else "Structure map",
            isDone = handoffStepsDone.contains(4),
            onToggle = { viewModel.toggleHandoffStep(4) },
            onCopy = { viewModel.copyHandoffStep(context, 4) }
          )

          HandoffStepItem(
            stepNumber = 5,
            title = "Tested Parameter Sliders",
            sub = "Variety $variety · Weirdness $weirdness · Style $styleInfluence · Audio $audioInfluence",
            isDone = handoffStepsDone.contains(5),
            onToggle = { viewModel.toggleHandoffStep(5) },
            onCopy = { viewModel.copyHandoffStep(context, 5) }
          )
        }
      }
    }

    // 6. Pre-flight Quality Checklist
    item {
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

    // 7. Action Buttons: Copy Bundle & Save Take
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = { viewModel.copyFullBundle(context) },
          colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1.3f).height(48.dp).testTag("action_copy_bundle")
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Copy Bundle", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        }

        Button(
          onClick = {
            takeNameInput = "$activeLane Take"
            showSaveModal = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("action_save_take")
        ) {
          Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.Black)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Save Take", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        }
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
  stepNumber: Int,
  title: String,
  sub: String,
  isDone: Boolean,
  onToggle: () -> Unit,
  onCopy: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f)
    ) {
      IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
          contentDescription = null,
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
    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
      Icon(Icons.Default.ContentCopy, contentDescription = "Copy step", tint = StudioCyan, modifier = Modifier.size(16.dp))
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
