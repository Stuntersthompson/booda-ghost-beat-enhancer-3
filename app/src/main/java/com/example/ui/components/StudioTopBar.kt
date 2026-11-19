package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobType
import com.example.ui.theme.*

@Composable
fun StudioTopBar(
  activeLane: String,
  jobType: JobType,
  onJobTypeChanged: (JobType) -> Unit,
  onLaneClick: () -> Unit,
  onSettingsClick: () -> Unit = {}
) {
  Surface(
    color = StudioSurface,
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .border(0.5.dp, StudioBorder),
    shadowElevation = 4.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp)
        ) {
          BrandLogoImage(
            height = 44.dp,
            contentDescription = "BooDa Beat Enhancer official logo",
            framed = true,
            modifier = Modifier.testTag("app_header_logo")
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "BOODA BEAT ENHANCER",
              style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp
              ),
              color = AmberGold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Suno v6 Production Console",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Active Lane Selector Chip
          Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .clip(RoundedCornerShape(16.dp))
              .clickable { onLaneClick() }
              .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
              .semantics {
                role = Role.Button
                contentDescription = "Active lane $activeLane. Tap to switch subgenre lane"
              }
              .testTag("active_lane_chip")
          ) {
            Row(
              modifier = Modifier
                .widthIn(max = 128.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = StudioCyan,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = activeLane,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          // Studio Settings Drawer Button
          IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .testTag("action_open_settings")
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Studio Settings",
              tint = TextSecondary,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Job Type Toggle Bar (Original vs Preserve)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(StudioBg)
          .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        val isPreserve = jobType == JobType.PRESERVE
        // Preserve Button
        Box(
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPreserve) AmberGold else Color.Transparent)
            .clickable { onJobTypeChanged(JobType.PRESERVE) }
            .semantics {
              role = Role.Tab
              selected = isPreserve
              contentDescription = "Preserve Extend mode, locks original sample and vocals"
            }
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag("job_preserve_tab"),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = if (isPreserve) Color.Black else TextSecondary,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "PRESERVE (Extend)",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isPreserve) FontWeight.Bold else FontWeight.Normal
              ),
              color = if (isPreserve) Color.Black else TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // Original Button
        Box(
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (!isPreserve) StudioCyan else Color.Transparent)
            .clickable { onJobTypeChanged(JobType.ORIGINAL) }
            .semantics {
              role = Role.Tab
              selected = !isPreserve
              contentDescription = "Original Create mode, fresh instrumental generation"
            }
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag("job_original_tab"),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = if (!isPreserve) Color.Black else TextSecondary,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "ORIGINAL (Create)",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (!isPreserve) FontWeight.Bold else FontWeight.Normal
              ),
              color = if (!isPreserve) Color.Black else TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}
