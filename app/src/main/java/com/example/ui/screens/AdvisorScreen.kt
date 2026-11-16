package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ComplianceAuditReport
import com.example.data.model.ComplianceCheckResult
import com.example.data.model.ComplianceRuleId
import com.example.data.model.ComplianceSnapshot
import com.example.data.model.LOCK_LINE
import com.example.data.model.SunoComplianceAuditor
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@Composable
fun AdvisorScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val jobType by viewModel.jobType.collectAsStateWithLifecycle()
  val stylePrompt by viewModel.stylePrompt.collectAsStateWithLifecycle()
  val lyricsMap by viewModel.lyricsMap.collectAsStateWithLifecycle()
  val excludeText by viewModel.excludeText.collectAsStateWithLifecycle()
  val variety by viewModel.variety.collectAsStateWithLifecycle()
  val audioInfluence by viewModel.audioInfluence.collectAsStateWithLifecycle()
  val undoSnapshot by viewModel.complianceUndoSnapshot.collectAsStateWithLifecycle()

  val auditReport = remember(jobType, stylePrompt, lyricsMap, excludeText, variety, audioInfluence) {
    SunoComplianceAuditor.audit(
      jobType = jobType,
      stylePrompt = stylePrompt,
      lyricsMap = lyricsMap,
      excludeText = excludeText,
      variety = variety,
      audioInfluence = audioInfluence
    )
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioBg)
  ) {
    val isWideLayout = maxWidth >= 760.dp

    if (isWideLayout) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        LazyColumn(
          modifier = Modifier.weight(0.95f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Column {
              Text(
                text = "SUNO V6 ADVISOR & AUDITOR",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AmberGold
              )
              Text(
                text = "Real-time compliance auditor with 1-tap Auto-Fix repairs and official engineering rules for Suno v6 beat enhancement.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
              )
            }
          }
          item {
            LiveAuditReportCard(
              report = auditReport,
              undoSnapshot = undoSnapshot,
              onAutoFixRule = { ruleId -> viewModel.autoFixComplianceRule(ruleId, context) },
              onAutoFixAll = { viewModel.autoFixAllCompliance(context) },
              onUndoFix = { viewModel.undoLastComplianceFix(context) },
              onDismissUndo = { viewModel.dismissComplianceUndo() }
            )
          }
        }

        LazyColumn(
          modifier = Modifier.weight(1.05f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item { OfficialLawsCard() }
          item { CreditsLimitsCard() }
          item { TroubleshootingCard() }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item {
          Column {
            Text(
              text = "SUNO V6 ADVISOR & AUDITOR",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = AmberGold
            )
            Text(
              text = "Real-time compliance auditor with 1-tap Auto-Fix repairs and official engineering rules for Suno v6 beat enhancement.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary
            )
          }
        }
        item {
          LiveAuditReportCard(
            report = auditReport,
            undoSnapshot = undoSnapshot,
            onAutoFixRule = { ruleId -> viewModel.autoFixComplianceRule(ruleId, context) },
            onAutoFixAll = { viewModel.autoFixAllCompliance(context) },
            onUndoFix = { viewModel.undoLastComplianceFix(context) },
            onDismissUndo = { viewModel.dismissComplianceUndo() }
          )
        }
        item { OfficialLawsCard() }
        item { CreditsLimitsCard() }
        item { TroubleshootingCard() }
      }
    }
  }
}

@Composable
private fun LiveAuditReportCard(
  report: ComplianceAuditReport,
  undoSnapshot: ComplianceSnapshot?,
  onAutoFixRule: (ComplianceRuleId) -> Unit,
  onAutoFixAll: () -> Unit,
  onUndoFix: () -> Unit,
  onDismissUndo: () -> Unit
) {
  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        1.dp,
        if (report.allPassed) StudioWinner.copy(alpha = 0.4f) else AmberGold.copy(alpha = 0.45f),
        RoundedCornerShape(12.dp)
      )
      .testTag("live_audit_report_card")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "LIVE AUDIT REPORT",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = StudioCyan
        )

        Surface(
          color = if (report.allPassed) StudioWinner.copy(alpha = 0.16f) else StudioMiss.copy(alpha = 0.16f),
          shape = RoundedCornerShape(6.dp)
        ) {
          Text(
            text = "${report.passedCount}/${report.totalCount} PASS",
            style = MonospaceMetric.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = if (report.allPassed) StudioWinner else StudioMiss,
            modifier = Modifier
              .padding(horizontal = 8.dp, vertical = 3.dp)
              .testTag("advisor_score_badge")
          )
        }
      }

      if (!report.allPassed) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = onAutoFixAll,
          colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("action_autofix_all")
        ) {
          Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Auto-Fix All Issues (${report.totalCount - report.passedCount})",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.Black
          )
        }
      }

      AnimatedVisibility(visible = undoSnapshot != null) {
        if (undoSnapshot != null) {
          Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp)
              .border(0.5.dp, StudioCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
              .testTag("advisor_undo_banner")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = undoSnapshot.fixSummary.ifBlank { "Applied compliance fix" },
                style = MaterialTheme.typography.labelSmall,
                color = StudioCyan,
                modifier = Modifier.weight(1f)
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                  onClick = onUndoFix,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("action_undo_autofix")
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo Auto-Fix",
                    tint = AmberGold,
                    modifier = Modifier.size(15.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Undo", color = AmberGold, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
                IconButton(
                  onClick = onDismissUndo,
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Dismiss undo notice", tint = TextMuted, modifier = Modifier.size(14.dp))
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      report.checks.forEach { check ->
        AuditCheckItem(
          check = check,
          onAutoFix = { onAutoFixRule(check.id) }
        )
      }
    }
  }
}

@Composable
private fun AuditCheckItem(
  check: ComplianceCheckResult,
  onAutoFix: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
        contentDescription = null,
        tint = if (check.passed) StudioWinner else StudioMiss,
        modifier = Modifier
          .padding(top = 2.dp)
          .size(16.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(
          text = check.label,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          color = TextPrimary
        )
        Text(
          text = check.detail,
          style = MaterialTheme.typography.labelSmall,
          color = if (check.passed) TextSecondary else StudioMiss
        )
      }
    }

    if (!check.passed && check.fixLabel != null) {
      Spacer(modifier = Modifier.width(8.dp))
      OutlinedButton(
        onClick = onAutoFix,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("action_autofix_${check.id.name.lowercase()}")
      ) {
        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = check.fixLabel,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = AmberGold
        )
      }
    }
  }
}

@Composable
private fun OfficialLawsCard() {
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

@Composable
private fun CreditsLimitsCard() {
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

@Composable
private fun TroubleshootingCard() {
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
        fix = "Add stop-time bar: '(Final bar: canvas cuts for one beat — stop-time silence — then the drop)'."
      )
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
