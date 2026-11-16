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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CompareArrows
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DiffOp
import com.example.data.model.TakeAnalytics
import com.example.data.model.TakeAnalyticsSummary
import com.example.data.model.TakeComparisonResult
import com.example.data.model.TakeEntity
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun TakesScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val takesList by viewModel.takesList.collectAsStateWithLifecycle()
  val filterResult by viewModel.takeFilterResult.collectAsStateWithLifecycle()

  val analytics = remember(takesList) { TakeAnalytics.summarize(takesList) }

  var compareTakeAId by rememberSaveable { mutableStateOf<Long?>(null) }
  var compareTakeBId by rememberSaveable { mutableStateOf<Long?>(null) }
  var showCompareCard by rememberSaveable { mutableStateOf(false) }
  var showImportModal by rememberSaveable { mutableStateOf(false) }
  var importJsonInput by rememberSaveable { mutableStateOf("") }

  val takeA = remember(takesList, compareTakeAId) {
    takesList.find { it.id == compareTakeAId } ?: takesList.getOrNull(0)
  }
  val takeB = remember(takesList, compareTakeBId, takeA) {
    takesList.find { it.id == compareTakeBId } ?: takesList.firstOrNull { it.id != takeA?.id }
  }

  val comparison = remember(takeA, takeB, showCompareCard) {
    if (showCompareCard && takeA != null && takeB != null && takeA.id != takeB.id) {
      TakeAnalytics.compareTakes(takeA, takeB)
    } else null
  }

  val filteredTakes = remember(takesList, filterResult) {
    if (filterResult == "ALL") takesList
    else takesList.filter { it.result.equals(filterResult, ignoreCase = true) }
  }

  val headerAndAnalyticsBlock: @Composable () -> Unit = {
    Column {
      Text(
        text = "PRODUCTION LOGBOOK (TAKES)",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = AmberGold
      )
      Text(
        text = "Local Room database log tracking your Suno v6 takes, lane win rates, and side-by-side A/B prompt diffs.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )

      Spacer(modifier = Modifier.height(10.dp))

      TakesAnalyticsCard(analytics = analytics)
    }
  }

  val exportImportCompareBlock: @Composable () -> Unit = {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = { viewModel.exportTakes(context, asJson = false) },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Export takes logbook as plain text" }
            .testTag("action_export_takes_text")
        ) {
          Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Export Text", style = MaterialTheme.typography.labelSmall, color = AmberGold)
        }

        OutlinedButton(
          onClick = { viewModel.exportTakes(context, asJson = true) },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Export takes logbook as JSON" }
            .testTag("action_export_takes_json")
        ) {
          Icon(Icons.Default.Code, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Export JSON", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
        }

        Button(
          onClick = { showImportModal = true },
          colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          modifier = Modifier
            .weight(0.9f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Import takes JSON into logbook" }
            .testTag("action_import_takes")
        ) {
          Icon(Icons.Default.FileDownload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Import", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
      }

      if (takesList.size >= 2) {
        Button(
          onClick = {
            if (!showCompareCard && takeA != null && takeB != null) {
              compareTakeAId = takeA.id
              compareTakeBId = takeB.id
            }
            showCompareCard = !showCompareCard
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (showCompareCard) StudioCyan else StudioCardElevated
          ),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
              contentDescription = if (showCompareCard) "Hide side-by-side A/B take prompt diff" else "Compare two takes side by side"
            }
            .testTag("action_toggle_ab_compare")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = null,
            tint = if (showCompareCard) Color.Black else StudioCyan,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (showCompareCard) "Hide A/B Prompt Diff" else "Compare Two Takes (A/B Diff)",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (showCompareCard) Color.Black else StudioCyan
          )
        }
      }
    }
  }

  val filterChipsBlock: @Composable () -> Unit = {
    val filterOptions = listOf("ALL", "WINNER", "PENDING", "MISS")
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      filterOptions.forEach { opt ->
        val isSelected = opt == filterResult
        Surface(
          color = if (isSelected) AmberGold else StudioCardElevated,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .semantics {
              role = Role.Button
              selected = isSelected
              contentDescription = "Filter takes by $opt"
            }
            .clickable { viewModel.setTakeFilterResult(opt) }
            .border(0.5.dp, StudioBorder, RoundedCornerShape(14.dp))
            .testTag("filter_take_$opt")
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
              text = opt,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = if (isSelected) Color.Black else TextPrimary
            )
          }
        }
      }
    }
  }

  val emptyTakesBlock: @Composable () -> Unit = {
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 20.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(Icons.Default.Bookmarks, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text("No takes logged yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text("Build a prompt package in the Build tab and tap 'Save Take' to log it here.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      }
    }
  }

  val renderTakeCard: @Composable (TakeEntity) -> Unit = { take ->
    TakeItemCard(
      take = take,
      isSelectedAsA = takeA?.id == take.id && showCompareCard,
      isSelectedAsB = takeB?.id == take.id && showCompareCard,
      canCompare = takesList.size >= 2,
      onSelectAsA = {
        compareTakeAId = take.id
        if (compareTakeBId == take.id) {
          compareTakeBId = takesList.firstOrNull { it.id != take.id }?.id
        }
        showCompareCard = true
      },
      onSelectAsB = {
        compareTakeBId = take.id
        if (compareTakeAId == take.id) {
          compareTakeAId = takesList.firstOrNull { it.id != take.id }?.id
        }
        showCompareCard = true
      },
      onLoadIntoBuild = { viewModel.loadTakeIntoBuild(take, context) },
      onUpdateResult = { res -> viewModel.updateTakeResult(take.id, res) },
      onUpdateNextChange = { txt -> viewModel.updateTakeNextChange(take.id, txt) },
      onDuplicate = { viewModel.duplicateTake(take.id) },
      onDelete = { viewModel.deleteTake(take.id) },
      onCopy = {
        val bundle = """=== TAKE: ${take.name.uppercase()} ===
Beat: ${if (take.beatTitle.isNotBlank()) take.beatTitle else take.name}
Lane: ${take.lane} | Job: ${take.jobType.uppercase()} | BPM: ${if (take.bpm > 0) take.bpm.toInt().toString() else "N/A"}
Status: ${take.result.uppercase()} | Settings: ${take.settings}
${if (take.nextChange.isNotBlank()) "Next Change: ${take.nextChange}\n" else ""}
--- STYLE OF MUSIC ---
${take.style}

--- EXCLUDE STYLES ---
${take.excludeText}

--- LYRICS BOX MAP ---
${take.lyrics}
=================================""".trimIndent()
        viewModel.copyToClipboard(context, "Take ${take.name}", bundle)
      }
    )
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
          .testTag("takes_wide_layout"),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        LazyColumn(
          modifier = Modifier
            .weight(0.95f)
            .fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item { headerAndAnalyticsBlock() }
          item { exportImportCompareBlock() }
          if (comparison != null) {
            item {
              TakeABDiffCard(
                comparison = comparison,
                onLoadTakeToBuild = { take -> viewModel.loadTakeIntoBuild(take, context) }
              )
            }
          }
          item { filterChipsBlock() }
        }

        LazyColumn(
          modifier = Modifier
            .weight(1.05f)
            .fillMaxHeight()
            .testTag("takes_list"),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          if (filteredTakes.isEmpty()) {
            item { emptyTakesBlock() }
          } else {
            items(filteredTakes, key = { it.id }) { take ->
              renderTakeCard(take)
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp)
          .testTag("takes_list"),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item { headerAndAnalyticsBlock() }
        item { exportImportCompareBlock() }
        if (comparison != null) {
          item {
            TakeABDiffCard(
              comparison = comparison,
              onLoadTakeToBuild = { take -> viewModel.loadTakeIntoBuild(take, context) }
            )
          }
        }
        item { filterChipsBlock() }
        if (filteredTakes.isEmpty()) {
          item { emptyTakesBlock() }
        } else {
          items(filteredTakes, key = { it.id }) { take ->
            renderTakeCard(take)
          }
        }
      }
    }
  }

  if (showImportModal) {
    AlertDialog(
      onDismissRequest = { showImportModal = false },
      title = { Text("Import Takes to Logbook", color = TextPrimary) },
      text = {
        Column {
          Text("Paste JSON takes backup below. Existing takes are safely preserved (merge-only import):", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = importJsonInput,
            onValueChange = { importJsonInput = it },
            placeholder = { Text("Paste JSON array or envelope here...") },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            textStyle = MonospacePrompt.copy(fontSize = 11.sp, color = TextPrimary)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (importJsonInput.isNotBlank()) {
              viewModel.importTakes(context, importJsonInput)
              importJsonInput = ""
            }
            showImportModal = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Text("Import", color = Color.Black)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showImportModal = false },
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
private fun TakesAnalyticsCard(analytics: TakeAnalyticsSummary) {
  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
      .testTag("takes_analytics_breakdown")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("TOTAL TAKES", style = MaterialTheme.typography.labelSmall, color = TextMuted)
          Text("${analytics.totalTakes}", style = MonospaceMetric.copy(fontSize = 22.sp), color = TextPrimary)
        }
        VerticalDivider(modifier = Modifier.height(30.dp), color = StudioBorder)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("WINNERS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
          Text("${analytics.winnerCount}", style = MonospaceMetric.copy(fontSize = 22.sp), color = StudioWinner)
        }
        VerticalDivider(modifier = Modifier.height(30.dp), color = StudioBorder)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("WIN RATE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
          Text("${analytics.overallWinRate}%", style = MonospaceMetric.copy(fontSize = 22.sp), color = AmberGold)
        }
      }

      if (analytics.totalTakes > 0) {
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = StudioBorder)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(8.dp)) {
              Text("PRESERVE MODE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = AmberGold)
              Text(
                text = "${analytics.preserveStats.winners}/${analytics.preserveStats.total} wins (${analytics.preserveStats.winRatePercent}%)",
                style = MonospaceMetric.copy(fontSize = 11.sp),
                color = TextPrimary
              )
            }
          }
          Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(8.dp)) {
              Text("ORIGINAL MODE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = StudioCyan)
              Text(
                text = "${analytics.originalStats.winners}/${analytics.originalStats.total} wins (${analytics.originalStats.winRatePercent}%)",
                style = MonospaceMetric.copy(fontSize = 11.sp),
                color = TextPrimary
              )
            }
          }
        }

        if (analytics.topLanes.isNotEmpty()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text("TOP LANE WIN RATES", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextMuted)
          Spacer(modifier = Modifier.height(4.dp))
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            analytics.topLanes.forEach { stat ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = stat.lane,
                  style = MaterialTheme.typography.labelSmall,
                  color = TextPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(0.42f)
                )
                LinearProgressIndicator(
                  progress = { (stat.winRatePercent / 100f).coerceIn(0f, 1f) },
                  color = if (stat.winRatePercent >= 50) StudioWinner else AmberGold,
                  trackColor = StudioBg,
                  modifier = Modifier
                    .weight(0.38f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                )
                Text(
                  text = " ${stat.winRatePercent}% (${stat.winners}/${stat.total})",
                  style = MonospaceMetric.copy(fontSize = 10.sp),
                  color = StudioCyan,
                  modifier = Modifier.weight(0.20f)
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
private fun TakeABDiffCard(
  comparison: TakeComparisonResult,
  onLoadTakeToBuild: (TakeEntity) -> Unit
) {
  val diffAnnotated = remember(comparison.styleDiffTokens) {
    val builder = AnnotatedString.Builder()
    comparison.styleDiffTokens.forEachIndexed { idx, token ->
      if (idx > 0) builder.append(" ")
      val start = builder.length
      builder.append(token.text)
      val end = builder.length
      when (token.op) {
        DiffOp.ADDED -> builder.addStyle(
          SpanStyle(
            background = StudioCyan.copy(alpha = 0.22f),
            color = StudioCyan,
            fontWeight = FontWeight.Bold
          ),
          start,
          end
        )
        DiffOp.REMOVED -> builder.addStyle(
          SpanStyle(
            background = StudioMiss.copy(alpha = 0.22f),
            color = StudioMiss,
            textDecoration = TextDecoration.LineThrough
          ),
          start,
          end
        )
        DiffOp.UNCHANGED -> builder.addStyle(
          SpanStyle(color = TextSecondary),
          start,
          end
        )
      }
    }
    builder.toAnnotatedString()
  }

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
      .testTag("takes_ab_diff_card")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("A/B TAKE PROMPT DIFF", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = StudioCyan)
        }
        Text(
          text = "-${comparison.removedWordCount} · +${comparison.addedWordCount}",
          style = MonospaceMetric.copy(fontSize = 11.sp),
          color = AmberGold
        )
      }

      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "A: ${comparison.takeA.name}  vs  B: ${comparison.takeB.name}",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = TextPrimary
      )

      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(comparison.sliderDeltas, key = { it.label }) { delta ->
          Surface(
            color = if (delta.changed) AmberContainer else StudioCardElevated,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.border(
              0.5.dp,
              if (delta.changed) AmberGold.copy(alpha = 0.5f) else StudioBorder,
              RoundedCornerShape(6.dp)
            )
          ) {
            Text(
              text = if (delta.changed) "${delta.label}: ${delta.valueA} -> ${delta.valueB}" else "${delta.label}: ${delta.valueA}",
              style = MonospaceMetric.copy(fontSize = 10.sp),
              color = if (delta.changed) AmberGold else TextSecondary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Surface(
        color = StudioBg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = diffAnnotated,
          style = MonospacePrompt.copy(fontSize = 12.sp, lineHeight = 18.sp),
          modifier = Modifier.padding(10.dp)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = { onLoadTakeToBuild(comparison.takeA) },
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Load Take A into Build" }
            .testTag("action_load_diff_a")
        ) {
          Text("Load A to Build", style = MaterialTheme.typography.labelSmall, color = AmberGold)
        }
        Button(
          onClick = { onLoadTakeToBuild(comparison.takeB) },
          colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Load Take B into Build" }
            .testTag("action_load_diff_b")
        ) {
          Text("Load B to Build", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
        }
      }
    }
  }
}

@Composable
private fun TakeItemCard(
  take: TakeEntity,
  isSelectedAsA: Boolean,
  isSelectedAsB: Boolean,
  canCompare: Boolean,
  onSelectAsA: () -> Unit,
  onSelectAsB: () -> Unit,
  onLoadIntoBuild: () -> Unit,
  onUpdateResult: (String) -> Unit,
  onUpdateNextChange: (String) -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onCopy: () -> Unit
) {
  var editingNote by rememberSaveable(take.id) { mutableStateOf(false) }
  var noteInput by rememberSaveable(take.id, take.nextChange) { mutableStateOf(take.nextChange) }

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        1.dp,
        if (isSelectedAsA || isSelectedAsB) StudioCyan else StudioBorder,
        RoundedCornerShape(12.dp)
      )
      .testTag("take_card_${take.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(take.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
          if (take.beatTitle.isNotBlank() && take.beatTitle != take.name) {
            Text("Beat: ${take.beatTitle}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(take.lane, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = AmberGold)
            Text(" · ${take.jobType.uppercase()}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (take.bpm > 0) {
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "${take.bpm.toInt()} BPM",
                  style = MonospaceMetric.copy(fontSize = 9.sp),
                  color = StudioCyan,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
              }
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          ResultPill(
            label = "WIN",
            active = take.result.equals("winner", true),
            activeColor = StudioWinner,
            accessibilityLabel = "Mark take ${take.name} as winner"
          ) {
            onUpdateResult("winner")
          }
          ResultPill(
            label = "MISS",
            active = take.result.equals("miss", true),
            activeColor = StudioMiss,
            accessibilityLabel = "Mark take ${take.name} as miss"
          ) {
            onUpdateResult("miss")
          }
          ResultPill(
            label = "PEND",
            active = take.result.equals("pending", true),
            activeColor = StudioPending,
            accessibilityLabel = "Mark take ${take.name} as pending"
          ) {
            onUpdateResult("pending")
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Surface(
        color = StudioBg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = take.style.take(160) + if (take.style.length > 160) "..." else "",
          style = MonospacePrompt.copy(fontSize = 12.sp),
          color = TextSecondary,
          modifier = Modifier.padding(8.dp)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text("Settings: ${take.settings}", style = MaterialTheme.typography.labelSmall, color = TextMuted)

      Spacer(modifier = Modifier.height(6.dp))
      if (editingNote) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = noteInput,
            onValueChange = { if (it.length <= 1000) noteInput = it },
            placeholder = { Text("What to change on next take...") },
            modifier = Modifier.weight(1f),
            singleLine = true
          )
          IconButton(
            onClick = {
              onUpdateNextChange(noteInput)
              editingNote = false
            },
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          ) {
            Icon(Icons.Default.Check, contentDescription = "Save Note", tint = StudioWinner)
          }
        }
      } else {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .semantics {
              role = Role.Button
              contentDescription = "Edit next-change note for ${take.name}"
            }
            .clickable { editingNote = true }
            .background(StudioCardElevated)
            .padding(horizontal = 10.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (take.nextChange.isBlank()) "Tap to add next-change note..." else "Next change: ${take.nextChange}",
            style = MaterialTheme.typography.labelSmall,
            color = if (take.nextChange.isBlank()) TextMuted else AmberGoldLight,
            modifier = Modifier.weight(1f)
          )
          Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          if (canCompare) {
            ResultPill(
              label = "A",
              active = isSelectedAsA,
              activeColor = AmberGold,
              tag = "action_set_compare_a_${take.id}",
              accessibilityLabel = "Select ${take.name} as Compare A",
              onClick = onSelectAsA
            )
            ResultPill(
              label = "B",
              active = isSelectedAsB,
              activeColor = StudioCyan,
              tag = "action_set_compare_b_${take.id}",
              accessibilityLabel = "Select ${take.name} as Compare B",
              onClick = onSelectAsB
            )
          }
          TextButton(
            onClick = onLoadIntoBuild,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          ) {
            Text("Load", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberGold)
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDuplicate, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate take ${take.name}", tint = StudioCyan, modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onCopy, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(Icons.Default.Share, contentDescription = "Copy take ${take.name}", tint = TextSecondary, modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onDelete, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete take ${take.name}", tint = StudioMiss, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun ResultPill(
  label: String,
  active: Boolean,
  activeColor: Color,
  tag: String? = null,
  accessibilityLabel: String = label,
  onClick: () -> Unit
) {
  Surface(
    color = if (active) activeColor else StudioCardElevated,
    shape = RoundedCornerShape(6.dp),
    modifier = Modifier
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .clip(RoundedCornerShape(6.dp))
      .semantics {
        role = Role.Button
        selected = active
        contentDescription = accessibilityLabel
      }
      .clickable { onClick() }
      .then(if (tag != null) Modifier.testTag(tag) else Modifier)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
        color = if (active) Color.Black else TextMuted
      )
    }
  }
}
