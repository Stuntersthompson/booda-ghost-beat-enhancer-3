package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobType
import com.example.data.model.PromptHistoryEntity
import com.example.data.model.STYLE_LIMIT
import com.example.data.repository.PromptHistoryRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun RecentPromptsHistoryCard(
  history: List<PromptHistoryEntity>,
  onRecallVariation: (PromptHistoryEntity) -> Unit,
  onCopyPrompt: (PromptHistoryEntity) -> Unit,
  onDeleteEntry: (Long) -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
  defaultExpanded: Boolean = true
) {
  var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }
  var showClearDialog by rememberSaveable { mutableStateOf(false) }

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
      .testTag("recent_prompts_history_card")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .semantics {
              contentDescription = if (expanded) {
                "Collapse recent 20 generated prompt variations"
              } else {
                "Expand recent 20 generated prompt variations"
              }
            }
            .padding(vertical = 4.dp)
            .testTag("recent_prompts_toggle")
        ) {
          Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = AmberGold,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "RECENT ${PromptHistoryRepository.MAX_HISTORY_COUNT} VARIATIONS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "${history.size} / ${PromptHistoryRepository.MAX_HISTORY_COUNT}",
              style = MonospaceMetric.copy(fontSize = 11.sp),
              color = StudioCyan,
              modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("recent_prompts_count")
            )
          }
          if (history.isNotEmpty()) {
            IconButton(
              onClick = { showClearDialog = true },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("action_clear_prompt_history")
            ) {
              Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "Clear prompt history",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
              )
            }
          }
          IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Icon(
              imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = if (expanded) "Collapse history" else "Expand history",
              tint = TextSecondary,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
          if (history.isEmpty()) {
            Surface(
              color = StudioBg,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("recent_prompts_empty")
            ) {
              Text(
                text = "Variations generated via Builder, Analyze, Randomize, and Send-to-Build are saved automatically for instant 1-tap recall.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(12.dp)
              )
            }
          } else {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("recent_prompts_row")
            ) {
              items(
                items = history,
                key = { it.id }
              ) { entry ->
                RecentHistoryVariationCard(
                  entry = entry,
                  onRecall = { onRecallVariation(entry) },
                  onCopy = { onCopyPrompt(entry) },
                  onDelete = { onDeleteEntry(entry.id) }
                )
              }
            }
          }
        }
      }
    }
  }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      title = { Text("Clear Prompt History?", color = TextPrimary) },
      text = {
        Text(
          "Remove all ${history.size} saved prompt variations from local history?",
          color = TextSecondary,
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onClearAll()
            showClearDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = StudioMiss)
        ) {
          Text("Clear History", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioCardElevated
    )
  }
}

@Composable
private fun RecentHistoryVariationCard(
  entry: PromptHistoryEntity,
  onRecall: () -> Unit,
  onCopy: () -> Unit,
  onDelete: () -> Unit
) {
  var isCopiedAnimation by remember { mutableStateOf(false) }
  LaunchedEffect(isCopiedAnimation) {
    if (isCopiedAnimation) {
      delay(1200)
      isCopiedAnimation = false
    }
  }

  val isCyanSource = entry.source.contains("Analyze", ignoreCase = true) ||
    entry.source.contains("Send", ignoreCase = true) ||
    entry.source.contains("Preset", ignoreCase = true)
  val sourceAccent = if (isCyanSource) StudioCyan else AmberGold
  val modeLabel = if (JobType.fromString(entry.jobType) == JobType.PRESERVE) "Preserve" else "Original"

  Surface(
    color = StudioCardElevated,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .width(276.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable { onRecall() }
      .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
      .semantics {
        contentDescription = "Load ${entry.source} ${entry.lane} prompt variation into Build"
      }
      .testTag("history_prompt_card_${entry.id}")
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.weight(1f)
        ) {
          Surface(
            color = sourceAccent.copy(alpha = 0.18f),
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = entry.source,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = sourceAccent,
              maxLines = 1,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
          }
          Text(
            text = entry.lane,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = {
              isCopiedAnimation = true
              onCopy()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("action_copy_history_${entry.id}")
          ) {
            Icon(
              imageVector = if (isCopiedAnimation) Icons.Default.Check else Icons.Default.ContentCopy,
              contentDescription = "Copy Prompt",
              tint = if (isCopiedAnimation) StudioWinner else StudioCyan,
              modifier = Modifier.size(16.dp)
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("action_delete_history_${entry.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Remove variation from history",
              tint = TextMuted,
              modifier = Modifier.size(15.dp)
            )
          }
        }
      }

      Surface(
        color = StudioBg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = entry.prompt,
          style = MonospacePrompt.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
          color = TextPrimary,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(8.dp)
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Tap card to Load in Build",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
          color = AmberGold
        )
        val bpmText = entry.bpm?.let { " · ${it} BPM" } ?: ""
        Text(
          text = "$modeLabel$bpmText · ${entry.chars}c",
          style = MonospaceMetric.copy(fontSize = 10.sp),
          color = if (entry.chars <= STYLE_LIMIT) StudioCyan else StudioMiss
        )
      }
    }
  }
}
