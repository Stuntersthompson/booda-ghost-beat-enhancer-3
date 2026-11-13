package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobType
import com.example.ui.theme.*

@Composable
fun SettingsCardView(
  jobType: JobType,
  variety: Int,
  onVarietyChange: (Int) -> Unit,
  weirdness: Int,
  onWeirdnessChange: (Int) -> Unit,
  styleInfluence: Int,
  onStyleInfluenceChange: (Int) -> Unit,
  audioInfluence: Int,
  onAudioInfluenceChange: (Int) -> Unit,
  onResetToDefaults: () -> Unit
) {
  val isPreserve = jobType == JobType.PRESERVE

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "SUNO V6 ENGINE CONTROLS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
          Text(
            text = if (isPreserve) "Preserve Profile (Extend Route)" else "Original Profile (Create Route)",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
          )
        }

        IconButton(
          onClick = onResetToDefaults,
          modifier = Modifier.minimumInteractiveComponentSize()
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Reset Suno v6 Settings to defaults",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Sliders
      SettingSlider(
        label = "VARIETY",
        value = variety,
        onValueChange = onVarietyChange,
        range = 0..100,
        tip = "Hold at 0 for strict preservation"
      )

      SettingSlider(
        label = "WEIRDNESS",
        value = weirdness,
        onValueChange = onWeirdnessChange,
        range = 0..100,
        tip = "Community sweet spot 20-30"
      )

      SettingSlider(
        label = "STYLE INFLUENCE",
        value = styleInfluence,
        onValueChange = onStyleInfluenceChange,
        range = 0..100,
        tip = "Tested sweet spot 65-75"
      )

      if (isPreserve) {
        SettingSlider(
          label = "AUDIO INFLUENCE",
          value = audioInfluence,
          onValueChange = onAudioInfluenceChange,
          range = 0..100,
          tip = "80-85 when vocals matter most"
        )
      }
    }
  }
}

@Composable
private fun SettingSlider(
  label: String,
  value: Int,
  onValueChange: (Int) -> Unit,
  range: IntRange,
  tip: String
) {
  Column(modifier = Modifier.padding(vertical = 4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = TextPrimary
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = tip, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextMuted)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "$value",
          style = MonospaceMetric.copy(fontSize = 14.sp),
          color = AmberGold
        )
      }
    }
    Slider(
      value = value.toFloat(),
      onValueChange = { onValueChange(it.toInt()) },
      valueRange = range.first.toFloat()..range.last.toFloat(),
      colors = SliderDefaults.colors(
        thumbColor = AmberGold,
        activeTrackColor = AmberGold,
        inactiveTrackColor = StudioBorder
      ),
      modifier = Modifier.height(28.dp)
    )
  }
}
