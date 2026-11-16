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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StylePrompt
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun LibraryScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val query by viewModel.libraryQuery.collectAsStateWithLifecycle()
  val filterTrack by viewModel.libraryFilterTrack.collectAsStateWithLifecycle()
  val filterLane by viewModel.libraryFilterLane.collectAsStateWithLifecycle()

  val allStyles = viewModel.bankRepo.stylePrompts
  val verifiedLanes = viewModel.bankRepo.verifiedLanes

  // Multi-word AND filter prompts
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

  val resetFilters = {
    viewModel.setLibraryQuery("")
    viewModel.setLibraryFilterTrack("All")
    viewModel.setLibraryFilterLane("All")
  }

  val headerAndSearchBlock: @Composable () -> Unit = {
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

      // Search Field with Clear (X) Button
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
            onClick = resetFilters,
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
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
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
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
          ) {
            Text(
              text = t,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = if (isSelected) Color.Black else TextPrimary
            )
          }
        }
      }
    }
  }

  val laneFilterBlock: @Composable () -> Unit = {
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
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
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
          ) {
            Text(
              text = "All Lanes",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = if (isSelected) Color.Black else TextPrimary
            )
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
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
          ) {
            Text(
              text = lane,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = if (isSelected) Color.Black else TextPrimary
            )
          }
        }
      }
    }
  }

  val emptyLibraryBlock: @Composable () -> Unit = {
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
        Icon(Icons.Default.SearchOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text("No matching style prompts", style = MaterialTheme.typography.titleMedium, color = AmberGold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Clear the search query or switch Track/Lane filters to inspect all 316 verified prompts.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
          onClick = resetFilters,
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
          modifier = Modifier
            .weight(0.9f)
            .fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item { headerAndSearchBlock() }
          item { trackFilterBlock() }
          item { laneFilterBlock() }
        }

        LazyColumn(
          modifier = Modifier
            .weight(1.1f)
            .fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
              PromptCardItem(prompt = prompt) {
                viewModel.copyToClipboard(context, "Prompt #${prompt.id}", prompt.text)
              }
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item { headerAndSearchBlock() }
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
            PromptCardItem(prompt = prompt) {
              viewModel.copyToClipboard(context, "Prompt #${prompt.id}", prompt.text)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PromptCardItem(prompt: StylePrompt, onCopy: () -> Unit) {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Surface(
            color = if (isPreserve) AmberContainer else CyanContainer,
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = "Track ${prompt.track} · ${if (isPreserve) "Preserve" else if (prompt.track == 1) "Original" else "Universal"}",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = if (isPreserve) AmberGold else StudioCyan,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = prompt.subgenre,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
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
