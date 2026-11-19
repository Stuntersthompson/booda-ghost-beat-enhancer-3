package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StylePrompt
import com.example.data.repository.PromptTemplateRepository
import com.example.ui.components.BrandLogoImage
import com.example.ui.components.PromptTemplateCard
import com.example.ui.components.SaveTemplateDialog
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  var showTemplatesMode by rememberSaveable { mutableStateOf(true) }
  var showSaveTemplateDialog by rememberSaveable { mutableStateOf(false) }

  // Room Templates state
  val allTemplates by viewModel.promptTemplates.collectAsStateWithLifecycle()
  val dynamicCategories by viewModel.templateCategories.collectAsStateWithLifecycle()
  val templateQuery by viewModel.templateSearchQuery.collectAsStateWithLifecycle()
  val templateCategory by viewModel.templateCategoryFilter.collectAsStateWithLifecycle()

  // Verified Prompt Bank state
  val query by viewModel.libraryQuery.collectAsStateWithLifecycle()
  val filterTrack by viewModel.libraryFilterTrack.collectAsStateWithLifecycle()
  val filterLane by viewModel.libraryFilterLane.collectAsStateWithLifecycle()
  val activeLane by viewModel.activeLane.collectAsStateWithLifecycle()
  val audioAnalysis by viewModel.audioAnalysis.collectAsStateWithLifecycle()

  val allStyles = viewModel.bankRepo.stylePrompts
  val verifiedLanes = viewModel.bankRepo.verifiedLanes

  val categoryOptions = remember(dynamicCategories) {
    (listOf("All", "Pinned") + dynamicCategories + PromptTemplateRepository.DEFAULT_CATEGORIES).distinct()
  }

  val filteredTemplates = remember(allTemplates, templateCategory, templateQuery) {
    if (templateCategory.equals("Pinned", ignoreCase = true)) {
      PromptTemplateRepository.filterTemplates(allTemplates.filter { it.isPinned }, "All", templateQuery)
    } else {
      PromptTemplateRepository.filterTemplates(allTemplates, templateCategory, templateQuery)
    }
  }

  val filteredPrompts = remember(query, filterTrack, filterLane, allStyles) {
    val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    allStyles.filter { p ->
      val matchesTrack = when (filterTrack) {
        "Track 1 (Original)" -> p.track == 1
        "Track 2 (Preserve)" -> p.track == 2
        "Track 3 (Universal)" -> p.track == 3
        else -> true
      }
      val matchesLane = if (filterLane == "All") true else p.subgenre.equals(filterLane, ignoreCase = true)
      val haystack = "${p.text} ${p.subgenre} Track ${p.track}".lowercase()
      val matchesTokens = if (tokens.isEmpty()) true else tokens.all { haystack.contains(it) }
      matchesTrack && matchesLane && matchesTokens
    }
  }

  val resetBankFilters = {
    viewModel.setLibraryQuery("")
    viewModel.setLibraryFilterTrack("All")
    viewModel.setLibraryFilterLane("All")
  }

  val resetTemplateFilters = {
    viewModel.setTemplateSearchQuery("")
    viewModel.setTemplateCategoryFilter("All")
  }

  val modeToggleBlock: @Composable () -> Unit = {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Surface(
        color = if (showTemplatesMode) AmberGold else StudioCardElevated,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .clip(RoundedCornerShape(10.dp))
          .border(1.dp, if (showTemplatesMode) AmberGold else StudioBorder, RoundedCornerShape(10.dp))
          .clickable { showTemplatesMode = true }
          .semantics {
            role = Role.Tab
            selected = showTemplatesMode
            contentDescription = "Saved Room Prompt Templates (${allTemplates.size})"
          }
          .testTag("library_mode_templates")
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
          Text(
            text = "Templates (${allTemplates.size})",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (showTemplatesMode) Color.Black else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Surface(
        color = if (!showTemplatesMode) StudioCyan else StudioCardElevated,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .clip(RoundedCornerShape(10.dp))
          .border(1.dp, if (!showTemplatesMode) StudioCyan else StudioBorder, RoundedCornerShape(10.dp))
          .clickable { showTemplatesMode = false }
          .semantics {
            role = Role.Tab
            selected = !showTemplatesMode
            contentDescription = "Verified Suno Prompt Bank (${allStyles.size})"
          }
          .testTag("library_mode_bank")
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
          Text(
            text = "Style Bank (${allStyles.size})",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (!showTemplatesMode) Color.Black else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }

  val templatesHeaderAndFilters: @Composable () -> Unit = {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "SUNO PROMPT TEMPLATES",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
          Text(
            text = "Room-backed categorized presets with sliders, lane, and one-tap Build apply.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
          onClick = { showSaveTemplateDialog = true },
          border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Save current Build setup as a prompt template" }
            .testTag("library_save_build_template")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Save Build", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberGold)
        }
      }

      OutlinedTextField(
        value = templateQuery,
        onValueChange = { viewModel.setTemplateSearchQuery(it) },
        placeholder = { Text("Search Lo-Fi Hip Hop, Dark Synthwave, 808...", color = TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search prompt templates", tint = TextSecondary) },
        trailingIcon = {
          if (templateQuery.isNotEmpty()) {
            IconButton(
              onClick = { viewModel.setTemplateSearchQuery("") },
              modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
              Icon(Icons.Default.Close, contentDescription = "Clear template search", tint = TextSecondary)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = StudioCard,
          unfocusedContainerColor = StudioCard,
          focusedBorderColor = StudioCyan,
          unfocusedBorderColor = StudioBorder
        ),
        modifier = Modifier
          .fillMaxWidth()
          .semantics { contentDescription = "Search saved Room prompt templates" }
          .testTag("input_template_search")
      )

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(categoryOptions) { cat ->
          val isSelected = cat.equals(templateCategory, ignoreCase = true)
          Surface(
            color = if (isSelected) StudioCyan else StudioCardElevated,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .clip(RoundedCornerShape(16.dp))
              .semantics {
                role = Role.Button
                selected = isSelected
                contentDescription = "Filter templates by $cat"
              }
              .clickable { viewModel.setTemplateCategoryFilter(cat) }
              .border(0.5.dp, if (isSelected) StudioCyan else StudioBorder, RoundedCornerShape(16.dp))
              .testTag("template_category_chip_$cat")
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
              Text(
                text = cat,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Color.Black else TextPrimary
              )
            }
          }
        }
      }
    }
  }

  val emptyTemplatesBlock: @Composable () -> Unit = {
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        BrandLogoImage(
          height = 72.dp,
          contentDescription = "BooDa Beat Enhancer official logo",
          framed = true,
          modifier = Modifier.testTag("library_templates_empty_logo")
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("No matching templates", style = MaterialTheme.typography.titleMedium, color = AmberGold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Reset search/category filters or restore the starter Suno templates.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = resetTemplateFilters,
            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          ) {
            Text("Reset Filters", color = Color.Black, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = { viewModel.restoreStarterTemplates(context) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          ) {
            Text("Restore Starters", color = StudioCyan, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }

  val bankHeaderAndSearchBlock: @Composable () -> Unit = {
    Column {
      Text(
        text = "VERIFIED PROMPT LIBRARY",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = AmberGold
      )
      Text(
        text = "316 Suno v6 Style Prompts across 21 subgenre lanes. All prompts strictly ≤ 200 chars.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )
      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
        value = query,
        onValueChange = { viewModel.setLibraryQuery(it) },
        placeholder = { Text("Search style prompts, 808s, textures...", color = TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search prompt library", tint = TextSecondary) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(
              onClick = { viewModel.setLibraryQuery("") },
              modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
              Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = StudioCard,
          unfocusedContainerColor = StudioCard,
          focusedBorderColor = StudioCyan,
          unfocusedBorderColor = StudioBorder
        ),
        modifier = Modifier
          .fillMaxWidth()
          .semantics { contentDescription = "Search across 316 verified Suno style prompts" }
          .testTag("input_library_search")
      )

      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Showing ${filteredPrompts.size} of ${allStyles.size} prompts",
          style = MaterialTheme.typography.labelSmall,
          color = StudioCyan
        )
        if (query.isNotEmpty() || filterTrack != "All" || filterLane != "All") {
          TextButton(
            onClick = resetBankFilters,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .semantics { contentDescription = "Reset all library filters and search" }
          ) {
            Text("Reset Filters", style = MaterialTheme.typography.labelSmall, color = AmberGold)
          }
        }
      }
    }
  }

  val trackFilterBlock: @Composable () -> Unit = {
    val trackOptions = listOf("All", "Track 1 (Original)", "Track 2 (Preserve)", "Track 3 (Universal)")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      items(trackOptions) { t ->
        val isSelected = t == filterTrack
        Surface(
          color = if (isSelected) AmberGold else StudioCardElevated,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics {
              role = Role.Button
              selected = isSelected
              contentDescription = "Filter by $t"
            }
            .clickable { viewModel.setLibraryFilterTrack(t) }
            .border(0.5.dp, if (isSelected) AmberGold else StudioBorder, RoundedCornerShape(16.dp))
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(t, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (isSelected) Color.Black else TextPrimary)
          }
        }
      }
    }
  }

  val laneFilterBlock: @Composable () -> Unit = {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      item {
        val isSelected = filterLane == "All"
        Surface(
          color = if (isSelected) StudioCyan else StudioCardElevated,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics {
              role = Role.Button
              selected = isSelected
              contentDescription = "Filter by All Lanes"
            }
            .clickable { viewModel.setLibraryFilterLane("All") }
            .border(0.5.dp, StudioBorder, RoundedCornerShape(16.dp))
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("All Lanes", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (isSelected) Color.Black else TextPrimary)
          }
        }
      }
      items(verifiedLanes) { lane ->
        val isSelected = lane == filterLane
        Surface(
          color = if (isSelected) StudioCyan else StudioCardElevated,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics {
              role = Role.Button
              selected = isSelected
              contentDescription = "Filter library by $lane lane"
            }
            .clickable { viewModel.setLibraryFilterLane(lane) }
            .border(0.5.dp, StudioBorder, RoundedCornerShape(16.dp))
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(lane, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (isSelected) Color.Black else TextPrimary)
          }
        }
      }
    }
  }

  val emptyLibraryBlock: @Composable () -> Unit = {
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandLogoImage(
          height = 72.dp,
          contentDescription = "BooDa Beat Enhancer official logo",
          framed = true,
          modifier = Modifier.testTag("library_bank_empty_logo")
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("No matching style prompts", style = MaterialTheme.typography.titleMedium, color = AmberGold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Clear the search query or switch Track/Lane filters to inspect all 316 verified prompts.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
          onClick = resetBankFilters,
          colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
          Text("Reset Filters", color = Color.Black, fontWeight = FontWeight.Bold)
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
          .padding(horizontal = 16.dp)
          .testTag("library_wide_layout"),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        LazyColumn(
          modifier = Modifier.weight(0.9f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item { modeToggleBlock() }
          if (showTemplatesMode) {
            item { templatesHeaderAndFilters() }
          } else {
            item { bankHeaderAndSearchBlock() }
            item { trackFilterBlock() }
            item { laneFilterBlock() }
          }
        }

        LazyColumn(
          modifier = Modifier.weight(1.1f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (showTemplatesMode) {
            item {
              Text(
                text = "Showing ${filteredTemplates.size} of ${allTemplates.size} saved templates",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
              )
            }
            if (filteredTemplates.isEmpty()) {
              item { emptyTemplatesBlock() }
            } else {
              items(filteredTemplates, key = { it.id }) { template ->
                PromptTemplateCard(
                  template = template,
                  onApply = { viewModel.applyPromptTemplate(template, context = context, switchToBuild = true) },
                  onTogglePin = { viewModel.toggleTemplatePinned(template) },
                  onDelete = { viewModel.deletePromptTemplate(template) },
                  onDuplicate = { viewModel.duplicatePromptTemplate(template, context) }
                )
              }
            }
          } else {
            item {
              Text(
                text = "Showing ${filteredPrompts.size} prompts",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
              )
            }
            if (filteredPrompts.isEmpty()) {
              item { emptyLibraryBlock() }
            } else {
              items(filteredPrompts, key = { it.id }) { prompt ->
                PromptCardItem(
                  prompt = prompt,
                  onCopy = { viewModel.copyToClipboard(context, "Prompt #${prompt.id}", prompt.text) },
                  onApply = { viewModel.applyStylePromptFromBank(prompt, context = context) }
                )
              }
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp)
          .testTag("library_list"),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item { modeToggleBlock() }
        if (showTemplatesMode) {
          item { templatesHeaderAndFilters() }
          item {
            Text(
              text = "Showing ${filteredTemplates.size} of ${allTemplates.size} saved templates",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted
            )
          }
          if (filteredTemplates.isEmpty()) {
            item { emptyTemplatesBlock() }
          } else {
            items(filteredTemplates, key = { it.id }) { template ->
              PromptTemplateCard(
                template = template,
                onApply = { viewModel.applyPromptTemplate(template, context = context, switchToBuild = true) },
                onTogglePin = { viewModel.toggleTemplatePinned(template) },
                onDelete = { viewModel.deletePromptTemplate(template) },
                onDuplicate = { viewModel.duplicatePromptTemplate(template, context) }
              )
            }
          }
        } else {
          item { bankHeaderAndSearchBlock() }
          item { trackFilterBlock() }
          item { laneFilterBlock() }
          item {
            Text(
              text = "Showing ${filteredPrompts.size} prompts",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted
            )
          }
          if (filteredPrompts.isEmpty()) {
            item { emptyLibraryBlock() }
          } else {
            items(filteredPrompts, key = { it.id }) { prompt ->
              PromptCardItem(
                prompt = prompt,
                onCopy = { viewModel.copyToClipboard(context, "Prompt #${prompt.id}", prompt.text) },
                onApply = { viewModel.applyStylePromptFromBank(prompt, context = context) }
              )
            }
          }
        }
      }
    }
  }

  if (showSaveTemplateDialog) {
    val defaultBpm = audioAnalysis?.bpm?.let { "${it.toInt()} BPM" } ?: ""
    SaveTemplateDialog(
      defaultTitle = "$activeLane Preset",
      defaultCategory = if (templateCategory != "All") templateCategory else "Custom",
      defaultBpmHint = defaultBpm,
      onDismiss = { showSaveTemplateDialog = false },
      onSave = { title, category, bpmHint ->
        viewModel.saveCurrentAsTemplate(title = title, category = category, bpmHint = bpmHint, context = context)
        showSaveTemplateDialog = false
      }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptCardItem(
  prompt: StylePrompt,
  onCopy: () -> Unit,
  onApply: () -> Unit
) {
  val isPreserve = prompt.track == 2
  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        FlowRow(
          verticalArrangement = Arrangement.spacedBy(4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .weight(1f)
            .padding(end = 4.dp)
        ) {
          Surface(
            color = if (isPreserve) AmberContainer else CyanContainer,
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = "Track ${prompt.track} · ${if (isPreserve) "Preserve" else if (prompt.track == 1) "Original" else "Universal"}",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = if (isPreserve) AmberGold else StudioCyan,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          Text(
            text = prompt.subgenre,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${prompt.chars}c",
            style = MonospaceMetric.copy(fontSize = 11.sp),
            color = TextMuted
          )
          Spacer(modifier = Modifier.width(4.dp))
          IconButton(
            onClick = onCopy,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy prompt #${prompt.id}", tint = TextSecondary, modifier = Modifier.size(16.dp))
          }
          IconButton(
            onClick = onApply,
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .testTag("bank_prompt_apply_${prompt.id}")
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Apply prompt #${prompt.id} to Build", tint = AmberGold, modifier = Modifier.size(18.dp))
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = prompt.text,
        style = MonospacePrompt.copy(fontSize = 13.sp),
        color = TextPrimary
      )
    }
  }
}
