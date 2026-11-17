package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobType
import com.example.data.model.PromptTemplateEntity
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.MonospaceMetric
import com.example.ui.theme.MonospacePrompt
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioMiss
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PromptTemplateCard(
  template: PromptTemplateEntity,
  onApply: () -> Unit,
  onTogglePin: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isPreserve = JobType.fromString(template.jobType) == JobType.PRESERVE

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(10.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (template.isPinned) AmberGold.copy(alpha = 0.55f) else StudioBorder,
        shape = RoundedCornerShape(10.dp)
      )
      .testTag("template_card_${template.id}")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f)
        ) {
          Surface(
            color = if (isPreserve) AmberContainer else CyanContainer,
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = template.category,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = if (isPreserve) AmberGold else StudioCyan,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = if (isPreserve) "Preserve" else "Original",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = TextSecondary,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          if (template.bpmHint.isNotBlank()) {
            Text(
              text = template.bpmHint,
              style = MonospaceMetric.copy(fontSize = 10.sp),
              color = AmberGold
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${template.useCount}×",
            style = MonospaceMetric.copy(fontSize = 11.sp),
            color = TextMuted
          )
          IconButton(
            onClick = onTogglePin,
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .testTag("template_pin_${template.id}")
          ) {
            Icon(
              imageVector = if (template.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
              contentDescription = if (template.isPinned) "Unpin template ${template.title}" else "Pin template ${template.title}",
              tint = if (template.isPinned) AmberGold else TextSecondary,
              modifier = Modifier.size(18.dp)
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .testTag("template_delete_${template.id}")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete template ${template.title}",
              tint = StudioMiss.copy(alpha = 0.85f),
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = template.title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = template.stylePrompt,
        style = MonospacePrompt.copy(fontSize = 12.sp),
        color = TextSecondary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${template.lane} · V${template.variety} W${template.weirdness} S${template.styleInfluence} A${template.audioInfluence}",
          style = MonospaceMetric.copy(fontSize = 10.sp),
          color = TextMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = onApply,
          colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Apply ${template.title} template to Build" }
            .testTag("template_apply_${template.id}")
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Apply",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.Black
          )
        }
      }
    }
  }
}
