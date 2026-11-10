package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LOCK_LINE
import com.example.ui.theme.*

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
  val hasLockLine = text.contains(LOCK_LINE)
  val isPreserveMissingLock = isPreserve && !hasLockLine && text.isNotBlank()

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
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
          Spacer(modifier = Modifier.width(8.dp))
          // Character count badge
          Surface(
            color = if (isOverLimit) Color(0x33EF4444) else StudioCardElevated,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "$chars / $limit",
              style = MonospaceMetric.copy(fontSize = 11.sp),
              color = if (isOverLimit) StudioMiss else if (chars > limit * 0.9) AmberGold else StudioCyan,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (extraHeaderAction != null) {
            extraHeaderAction()
            Spacer(modifier = Modifier.width(6.dp))
          }
          IconButton(
            onClick = onCopy,
            modifier = Modifier
              .size(32.dp)
              .testTag("copy_${title.lowercase().replace(" ", "_")}")
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy $title",
              tint = TextSecondary,
              modifier = Modifier.size(16.dp)
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
            modifier = Modifier.padding(6.dp),
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
              color = StudioMiss
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Input TextField
      OutlinedTextField(
        value = text,
        onValueChange = onTextChanged,
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
