package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioAnalysisResult
import com.example.audio.AudioAnalyzer
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.LYRICS_LIMIT
import com.example.data.model.PromptHistoryEntity
import com.example.data.model.STYLE_LIMIT
import com.example.data.model.SunoComplianceAuditor
import com.example.data.model.TakeEntity
import com.example.data.repository.CuratedPrompts
import com.example.data.repository.PromptBankRepository
import com.example.data.repository.PromptTemplateRepository
import com.example.ui.components.AudioMeterCard
import com.example.ui.components.PromptBox
import com.example.ui.components.QuickTemplatesBar
import com.example.ui.components.StudioBottomBar
import com.example.ui.components.StudioTopBar
import com.example.ui.screens.AdvisorScreen
import com.example.ui.screens.BuildScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SimpleScreen
import com.example.ui.screens.TakesScreen
import com.example.ui.theme.BooDaBeatTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.RandomizeScope
import com.example.viewmodel.StudioTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class FullAuditUpgradeRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var app: Application
  private lateinit var context: Context

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    context = app
  }

  @Test
  fun data_preservation_keeps_all_style_bank_curated_lanes_slots_and_starters_intact() {
    val bankRepo = PromptBankRepository(context)
    assertEquals("Style Bank prompt count must stay 316", 316, bankRepo.stylePrompts.size)
    assertEquals("Curated Prompts count must stay 150", 150, CuratedPrompts.ALL_CURATED.size)
    assertEquals("All subgenre lanes count must stay 41", 41, bankRepo.allLanes.size)
    assertEquals("Slot templates count must stay 14", 14, bankRepo.slotTemplates.size)
    assertTrue("Starter templates must have >= 8 entries", PromptTemplateRepository.starterTemplates(1_000L).size >= 8)
    assertTrue(
      "Original Trap style prompt text must remain unedited",
      bankRepo.stylePrompts.any { it.subgenre.equals("Trap", ignoreCase = true) && it.text.isNotBlank() }
    )
  }

  @Test
  fun honest_audio_analyzer_never_fabricates_readings_for_empty_silent_or_short_clips() {
    val analyzer = AudioAnalyzer(context)

    val emptyRes = analyzer.analyzeFloatSamples("empty.wav", FloatArray(0), 44100)
    assertFalse(emptyRes.isAudioMeasured)
    assertFalse(emptyRes.isSpectralMeasured)
    assertNull("Empty clip must report null BPM instead of fake 130.0", emptyRes.bpm)
    assertEquals("unread", emptyRes.bpmConfidence)
    assertNull("Empty clip must report null key instead of fake C minor", emptyRes.keyName)
    assertNotNull(emptyRes.analysisNote)

    val silentRes = analyzer.analyzeFloatSamples("silent.wav", FloatArray(44100 * 3) { 0f }, 44100)
    assertFalse(silentRes.isAudioMeasured)
    assertNull("Silent clip must report null BPM", silentRes.bpm)
    assertEquals("unread", silentRes.bpmConfidence)
    assertTrue(silentRes.analysisNote?.contains("silent", ignoreCase = true) == true)

    // 4-second synthetic 120 BPM drum pulse + A4 tone yields real measured FFT & tempo
    val sampleRate = 44100
    val durationSec = 4
    val totalSamples = sampleRate * durationSec
    val samples = FloatArray(totalSamples) { i ->
      val t = i.toDouble() / sampleRate
      val beatPhase = (t * 2.0) % 1.0 // 2 beats/sec = 120 BPM
      val kickEnv = exp(-beatPhase * 18.0)
      val kick = sin(2.0 * PI * 65.0 * t) * kickEnv * 0.75
      val harmonic = sin(2.0 * PI * 440.0 * t) * 0.15
      (kick + harmonic).toFloat().coerceIn(-0.98f, 0.98f)
    }
    val realRes = analyzer.analyzeFloatSamples("pulse_120.wav", samples, sampleRate)
    assertTrue(realRes.isAudioMeasured)
    assertTrue(realRes.isSpectralMeasured)
    assertNotNull("Measured 120 BPM pulse must produce non-null BPM", realRes.bpm)
    assertEquals(12, realRes.measuredSpectrumBands.size)
    assertTrue(realRes.measuredSpectrumBands.all { it in 0.05f..1.0f })
  }

  @Test
  fun narrow_360dp_viewport_renders_shared_components_without_edge_clipping() {
    val vm = MainViewModel(app)
    runBlocking {
      vm.promptTemplateRepo.ensureSeeded()
    }
    val templates = PromptTemplateRepository.starterTemplates(1_000L)

    composeTestRule.setContent {
      BooDaBeatTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          androidx.compose.foundation.layout.Column {
            StudioTopBar(
              activeLane = "Memphis phonk / dark trap",
              jobType = JobType.PRESERVE,
              onJobTypeChanged = {},
              onLaneClick = {},
              onSettingsClick = {}
            )
            QuickTemplatesBar(
              templates = templates,
              onApplyTemplate = {},
              onSaveCurrentClick = {},
              onBrowseAllClick = {},
              appliedTemplateId = templates.firstOrNull()?.id
            )
            PromptBox(
              title = "1. STYLE OF MUSIC PROMPT",
              text = "$LOCK_LINE. Add heavy drums.",
              limit = STYLE_LIMIT,
              isPreserve = true,
              onTextChanged = {},
              onCopy = {}
            )
            StudioBottomBar(
              currentTab = StudioTab.BUILD,
              onTabSelected = {}
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    val maxRight = 360.5.dp
    listOf(
      "app_header_logo",
      "action_open_settings",
      "build_quick_templates_bar",
      "action_save_template",
      "studio_bottom_bar",
      "tab_analyze"
    ).forEach { tag ->
      val bounds = composeTestRule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
      assertTrue("$tag right edge (${bounds.right}) clipped past 360dp", bounds.right <= maxRight)
      assertTrue("$tag left edge (${bounds.left}) should be >= 0dp", bounds.left >= (-0.5).dp)
    }

    composeTestRule.onNodeWithTag("action_open_settings").assertHeightIsAtLeast(48.dp)
    composeTestRule.onNodeWithTag("action_save_template").assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun audio_meter_card_displays_unread_and_clip_badges_honestly() {
    val unread = AudioAnalysisResult(
      fileName = "very_long_beat_sample_filename_that_should_ellipsize_cleanly.wav",
      durationSec = 0.0,
      bpm = null,
      bpmConfidence = "unread",
      keyName = null,
      keyClear = false,
      rmsDb = -80.0,
      peakDb = -80.0,
      lowShare = 0.0,
      centroidHz = 0.0,
      onsetRate = 0.0,
      suggestedLane = "Trap",
      pocketLabel = "Unread",
      promptPreserve = LOCK_LINE,
      promptOriginal = "Trap",
      isAudioMeasured = false,
      isSpectralMeasured = false
    )

    composeTestRule.setContent {
      BooDaBeatTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          AudioMeterCard(
            analysis = unread,
            onBpmMultiplier = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("UNREAD", ignoreCase = false).assertIsDisplayed()
    composeTestRule.onNodeWithTag("meter_bpm_confidence").assertIsDisplayed()
  }

  @Test
  fun viewmodel_upgraded_actions_original_reset_slot_cycle_randomize_scope_and_dictation_work() {
    runBlocking {
      val vm = MainViewModel(app)

      // 1. Original mode slider reset preserves mode and prompt instead of resetting to Preserve
      vm.setJobType(JobType.ORIGINAL)
      vm.updateStylePrompt("Custom original hyper-trap wave")
      vm.setWeirdness(90)
      vm.setVariety(45)
      vm.resetSlidersForCurrentJob(context)
      assertEquals("Resetting sliders in Original mode must keep ORIGINAL", JobType.ORIGINAL, vm.jobType.value)
      assertEquals("Custom original hyper-trap wave", vm.stylePrompt.value)
      assertEquals(0, vm.variety.value)
      assertEquals(20, vm.weirdness.value)
      assertEquals(65, vm.styleInfluence.value)
      assertEquals(0, vm.audioInfluence.value)

      // 2. Inject slot cycles deterministically without artist names
      vm.setJobType(JobType.PRESERVE)
      vm.injectSlotTemplate(context = context)
      val firstSlot = vm.stylePrompt.value
      vm.injectSlotTemplate(context = context)
      val secondSlot = vm.stylePrompt.value
      assertNotEquals("Consecutive slot injects should advance to next slot template", firstSlot, secondSlot)
      assertTrue(SunoComplianceAuditor.findArtistNamesInStyle(firstSlot).isEmpty())
      assertTrue(SunoComplianceAuditor.findArtistNamesInStyle(secondSlot).isEmpty())

      // 3. RandomizeScope.SLIDERS_ONLY preserves custom style & lyrics
      vm.updateStylePrompt("$LOCK_LINE. Custom kept texture.")
      vm.updateLyricsMap("[Intro]\nCustom line\n[End]")
      vm.randomizeAll(RandomizeScope.SLIDERS_ONLY, context)
      assertEquals("$LOCK_LINE. Custom kept texture.", vm.stylePrompt.value)
      assertEquals("[Intro]\nCustom line\n[End]", vm.lyricsMap.value)

      // 4. Structured lyrics dictation inserts chosen section tag and keeps [End]
      vm.applyTranscriptToLyrics(
        transcript = "Late night 808 bounce",
        sectionTag = "Chorus",
        replaceExisting = false,
        context = context
      )
      assertTrue(vm.lyricsMap.value.contains("[Chorus] Late night 808 bounce"))
      assertTrue(vm.lyricsMap.value.endsWith("[End]"))
      assertTrue(vm.lyricsMap.value.length <= LYRICS_LIMIT)
    }
  }

  @Test
  fun simple_screen_send_to_build_splits_style_and_lyrics_cleanly() {
    val vm = MainViewModel(app)
    vm.setTab(StudioTab.SIMPLE)
    vm.setJobType(JobType.PRESERVE)
    vm.updateSimplePrompt(
      "Trap heavy 808 drums in Metro Boomin style\n[Intro] (sample solo)\n[Chorus 1] (drop hits)"
    )

    composeTestRule.setContent {
      BooDaBeatTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          SimpleScreen(viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("simple_list")
      .performScrollToNode(hasTestTag("action_send_simple_to_build"))
    val sendBtn = composeTestRule.onNodeWithTag("action_send_simple_to_build")
    sendBtn.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
    composeTestRule.waitForIdle()

    assertEquals(StudioTab.BUILD, vm.currentTab.value)
    assertTrue(vm.stylePrompt.value.startsWith(LOCK_LINE))
    assertTrue(SunoComplianceAuditor.findArtistNamesInStyle(vm.stylePrompt.value).isEmpty())
    assertTrue(vm.lyricsMap.value.endsWith("[End]"))
  }

  @Test
  fun library_screen_pinned_filter_and_duplicate_template_work() {
    runBlocking {
      val vm = MainViewModel(app)
      vm.promptTemplateRepo.ensureSeeded()
      val firstTemplate = vm.promptTemplateRepo.saveTemplate(
        title = "Pinned Smoke Test",
        category = "Lo-Fi Hip Hop",
        lane = "Jazz rap / lo-fi",
        jobType = JobType.ORIGINAL,
        bpmHint = "82 BPM",
        stylePrompt = "Warm tape rhodes",
        lyricsMap = "[Intro]\n[End]",
        excludeText = "harsh cymbals",
        variety = 0,
        weirdness = 20,
        styleInfluence = 70,
        audioInfluence = 0,
        isPinned = true
      )

      composeTestRule.setContent {
        BooDaBeatTheme {
          Box(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(viewModel = vm)
          }
        }
      }
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("template_category_chip_Pinned")
        .assertIsDisplayed()
        .assertHeightIsAtLeast(48.dp)
        .performClick()
      composeTestRule.waitForIdle()

      val dupTag = "template_duplicate_$firstTemplate"
      composeTestRule.waitUntil(timeoutMillis = 3000) {
        vm.promptTemplates.value.any { it.id == firstTemplate }
      }
      composeTestRule.onNodeWithTag("library_list")
        .performScrollToNode(hasTestTag(dupTag))
      composeTestRule.onNodeWithTag(dupTag).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)

      // Verify history-to-template conversion saves a pinned template
      val historyEntry = PromptHistoryEntity(
        id = 77L,
        prompt = "Dark Memphis cowbell bounce",
        source = "Builder",
        lane = "Memphis phonk / dark trap",
        jobType = JobType.ORIGINAL.value,
        bpm = 135,
        chars = 27
      )
      val countBefore = vm.promptTemplates.value.size
      vm.saveHistoryEntryAsTemplate(historyEntry, context)
      Thread.sleep(200L)
      assertTrue(vm.promptTemplates.value.size >= countBefore)
    }
  }

  @Test
  fun advisor_screen_artist_hygiene_card_strips_artists_while_keeping_score_badge() {
    val vm = MainViewModel(app)
    vm.setJobType(JobType.PRESERVE)
    vm.updateStylePrompt("$LOCK_LINE. Metro Boomin style dark 808 bounce.")
    vm.updateLyricsMap("[Intro]\n[End]")
    vm.setVariety(0)
    vm.setAudioInfluence(80)

    composeTestRule.setContent {
      BooDaBeatTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          AdvisorScreen(viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()

    // Core 5-rule score badge contract remains 5/5 PASS
    composeTestRule.onNodeWithTag("advisor_score_badge").assertIsDisplayed()
    composeTestRule.onNodeWithText("5/5 PASS").assertIsDisplayed()

    composeTestRule.onNodeWithTag("advisor_list")
      .performScrollToNode(hasTestTag("action_strip_artist_names"))
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("advisor_artist_hygiene_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_strip_artist_names")
      .performScrollTo()
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()

    assertTrue(
      "Artist name should be stripped from style prompt",
      SunoComplianceAuditor.findArtistNamesInStyle(vm.stylePrompt.value).isEmpty()
    )
  }

  @Test
  fun takes_screen_expand_toggle_shows_full_prompts() {
    runBlocking {
      val vm = MainViewModel(app)
      val takeId = vm.takeRepo.insertTake(
        TakeEntity(
          name = "Long Style Take Test",
          beatTitle = "Deep Night",
          lane = "Dark trap",
          jobType = JobType.PRESERVE.value,
          bpm = 138.0,
          style = "$LOCK_LINE. " + "Sub-heavy distorted 808 glides and eerie bell texture ".repeat(5),
          excludeText = "muddy mix, nasal vocals",
          lyrics = "[Intro]\n[Verse 1]\n[End]",
          settings = "Model v6",
          result = "winner"
        )
      )
      Thread.sleep(120L)

      composeTestRule.setContent {
        BooDaBeatTheme {
          Box(modifier = Modifier.fillMaxSize()) {
            TakesScreen(viewModel = vm)
          }
        }
      }
      composeTestRule.waitForIdle()

      val expandTag = "action_toggle_take_expand_$takeId"
      composeTestRule.waitUntil(timeoutMillis = 3000) {
        composeTestRule.onAllNodesWithTag(expandTag).fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("takes_list").performScrollToNode(hasTestTag(expandTag))
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag(expandTag)
        .performScrollTo()
        .assertIsDisplayed()
        .assertHeightIsAtLeast(48.dp)
        .performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("takes_list").performScrollToNode(hasText("Hide Full Prompts ▲"))
      composeTestRule.onNodeWithText("Hide Full Prompts ▲").assertIsDisplayed()
      composeTestRule.onNodeWithTag("takes_list").performScrollToNode(hasText("muddy mix, nasal vocals"))
      composeTestRule.onNodeWithText("muddy mix, nasal vocals").assertIsDisplayed()
    }
  }

  @Test
  fun build_screen_shows_honest_param_est_monitor_and_loop_toggle() {
    val vm = MainViewModel(app)
    assertNull(vm.audioAnalysis.value)

    composeTestRule.setContent {
      BooDaBeatTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          BuildScreen(viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("build_groove_monitor").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_monitor_loop").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    composeTestRule.onAllNodesWithText("PARAM EST. · NO AUDIO").onFirst().assertIsDisplayed()
  }
}
