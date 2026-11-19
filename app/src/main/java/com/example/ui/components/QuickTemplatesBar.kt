package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobType
import com.example.data.model.PromptTemplateEntity
import com.example.data.repository.PromptTemplateRepository
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.MonospaceMetric
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Horizontal Room-backed Quick Templates bar on the Build screen for one-tap recall
 * of pinned and frequently used Suno prompt templates (e.g., Lo-Fi Hip Hop, Dark Synthwave).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickTemplatesBar(
  templates: List<PromptTemplateEntity>,
  onApplyTemplate: (PromptTemplateEntity) -> Unit,
  onSaveCurrentClick: () -> Unit,
  onBrowseAllClick: () -> Unit,
  modifier: Modifier = Modifier,
  appliedTemplateId: Long? = null
) {
  var pinnedOnly by rememberSaveable { mutableStateOf(false) }
  val visibleTemplates = remember(templates, pinnedOnly) {
    if (pinnedOnly) templates.filter { it.isPinned } else templates
  }

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
      .testTag("build_quick_templates_bar")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.align(Alignment.CenterVertically).padding(end = 6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Bookmarks,
            contentDescription = null,
            tint = AmberGold,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "QUICK TEMPLATES",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          TextButton(
            onClick = { pinnedOnly = !pinnedOnly },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .semantics { contentDescription = if (pinnedOnly) "Show all quick templates" else "Show pinned quick templates only" }
              .testTag("action_toggle_pinned_quick_templates")
          ) {
            Icon(
              imageVector = Icons.Default.PushPin,
              contentDescription = null,
              tint = if (pinnedOnly) AmberGold else TextMuted,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = if (pinnedOnly) "Pinned" else "All",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = if (pinnedOnly) AmberGold else TextSecondary,
              maxLines = 1
            )
          }

          TextButton(
            onClick = onBrowseAllClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .semantics { contentDescription = "Open full templates library" }
              .testTag("action_browse_templates")
          ) {
            Text(
              text = "All (${templates.size})",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = StudioCyan,
              maxLines = 1
            )
          }

          OutlinedButton(
            onClick = onSaveCurrentClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .semantics { contentDescription = "Save current beat parameters as a reusable template" }
              .testTag("action_save_template")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = AmberGold, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Save Preset",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = AmberGold,
              maxLines = 1
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      if (visibleTemplates.isEmpty()) {
        Text(
          text = if (pinnedOnly) {
            "No pinned templates yet. Tap All or pin favorites in Library."
          } else {
            "No saved templates yet. Tap Save Preset to store your current prompt & sliders."
          },
          style = MaterialTheme.typography.bodySmall,
          color = TextMuted
        )
      } else {
        LazyRow(
          contentPadding = PaddingValues(horizontal = 2.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          itemsIndexed(
            visibleTemplates.take(12),
            key = { index, tmpl -> if (tmpl.id != 0L) tmpl.id else "${tmpl.title}_${tmpl.category}_$index" }
          ) { _, template ->
            val isApplied = appliedTemplateId != null && appliedTemplateId == template.id
            val isPreserve = JobType.fromString(template.jobType) == JobType.PRESERVE
            Surface(
              color = when {
                isApplied -> AmberContainer
                else -> StudioCardElevated
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .widthIn(min = 128.dp, max = 210.dp)
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                  width = if (isApplied) 1.5.dp else 0.75.dp,
                  color = when {
                    isApplied -> StudioCyan
                    template.isPinned -> AmberGold.copy(alpha = 0.55f)
                    else -> StudioBorder
                  },
                  shape = RoundedCornerShape(10.dp)
                )
                .clickable { onApplyTemplate(template) }
                .semantics {
                  role = Role.Button
                  contentDescription =
                    "Apply ${template.title} template, category ${template.category}, ${template.bpmHint.ifBlank { template.lane }}, used ${template.useCount} times"
                }
                .testTag("quick_template_chip_${template.id}")
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.Center
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  if (isApplied) {
                    Icon(
                      imageVector = Icons.Default.CheckCircle,
                      contentDescription = null,
                      tint = StudioCyan,
                      modifier = Modifier.size(12.dp)
                    )
                  } else if (template.isPinned) {
                    Icon(
                      imageVector = Icons.Default.PushPin,
                      contentDescription = null,
                      tint = AmberGold,
                      modifier = Modifier.size(11.dp)
                    )
                  }
                  Text(
                    text = template.category,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (isPreserve) AmberGold else StudioCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                  )
                  if (template.bpmHint.isNotBlank()) {
                    Text(
                      text = "· ${template.bpmHint}",
                      style = MonospaceMetric.copy(fontSize = 9.sp),
                      color = TextSecondary,
                      maxLines = 1
                    )
                  } else if (template.useCount > 0) {
                    Text(
                      text = "×${template.useCount}",
                      style = MonospaceMetric.copy(fontSize = 9.sp),
                      color = TextMuted,
                      maxLines = 1
                    )
                  }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = template.title,
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = TextPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SaveTemplateDialog(
  defaultTitle: String,
  defaultCategory: String,
  defaultBpmHint: String,
  onDismiss: () -> Unit,
  onSave: (title: String, category: String, bpmHint: String) -> Unit
) {
  var title by rememberSaveable { mutableStateOf(defaultTitle) }
  var category by rememberSaveable { mutableStateOf(defaultCategory) }
  var bpmHint by rememberSaveable { mutableStateOf(defaultBpmHint) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = StudioCard,
    title = {
      Text(
        text = "Save Suno Prompt Template",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = AmberGold
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Saves your prompt boxes, lane, track mode, and Suno sliders locally for one-tap reuse.",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary
        )
        OutlinedTextField(
          value = title,
          onValueChange = { title = it.take(60) },
          label = { Text("Template Name", color = TextSecondary) },
          placeholder = { Text("e.g., Midnight Rhodes Lo-Fi", color = TextMuted) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = StudioBg,
            unfocusedContainerColor = StudioBg,
            focusedBorderColor = StudioCyan,
            unfocusedBorderColor = StudioBorder
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_template_title")
        )

        OutlinedTextField(
          value = category,
          onValueChange = { category = it.take(40) },
          label = { Text("Category", color = TextSecondary) },
          placeholder = { Text("e.g., Lo-Fi Hip Hop, Dark Synthwave", color = TextMuted) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = StudioBg,
            unfocusedContainerColor = StudioBg,
            focusedBorderColor = StudioCyan,
            unfocusedBorderColor = StudioBorder
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_template_category")
        )

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(PromptTemplateRepository.DEFAULT_CATEGORIES) { cat ->
            val selected = category.equals(cat, ignoreCase = true)
            Surface(
              color = if (selected) StudioCyan else StudioCardElevated,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { category = cat }
                .semantics {
                  role = Role.Button
                  contentDescription = "Choose category $cat"
                }
            ) {
              Text(
                text = cat,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) Color.Black else TextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
              )
            }
          }
        }

        OutlinedTextField(
          value = bpmHint,
          onValueChange = { bpmHint = it.take(24) },
          label = { Text("BPM / Tag (optional)", color = TextSecondary) },
          placeholder = { Text("e.g., 84 BPM", color = TextMuted) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = StudioBg,
            unfocusedContainerColor = StudioBg,
            focusedBorderColor = StudioCyan,
            unfocusedBorderColor = StudioBorder
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_template_bpm")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(title, category, bpmHint) },
        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
        modifier = Modifier
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .testTag("action_confirm_save_template")
      ) {
        Text("Save Template", color = Color.Black, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .testTag("action_cancel_save_template")
      ) {
        Text("Cancel", color = TextSecondary)
      }
    }
  )
}
