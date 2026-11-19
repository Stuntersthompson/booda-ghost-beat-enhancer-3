package com.example

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.STYLE_LIMIT
import com.example.ui.components.sanitizeStudioTagKey
import com.example.ui.screens.BuildScreen
import com.example.ui.screens.LanesScreen
import com.example.ui.screens.SimpleScreen
import com.example.ui.theme.BooDaBeatTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class StudioGridAndWorkstationRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var app: Application

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun lanes_screen_studio_grid_displays_all_41_lanes_with_mute_solo_slider_tag_and_build_sync() {
    val vm = MainViewModel(app)
    assertEquals(41, vm.laneLayerStates.value.size)

    composeTestRule.setContent {
      BooDaBeatTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          LanesScreen(viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()

    // Switch into Studio Grid mode
    composeTestRule.onNodeWithTag("lanes_mode_studio_grid")
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("studio_grid_summary").assertIsDisplayed()
    composeTestRule.onNodeWithTag("studio_grid_metrics_badge").assertIsDisplayed()

    // Toggle Mute and Solo on the first lane (Trap)
    val trapKey = sanitizeStudioTagKey("Trap")
    composeTestRule.onNodeWithTag("production_studio_grid_list")
      .performScrollToNode(hasTestTag("studio_grid_lane_$trapKey"))
    composeTestRule.onNodeWithTag("lane_mute_$trapKey").assertHeightIsAtLeast(48.dp).performClick()
    composeTestRule.onNodeWithTag("lane_solo_$trapKey").assertHeightIsAtLeast(48.dp).performClick()
    composeTestRule.waitForIdle()
    assertTrue(vm.laneLayerStates.value.first { it.laneName == "Trap" }.isMuted)
    assertTrue(vm.laneLayerStates.value.first { it.laneName == "Trap" }.isSoloed)

    // Scroll all the way to lane #41 ("Tread") and assign a custom Layer Tag
    val lastLane = vm.bankRepo.allLanes.last()
    val lastKey = sanitizeStudioTagKey(lastLane)
    composeTestRule.onNodeWithTag("production_studio_grid_list")
      .performScrollToNode(hasTestTag("studio_grid_lane_$lastKey"))
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("lane_tag_$lastKey")
      .performScrollTo()
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("808 Sub").performClick()
    composeTestRule.waitForIdle()

    assertEquals(
      com.example.data.model.StudioLayerTag.EIGHT_OH_EIGHT,
      vm.laneLayerStates.value.first { it.laneName == lastLane }.layerTag
    )

    // Scroll back to top and push mix to Build
    composeTestRule.onNodeWithTag("production_studio_grid_list")
      .performScrollToNode(hasTestTag("action_studio_grid_to_build"))
    composeTestRule.onNodeWithTag("action_studio_grid_to_build")
      .performScrollTo()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()

    assertEquals(StudioTab.BUILD, vm.currentTab.value)
    assertTrue(vm.stylePrompt.value.contains(LOCK_LINE))
  }

  @Test
  fun build_screen_suno_prompt_workstation_toggles_chips_and_enforces_1000_char_cap() {
    val vm = MainViewModel(app)
    vm.setJobType(JobType.PRESERVE)
    vm.updateStylePrompt("$LOCK_LINE. Add Trap drums underneath only.")

    composeTestRule.setContent {
      BooDaBeatTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          BuildScreen(viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("build_list")
      .performScrollToNode(hasTestTag("suno_prompt_workstation"))
    composeTestRule.waitForIdle()

    // Smart chip add
    composeTestRule.onNodeWithTag("workstation_chip_inst_rhodes")
      .performScrollTo()
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()
    assertTrue(vm.stylePrompt.value.contains("filtered Rhodes chords"))

    // Smart chip remove
    composeTestRule.onNodeWithTag("workstation_chip_inst_rhodes").performClick()
    composeTestRule.waitForIdle()
    assertFalse(vm.stylePrompt.value.contains("filtered Rhodes chords"))
    assertTrue(vm.stylePrompt.value.contains(LOCK_LINE))

    // Enforce 1,000 char block when adding a chip near cap
    val nearCapText = ("$LOCK_LINE. " + "heavy trap texture ".repeat(48)).take(STYLE_LIMIT - 2)
    composeTestRule.onNodeWithTag("input_style_of_music").performScrollTo().performTextReplacement(nearCapText)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("workstation_instrument_chips")
      .performScrollToNode(hasTestTag("workstation_chip_inst_soul_vocal"))
    composeTestRule.onNodeWithTag("workstation_chip_inst_soul_vocal").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("workstation_limit_warning").performScrollTo().assertIsDisplayed()
    assertEquals(nearCapText, vm.stylePrompt.value)
  }

  @Test
  fun simple_screen_embeds_suno_prompt_workstation_with_chip_sync_and_open_grid_action() {
    val vm = MainViewModel(app)
    vm.setTab(StudioTab.SIMPLE)
    vm.setJobType(JobType.PRESERVE)
    vm.updateSimplePrompt("$LOCK_LINE. Trap drums.\n[Instrumental]\n[Intro]\n[End]")

    composeTestRule.setContent {
      BooDaBeatTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          SimpleScreen(viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("simple_list")
      .performScrollToNode(hasTestTag("suno_prompt_workstation"))
    composeTestRule.onNodeWithTag("workstation_chip_genre_uk_drill")
      .performScrollTo()
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()

    assertTrue(vm.simplePrompt.value.contains("UK drill"))
    assertTrue(vm.simplePrompt.value.endsWith("[End]"))

    // Jump straight from Simple Workstation to 41-Lane Studio Grid tab
    composeTestRule.onNodeWithTag("workstation_open_lane_grid")
      .performScrollTo()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()
    assertEquals(StudioTab.LANES, vm.currentTab.value)
  }
}
