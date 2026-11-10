package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.StudioBottomBar
import com.example.ui.components.StudioTopBar
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      BooDaBeatTheme {
        MainAppContent(viewModel)
      }
    }
  }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
  val currentTab by viewModel.currentTab.collectAsState()
  val activeLane by viewModel.activeLane.collectAsState()
  val jobType by viewModel.jobType.collectAsState()

  var showLaneDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = StudioBg,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      StudioTopBar(
        activeLane = activeLane,
        jobType = jobType,
        onJobTypeChanged = { viewModel.setJobType(it) },
        onLaneClick = { showLaneDialog = true }
      )
    },
    bottomBar = {
      StudioBottomBar(
        currentTab = currentTab,
        onTabSelected = { viewModel.setTab(it) }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      when (currentTab) {
        StudioTab.ANALYZE -> AnalyzeScreen(viewModel)
        StudioTab.STYLES -> StyleGeneratorScreen(viewModel)
        StudioTab.LIBRARY -> LibraryScreen(viewModel)
        StudioTab.BUILD -> BuildScreen(viewModel)
        StudioTab.SIMPLE -> SimpleScreen(viewModel)
        StudioTab.LANES -> LanesScreen(viewModel)
        StudioTab.TAKES -> TakesScreen(viewModel)
        StudioTab.ADVISOR -> AdvisorScreen(viewModel)
      }
    }
  }

  // Quick Lane Picker Dialog
  if (showLaneDialog) {
    Dialog(onDismissRequest = { showLaneDialog = false }) {
      Surface(
        color = StudioCardElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.75f)
          .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "SELECT SUBGENRE LANE",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = AmberGold
            )
            IconButton(onClick = { showLaneDialog = false }) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(viewModel.bankRepo.allLanes) { lane ->
              val isSelected = lane == activeLane
              Surface(
                color = if (isSelected) AmberContainer else StudioCard,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    viewModel.setLane(lane)
                    showLaneDialog = false
                  }
                  .border(0.5.dp, if (isSelected) AmberGold else StudioBorder, RoundedCornerShape(8.dp))
                  .testTag("dialog_lane_$lane")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = lane,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) AmberGold else TextPrimary
                  )
                  if (isSelected) {
                    Text(
                      text = "ACTIVE",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      color = AmberGold
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
}
