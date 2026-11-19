package com.example

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.JobType
import com.example.ui.components.StudioSettingsDrawerSheet
import com.example.ui.components.StudioTopBar
import com.example.ui.screens.LanesScreen
import com.example.ui.screens.TakesScreen
import com.example.ui.theme.BooDaBeatTheme
import com.example.viewmodel.MainViewModel
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class BrandLogoRobolectricTest {

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
  fun testOfficialLogoMasterAssetDimensionsAndDrawablesResolve() {
    val options = BitmapFactory.Options().apply { inScaled = false }
    val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.booda_official_logo, options)
    assertNotNull("booda_official_logo must decode cleanly", bitmap)
    assertEquals("Original source width must remain 302px", 302, bitmap.width)
    assertEquals("Original source height must remain 246px", 246, bitmap.height)

    val fgBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.booda_launcher_foreground, options)
    assertNotNull("booda_launcher_foreground must decode cleanly", fgBitmap)
    assertEquals("Foreground canvas should be square padded 302x302", 302, fgBitmap.width)
    assertEquals("Foreground canvas should be square padded 302x302", 302, fgBitmap.height)

    assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground))
    assertNotNull(context.getDrawable(R.drawable.ic_launcher_background))
    assertNotNull(context.getDrawable(R.drawable.splash_brand_logo))
    assertNotNull(context.getDrawable(R.mipmap.ic_launcher))
    assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
  }

  @Test
  fun testAllMipmapBucketsContainExactPngFallbacksWithoutLegacyWebp() {
    val buckets = mapOf(
      "mipmap-mdpi" to 48,
      "mipmap-hdpi" to 72,
      "mipmap-xhdpi" to 96,
      "mipmap-xxhdpi" to 144,
      "mipmap-xxxhdpi" to 192
    )
    val resRoot = listOf(
      File("app/src/main/res"),
      File("src/main/res")
    ).firstOrNull { it.exists() }
    assertNotNull("Could not locate src/main/res directory", resRoot)

    buckets.forEach { (dirName, expectedPx) ->
      val dir = File(resRoot, dirName)
      assertTrue("Missing density folder $dirName", dir.exists())
      val square = File(dir, "ic_launcher.png")
      val round = File(dir, "ic_launcher_round.png")
      assertTrue("Missing $dirName/ic_launcher.png", square.exists())
      assertTrue("Missing $dirName/ic_launcher_round.png", round.exists())

      val squareBmp = BitmapFactory.decodeFile(square.absolutePath)
      val roundBmp = BitmapFactory.decodeFile(round.absolutePath)
      assertEquals("$dirName square width", expectedPx, squareBmp.width)
      assertEquals("$dirName square height", expectedPx, squareBmp.height)
      assertEquals("$dirName round width", expectedPx, roundBmp.width)
      assertEquals("$dirName round height", expectedPx, roundBmp.height)

      val webpLeftovers = dir.listFiles { _, name -> name.endsWith(".webp") }.orEmpty()
      assertTrue("Legacy webp files must be removed in $dirName", webpLeftovers.isEmpty())
    }
  }

  @Test
  fun testStudioTopBarDisplays44dpProportionalOfficialLogoWithAccessibilityLabel() {
    composeTestRule.setContent {
      BooDaBeatTheme {
        StudioTopBar(
          activeLane = "Boom trap",
          jobType = JobType.PRESERVE,
          onJobTypeChanged = {},
          onLaneClick = {},
          onSettingsClick = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    val logoNode = composeTestRule.onNodeWithTag("app_header_logo")
    logoNode.assertIsDisplayed()
    logoNode.assertHeightIsEqualTo(44.dp)
    // 44dp * (302 / 246) ≈ 54.02dp
    logoNode.assertWidthIsAtLeast(53.dp)
    val bounds = logoNode.fetchSemanticsNode().boundsInRoot
    val ratio = bounds.width / bounds.height
    assertTrue("Header logo ratio ($ratio) should match 302:246", abs(ratio - (302f / 246f)) < 0.05f)

    val cd = logoNode.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
    assertTrue("Header logo must expose accessibility description", cd.contains("logo", ignoreCase = true))
  }

  @Test
  fun testSettingsDrawerAndEmptyStatesDisplayOfficialLogo() {
    val viewModel = MainViewModel(app)
    composeTestRule.setContent {
      BooDaBeatTheme {
        StudioSettingsDrawerSheet(viewModel = viewModel, onClose = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_drawer_logo").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_drawer_logo").assertHeightIsEqualTo(40.dp)
  }

  @Test
  fun testTakesEmptyStateDisplaysOfficialLogo() {
    val viewModel = MainViewModel(app)
    runBlocking {
      viewModel.takeRepo.allTakes.first().forEach { viewModel.takeRepo.deleteTake(it.id) }
    }
    composeTestRule.setContent {
      BooDaBeatTheme {
        TakesScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 3000) {
      viewModel.takesList.value.isEmpty()
    }
    // Initial room database starts with 0 takes, so empty takes block renders official logo
    composeTestRule.onNodeWithTag("takes_list")
      .performScrollToNode(hasTestTag("takes_empty_logo"))
    composeTestRule.onNodeWithTag("takes_empty_logo").assertIsDisplayed()
  }

  @Test
  fun testLanesEmptySearchDisplaysOfficialLogo() {
    val viewModel = MainViewModel(app)
    composeTestRule.setContent {
      BooDaBeatTheme {
        LanesScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("input_lanes_search").performTextInput("zzzznonexistentlane")
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 3000) {
      composeTestRule.onAllNodesWithText("Showing 0 of", substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("lanes_catalog_list")
      .performScrollToNode(hasTestTag("lanes_empty_logo"))
    composeTestRule.onNodeWithTag("lanes_empty_logo").assertIsDisplayed()
  }
}
