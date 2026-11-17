package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.JobType
import com.example.data.model.PromptTemplateEntity
import com.example.data.repository.PromptTemplateRepository
import com.example.ui.screens.BuildScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.theme.BooDaBeatTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.StudioTab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BeatMeterAndTemplateRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var app: Application
  private lateinit var context: Context
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    context = app
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun testRoomPromptTemplateSeedingUsageFrequencyAndPinOrdering() = runBlocking {
    val repo = PromptTemplateRepository(database.promptTemplateDao())
    repo.ensureSeeded()
    // Calling ensureSeeded again must be idempotent
    repo.ensureSeeded()

    val seeded = repo.allTemplates.first()
    assertEquals(8, seeded.size)
    assertTrue(seeded.any { it.category == "Lo-Fi Hip Hop" })
    assertTrue(seeded.any { it.category == "Dark Synthwave" })

    // Save custom unpinned template and boost its usage count to verify frequency sorting
    val customId = repo.saveTemplate(
      title = "Late Night Cassette",
      category = "Lo-Fi Hip Hop",
      lane = "Jazz rap / lo-fi",
      jobType = JobType.ORIGINAL,
      bpmHint = "80 BPM",
      stylePrompt = "Warm tape lo-fi chords",
      lyricsMap = "[Intro]\n[End]",
      excludeText = "harsh cymbals",
      variety = 14,
      weirdness = 22,
      styleInfluence = 72,
      audioInfluence = 0,
      isPinned = false
    )
    repeat(10) { repo.recordUsage(customId, usedAt = 2_000_000L + it) }

    val afterUsage = repo.allTemplates.first()
    val customEntry = afterUsage.first { it.id == customId }
    assertEquals(11, customEntry.useCount)

    // Pin custom entry so it sorts ahead of starters with fewer uses
    repo.setPinned(customId, true)
    val afterPin = repo.allTemplates.first()
    assertEquals(customId, afterPin.first().id)

    repo.deleteById(customId)
    val afterDelete = repo.allTemplates.first()
    assertFalse(afterDelete.any { it.id == customId })
  }

  @Test
  fun testMigration4To5CreatesPromptTemplatesTableCleanly() {
    val dbName = "migration_4_5_test_${System.nanoTime()}.db"
    val config = SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(dbName)
      .callback(object : SupportSQLiteOpenHelper.Callback(4) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS prompt_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, prompt TEXT NOT NULL, source TEXT NOT NULL, lane TEXT NOT NULL, jobType TEXT NOT NULL, bpm INTEGER, chars INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
          )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      })
      .build()

    val helper = FrameworkSQLiteOpenHelperFactory().create(config)
    val rawDb = helper.writableDatabase
    AppDatabase.MIGRATION_4_5.migrate(rawDb)

    rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='prompt_templates'").use { cursor ->
      assertTrue("prompt_templates table must exist after MIGRATION_4_5", cursor.moveToFirst())
    }
    rawDb.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun testBuildScreenCanvasMeterAndQuickTemplatesBarWithAccessibility() {
    val vm = MainViewModel(app)
    val initialIntensity = vm.beatMeterSnapshot.value.intensityScore

    composeTestRule.setContent {
      BooDaBeatTheme {
        BuildScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    // Canvas beat meter inside Groove Preview Monitor is visible and exposes TalkBack description
    val meterNode = composeTestRule.onNodeWithTag("beat_intensity_canvas_meter")
    meterNode.assertIsDisplayed()
    val semanticsNode = meterNode.fetchSemanticsNode()
    val cd = semanticsNode.config[SemanticsProperties.ContentDescription].joinToString(" ")
    assertTrue("TalkBack contentDescription should describe Canvas meter: $cd", cd.contains("Intensity"))
    assertTrue("TalkBack contentDescription should include Complexity: $cd", cd.contains("Complexity"))

    // Quick Templates bar controls meet 48dp touch target
    composeTestRule.onNodeWithTag("build_quick_templates_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_save_template")
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
    composeTestRule.onNodeWithTag("action_browse_templates")
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)

    // Adjusting Suno sliders + prompt updates BeatMeterSnapshot reactively
    vm.setWeirdness(95)
    vm.setStyleInfluence(98)
    vm.updateStylePrompt("Hyper distorted rage synth 808 maximal stereo width fast hi-hats glitch fills")
    composeTestRule.waitForIdle()

    val updatedSnapshot = vm.beatMeterSnapshot.value
    assertTrue(
      "Updated intensity (${updatedSnapshot.intensityScore}) should change cleanly on high settings",
      updatedSnapshot.intensityScore != initialIntensity || updatedSnapshot.complexityScore > 30
    )

    // Applying a Dark Synthwave template restores category prompt, lane, and sliders
    val synthTemplate = PromptTemplateEntity(
      id = 0L,
      title = "Neon Test Synthwave",
      category = "Dark Synthwave",
      lane = "Dark trap",
      jobType = JobType.ORIGINAL.value,
      bpmHint = "110 BPM",
      stylePrompt = "Dark synthwave Juno pulse and gated 80s snare",
      lyricsMap = "[Intro]\n[Drop]\n[End]",
      excludeText = "acoustic guitar",
      variety = 30,
      weirdness = 45,
      styleInfluence = 88,
      audioInfluence = 0
    )
    vm.applyPromptTemplate(synthTemplate, switchToBuild = false)
    composeTestRule.waitForIdle()

    assertEquals("Dark synthwave Juno pulse and gated 80s snare", vm.stylePrompt.value)
    assertEquals(45, vm.weirdness.value)
    assertEquals(88, vm.styleInfluence.value)
    assertEquals(JobType.ORIGINAL, vm.jobType.value)
  }

  @Test
  fun testLibraryScreenTemplateCategorizationSearchAndOneTapApply() {
    val vm = MainViewModel(app)
    vm.setTab(StudioTab.LIBRARY)
    runBlocking {
      vm.promptTemplateRepo.ensureSeeded()
    }

    composeTestRule.setContent {
      BooDaBeatTheme {
        LibraryScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    // Templates mode and category chips meet 48dp accessibility targets
    composeTestRule.onNodeWithTag("library_mode_templates")
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
    composeTestRule.onNodeWithTag("library_mode_bank")
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)

    composeTestRule.onNodeWithTag("template_category_chip_Lo-Fi Hip Hop")
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
    composeTestRule.onNodeWithTag("template_category_chip_Dark Synthwave")
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()

    assertEquals("Dark Synthwave", vm.templateCategoryFilter.value)

    // Search filters within selected category
    composeTestRule.onNodeWithTag("input_template_search").performTextInput("Neon")
    composeTestRule.waitForIdle()

    val matching = PromptTemplateRepository.filterTemplates(
      vm.promptTemplates.value,
      vm.templateCategoryFilter.value,
      vm.templateSearchQuery.value
    )
    assertTrue("Expected Dark Synthwave match for 'Neon'", matching.isNotEmpty())
    val target = matching.first()

    // One-tap Apply switches back to Build tab and populates all fields
    composeTestRule.onNodeWithTag("template_apply_${target.id}")
      .performScrollTo()
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()

    assertEquals(StudioTab.BUILD, vm.currentTab.value)
    assertEquals(target.stylePrompt, vm.stylePrompt.value)
    assertEquals(target.weirdness, vm.weirdness.value)
  }
}
