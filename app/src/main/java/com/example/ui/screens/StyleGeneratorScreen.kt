package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CuratedSunoPrompt
import com.example.data.model.JobType
import com.example.data.model.SUNO_STYLE_BOX_LIMIT
import com.example.data.repository.CuratedPrompts
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StyleGeneratorScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val jobType by viewModel.jobType.collectAsStateWithLifecycle()
  val bpm by viewModel.audioAnalysis.collectAsStateWithLifecycle()
  val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
  val curatedCategory by viewModel.curatedCategory.collectAsStateWithLifecycle()
  val searchQuery by viewModel.curatedSearchQuery.collectAsStateWithLifecycle()

  // Local query state with 250ms debounce
  var rawSearchInput by rememberSaveable { mutableStateOf(searchQuery) }
  val searchFocusRequester = remember { FocusRequester() }

  LaunchedEffect(rawSearchInput) {
    delay(250)
    viewModel.setCuratedSearchQuery(rawSearchInput)
  }

  // Interactive Style Builder Options
  val sampleOptions = listOf(
    "chopped soul sample and vocals",
    "pitched chipmunk vocal chops",
    "chopped gospel choir and B3 organ",
    "dusty vinyl melodic chops",
    "minor-key piano loop and vocal stabs",
    "ambient vocal fragments and guitar"
  )
  val drumOptions = listOf(
    "punchy kick, hard-hitting 808 sub, and rolling trap hi-hats",
    "chest-thumping 808 bass, snapping snare on 3, and light hat rolls",
    "deep sliding 808 glides, syncopated snare, and stuttering hats",
    "hard-knocking kick, deep resonant 808, and tight trap hats",
    "short punchy 808 stabs, clapping backbeat, and sparse hi-hats",
    "heavy round 808, crisp dry snare on 2 and 4, and swinging hats"
  )
  val vibeOptions = listOf(
    "Let the beat breathe. Spacious and soulful",
    "Mellow atmospheric pocket, letting the sample lead",
    "High-energy street bounce with heavy bass punch",
    "Menacing, dark, letting the core melody breathe",
    "Triumphant spiritual praise with modern trap punch",
    "Laid-back, sun-drenched, spacious bounce"
  )

  var selectedSample by rememberSaveable { mutableStateOf(sampleOptions[0]) }
  var selectedDrums by rememberSaveable { mutableStateOf(drumOptions[0]) }
  var selectedVibe by rememberSaveable { mutableStateOf(vibeOptions[0]) }

  val currentBpm = bpm?.bpm?.toInt() ?: 130
  val isPreserve = jobType == JobType.PRESERVE

  val generatedPrompt = remember(selectedSample, selectedDrums, selectedVibe, isPreserve, currentBpm) {
    viewModel.bankRepo.composeSuno200CharPrompt(
      sampleType = selectedSample,
      drumStyle = selectedDrums,
      vibe = selectedVibe,
      bpm = currentBpm,
      isPreserve = isPreserve
    )
  }

  // Multi-word AND filter across prompt, category, subgenre, and bpmHint
  val allCurated = CuratedPrompts.ALL_CURATED
  val filteredPrompts = remember(curatedCategory, searchQuery, favoriteIds) {
    val baseList = if (curatedCategory == "Favorites") {
      allCurated.filter { favoriteIds.contains(it.id.toLong()) }
    } else {
      allCurated
    }

    val trimmed = searchQuery.trim()
    val tokens = if (trimmed.isEmpty()) emptyList() else trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }

    baseList.filter { item ->
      val matchesCat = when (curatedCategory) {
        "All" -> true
        "Favorites" -> true
        else -> item.category.equals(curatedCategory, ignoreCase = true)
      }
      val matchesQuery = if (tokens.isEmpty()) {
        true
      } else {
        val target = "${item.prompt} ${item.category} ${item.subgenre} ${item.bpmHint}".lowercase()
        tokens.all { token -> target.contains(token.lowercase()) }
      }
      matchesCat && matchesQuery
    }
  }

  val totalCategoryCount = when (curatedCategory) {
    "Favorites" -> favoriteIds.size
    "All" -> allCurated.size
    "Sample Beef-Up" -> 100
    "Gospel Trap" -> 50
    else -> allCurated.size
  }

  @Composable
  fun SearchAndFilterCard() {
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        OutlinedTextField(
          value = rawSearchInput,
          onValueChange = { rawSearchInput = it },
          placeholder = { Text("Search 808s, gospel, chipmunk, drill, BPM...", color = TextMuted) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search",
              tint = if (rawSearchInput.isNotEmpty()) StudioCyan else TextSecondary
            )
          },
          trailingIcon = {
            if (rawSearchInput.isNotEmpty()) {
              IconButton(
                onClick = {
                  rawSearchInput = ""
                  viewModel.setCuratedSearchQuery("")
                  searchFocusRequester.requestFocus()
                },
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Clear search",
                  tint = TextSecondary,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(10.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = StudioBg,
            unfocusedContainerColor = StudioBg,
            focusedBorderColor = StudioCyan,
            unfocusedBorderColor = StudioBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(searchFocusRequester)
            .testTag("input_curated_search")
        )

        Spacer(modifier = Modifier.height(10.dp))

        val catOptions = listOf("All", "Sample Beef-Up", "Gospel Trap", "Favorites")
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          items(catOptions) { cat ->
            val isSelected = cat == curatedCategory
            val isFavChip = cat == "Favorites"
            val chipLabel = when (cat) {
              "All" -> "All (150)"
              "Sample Beef-Up" -> "Beef-Up (100)"
              "Gospel Trap" -> "Gospel Trap (50)"
              "Favorites" -> "Favorites (${favoriteIds.size})"
              else -> cat
            }

            Surface(
              color = if (isSelected) {
                if (isFavChip) StudioMiss else AmberGold
              } else {
                StudioCardElevated
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.setCuratedCategory(cat) }
                .border(
                  0.5.dp,
                  if (isSelected) Color.Transparent else StudioBorder,
                  RoundedCornerShape(12.dp)
                )
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                if (isFavChip) {
                  Icon(
                    imageVector = if (isSelected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else StudioMiss,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                  text = chipLabel,
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = if (isSelected) {
                    if (isFavChip) Color.White else Color.Black
                  } else {
                    TextPrimary
                  }
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${filteredPrompts.size} of $totalCategoryCount prompts",
            style = MonospaceMetric.copy(fontSize = 11.sp),
            color = StudioCyan
          )

          if (curatedCategory == "Favorites" && favoriteIds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Button(
                onClick = { viewModel.exportFavorites(context, asJson = true) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp)
              ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = AmberGold, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export JSON", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = AmberGold)
              }
            }
          }
        }
      }
    }
  }

  @Composable
  fun InteractiveBuilderCard() {
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("INTERACTIVE STYLE BUILDER", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)
          }
          Surface(
            color = if (generatedPrompt.length <= SUNO_STYLE_BOX_LIMIT) StudioCardElevated else Color(0x33EF4444),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "${generatedPrompt.length} / $SUNO_STYLE_BOX_LIMIT c",
              style = MonospaceMetric.copy(fontSize = 12.sp),
              color = if (generatedPrompt.length <= SUNO_STYLE_BOX_LIMIT) StudioCyan else StudioMiss,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text("1. SAMPLE FOUNDATION (MAIN MELODY):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(sampleOptions) { s ->
            ChipOption(text = s.take(28) + "...", isSelected = s == selectedSample) { selectedSample = s }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("2. DRUMS & 808 BEEF-UP:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(drumOptions) { d ->
            ChipOption(text = d.take(28) + "...", isSelected = d == selectedDrums) { selectedDrums = d }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("3. VIBE & BREATHING ROOM:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(vibeOptions) { v ->
            ChipOption(text = v.take(28) + "...", isSelected = v == selectedVibe) { selectedVibe = v }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
          color = StudioBg,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = generatedPrompt,
            style = MonospacePrompt.copy(fontSize = 13.sp),
            color = TextPrimary,
            modifier = Modifier.padding(10.dp)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = {
              viewModel.copyToClipboard(context, "Suno Style Prompt", generatedPrompt)
              viewModel.addRecentPrompt(generatedPrompt)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).height(44.dp).testTag("action_copy_generator_prompt")
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy Prompt", color = Color.Black)
          }

          Button(
            onClick = {
              viewModel.sharePrompt(context, "Suno Style Prompt", generatedPrompt)
            },
            colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(0.8f).height(44.dp).testTag("action_share_generator_prompt")
          ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share", color = TextPrimary)
          }

          Button(
            onClick = {
              viewModel.updateStylePrompt(generatedPrompt)
              viewModel.setTab(StudioTab.BUILD)
            },
            colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).height(44.dp).testTag("action_send_to_build_from_gen")
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Send to Build", color = Color.Black)
          }
        }
      }
    }
  }

  @Composable
  fun CuratedEmptyStateCard() {
    Surface(
      color = StudioCard,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        .padding(vertical = 12.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        if (curatedCategory == "Favorites" && favoriteIds.isEmpty()) {
          Icon(
            imageVector = Icons.Default.FavoriteBorder,
            contentDescription = null,
            tint = StudioMiss,
            modifier = Modifier.size(44.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "NO FAVORITE PROMPTS YET",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Tap the heart on any prompt to save it here for instant 1-tap access.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
          )
          Spacer(modifier = Modifier.height(14.dp))
          Button(
            onClick = { viewModel.setCuratedCategory("All") },
            colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text("Browse All 150 Prompts", color = AmberGold)
          }
        } else {
          Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(44.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "NO PROMPTS MATCH \"${searchQuery.take(24)}\"",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = AmberGold
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Try searching for terms like '808', 'gospel', 'drill', 'soul', or clear filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
          )
          Spacer(modifier = Modifier.height(14.dp))
          Button(
            onClick = {
              rawSearchInput = ""
              viewModel.clearCuratedFilters()
            },
            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text("Clear Filters", color = Color.Black)
          }
        }
      }
    }
  }

  @Composable
  fun RenderCuratedCard(item: CuratedSunoPrompt) {
    val isFav = favoriteIds.contains(item.id.toLong())
    CuratedPromptItemCard(
      item = item,
      searchQuery = searchQuery,
      isFavorite = isFav,
      onToggleFavorite = {
        viewModel.toggleFavorite(item) { isSaved, _ ->
          coroutineScope.launch {
            if (isSaved) {
              snackbarHostState.showSnackbar(
                message = "Saved #${item.id} to Favorites",
                duration = SnackbarDuration.Short
              )
            } else {
              val result = snackbarHostState.showSnackbar(
                message = "Removed #${item.id} from Favorites",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
              )
              if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoRemoveFavorite()
              }
            }
          }
        }
      },
      onCopy = {
        viewModel.copyToClipboard(context, "Suno Prompt #${item.id}", item.prompt)
        viewModel.addRecentPrompt(item.prompt)
      },
      onShare = {
        viewModel.sharePrompt(context, "Suno Prompt #${item.id} (${item.subgenre})", item.prompt)
      },
      onUseInBuild = {
        viewModel.updateStylePrompt(item.prompt)
        viewModel.setTab(StudioTab.BUILD)
      }
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
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Left Pane: Header + Search + Interactive Builder
        LazyColumn(
          modifier = Modifier.weight(0.95f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Column {
              Text(
                text = "SUNO V6 STYLE GENERATOR",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AmberGold
              )
              Text(
                text = "Searchable curated Suno prompt engine with sample-locked 808 beef-ups, favorites library, and instant export.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
              )
            }
          }
          item { SearchAndFilterCard() }
          item { InteractiveBuilderCard() }
        }

        // Right Pane: Curated 150-Prompt Bank
        LazyColumn(
          modifier = Modifier.weight(1.05f).fillMaxHeight(),
          contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          if (filteredPrompts.isEmpty()) {
            item { CuratedEmptyStateCard() }
          } else {
            items(
              items = filteredPrompts,
              key = { it.id }
            ) { item ->
              RenderCuratedCard(item)
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item {
          Column {
            Text(
              text = "SUNO V6 STYLE GENERATOR",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = AmberGold
            )
            Text(
              text = "Searchable curated Suno prompt engine with sample-locked 808 beef-ups, favorites library, and instant export.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary
            )
          }
        }
        item { SearchAndFilterCard() }
        item { InteractiveBuilderCard() }
        if (filteredPrompts.isEmpty()) {
          item { CuratedEmptyStateCard() }
        } else {
          items(
            items = filteredPrompts,
            key = { it.id }
          ) { item ->
            RenderCuratedCard(item)
          }
        }
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = if (isWideLayout) 16.dp else 76.dp)
        .testTag("style_generator_snackbar")
    )
  }
}

@Composable
private fun ChipOption(text: String, isSelected: Boolean, onClick: () -> Unit) {
  Surface(
    color = if (isSelected) AmberContainer else StudioCardElevated,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable { onClick() }
      .border(0.5.dp, if (isSelected) AmberGold else StudioBorder, RoundedCornerShape(8.dp))
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
      color = if (isSelected) AmberGold else TextPrimary,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
    )
  }
}

@Composable
private fun CuratedPromptItemCard(
  item: CuratedSunoPrompt,
  searchQuery: String,
  isFavorite: Boolean,
  onToggleFavorite: () -> Unit,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  onUseInBuild: () -> Unit
) {
  val haptic = LocalHapticFeedback.current
  val isGospel = item.category.contains("Gospel", ignoreCase = true)

  var isCopiedAnimation by remember { mutableStateOf(false) }
  LaunchedEffect(isCopiedAnimation) {
    if (isCopiedAnimation) {
      delay(1200)
      isCopiedAnimation = false
    }
  }

  val heartScale by animateFloatAsState(
    targetValue = if (isFavorite) 1.22f else 1.0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow
    ),
    label = "heartScale"
  )

  Surface(
    color = StudioCard,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, if (isFavorite) StudioMiss.copy(alpha = 0.4f) else StudioBorder, RoundedCornerShape(10.dp))
      .testTag("curated_prompt_${item.id}")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            color = if (isGospel) AmberContainer else CyanContainer,
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = item.subgenre,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = if (isGospel) AmberGold else StudioCyan,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(item.bpmHint, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${item.chars}c",
            style = MonospaceMetric.copy(fontSize = 11.sp),
            color = if (item.chars <= SUNO_STYLE_BOX_LIMIT) StudioCyan else StudioMiss
          )
          Spacer(modifier = Modifier.width(4.dp))

          // Favorite Heart Toggle
          IconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onToggleFavorite()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .scale(heartScale)
              .testTag("action_favorite_${item.id}")
          ) {
            Icon(
              imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
              contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
              tint = if (isFavorite) StudioMiss else TextMuted,
              modifier = Modifier.size(18.dp)
            )
          }

          // Copy with checkmark visual animation
          IconButton(
            onClick = {
              isCopiedAnimation = true
              onCopy()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("action_copy_${item.id}")
          ) {
            Icon(
              imageVector = if (isCopiedAnimation) Icons.Default.Check else Icons.Default.ContentCopy,
              contentDescription = "Copy prompt #${item.id}",
              tint = if (isCopiedAnimation) StudioWinner else TextSecondary,
              modifier = Modifier.size(16.dp)
            )
          }

          // Share Sheet
          IconButton(
            onClick = onShare,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("action_share_${item.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = "Share prompt #${item.id}",
              tint = TextSecondary,
              modifier = Modifier.size(16.dp)
            )
          }

          // Send to Build
          IconButton(
            onClick = onUseInBuild,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("action_send_${item.id}")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Use prompt #${item.id} in Build",
              tint = AmberGold,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Highlighted prompt text
      Text(
        text = highlightQueryMatches(item.prompt, searchQuery),
        style = MonospacePrompt.copy(fontSize = 13.sp),
        color = TextPrimary
      )
    }
  }
}

/**
 * Highlights matched search terms case-insensitively within prompt text
 */
private fun highlightQueryMatches(text: String, query: String): AnnotatedString {
  val trimmed = query.trim()
  if (trimmed.isEmpty()) return AnnotatedString(text)
  val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
  val builder = AnnotatedString.Builder(text)

  for (token in tokens) {
    var startIndex = 0
    while (startIndex < text.length) {
      val foundIndex = text.indexOf(token, startIndex, ignoreCase = true)
      if (foundIndex < 0) break
      builder.addStyle(
        style = SpanStyle(
          background = Color(0x55F5A524),
          color = AmberGoldLight,
          fontWeight = FontWeight.Bold
        ),
        start = foundIndex,
        end = foundIndex + token.length
      )
      startIndex = foundIndex + token.length
    }
  }
  return builder.toAnnotatedString()
}
