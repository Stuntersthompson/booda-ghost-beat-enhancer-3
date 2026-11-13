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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.SIMPLE_WORKING_LIMIT
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun SimpleScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val jobType by viewModel.jobType.collectAsStateWithLifecycle()
  val activeLane by viewModel.activeLane.collectAsStateWithLifecycle()
  val simplePrompt by viewModel.simplePrompt.collectAsStateWithLifecycle()

  val isPreserve = jobType == JobType.PRESERVE
  val chars = simplePrompt.length
  val isOverLimit = chars > SIMPLE_WORKING_LIMIT
  val remixTemplates = remember(activeLane) { viewModel.bankRepo.getRemixTemplates(activeLane) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioBg)
      .padding(horizontal = 14.dp),
    contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header
    item {
      Column {
        Text(
          text = "SIMPLE COMPOSER (SINGLE BOX)",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = AmberGold
        )
        Text(
          text = "Unified all-in-one prompt combining lane texture, drum moves, and full-song arrangement directives. Working limit ~3,000 characters.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary
        )
      }
    }

    // Quick Actions
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = { viewModel.updateSimplePrompt() },
          colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f).testTag("action_regen_simple")
        ) {
          Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Regenerate", color = TextPrimary)
        }

        Button(
          onClick = { viewModel.randomizeAll() },
          colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f).testTag("action_random_simple")
        ) {
          Icon(Icons.Default.Shuffle, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Randomize", color = TextPrimary)
        }
      }
    }

    // Remix Family Templates Chips
    item {
      Column {
        Text(
          text = "REMIX FAMILIES FOR $activeLane",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = TextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(remixTemplates, key = { it.id }) { rt ->
            Surface(
              color = StudioCardElevated,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  val newPrompt = if (isPreserve) {
                    "${rt.prompt}\n\n[Instrumental]\n[Intro] (uploaded sample plays solo exactly as performed)\n[Verse 1] (canvas drums enter underneath)\n[Build-Up] (tension builds into chorus drop)\n[Chorus 1] (full canvas drop at peak weight)\n[Breakdown] (sample plays solo untouched)\n[Chorus 2] (final heaviest drop)\n[Outro] (canvas drums fade, sample finishes solo)\n[End]"
                  } else {
                    "${rt.prompt.replace("$LOCK_LINE. ", "")}\n\n[Instrumental]\n[Intro] (melodic atmosphere)\n[Verse 1] (full groove enters)\n[Build-Up] (build into chorus)\n[Chorus 1] (peak drop energy)\n[Breakdown] (melody solo)\n[Chorus 2] (heaviest finish)\n[Outro] (clean fade)\n[End]"
                  }
                  viewModel.updateSimplePrompt(newPrompt)
                }
                .border(0.5.dp, StudioBorder, RoundedCornerShape(14.dp))
            ) {
              Text(
                text = rt.family,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = StudioCyan,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }
          }
        }
      }
    }

    // Simple Prompt Editor Box
    item {
      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(
            1.dp,
            if (isOverLimit) StudioMiss else StudioBorder,
            RoundedCornerShape(12.dp)
          )
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "SIMPLE PROMPT BOX",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = AmberGold
              )
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                color = if (isOverLimit) Color(0x33EF4444) else StudioCardElevated,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "$chars / $SIMPLE_WORKING_LIMIT c",
                  style = MonospaceMetric.copy(fontSize = 11.sp),
                  color = if (isOverLimit) StudioMiss else StudioCyan,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            IconButton(
              onClick = { viewModel.copyToClipboard(context, "Simple Prompt", simplePrompt) },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("action_copy_simple")
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "Copy Simple Prompt", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = simplePrompt,
            onValueChange = { viewModel.updateSimplePrompt(it) },
            modifier = Modifier.fillMaxWidth().testTag("input_simple_prompt"),
            textStyle = MonospacePrompt.copy(color = TextPrimary),
            minLines = 10,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = StudioBg,
              unfocusedContainerColor = StudioBg,
              focusedBorderColor = StudioCyan,
              unfocusedBorderColor = StudioBorder
            ),
            shape = RoundedCornerShape(8.dp)
          )
        }
      }
    }

    // Primary Copy Button
    item {
      Button(
        onClick = { viewModel.copyToClipboard(context, "Simple Prompt", simplePrompt) },
        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("action_copy_simple_main")
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Copy Simple Prompt to Clipboard", style = MaterialTheme.typography.titleMedium, color = Color.Black)
      }
    }
  }
}
