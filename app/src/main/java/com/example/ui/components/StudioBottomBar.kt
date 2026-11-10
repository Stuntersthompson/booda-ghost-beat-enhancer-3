package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.StudioTab

import androidx.compose.material.icons.automirrored.filled.MenuBook

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
        .padding(horizontal = 4.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      StudioTab.entries.forEach { tab ->
        val selected = tab == currentTab
        val (icon, label) = when (tab) {
          StudioTab.ANALYZE -> Pair(Icons.Default.GraphicEq, "Analyze")
          StudioTab.STYLES -> Pair(Icons.Default.AutoFixHigh, "Styles")
          StudioTab.LIBRARY -> Pair(Icons.AutoMirrored.Filled.MenuBook, "Library")
          StudioTab.BUILD -> Pair(Icons.Default.Build, "Build")
          StudioTab.SIMPLE -> Pair(Icons.Default.Bolt, "Simple")
          StudioTab.LANES -> Pair(Icons.Default.ViewKanban, "Lanes")
          StudioTab.TAKES -> Pair(Icons.Default.Bookmarks, "Takes")
          StudioTab.ADVISOR -> Pair(Icons.Default.Security, "Advisor")
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 6.dp, vertical = 4.dp)
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
