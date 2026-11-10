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
import com.example.data.model.TakeEntity
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun TakesScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val takesList by viewModel.takesList.collectAsState()
  val filterResult by viewModel.takeFilterResult.collectAsState()

  val totalTakes = takesList.size
  val winnerCount = takesList.count { it.result.equals("winner", ignoreCase = true) }
  val winRate = if (totalTakes > 0) (winnerCount * 100 / totalTakes) else 0

  val filteredTakes = remember(takesList, filterResult) {
    if (filterResult == "ALL") takesList
    else takesList.filter { it.result.equals(filterResult, ignoreCase = true) }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioBg)
      .padding(horizontal = 14.dp),
    contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Header & Win-Rate KPI Card
    item {
      Column {
        Text(
          text = "PRODUCTION LOGBOOK (TAKES)",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = AmberGold
        )
        Text(
          text = "Local Room database log tracking your Suno v6 takes, prompt snapshots, winning formulas, and next-change notes.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // KPI Card
        Surface(
          color = StudioCard,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("TOTAL TAKES", style = MaterialTheme.typography.labelSmall, color = TextMuted)
              Text("$totalTakes", style = MonospaceMetric.copy(fontSize = 22.sp), color = TextPrimary)
            }
            VerticalDivider(modifier = Modifier.height(30.dp), color = StudioBorder)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("WINNERS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
              Text("$winnerCount", style = MonospaceMetric.copy(fontSize = 22.sp), color = StudioWinner)
            }
            VerticalDivider(modifier = Modifier.height(30.dp), color = StudioBorder)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("WIN RATE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
              Text("$winRate%", style = MonospaceMetric.copy(fontSize = 22.sp), color = AmberGold)
            }
          }
        }
      }
    }

    // Filter Chips
    item {
      val filterOptions = listOf("ALL", "WINNER", "PENDING", "MISS")
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        filterOptions.forEach { opt ->
          val selected = opt == filterResult
          Surface(
            color = if (selected) AmberGold else StudioCardElevated,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .clickable { viewModel.setTakeFilterResult(opt) }
              .border(0.5.dp, StudioBorder, RoundedCornerShape(14.dp))
              .testTag("filter_take_$opt")
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
              Text(
                text = opt,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) Color.Black else TextPrimary
              )
            }
          }
        }
      }
    }

    // Empty State
    if (filteredTakes.isEmpty()) {
      item {
        Surface(
          color = StudioCard,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
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
    }

    // Take Items List
    items(filteredTakes, key = { it.id }) { take ->
      TakeItemCard(
        take = take,
        onUpdateResult = { res -> viewModel.updateTakeResult(take.id, res) },
        onUpdateNextChange = { txt -> viewModel.updateTakeNextChange(take.id, txt) },
        onDuplicate = { viewModel.duplicateTake(take.id) },
        onDelete = { viewModel.deleteTake(take.id) },
        onCopy = {
          val bundle = "Take: ${take.name}\nLane: ${take.lane}\nSettings: ${take.settings}\n\nStyle:\n${take.style}\n\nLyrics:\n${take.lyrics}"
          viewModel.copyToClipboard(context, "Take ${take.name}", bundle)
        }
      )
    }
  }
}

@Composable
private fun TakeItemCard(
  take: TakeEntity,
  onUpdateResult: (String) -> Unit,
  onUpdateNextChange: (String) -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onCopy: () -> Unit
) {
  var editingNote by remember { mutableStateOf(false) }
  var noteInput by remember { mutableStateOf(take.nextChange) }

  val resultColor = when (take.result.lowercase()) {
    "winner" -> StudioWinner
    "miss" -> StudioMiss
    else -> StudioPending
  }

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
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

        // Result Selector Dropdown / Badges
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          ResultPill(label = "WIN", active = take.result.equals("winner", true), activeColor = StudioWinner) {
            onUpdateResult("winner")
          }
          ResultPill(label = "MISS", active = take.result.equals("miss", true), activeColor = StudioMiss) {
            onUpdateResult("miss")
          }
          ResultPill(label = "PEND", active = take.result.equals("pending", true), activeColor = StudioPending) {
            onUpdateResult("pending")
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Style Preview
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

      // Next Change Note
      Spacer(modifier = Modifier.height(6.dp))
      if (editingNote) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            placeholder = { Text("What to change on next take...") },
            modifier = Modifier.weight(1f),
            singleLine = true
          )
          IconButton(onClick = {
            onUpdateNextChange(noteInput)
            editingNote = false
          }) {
            Icon(Icons.Default.Check, contentDescription = "Save Note", tint = StudioWinner)
          }
        }
      } else {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { editingNote = true }
            .background(StudioCardElevated)
            .padding(8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (take.nextChange.isBlank()) "Tap to add next-change note..." else "Next change: ${take.nextChange}",
            style = MaterialTheme.typography.labelSmall,
            color = if (take.nextChange.isBlank()) TextMuted else AmberGoldLight
          )
          Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Card Bottom Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = StudioCyan, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Share, contentDescription = "Copy Take", tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StudioMiss, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun ResultPill(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
  Surface(
    color = if (active) activeColor else StudioCardElevated,
    shape = RoundedCornerShape(4.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .clickable { onClick() }
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
      color = if (active) Color.Black else TextMuted,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
    )
  }
}
