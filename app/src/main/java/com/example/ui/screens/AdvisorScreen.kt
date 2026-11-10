package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun AdvisorScreen(viewModel: MainViewModel) {
  val jobType by viewModel.jobType.collectAsState()
  val stylePrompt by viewModel.stylePrompt.collectAsState()
  val lyricsMap by viewModel.lyricsMap.collectAsState()
  val excludeText by viewModel.excludeText.collectAsState()

  val isPreserve = jobType == JobType.PRESERVE
  val hasLockLine = stylePrompt.contains(LOCK_LINE)
  val hasEnd = lyricsMap.trim().endsWith("[End]")
  val hasNegativeInStyle = listOf("no vocals", "no drums", "exclude", "without", "remove").any {
    stylePrompt.contains(it, ignoreCase = true)
  }

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
          text = "SUNO V6 ADVISOR & AUDITOR",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = AmberGold
        )
        Text(
          text = "Real-time compliance auditor and official engineering guide for Suno v6 beat enhancement.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary
        )
      }
    }

    // Active Prompt Audit Report
    item {
      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "LIVE AUDIT REPORT",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = StudioCyan
          )
          Spacer(modifier = Modifier.height(10.dp))

          AuditCheckItem(
            label = "Preserve Lock Line",
            passed = !isPreserve || hasLockLine,
            detail = if (isPreserve && !hasLockLine) "FAIL: Missing '$LOCK_LINE' in Style Box." else "PASS: Lock line intact or not required."
          )

          AuditCheckItem(
            label = "Lyrics Map Closure",
            passed = hasEnd,
            detail = if (!hasEnd) "FAIL: Lyrics map must terminate with [End]." else "PASS: Map ends with [End]."
          )

          AuditCheckItem(
            label = "Negative Prompt Placement",
            passed = !hasNegativeInStyle,
            detail = if (hasNegativeInStyle) "WARNING: Found negative terms in Style box. Move them to Exclude box!" else "PASS: Clean style block without negative keywords."
          )

          AuditCheckItem(
            label = "Character Caps Compliance",
            passed = stylePrompt.length <= 1000 && lyricsMap.length <= 5000 && excludeText.length <= 1000,
            detail = "Style ${stylePrompt.length}/1000 · Lyrics ${lyricsMap.length}/5000 · Exclude ${excludeText.length}/1000"
          )
        }
      }
    }

    // Official Rules & Tested Recipes
    item {
      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "OFFICIAL SUNO V6 LAWS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
          Spacer(modifier = Modifier.height(8.dp))

          LawItem(
            number = "1",
            title = "THE PRESERVE LAW",
            description = "Route MUST be Extend. Style box must carry the exact lock line: '$LOCK_LINE'. Audio Influence 75-85 (80-85 when vocals matter most). Variety MUST default to 0."
          )

          LawItem(
            number = "2",
            title = "THE NEGATIVE SEPARATION LAW",
            description = "Never put 'no drums', 'no vocals', or negative commands in the Style box. Put all negative directives exclusively inside the Exclude Styles box."
          )

          LawItem(
            number = "3",
            title = "THE LYRICS MAP CLOSURE LAW",
            description = "Every structure map in the Lyrics Box must end with [End] to prevent indefinite loop drift and hallucinated fadeouts."
          )

          LawItem(
            number = "4",
            title = "ONE VARIABLE PER TAKE",
            description = "When optimizing generation takes, change only one parameter (e.g., Weirdness or 808 sub prompt) at a time to isolate cause and effect."
          )
        }
      }
    }

    // Credits, Upload Limits & Slider Semantics
    item {
      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "CREDITS, LIMITS & SLIDER SEMANTICS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = StudioCyan
          )
          Spacer(modifier = Modifier.height(10.dp))

          TroubleItem(
            issue = "Generation Cost",
            fix = "Standard generation costs 10 credits (generates 2 songs). Max Mode costs double (20 credits) for long tracks or close covers."
          )

          TroubleItem(
            issue = "Upload Limits",
            fix = "Free tier: up to 60 seconds. Pro/Premier tier: up to 8 minutes. Single v6 generation runs up to 8 minutes; Extend continues past it."
          )

          TroubleItem(
            issue = "Variety Slider",
            fix = "Controls prompt rewriting. At Variety 0, Suno keeps your style text verbatim. Above 0, Suno dynamically rewrites the style prompt."
          )

          TroubleItem(
            issue = "Weirdness Slider",
            fix = "Controls conventionality. 50% is normal expected result. The tested 20-30 band is safe-side for sample fidelity and tight bounce."
          )
        }
      }
    }

    // Common Fixes Flowchart
    item {
      Surface(
        color = StudioCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "TROUBLESHOOTING CHEAT SHEET",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
          Spacer(modifier = Modifier.height(10.dp))

          TroubleItem(
            issue = "Suno drifts away from original sample melody",
            fix = "Lower Variety to 0, raise Audio Influence to 80-85, ensure Lock Line is the very first sentence in Style."
          )

          TroubleItem(
            issue = "Muddy, boomy low end drowning the mix",
            fix = "Use 'soft-clipped 808', 'ducking under kick transient', or choose 'Low-end only' Track 3 utility prompt."
          )

          TroubleItem(
            issue = "Vocal hallucinations over instrumental section",
            fix = "Paste the 'Vocal-hallucination defense' Exclude bank and add '[Instrumental]' bracket to top of Lyrics map."
          )

          TroubleItem(
            issue = "Drop feels weak and lacks impact",
            fix = "Add stop-time bar: '(Final bar: canvas cuts for one beat — stop-time silence — then the drop)'. "
          )
        }
      }
    }
  }
}

@Composable
private fun AuditCheckItem(label: String, passed: Boolean, detail: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.Top
  ) {
    Icon(
      imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
      contentDescription = null,
      tint = if (passed) StudioWinner else StudioMiss,
      modifier = Modifier.size(16.dp).padding(top = 2.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
      Text(detail, style = MaterialTheme.typography.labelSmall, color = if (passed) TextSecondary else StudioMiss)
    }
  }
}

@Composable
private fun LawItem(number: String, title: String, description: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.Top
  ) {
    Surface(
      color = AmberContainer,
      shape = RoundedCornerShape(4.dp),
      modifier = Modifier.size(20.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(number, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberGold)
      }
    }
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)
      Text(description, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
  }
}

@Composable
private fun TroubleItem(issue: String, fix: String) {
  Column(modifier = Modifier.padding(vertical = 4.dp)) {
    Text("• $issue", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = StudioCyan)
    Text("  Fix: $fix", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
  }
}
