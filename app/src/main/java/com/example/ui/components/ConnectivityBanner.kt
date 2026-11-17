package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.FirebaseHealthStatus
import com.example.viewmodel.GeminiHealthStatus
import com.example.viewmodel.GlobalConnectivityState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalConnectivityBanner(
  state: GlobalConnectivityState,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = state.shouldShowBanner,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut()
  ) {
    val accentColor = if (!state.isNetworkOnline) StudioPending else StudioMiss
    val bannerContainer = if (!state.isNetworkOnline) {
      Color(0x22F59E0B)
    } else {
      Color(0x22EF4444)
    }

    val firebaseColor = when (state.firebaseHealth) {
      FirebaseHealthStatus.HEALTHY -> StudioWinner
      FirebaseHealthStatus.INITIALIZING -> StudioCyan
      FirebaseHealthStatus.UNREACHABLE -> StudioMiss
      FirebaseHealthStatus.OFFLINE -> StudioPending
    }

    val geminiColor = when (state.geminiHealth) {
      GeminiHealthStatus.HEALTHY -> StudioWinner
      GeminiHealthStatus.UNREACHABLE -> StudioMiss
      GeminiHealthStatus.TIMED_OUT -> StudioPending
      GeminiHealthStatus.OFFLINE -> StudioPending
    }

    Surface(
      color = bannerContainer,
      modifier = modifier
        .fillMaxWidth()
        .border(0.5.dp, accentColor.copy(alpha = 0.55f))
        .semantics {
          liveRegion = LiveRegionMode.Polite
          contentDescription = "Connection Error. Firebase ${state.firebaseHealth.label}, Gemini ${state.geminiHealth.label}. Offline tools stay active."
        }
        .testTag("global_connectivity_banner")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = if (!state.isNetworkOnline) Icons.Default.CloudOff else Icons.Default.Warning,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "CONNECTION ERROR",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
              color = accentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Offline tools stay active",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
              onClick = onRetry,
              enabled = !state.isRetrying,
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics {
                  contentDescription = if (state.lastFailedRequest != null) {
                    "Retry connection and ${state.lastFailedRequest.label}"
                  } else {
                    "Retry Firebase and Gemini connection"
                  }
                }
                .testTag("action_retry_connectivity")
            ) {
              if (state.isRetrying) {
                CircularProgressIndicator(
                  color = AmberGold,
                  strokeWidth = 2.dp,
                  modifier = Modifier.size(14.dp)
                )
              } else {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = null,
                  tint = AmberGold,
                  modifier = Modifier.size(15.dp)
                )
              }
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (state.isRetrying) "Retrying" else "Retry",
                color = AmberGold,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
              )
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Dismiss connection error banner" }
                .testTag("action_dismiss_connectivity_banner")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          HealthStatusPill(
            serviceName = "Firebase",
            statusLabel = state.firebaseHealth.label,
            accent = firebaseColor,
            tag = "connectivity_firebase_pill"
          )
          HealthStatusPill(
            serviceName = "Gemini",
            statusLabel = state.geminiHealth.label,
            accent = geminiColor,
            tag = "connectivity_gemini_pill"
          )
        }

        val errDetail = state.lastErrorMessage
        if (!errDetail.isNullOrBlank()) {
          Text(
            text = errDetail,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = TextPrimary
          )
        }
      }
    }
  }
}

@Composable
private fun HealthStatusPill(
  serviceName: String,
  statusLabel: String,
  accent: Color,
  tag: String
) {
  Surface(
    color = accent.copy(alpha = 0.16f),
    shape = RoundedCornerShape(6.dp),
    modifier = Modifier
      .border(0.5.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
      .semantics { contentDescription = "$serviceName status: $statusLabel" }
      .testTag(tag)
  ) {
    Text(
      text = "$serviceName: $statusLabel",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
      color = accent,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}
