package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.StudioTab

private fun tabIconAndLabel(tab: StudioTab): Pair<ImageVector, String> {
  return when (tab) {
    StudioTab.ANALYZE -> Pair(Icons.Default.GraphicEq, "Analyze")
    StudioTab.STYLES -> Pair(Icons.Default.AutoFixHigh, "Styles")
    StudioTab.LIBRARY -> Pair(Icons.AutoMirrored.Filled.MenuBook, "Library")
    StudioTab.BUILD -> Pair(Icons.Default.Build, "Build")
    StudioTab.SIMPLE -> Pair(Icons.Default.Bolt, "Simple")
    StudioTab.LANES -> Pair(Icons.Default.ViewKanban, "Lanes")
    StudioTab.TAKES -> Pair(Icons.Default.Bookmarks, "Takes")
    StudioTab.ADVISOR -> Pair(Icons.Default.Security, "Advisor")
  }
}

@Composable
fun StudioBottomBar(
  currentTab: StudioTab,
  onTabSelected: (StudioTab) -> Unit
) {
  Surface(
    color = StudioSurface,
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .border(0.5.dp, StudioBorder),
    shadowElevation = 8.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      StudioTab.entries.forEach { tab ->
        val selected = tab == currentTab
        val (icon, label) = tabIconAndLabel(tab)

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .semantics { this.selected = selected }
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("tab_${tab.name.lowercase()}")
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(if (selected) AmberContainer else Color.Transparent),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = label,
              tint = if (selected) AmberGold else TextSecondary,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 10.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) AmberGold else TextMuted
          )
        }
      }
    }
  }
}

/**
 * Adaptive vertical Navigation Rail shown on Medium and Expanded tablet/foldable screens (>= 600.dp).
 */
@Composable
fun StudioNavigationRail(
  currentTab: StudioTab,
  onTabSelected: (StudioTab) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = StudioSurface,
    modifier = modifier
      .width(80.dp)
      .fillMaxHeight()
      .navigationBarsPadding()
      .border(0.5.dp, StudioBorder)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      StudioTab.entries.forEach { tab ->
        val selected = tab == currentTab
        val (icon, label) = tabIconAndLabel(tab)

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AmberContainer else Color.Transparent)
            .semantics { this.selected = selected }
            .clickable { onTabSelected(tab) }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("rail_tab_${tab.name.lowercase()}")
        ) {
          Icon(
            imageVector = icon,
            contentDescription = "$label workspace tab",
            tint = if (selected) AmberGold else TextSecondary,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 11.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) AmberGold else TextSecondary
          )
        }
      }
    }
  }
}

