package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LOCK_LINE
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptBox(
  title: String,
  text: String,
  onTextChanged: (String) -> Unit,
  limit: Int,
  minLines: Int = 3,
  isPreserve: Boolean = false,
  extraHeaderAction: (@Composable () -> Unit)? = null,
  onCopy: () -> Unit
) {
  val chars = text.length
  val isOverLimit = chars > limit
  val isAtCap = chars == limit
  val isNearCap = !isOverLimit && !isAtCap && chars >= (limit * 0.9).toInt()
  val hasLockLine = text.contains(LOCK_LINE)
  val isPreserveMissingLock = isPreserve && !hasLockLine && text.isNotBlank()
  var isCopiedFlash by remember { mutableStateOf(false) }
  LaunchedEffect(isCopiedFlash) {
    if (isCopiedFlash) {
      delay(1200L)
      isCopiedFlash = false
    }
  }

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (isOverLimit || isPreserveMissingLock) StudioMiss else StudioBorder,
        shape = RoundedCornerShape(12.dp)
      )
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      // Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        FlowRow(
          modifier = Modifier
            .weight(1f)
            .padding(end = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          // Character count badge
          Surface(
            color = if (isOverLimit) Color(0x33EF4444) else StudioCardElevated,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "$chars / $limit",
              style = MonospaceMetric.copy(fontSize = 11.sp),
              color = if (isOverLimit) StudioMiss else if (isAtCap || isNearCap) AmberGold else StudioCyan,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          if (isOverLimit || isAtCap || isNearCap) {
            Surface(
              color = if (isOverLimit) Color(0x28EF4444) else Color(0x28F5A524),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = when {
                  isOverLimit -> "Over cap"
                  isAtCap -> "At cap"
                  else -> "Near cap"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = if (isOverLimit) StudioMiss else AmberGold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (extraHeaderAction != null) {
            extraHeaderAction()
            Spacer(modifier = Modifier.width(6.dp))
          }
          IconButton(
            onClick = {
              isCopiedFlash = true
              onCopy()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("copy_${title.lowercase().replace(" ", "_")}")
          ) {
            Icon(
              imageVector = if (isCopiedFlash) Icons.Default.Check else Icons.Default.ContentCopy,
              contentDescription = "Copy Prompt: $title",
              tint = if (isCopiedFlash) StudioWinner else TextSecondary,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      // Preserve Warning if missing lock line
      if (isPreserveMissingLock) {
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
          color = Color(0x22EF4444),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Lock Missing",
              tint = StudioMiss,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Preserve jobs must include the exact lock line!",
              style = MaterialTheme.typography.labelSmall,
              color = StudioMiss,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Input TextField
      OutlinedTextField(
        value = text,
        onValueChange = { onTextChanged(it.take(limit)) },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_${title.lowercase().replace(" ", "_")}"),
        textStyle = MonospacePrompt.copy(color = TextPrimary),
        minLines = minLines,
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
