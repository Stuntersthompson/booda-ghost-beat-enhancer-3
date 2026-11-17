package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobType
import com.example.data.model.PromptHistoryEntity
import com.example.data.model.STYLE_LIMIT
import com.example.data.repository.HistoryImportMode
import com.example.data.repository.HistorySchemaValidationResult
import com.example.data.repository.PromptHistoryRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecentPromptsHistoryCard(
  history: List<PromptHistoryEntity>,
  onRecallVariation: (PromptHistoryEntity) -> Unit,
  onCopyPrompt: (PromptHistoryEntity) -> Unit,
  onDeleteEntry: (Long) -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
  defaultExpanded: Boolean = true,
  onExportBackupFile: ((includeSnapshot: Boolean) -> Unit)? = null,
  onCopyBackupJson: ((includeSnapshot: Boolean) -> Unit)? = null,
  onImportBackupFile: ((mode: HistoryImportMode, restoreSnapshot: Boolean) -> Unit)? = null,
  onImportBackupJson: ((jsonStr: String, mode: HistoryImportMode, restoreSnapshot: Boolean) -> Unit)? = null,
  validateSchema: (String) -> HistorySchemaValidationResult = PromptHistoryRepository::validateSchema
) {
  val context = LocalContext.current
  var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }
  var showClearDialog by rememberSaveable { mutableStateOf(false) }
  var showBackupSheet by rememberSaveable { mutableStateOf(false) }
  var includeSnapshotInExport by rememberSaveable { mutableStateOf(true) }
  var importMode by rememberSaveable { mutableStateOf(HistoryImportMode.MERGE) }
  var restoreSnapshotToggle by rememberSaveable { mutableStateOf(true) }
  var showPasteSection by rememberSaveable { mutableStateOf(false) }
  var pasteJsonInput by rememberSaveable { mutableStateOf("") }

  val hasBackupActions = onExportBackupFile != null ||
    onCopyBackupJson != null ||
    onImportBackupFile != null ||
    onImportBackupJson != null

  val validationResult = remember(pasteJsonInput) {
    if (pasteJsonInput.isBlank()) null else validateSchema(pasteJsonInput)
  }

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
            .defaultMinSize(minHeight = 48.dp)
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
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
          if (hasBackupActions) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
              horizontalArrangement = Arrangement.Start,
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedButton(
                onClick = { showBackupSheet = true },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .semantics { contentDescription = "Open Backup and Sync bottom sheet to export or import prompt history JSON" }
                  .testTag("action_open_history_sheet")
              ) {
                Icon(
                  imageVector = Icons.Default.ImportExport,
                  contentDescription = null,
                  tint = StudioCyan,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Backup & Sync",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = StudioCyan
                )
              }
            }
          }

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
          colors = ButtonDefaults.buttonColors(containerColor = StudioMiss),
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Text("Clear History", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showClearDialog = false },
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = StudioCardElevated
    )
  }

  if (showBackupSheet && hasBackupActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showBackupSheet = false },
      sheetState = sheetState,
      containerColor = StudioCardElevated,
      contentColor = TextPrimary
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp)
          .testTag("history_backup_bottom_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Sheet Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "RECENT ${PromptHistoryRepository.MAX_HISTORY_COUNT} BACKUP & SYNC",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = AmberGold
            )
            Text(
              text = "Export or restore up to ${PromptHistoryRepository.MAX_HISTORY_COUNT} prompt variations safely offline.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary
            )
          }
          IconButton(
            onClick = { showBackupSheet = false },
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .semantics { contentDescription = "Close Backup and Sync sheet" }
              .testTag("action_close_history_sheet")
          ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
          }
        }

        HorizontalDivider(color = StudioBorder)

        // Section 1: Export JSON
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "EXPORT JSON",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = StudioCyan
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .clip(RoundedCornerShape(8.dp))
              .clickable { includeSnapshotInExport = !includeSnapshotInExport }
              .semantics { contentDescription = "Include current compliance snapshot and sliders in export" }
              .testTag("toggle_include_snapshot_backup")
              .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = includeSnapshotInExport,
              onCheckedChange = { includeSnapshotInExport = it }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Include compliance snapshot & Suno v6 slider settings",
              style = MaterialTheme.typography.bodySmall,
              color = TextPrimary
            )
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            if (onExportBackupFile != null) {
              Button(
                onClick = {
                  onExportBackupFile(includeSnapshotInExport)
                  showBackupSheet = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .semantics { contentDescription = "Export prompt history JSON to a user-selected file" }
                  .testTag("action_sheet_export_file")
              ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save JSON File", color = Color.Black, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
              }
            }
            if (onCopyBackupJson != null) {
              OutlinedButton(
                onClick = {
                  onCopyBackupJson(includeSnapshotInExport)
                  showBackupSheet = false
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .semantics { contentDescription = "Copy prompt history backup JSON to clipboard" }
                  .testTag("action_sheet_copy_json")
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy JSON", color = StudioCyan, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
              }
            }
          }
        }

        HorizontalDivider(color = StudioBorder)

        // Section 2: Import JSON
        if (onImportBackupFile != null || onImportBackupJson != null) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "IMPORT JSON (20-ITEM CAP)",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = StudioCyan
            )

            // Merge / Replace Selector (Merge pre-selected by default)
            Column(modifier = Modifier.selectableGroup()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .defaultMinSize(minHeight = 48.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .selectable(
                    selected = importMode == HistoryImportMode.MERGE,
                    onClick = { importMode = HistoryImportMode.MERGE },
                    role = Role.RadioButton
                  )
                  .semantics { contentDescription = "Merge mode: deduplicate and keep newest 20 variations" }
                  .testTag("radio_import_mode_merge")
                  .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                  selected = importMode == HistoryImportMode.MERGE,
                  onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = "Merge (Recommended)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                  )
                  Text(
                    text = "Deduplicate matching prompts & keep newest ${PromptHistoryRepository.MAX_HISTORY_COUNT} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                  )
                }
              }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .defaultMinSize(minHeight = 48.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .selectable(
                    selected = importMode == HistoryImportMode.REPLACE,
                    onClick = { importMode = HistoryImportMode.REPLACE },
                    role = Role.RadioButton
                  )
                  .semantics { contentDescription = "Replace mode: overwrite current history keeping up to 20 variations" }
                  .testTag("radio_import_mode_replace")
                  .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                  selected = importMode == HistoryImportMode.REPLACE,
                  onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = "Replace",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                  )
                  Text(
                    text = "Clear current history & load imported items (max ${PromptHistoryRepository.MAX_HISTORY_COUNT})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                  )
                }
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { restoreSnapshotToggle = !restoreSnapshotToggle }
                .semantics { contentDescription = "Also restore compliance snapshot and sliders if present in JSON" }
                .testTag("toggle_restore_snapshot_import")
                .padding(horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = restoreSnapshotToggle,
                onCheckedChange = { restoreSnapshotToggle = it }
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Also restore compliance snapshot if included",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              if (onImportBackupFile != null) {
                OutlinedButton(
                  onClick = {
                    onImportBackupFile(importMode, restoreSnapshotToggle)
                    showBackupSheet = false
                  },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Choose JSON backup file via Android document picker" }
                    .testTag("action_sheet_import_file")
                ) {
                  Icon(Icons.Default.FileDownload, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Choose JSON File", color = AmberGold, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
              }

              if (onImportBackupJson != null) {
                OutlinedButton(
                  onClick = { showPasteSection = !showPasteSection },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = if (showPasteSection) "Hide clipboard paste JSON editor" else "Open clipboard paste JSON editor" }
                    .testTag("action_sheet_toggle_paste")
                ) {
                  Icon(Icons.Default.ContentPaste, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (showPasteSection) "Hide Paste Box" else "Paste JSON",
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  )
                }
              }
            }

            AnimatedVisibility(visible = showPasteSection && onImportBackupJson != null) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 6.dp)
                  .testTag("sheet_paste_json_section"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "PASTE & VALIDATE JSON",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary
                  )
                  TextButton(
                    onClick = {
                      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                      val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                      if (clipText.isNotBlank()) {
                        pasteJsonInput = clipText
                      }
                    },
                    modifier = Modifier
                      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                      .semantics { contentDescription = "Paste JSON text from system clipboard" }
                      .testTag("action_sheet_paste_from_clipboard")
                  ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste Clipboard", color = StudioCyan, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                  }
                }

                OutlinedTextField(
                  value = pasteJsonInput,
                  onValueChange = { pasteJsonInput = it },
                  placeholder = { Text("Paste prompt history JSON backup here...") },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("input_import_history_json"),
                  textStyle = MonospacePrompt.copy(fontSize = 11.sp, color = TextPrimary)
                )

                if (validationResult != null) {
                  if (validationResult.isValid) {
                    Surface(
                      color = Color(0x2210B981),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, StudioWinner.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("sheet_schema_status_valid")
                    ) {
                      Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StudioWinner, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          text = "Schema OK · ${validationResult.entryCount} variation(s)" +
                            (if (validationResult.hasSnapshot) " + Snapshot" else "") +
                            (if (validationResult.skippedCount > 0) " (${validationResult.skippedCount} skipped)" else ""),
                          style = MaterialTheme.typography.labelSmall,
                          color = StudioWinner
                        )
                      }
                    }
                  } else {
                    Surface(
                      color = Color(0x22EF4444),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, StudioMiss.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("sheet_schema_status_error")
                    ) {
                      Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StudioMiss, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          text = validationResult.errorMessage ?: "Invalid JSON schema",
                          style = MaterialTheme.typography.labelSmall,
                          color = StudioMiss
                        )
                      }
                    }
                  }
                }

                Button(
                  onClick = {
                    val handler = onImportBackupJson ?: return@Button
                    if (validationResult?.isValid == true) {
                      handler(pasteJsonInput, importMode, restoreSnapshotToggle)
                      pasteJsonInput = ""
                      showPasteSection = false
                      showBackupSheet = false
                    }
                  },
                  enabled = validationResult?.isValid == true,
                  colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Import validated JSON prompt history using ${importMode.name.lowercase()} mode" }
                    .testTag("action_confirm_import_history")
                ) {
                  Text(
                    text = if (importMode == HistoryImportMode.MERGE) "Merge Validated JSON" else "Replace with Validated JSON",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  )
                }
              }
            }
          }
        }
      }
    }
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
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
