package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun LanesScreen(viewModel: MainViewModel) {
  val activeLane by viewModel.activeLane.collectAsState()
  var searchQuery by remember { mutableStateOf("") }
  var expandedLane by remember { mutableStateOf<String?>(activeLane) }

  val allLanes = viewModel.bankRepo.allLanes
  val filteredLanes = remember(searchQuery) {
    if (searchQuery.isBlank()) allLanes else allLanes.filter { it.contains(searchQuery, ignoreCase = true) }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioBg)
      .padding(horizontal = 14.dp),
    contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Header
    item {
      Column {
        Text(
          text = "41 SUBGENRE LANES & FRAMEWORKS",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = AmberGold
        )
        Text(
          text = "21 Verified-Bank Lanes + 20 Expansion Lanes. Each with its own named structural profile, builds, drops, and drum kits.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Search Field
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search 41 subgenre lanes...", color = TextMuted) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
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
      }
    }

    // Side-by-Side 2-Lane Comparison Tool Deck
    item {
      var showComparisonDeck by remember { mutableStateOf(false) }
      var laneA by remember { mutableStateOf(activeLane) }
      var laneB by remember { mutableStateOf(if (activeLane != "Memphis Trap") "Memphis Trap" else "Boom Trap") }
      var showDropdownA by remember { mutableStateOf(false) }
      var showDropdownB by remember { mutableStateOf(false) }

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
            TextButton(
              onClick = { showComparisonDeck = !showComparisonDeck },
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = if (showComparisonDeck) "Hide Deck ▲" else "Compare Lanes ▾",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = AmberGold
              )
            }
          }

          if (showComparisonDeck) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Lane A Selector
              Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                  onClick = { showDropdownA = true },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.fillMaxWidth().height(36.dp),
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(laneA.take(16), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberGold)
                }
                DropdownMenu(expanded = showDropdownA, onDismissRequest = { showDropdownA = false }) {
                  allLanes.forEach { l ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { laneA = l; showDropdownA = false })
                  }
                }
              }

              // Lane B Selector
              Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                  onClick = { showDropdownB = true },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.fillMaxWidth().height(36.dp),
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
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

            // Two Cards
            val fwA = viewModel.bankRepo.frameworks[laneA]
            val fwB = viewModel.bankRepo.frameworks[laneB]
            val drumsA = viewModel.bankRepo.laneDrums(laneA)
            val drumsB = viewModel.bankRepo.laneDrums(laneB)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Deck Lane A
              Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).border(0.5.dp, StudioBorder, RoundedCornerShape(8.dp))
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text(laneA, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("Drums: $drumsA", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                  if (fwA != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Build: ${fwA.build}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Drop: ${fwA.drop}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Sig: ${fwA.signature}", style = MaterialTheme.typography.labelSmall, color = AmberGoldLight)
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Button(
                    onClick = { viewModel.setLane(laneA) },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                  ) {
                    Text("Use $laneA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                  }
                }
              }

              // Deck Lane B
              Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).border(0.5.dp, StudioBorder, RoundedCornerShape(8.dp))
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text(laneB, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = StudioCyan)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("Drums: $drumsB", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                  if (fwB != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Build: ${fwB.build}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Drop: ${fwB.drop}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Sig: ${fwB.signature}", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Button(
                    onClick = { viewModel.setLane(laneB) },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                  ) {
                    Text("Use $laneB", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                  }
                }
              }
            }
          }
        }
      }
    }

    // Lane Cards
    items(filteredLanes) { lane ->
      val isSelected = lane == activeLane
      val isExpanded = lane == expandedLane
      val fw = viewModel.bankRepo.frameworks[lane]
      val drums = viewModel.bankRepo.laneDrums(lane)
      val exp = viewModel.bankRepo.expansionLanes.find { it.name.equals(lane, ignoreCase = true) }

      Surface(
        color = if (isSelected) StudioCardElevated else StudioCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable { expandedLane = if (isExpanded) null else lane }
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
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = lane,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = if (isSelected) AmberGold else TextPrimary
                )
                if (exp != null) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Surface(
                    color = CyanContainer,
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(
                      text = exp.region,
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                      color = StudioCyan,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                  }
                }
              }
              if (fw != null) {
                Text(
                  text = fw.name,
                  style = MaterialTheme.typography.labelSmall,
                  color = TextMuted
                )
              }
            }

            if (isSelected) {
              Surface(
                color = AmberGold,
                shape = RoundedCornerShape(6.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("ACTIVE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                }
              }
            } else {
              OutlinedButton(
                onClick = { viewModel.setLane(lane) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(28.dp).testTag("select_lane_${lane.lowercase().replace(" ", "_")}")
              ) {
                Text("Select", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
              }
            }
          }

          // Expanded Details
          if (isExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = StudioBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Drum Pocket
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
          }
        }
      }
    }
  }
}
