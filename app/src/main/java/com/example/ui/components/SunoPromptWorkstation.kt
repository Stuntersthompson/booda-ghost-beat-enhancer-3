package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.model.LOCK_LINE
import com.example.data.model.STYLE_LIMIT
import com.example.data.model.SunoComplianceAuditor
import com.example.data.model.SunoWorkstationCatalog
import com.example.data.model.SunoWorkstationPromptEngine
import com.example.data.model.WorkstationTagChip
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.MonospaceMetric
import com.example.ui.theme.MonospacePrompt
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioMiss
import com.example.ui.theme.StudioWinner
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Reusable Suno v6 Prompt Workstation providing a multi-line prompt editor,
 * live 1,000-char Style cap telemetry, preservation/artist guardrails, and
 * smart toggleable Genre & Instrument tag chip rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SunoPromptWorkstation(
  title: String,
  promptText: String,
  onPromptChanged: (String) -> Unit,
  onToggleChip: (WorkstationTagChip) -> String?,
  onCopy: () -> Unit,
  modifier: Modifier = Modifier,
  maxChars: Int = STYLE_LIMIT,
  styleHeaderCap: Int = STYLE_LIMIT,
  isSimpleBoxMode: Boolean = false,
  isPreserve: Boolean = false,
  activeLane: String = "Trap",
  minLines: Int = 4,
  placeholderText: String = "Enter Suno v6 prompt textures, drum pocket, 808 feel, and mix cues...",
  extraHeaderAction: (@Composable () -> Unit)? = null,
  onInsertLockLine: (() -> Unit)? = null,
  onStripArtists: (() -> Unit)? = null,
  onOpenLaneGrid: (() -> Unit)? = null,
  inputTestTag: String = "workstation_prompt_input",
  copyTestTag: String = "workstation_copy_button"
) {
  val totalChars = promptText.length
  val styleHeaderText = remember(promptText, isSimpleBoxMode) {
    if (isSimpleBoxMode) SunoWorkstationPromptEngine.extractSimpleStyleHeader(promptText) else promptText
  }
  val styleChars = styleHeaderText.length
  val isOverLimit = totalChars > maxChars || styleChars > styleHeaderCap
  val isNearLimit = !isOverLimit && styleChars >= (styleHeaderCap * 0.9).toInt()
  val hasLockLine = promptText.contains(LOCK_LINE)
  val isPreserveMissingLock = isPreserve && !hasLockLine && promptText.isNotBlank()
  val detectedArtists = remember(styleHeaderText) {
    SunoComplianceAuditor.findArtistNamesInStyle(styleHeaderText)
  }

  var isCopiedFlash by remember { mutableStateOf(false) }
  var chipBlockedMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(isCopiedFlash) {
    if (isCopiedFlash) {
      delay(1200L)
      isCopiedFlash = false
    }
  }
  LaunchedEffect(chipBlockedMessage) {
    if (chipBlockedMessage != null) {
      delay(3000L)
      chipBlockedMessage = null
    }
  }

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (isOverLimit || isPreserveMissingLock || detectedArtists.isNotEmpty()) StudioMiss else StudioBorder,
        shape = RoundedCornerShape(12.dp)
      )
      .testTag("suno_prompt_workstation")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      // Header Bar: Title + Live Char Telemetry + Extra Header Actions + Copy Button
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
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterVertically)
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = null,
              tint = AmberGold,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = title.uppercase(),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = AmberGold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Surface(
            color = if (isOverLimit) Color(0x33EF4444) else StudioCardElevated,
            shape = RoundedCornerShape(6.dp)
          ) {
            val counterLabel = if (isSimpleBoxMode) {
              "Style $styleChars/$styleHeaderCap · Box $totalChars/$maxChars"
            } else {
              "$totalChars / $maxChars c"
            }
            Text(
              text = counterLabel,
              style = MonospaceMetric.copy(fontSize = 11.sp),
              color = when {
                isOverLimit -> StudioMiss
                isNearLimit -> AmberGold
                else -> StudioCyan
              },
              maxLines = 1,
              softWrap = false,
              modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("workstation_char_counter")
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (extraHeaderAction != null) {
            extraHeaderAction()
            Spacer(modifier = Modifier.width(4.dp))
          }
          if (onOpenLaneGrid != null) {
            TextButton(
              onClick = onOpenLaneGrid,
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Open 41-lane Production Studio Grid in Lanes tab" }
                .testTag("workstation_open_lane_grid")
            ) {
              Icon(Icons.Default.Layers, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("41 Lanes", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = StudioCyan)
            }
          }
          IconButton(
            onClick = {
              isCopiedFlash = true
              onCopy()
            },
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .testTag(copyTestTag)
          ) {
            Icon(
              imageVector = if (isCopiedFlash) Icons.Default.Check else Icons.Default.ContentCopy,
              contentDescription = "Copy prompt from $title",
              tint = if (isCopiedFlash) StudioWinner else TextSecondary,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Genre Tag Chips Row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(
          imageVector = Icons.Default.MusicNote,
          contentDescription = null,
          tint = AmberGold,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "GENRE TAGS (TAP TO TOGGLE)",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
          color = TextMuted
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("workstation_genre_chips")
      ) {
        items(SunoWorkstationCatalog.genreChips, key = { it.id }) { chip ->
          val active = SunoWorkstationPromptEngine.isTokenActive(styleHeaderText, chip.token)
          WorkstationChipPill(
            chip = chip,
            isActive = active,
            accentColor = AmberGold,
            activeContainerColor = AmberContainer,
            onClick = {
              val blocked = onToggleChip(chip)
              chipBlockedMessage = blocked
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Instrument & Sonic Texture Tag Chips Row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(
          imageVector = Icons.Default.GraphicEq,
          contentDescription = null,
          tint = StudioCyan,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "INSTRUMENTS & TEXTURES ($activeLane READY)",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
          color = TextMuted
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("workstation_instrument_chips")
      ) {
        items(SunoWorkstationCatalog.instrumentChips, key = { it.id }) { chip ->
          val active = SunoWorkstationPromptEngine.isTokenActive(styleHeaderText, chip.token)
          WorkstationChipPill(
            chip = chip,
            isActive = active,
            accentColor = StudioCyan,
            activeContainerColor = CyanContainer,
            onClick = {
              val blocked = onToggleChip(chip)
              chipBlockedMessage = blocked
            }
          )
        }
      }

      AnimatedVisibility(visible = chipBlockedMessage != null) {
        chipBlockedMessage?.let { msg ->
          Surface(
            color = Color(0x28EF4444),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp)
              .border(0.5.dp, StudioMiss.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
              .testTag("workstation_limit_warning")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = StudioMiss, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = msg,
                style = MaterialTheme.typography.labelSmall,
                color = StudioMiss,
                modifier = Modifier.weight(1f)
              )
              IconButton(
                onClick = { chipBlockedMessage = null },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              ) {
                Icon(Icons.Default.Clear, contentDescription = "Dismiss limit notice", tint = TextMuted, modifier = Modifier.size(15.dp))
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Multi-Line Suno v6 Prompt Text Field
      OutlinedTextField(
        value = promptText,
        onValueChange = { next ->
          chipBlockedMessage = null
          onPromptChanged(next.take(maxChars))
        },
        placeholder = {
          Text(
            text = placeholderText,
            style = MonospacePrompt.copy(color = TextMuted, fontSize = 12.sp)
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .semantics { contentDescription = "Multi-line Suno v6 prompt input for $title" }
          .testTag(inputTestTag),
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

      // Guardrail 1: Preserve Lock Warning
      if (isPreserveMissingLock) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          color = Color(0x22EF4444),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, StudioMiss.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        ) {
          FlowRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f, fill = false)
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = StudioMiss, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Preserve mode missing exact sample lock line.",
                style = MaterialTheme.typography.labelSmall,
                color = StudioMiss
              )
            }
            if (onInsertLockLine != null) {
              TextButton(
                onClick = onInsertLockLine,
                modifier = Modifier
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .testTag("action_workstation_insert_lock")
              ) {
                Text(
                  text = "Insert Lock Line",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = AmberGold
                )
              }
            }
          }
        }
      }

      // Guardrail 2: Artist Name Hygiene Warning
      if (detectedArtists.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          color = Color(0x22EF4444),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, StudioMiss.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .testTag("workstation_artist_warning")
        ) {
          FlowRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f, fill = false)
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = StudioMiss, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Blocked artist reference(s): ${detectedArtists.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = StudioMiss
              )
            }
            if (onStripArtists != null) {
              TextButton(
                onClick = onStripArtists,
                modifier = Modifier
                  .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                  .testTag("action_workstation_strip_artists")
              ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Strip Names",
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

@Composable
private fun WorkstationChipPill(
  chip: WorkstationTagChip,
  isActive: Boolean,
  accentColor: Color,
  activeContainerColor: Color,
  onClick: () -> Unit
) {
  Surface(
    color = if (isActive) activeContainerColor else StudioCardElevated,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(
      width = if (isActive) 1.5.dp else 0.75.dp,
      color = if (isActive) accentColor else StudioBorder
    ),
    modifier = Modifier
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .semantics {
        role = Role.Button
        selected = isActive
        contentDescription = "${chip.category.label} tag ${chip.label}, ${if (isActive) "active, tap to remove" else "inactive, tap to add"}"
      }
      .testTag("workstation_chip_${chip.id}")
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      if (isActive) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      Text(
        text = chip.label,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        ),
        color = if (isActive) accentColor else TextPrimary,
        maxLines = 1
      )
    }
  }
}
