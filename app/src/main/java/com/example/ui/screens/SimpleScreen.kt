package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.SIMPLE_WORKING_LIMIT
import com.example.data.model.STYLE_LIMIT
import com.example.data.model.SunoComplianceAuditor
import com.example.ui.components.SunoPromptWorkstation
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimpleScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val jobType by viewModel.jobType.collectAsStateWithLifecycle()
  val activeLane by viewModel.activeLane.collectAsStateWithLifecycle()
  val simplePrompt by viewModel.simplePrompt.collectAsStateWithLifecycle()

  val isPreserve = jobType == JobType.PRESERVE
  val remixTemplates = remember(activeLane) { viewModel.bankRepo.getRemixTemplates(activeLane) }

  val headerBlock: @Composable () -> Unit = {
    Column {
      Text(
        text = "SIMPLE COMPOSER (SINGLE BOX)",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = AmberGold
      )
      Text(
        text = "Unified all-in-one prompt combining lane texture, drum moves, and full-song arrangement directives. Working limit ~3,000 characters.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )
    }
  }

  val quickActionsBlock: @Composable () -> Unit = {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { viewModel.updateSimplePrompt() },
        colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .semantics { contentDescription = "Regenerate Simple composer prompt" }
          .testTag("action_regen_simple")
      ) {
        Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Regenerate", color = TextPrimary)
      }

      Button(
        onClick = { viewModel.randomizeAll() },
        colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .semantics { contentDescription = "Randomize lane and Simple composer prompt" }
          .testTag("action_random_simple")
      ) {
        Icon(Icons.Default.Shuffle, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Randomize", color = TextPrimary)
      }
    }
  }

  val remixFamiliesBlock: @Composable () -> Unit = {
    Column {
      Text(
        text = "REMIX FAMILIES FOR $activeLane",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = TextMuted
      )
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(remixTemplates, key = { it.id }) { rt ->
          Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .clip(RoundedCornerShape(14.dp))
              .semantics {
                role = Role.Button
                contentDescription = "Load ${rt.family} remix template for $activeLane"
              }
              .clickable {
                val newPrompt = if (isPreserve) {
                  "${rt.prompt}\n\n[Instrumental]\n[Intro] (uploaded sample plays solo exactly as performed)\n[Verse 1] (canvas drums enter underneath)\n[Build-Up] (tension builds into chorus drop)\n[Chorus 1] (full canvas drop at peak weight)\n[Breakdown] (sample plays solo untouched)\n[Chorus 2] (final heaviest drop)\n[Outro] (canvas drums fade, sample finishes solo)\n[End]"
                } else {
                  "${rt.prompt.replace("$LOCK_LINE. ", "")}\n\n[Instrumental]\n[Intro] (melodic atmosphere)\n[Verse 1] (full groove enters)\n[Build-Up] (build into chorus)\n[Chorus 1] (peak drop energy)\n[Breakdown] (melody solo)\n[Chorus 2] (heaviest finish)\n[Outro] (clean fade)\n[End]"
                }
                viewModel.updateSimplePrompt(newPrompt)
              }
              .border(0.5.dp, StudioBorder, RoundedCornerShape(14.dp))
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
              Text(
                text = rt.family,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = StudioCyan
              )
            }
          }
        }
      }
    }
  }

  val promptEditorCard: @Composable () -> Unit = {
    SunoPromptWorkstation(
      title = "Simple Composer · v6 Workstation",
      promptText = simplePrompt,
      onPromptChanged = { viewModel.updateSimplePrompt(it) },
      onToggleChip = { chip -> viewModel.toggleWorkstationChipForSimple(chip, context) },
      onCopy = { viewModel.copyToClipboard(context, "Simple Prompt", simplePrompt) },
      maxChars = SIMPLE_WORKING_LIMIT,
      styleHeaderCap = STYLE_LIMIT,
      isSimpleBoxMode = true,
      isPreserve = isPreserve,
      activeLane = activeLane,
      minLines = 8,
      placeholderText = "Style header (<= 1,000c) followed by [Instrumental] and Suno v6 song structure tags...",
      onInsertLockLine = {
        val body = simplePrompt.trimStart()
        val prefix = "$LOCK_LINE. "
        val next = if (body.isBlank()) prefix.trimEnd() else "$prefix$body"
        viewModel.updateSimplePrompt(next.take(SIMPLE_WORKING_LIMIT))
      },
      onStripArtists = {
        val cleaned = SunoComplianceAuditor.stripArtistNames(simplePrompt)
        viewModel.updateSimplePrompt(cleaned)
      },
      onOpenLaneGrid = { viewModel.setTab(StudioTab.LANES) },
      inputTestTag = "input_simple_prompt",
      copyTestTag = "action_copy_simple"
    )
  }

  val primaryCopyButton: @Composable () -> Unit = {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { viewModel.copyToClipboard(context, "Simple Prompt", simplePrompt) },
        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .semantics { contentDescription = "Copy full Simple Prompt to clipboard" }
          .testTag("action_copy_simple_main")
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Copy Simple Prompt to Clipboard",
          style = MaterialTheme.typography.titleMedium,
          color = Color.Black,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      OutlinedButton(
        onClick = { viewModel.sendSimplePromptToBuild(context) },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .semantics { contentDescription = "Split Simple Prompt into Style and Lyrics boxes in Build" }
          .testTag("action_send_simple_to_build")
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Send to Build (Split Boxes)",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = StudioCyan,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
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
          .padding(horizontal = 16.dp)
          .testTag("simple_wide_layout"),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        LazyColumn(
          modifier = Modifier
            .weight(0.95f)
            .fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item { headerBlock() }
          item { quickActionsBlock() }
          item { remixFamiliesBlock() }
          item { primaryCopyButton() }
        }

        LazyColumn(
          modifier = Modifier
            .weight(1.05f)
            .fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item { promptEditorCard() }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp)
          .testTag("simple_list"),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item { headerBlock() }
        item { quickActionsBlock() }
        item { remixFamiliesBlock() }
        item { promptEditorCard() }
        item { primaryCopyButton() }
      }
    }
  }
}
