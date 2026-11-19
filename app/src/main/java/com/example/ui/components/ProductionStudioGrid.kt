package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudioLaneLayerState
import com.example.data.model.StudioLayerTag
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberGoldLight
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.MonospaceMetric
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

/**
 * Scrollable, 41-lane lazy layout template for organizing audio layers with Mute/Solo toggles,
 * mix level sliders, and layer role tag assignment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductionStudioGrid(
  lanes: List<StudioLaneLayerState>,
  activeLane: String,
  onToggleMute: (String) -> Unit,
  onToggleSolo: (String) -> Unit,
  onLevelChanged: (String, Float) -> Unit,
  onLayerTagSelected: (String, StudioLayerTag) -> Unit,
  onSelectLane: (String) -> Unit,
  onApplyMixToBuild: () -> Unit,
  onResetMixers: () -> Unit,
  modifier: Modifier = Modifier,
  totalCatalogCount: Int = 41,
  headerSlot: (@Composable () -> Unit)? = null,
  emptySlot: (@Composable () -> Unit)? = null
) {
  val anySoloed = remember(lanes) { lanes.any { it.isSoloed } }
  val audibleCount = remember(lanes, anySoloed) { lanes.count { it.isAudible(anySoloed) } }
  val soloCount = remember(lanes) { lanes.count { it.isSoloed } }
  val mutedCount = remember(lanes) { lanes.count { it.isMuted } }

  val summaryHeaderCard: @Composable () -> Unit = {
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, StudioCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
        .testTag("studio_grid_summary")
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterVertically)
          ) {
            Icon(
              imageVector = Icons.Default.Layers,
              contentDescription = null,
              tint = StudioCyan,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "PRODUCTION STUDIO GRID",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = StudioCyan,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "${lanes.size}/$totalCatalogCount LANES · $audibleCount AUDIBLE",
              style = MonospaceMetric.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
              color = AmberGold,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("studio_grid_metrics_badge")
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = buildString {
            append("41-lane audio layer console — configure Mute (M), Solo (S), Layer Level, and Role Tag per lane.")
            if (soloCount > 0) append(" Solo bus active ($soloCount soloed).")
            if (mutedCount > 0) append(" $mutedCount muted.")
          },
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = onResetMixers,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, StudioBorder),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .semantics { contentDescription = "Reset all 41 studio lane mixers to defaults" }
              .testTag("action_reset_lane_mixers")
          ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Reset Mix",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = TextPrimary,
              maxLines = 1
            )
          }

          Button(
            onClick = onApplyMixToBuild,
            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .semantics { contentDescription = "Send active 41-lane Studio Grid mix into Build workspace" }
              .testTag("action_studio_grid_to_build")
          ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Send Active Mix to Build",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.Black,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(StudioBg)
  ) {
    val isWideGrid = maxWidth >= 760.dp

    if (isWideGrid) {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 330.dp),
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp)
          .testTag("production_studio_grid_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (headerSlot != null) {
          item(key = "grid_header_slot", span = { GridItemSpan(maxLineSpan) }) {
            headerSlot()
          }
        }
        item(key = "grid_summary_slot", span = { GridItemSpan(maxLineSpan) }) {
          summaryHeaderCard()
        }
        if (lanes.isEmpty() && emptySlot != null) {
          item(key = "grid_empty_slot", span = { GridItemSpan(maxLineSpan) }) {
            emptySlot()
          }
        } else {
          gridItems(lanes, key = { it.laneName }) { layer ->
            ProductionStudioLaneRow(
              layer = layer,
              isActiveLane = layer.laneName == activeLane,
              anySoloed = anySoloed,
              onToggleMute = { onToggleMute(layer.laneName) },
              onToggleSolo = { onToggleSolo(layer.laneName) },
              onLevelChanged = { value -> onLevelChanged(layer.laneName, value) },
              onLayerTagSelected = { tag -> onLayerTagSelected(layer.laneName, tag) },
              onSelectLane = { onSelectLane(layer.laneName) }
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp)
          .testTag("production_studio_grid_list"),
        contentPadding = PaddingValues(top = 10.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (headerSlot != null) {
          item(key = "grid_header_slot") {
            headerSlot()
          }
        }
        item(key = "grid_summary_slot") {
          summaryHeaderCard()
        }
        if (lanes.isEmpty() && emptySlot != null) {
          item(key = "grid_empty_slot") {
            emptySlot()
          }
        } else {
          items(lanes, key = { it.laneName }) { layer ->
            ProductionStudioLaneRow(
              layer = layer,
              isActiveLane = layer.laneName == activeLane,
              anySoloed = anySoloed,
              onToggleMute = { onToggleMute(layer.laneName) },
              onToggleSolo = { onToggleSolo(layer.laneName) },
              onLevelChanged = { value -> onLevelChanged(layer.laneName, value) },
              onLayerTagSelected = { tag -> onLayerTagSelected(layer.laneName, tag) },
              onSelectLane = { onSelectLane(layer.laneName) }
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductionStudioLaneRow(
  layer: StudioLaneLayerState,
  isActiveLane: Boolean,
  anySoloed: Boolean,
  onToggleMute: () -> Unit,
  onToggleSolo: () -> Unit,
  onLevelChanged: (Float) -> Unit,
  onLayerTagSelected: (StudioLayerTag) -> Unit,
  onSelectLane: () -> Unit,
  modifier: Modifier = Modifier
) {
  val laneKey = remember(layer.laneName) { sanitizeStudioTagKey(layer.laneName) }
  val audible = layer.isAudible(anySoloed)
  var showTagMenu by remember { mutableStateOf(false) }

  Surface(
    color = if (isActiveLane) StudioCardElevated else StudioCard,
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = if (layer.isSoloed || isActiveLane) 1.5.dp else 1.dp,
        color = when {
          layer.isSoloed -> AmberGold
          isActiveLane -> StudioCyan
          layer.isMuted -> StudioMiss.copy(alpha = 0.45f)
          else -> StudioBorder
        },
        shape = RoundedCornerShape(12.dp)
      )
      .alpha(if (audible) 1f else 0.68f)
      .testTag("studio_grid_lane_$laneKey")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      // Top Line: Lane Index + Name + Active / Audible Badges
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
          Surface(
            color = if (isActiveLane) AmberGold else StudioCardElevated,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "%02d".format(layer.laneIndex),
              style = MonospaceMetric.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
              color = if (isActiveLane) Color.Black else AmberGold,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = layer.laneName,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = if (isActiveLane) AmberGold else TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${layer.genreFamily} · ${layer.bpmRange} · ${layer.regionTag}",
              style = MaterialTheme.typography.labelSmall,
              color = TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Surface(
          color = when {
            layer.isMuted -> StudioMiss.copy(alpha = 0.16f)
            layer.isSoloed -> AmberContainer
            audible -> StudioWinner.copy(alpha = 0.15f)
            else -> StudioCardElevated
          },
          shape = RoundedCornerShape(6.dp)
        ) {
          Text(
            text = when {
              layer.isMuted -> "MUTED"
              layer.isSoloed -> "SOLO"
              anySoloed -> "BYPASSED"
              else -> "${layer.effectivePercent}%"
            },
            style = MonospaceMetric.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = when {
              layer.isMuted -> StudioMiss
              layer.isSoloed -> AmberGold
              audible -> StudioWinner
              else -> TextMuted
            },
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = layer.drumTexture,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Controls Row: Mute (M) + Solo (S) + Layer Tag Dropdown + Quick Select
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Mute button (>= 48dp touch target)
        Surface(
          color = if (layer.isMuted) StudioMiss else StudioCardElevated,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggleMute() }
            .border(
              1.dp,
              if (layer.isMuted) StudioMiss else StudioBorder,
              RoundedCornerShape(8.dp)
            )
            .semantics {
              role = Role.Button
              contentDescription = "Mute ${layer.laneName} lane, currently ${if (layer.isMuted) "muted" else "unmuted"}"
            }
            .testTag("lane_mute_$laneKey")
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
          ) {
            Text(
              text = "M",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = if (layer.isMuted) Color.White else TextPrimary
            )
          }
        }

        // Solo button (>= 48dp touch target)
        Surface(
          color = if (layer.isSoloed) AmberGold else StudioCardElevated,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggleSolo() }
            .border(
              1.dp,
              if (layer.isSoloed) AmberGold else StudioBorder,
              RoundedCornerShape(8.dp)
            )
            .semantics {
              role = Role.Button
              contentDescription = "Solo ${layer.laneName} lane, currently ${if (layer.isSoloed) "soloed" else "not soloed"}"
            }
            .testTag("lane_solo_$laneKey")
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
          ) {
            Text(
              text = "S",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = if (layer.isSoloed) Color.Black else TextPrimary
            )
          }
        }

        // Layer Tag Assignment Selector
        Box {
          Surface(
            color = CyanContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
              .clip(RoundedCornerShape(8.dp))
              .clickable { showTagMenu = true }
              .border(1.dp, StudioCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
              .semantics {
                role = Role.DropdownList
                contentDescription = "Assign audio layer tag for ${layer.laneName}, currently ${layer.layerTag.label}"
              }
              .testTag("lane_tag_$laneKey")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
              Text(
                text = layer.layerTag.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = StudioCyan,
                maxLines = 1
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = StudioCyan,
                modifier = Modifier.size(16.dp)
              )
            }
          }

          DropdownMenu(
            expanded = showTagMenu,
            onDismissRequest = { showTagMenu = false }
          ) {
            StudioLayerTag.entries.forEach { tag ->
              DropdownMenuItem(
                text = {
                  Column {
                    Text(
                      text = tag.label,
                      style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (tag == layer.layerTag) FontWeight.Bold else FontWeight.Medium
                      ),
                      color = if (tag == layer.layerTag) AmberGold else TextPrimary
                    )
                    Text(
                      text = tag.promptPhrase,
                      style = MaterialTheme.typography.labelSmall,
                      color = TextMuted
                    )
                  }
                },
                onClick = {
                  onLayerTagSelected(tag)
                  showTagMenu = false
                }
              )
            }
          }
        }

        TextButton(
          onClick = onSelectLane,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Set ${layer.laneName} as active studio lane" }
            .testTag("lane_activate_$laneKey")
        ) {
          Text(
            text = if (isActiveLane) "Active Lead" else "Set Lead",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isActiveLane) AmberGoldLight else StudioCyan
          )
        }
      }

      // Layer Level Slider Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = if (layer.isMuted || layer.level <= 0.01f) {
            Icons.AutoMirrored.Filled.VolumeOff
          } else {
            Icons.AutoMirrored.Filled.VolumeUp
          },
          contentDescription = null,
          tint = if (audible) StudioCyan else TextMuted,
          modifier = Modifier.size(16.dp)
        )
        Slider(
          value = layer.level,
          onValueChange = onLevelChanged,
          valueRange = 0f..1f,
          colors = SliderDefaults.colors(
            thumbColor = if (layer.isSoloed) AmberGold else StudioCyan,
            activeTrackColor = if (layer.isSoloed) AmberGold else StudioCyan,
            inactiveTrackColor = StudioBorder
          ),
          modifier = Modifier
            .weight(1f)
            .semantics {
              contentDescription = "Mix level for ${layer.laneName}, ${layer.effectivePercent} percent"
            }
            .testTag("lane_level_$laneKey")
        )
        Text(
          text = "${layer.effectivePercent}%",
          style = MonospaceMetric.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
          color = if (audible) TextPrimary else TextMuted,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.width(38.dp)
        )
      }
    }
  }
}

fun sanitizeStudioTagKey(name: String): String {
  return name.lowercase()
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')
}
