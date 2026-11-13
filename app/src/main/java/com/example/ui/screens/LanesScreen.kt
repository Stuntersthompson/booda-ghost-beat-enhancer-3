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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab

@Composable
fun LanesScreen(viewModel: MainViewModel) {
  val activeLane by viewModel.activeLane.collectAsStateWithLifecycle()
  val audioAnalysis by viewModel.audioAnalysis.collectAsStateWithLifecycle()

  val allLanes = viewModel.bankRepo.allLanes
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var bankFilter by rememberSaveable { mutableStateOf("All") }
  var bpmFilter by rememberSaveable { mutableStateOf("Any BPM") }
  var expandedLane by rememberSaveable { mutableStateOf<String?>(activeLane) }

  var showComparisonDeck by rememberSaveable { mutableStateOf(false) }
  var laneA by rememberSaveable { mutableStateOf(activeLane) }
  val defaultLaneB = remember(allLanes, activeLane) {
    when {
      activeLane != "Memphis phonk / dark trap" && allLanes.contains("Memphis phonk / dark trap") -> "Memphis phonk / dark trap"
      activeLane != "Boom trap" && allLanes.contains("Boom trap") -> "Boom trap"
      else -> allLanes.firstOrNull { it != activeLane } ?: activeLane
    }
  }
  var laneB by rememberSaveable { mutableStateOf(defaultLaneB) }

  val filteredLanes = remember(searchQuery, bankFilter, bpmFilter, allLanes) {
    allLanes.filter { lane ->
      val profile = viewModel.bankRepo.laneTempoProfile(lane)
      val exp = viewModel.bankRepo.expansionLanes.find { it.name.equals(lane, ignoreCase = true) }
      val matchesSearch = if (searchQuery.isBlank()) {
        true
      } else {
        lane.contains(searchQuery, ignoreCase = true) ||
          profile.regionTag.contains(searchQuery, ignoreCase = true) ||
          (exp?.drumTexture?.contains(searchQuery, ignoreCase = true) == true)
      }
      val matchesBank = when (bankFilter) {
        "Verified" -> profile.isVerified
        "Expansion" -> !profile.isVerified
        else -> true
      }
      val matchesBpm = viewModel.bankRepo.matchesBpmBracket(lane, bpmFilter)
      matchesSearch && matchesBank && matchesBpm
    }
  }

  val bpmMatches = remember(audioAnalysis?.bpm, allLanes) {
    val detectedBpm = audioAnalysis?.bpm
    if (detectedBpm != null && detectedBpm > 0.0) {
      viewModel.bankRepo.rankLanesForBpm(detectedBpm, limit = 3)
    } else {
      emptyList()
    }
  }

  val headerAndFilters: @Composable () -> Unit = {
    Column {
      Text(
        text = "41 SUBGENRE LANES & FRAMEWORKS",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = AmberGold
      )
      Text(
        text = "21 Verified-Bank Lanes + 20 Expansion Lanes. Filter by region or BPM bracket, compare side-by-side, or match your analyzed beat.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )
      Spacer(modifier = Modifier.height(10.dp))

      // Search Field
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search lanes, regions, 808 styles...", color = TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search lanes", tint = TextSecondary) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { searchQuery = "" },
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(Icons.Default.Close, contentDescription = "Clear lane search", tint = TextSecondary)
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
        modifier = Modifier.fillMaxWidth().testTag("input_lanes_search")
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Bank Tier Filter Row
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val bankChips = listOf("All" to "All (41)", "Verified" to "Verified (21)", "Expansion" to "Expansion (20)")
        items(bankChips, key = { it.first }) { (key, label) ->
          val selected = bankFilter == key
          FilterChip(
            selected = selected,
            onClick = { bankFilter = key },
            label = {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = AmberGold,
              selectedLabelColor = Color.Black,
              containerColor = StudioCardElevated,
              labelColor = TextPrimary
            ),
            modifier = Modifier.testTag("filter_bank_$key")
          )
        }
      }

      // BPM Bracket Filter Row
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val bpmBrackets = listOf("Any BPM", "< 100", "100-128", "128+")
        items(bpmBrackets, key = { it }) { bracket ->
          val selected = bpmFilter == bracket
          FilterChip(
            selected = selected,
            onClick = { bpmFilter = bracket },
            label = {
              Text(
                text = if (bracket == "Any BPM") bracket else "$bracket BPM",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = StudioCyan,
              selectedLabelColor = Color.Black,
              containerColor = StudioCardElevated,
              labelColor = TextPrimary
            ),
            modifier = Modifier.testTag("filter_bpm_$bracket")
          )
        }
      }

      Text(
        text = "Showing ${filteredLanes.size} of ${allLanes.size} lanes",
        style = MaterialTheme.typography.labelSmall,
        color = StudioCyan
      )
    }
  }

  val matchBeatBpmBanner: @Composable () -> Unit = {
    if (bpmMatches.isNotEmpty() && audioAnalysis?.bpm != null) {
      val detectedBpm = audioAnalysis!!.bpm!!.toInt()
      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, StudioCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
          .testTag("match_beat_bpm_card")
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Speed, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "MATCH BEAT BPM ($detectedBpm BPM)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = StudioCyan
              )
            }
            Text(
              text = audioAnalysis!!.fileName.take(18),
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            bpmMatches.forEach { match ->
              val laneName = match.profile.lane
              val isCurrent = laneName == activeLane
              Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(0.5.dp, if (isCurrent) AmberGold else StudioBorder, RoundedCornerShape(8.dp))
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(laneName, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Text(match.matchNote, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                  }
                  Button(
                    onClick = {
                      viewModel.applyBpmMatchedLanePreset(laneName)
                      expandedLane = laneName
                      viewModel.setTab(StudioTab.BUILD)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .defaultMinSize(minHeight = 36.dp)
                      .testTag("action_apply_bpm_preset_$laneName")
                  ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Match Preset", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  val comparisonDeckCard: @Composable (Boolean) -> Unit = { forceExpanded ->
    var showDropdownA by remember { mutableStateOf(false) }
    var showDropdownB by remember { mutableStateOf(false) }
    val deckOpen = forceExpanded || showComparisonDeck

    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        .testTag("lane_comparison_deck")
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("SIDE-BY-SIDE LANE COMPARISON", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = StudioCyan)
          }
          if (!forceExpanded) {
            TextButton(
              onClick = { showComparisonDeck = !showComparisonDeck },
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Text(
                text = if (showComparisonDeck) "Hide Deck ▲" else "Compare Lanes ▾",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = AmberGold
              )
            }
          }
        }

        if (deckOpen) {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(modifier = Modifier.weight(1f)) {
              OutlinedButton(
                onClick = { showDropdownA = true },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(laneA.take(16), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberGold)
              }
              DropdownMenu(expanded = showDropdownA, onDismissRequest = { showDropdownA = false }) {
                allLanes.forEach { l ->
                  DropdownMenuItem(text = { Text(l) }, onClick = { laneA = l; showDropdownA = false })
                }
              }
            }

            Box(modifier = Modifier.weight(1f)) {
              OutlinedButton(
                onClick = { showDropdownB = true },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(laneB.take(16), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = StudioCyan)
              }
              DropdownMenu(expanded = showDropdownB, onDismissRequest = { showDropdownB = false }) {
                allLanes.forEach { l ->
                  DropdownMenuItem(text = { Text(l) }, onClick = { laneB = l; showDropdownB = false })
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          val fwA = viewModel.bankRepo.frameworks[laneA]
          val fwB = viewModel.bankRepo.frameworks[laneB]
          val drumsA = viewModel.bankRepo.laneDrums(laneA)
          val drumsB = viewModel.bankRepo.laneDrums(laneB)
          val profileA = viewModel.bankRepo.laneTempoProfile(laneA)
          val profileB = viewModel.bankRepo.laneTempoProfile(laneB)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            ComparisonLaneColumn(
              lane = laneA,
              tempoLabel = "${profileA.bpmMin}-${profileA.bpmMax} BPM",
              drums = drumsA,
              fw = fwA,
              accent = AmberGold,
              onSelect = { viewModel.setLane(laneA) },
              modifier = Modifier.weight(1f)
            )
            ComparisonLaneColumn(
              lane = laneB,
              tempoLabel = "${profileB.bpmMin}-${profileB.bpmMax} BPM",
              drums = drumsB,
              fw = fwB,
              accent = StudioCyan,
              onSelect = { viewModel.setLane(laneB) },
              modifier = Modifier.weight(1f)
            )
          }
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
        // Left Master Pane: Search + Filters + BPM Match + Lane List
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item { headerAndFilters() }
          item { matchBeatBpmBanner() }
          items(filteredLanes, key = { it }) { lane ->
            LaneCatalogCard(
              lane = lane,
              isSelected = lane == activeLane,
              isExpanded = lane == expandedLane,
              viewModel = viewModel,
              onToggleExpand = { expandedLane = lane },
              onSelectLane = { viewModel.setLane(lane) },
              onApplyBpmPreset = {
                viewModel.applyBpmMatchedLanePreset(lane)
                viewModel.setTab(StudioTab.BUILD)
              }
            )
          }
        }

        // Right Detail Pane: Pinned Selected Lane Profile + Side-by-Side Comparison Deck
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          val inspected = expandedLane?.takeIf { allLanes.contains(it) } ?: activeLane
          item {
            LaneCatalogCard(
              lane = inspected,
              isSelected = inspected == activeLane,
              isExpanded = true,
              viewModel = viewModel,
              onToggleExpand = {},
              onSelectLane = { viewModel.setLane(inspected) },
              onApplyBpmPreset = {
                viewModel.applyBpmMatchedLanePreset(inspected)
                viewModel.setTab(StudioTab.BUILD)
              }
            )
          }
          item { comparisonDeckCard(true) }
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
        item { headerAndFilters() }
        item { matchBeatBpmBanner() }
        item { comparisonDeckCard(false) }
        items(filteredLanes, key = { it }) { lane ->
          LaneCatalogCard(
            lane = lane,
            isSelected = lane == activeLane,
            isExpanded = lane == expandedLane,
            viewModel = viewModel,
            onToggleExpand = { expandedLane = if (expandedLane == lane) null else lane },
            onSelectLane = { viewModel.setLane(lane) },
            onApplyBpmPreset = {
              viewModel.applyBpmMatchedLanePreset(lane)
              viewModel.setTab(StudioTab.BUILD)
            }
          )
        }
      }
    }
  }
}

@Composable
private fun ComparisonLaneColumn(
  lane: String,
  tempoLabel: String,
  drums: String,
  fw: com.example.data.model.FrameworkProfile?,
  accent: Color,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = StudioCardElevated,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier.border(0.5.dp, StudioBorder, RoundedCornerShape(8.dp))
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Text(lane, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accent)
      Text(tempoLabel, style = MonospaceMetric.copy(fontSize = 10.sp), color = TextMuted)
      Spacer(modifier = Modifier.height(4.dp))
      Text("Drums: $drums", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
      if (fw != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("Build: ${fw.build}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text("Drop: ${fw.drop}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text("Sig: ${fw.signature}", style = MaterialTheme.typography.labelSmall, color = accent)
      }
      Spacer(modifier = Modifier.height(8.dp))
      Button(
        onClick = onSelect,
        colors = ButtonDefaults.buttonColors(containerColor = accent),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .defaultMinSize(minHeight = 36.dp)
      ) {
        Text("Use $lane", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black, maxLines = 1)
      }
    }
  }
}

@Composable
private fun LaneCatalogCard(
  lane: String,
  isSelected: Boolean,
  isExpanded: Boolean,
  viewModel: MainViewModel,
  onToggleExpand: () -> Unit,
  onSelectLane: () -> Unit,
  onApplyBpmPreset: () -> Unit
) {
  val fw = viewModel.bankRepo.frameworks[lane]
  val drums = viewModel.bankRepo.laneDrums(lane)
  val profile = viewModel.bankRepo.laneTempoProfile(lane)

  Surface(
    color = if (isSelected) StudioCardElevated else StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable { onToggleExpand() }
      .border(
        width = if (isSelected) 1.5.dp else 1.dp,
        color = if (isSelected) AmberGold else StudioBorder,
        shape = RoundedCornerShape(12.dp)
      )
      .testTag("lane_item_${lane.lowercase().replace(" ", "_")}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = lane,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = if (isSelected) AmberGold else TextPrimary
            )
            Surface(
              color = if (profile.isVerified) AmberContainer else CyanContainer,
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = "${profile.bpmMin}-${profile.bpmMax} BPM",
                style = MonospaceMetric.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = if (profile.isVerified) AmberGold else StudioCyan,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
              )
            }
          }
          Text(
            text = "${profile.regionTag}${if (fw != null) " · ${fw.name}" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
          )
        }

        if (isSelected) {
          Surface(
            color = AmberGold,
            shape = RoundedCornerShape(6.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Check, contentDescription = "Active lane", tint = Color.Black, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("ACTIVE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
            }
          }
        } else {
          OutlinedButton(
            onClick = onSelectLane,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .defaultMinSize(minHeight = 36.dp)
              .testTag("select_lane_${lane.lowercase().replace(" ", "_")}")
          ) {
            Text("Select", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
          }
        }
      }

      if (isExpanded) {
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = StudioBorder)
        Spacer(modifier = Modifier.height(10.dp))

        Text("DRUM MOVES & TEXTURE:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextMuted)
        Text(drums, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)

        if (fw != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text("LAYOUT: ${fw.layout}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Spacer(modifier = Modifier.height(4.dp))
          Text("BUILD: ${fw.build}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Spacer(modifier = Modifier.height(4.dp))
          Text("DROP: ${fw.drop}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Spacer(modifier = Modifier.height(4.dp))
          Text("BREAKDOWN: ${fw.breakdown}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Spacer(modifier = Modifier.height(4.dp))
          Text("SIGNATURE: ${fw.signature}", style = MaterialTheme.typography.labelSmall, color = AmberGoldLight)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = onApplyBpmPreset,
          colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
        ) {
          Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Load $lane BPM Preset in Build", style = MaterialTheme.typography.labelMedium, color = AmberGold)
        }
      }
    }
  }
}
