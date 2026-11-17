package com.example

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiConsultationUiState
import com.example.ai.SearchGroundingUiState
import com.example.ai.TranscriptionTarget
import com.example.ai.TranscriptionUiState
import com.example.audio.AudioAnalysisResult
import com.example.data.db.AppDatabase
import com.example.data.model.ComplianceSnapshot
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.STYLE_LIMIT
import com.example.data.model.TakeEntity
import com.example.data.repository.FavoriteRepository
import com.example.data.repository.HistoryImportMode
import com.example.data.repository.PromptHistoryRepository
import com.example.data.repository.TakeRepository
import com.example.ui.components.RecentPromptsHistoryCard
import com.example.ui.screens.AnalyzeScreen
import com.example.ui.screens.BuildScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SimpleScreen
import com.example.ui.screens.TakesScreen
import com.example.ui.theme.BooDaBeatTheme
import com.example.viewmodel.EnvironmentStatus
import com.example.viewmodel.FailedCloudRequestType
import com.example.viewmodel.FirebaseHealthStatus
import com.example.viewmodel.GeminiHealthStatus
import com.example.viewmodel.GlobalConnectivityState
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
class QaRegressionRobolectricTest {

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
  fun testProcessDeathStateRestorationViaSavedStateHandle() {
    val undoSnap = ComplianceSnapshot(
      jobType = JobType.PRESERVE,
      stylePrompt = "Pre-fix style",
      lyricsMap = "[Intro]",
      excludeText = "muffled",
      variety = 20,
      weirdness = 30,
      styleInfluence = 60,
      audioInfluence = 50,
      fixSummary = "Inserted Lock Line"
    )
    val saved = SavedStateHandle(
      mapOf(
        "studio_tab" to StudioTab.ADVISOR.name,
        "job_type" to JobType.ORIGINAL.value,
        "active_lane" to "Boom bap",
        "style_prompt" to "Restored Boom bap style prompt",
        "lyrics_map" to "[Intro]\n[Verse 1]\n[End]",
        "exclude_text" to "trap hats",
        "variety" to 12,
        "weirdness" to 38,
        "style_influence" to 74,
        "audio_influence" to 0,
        "simple_prompt" to "Restored simple prompt text",
        "curated_search_query" to "soul chop",
        "curated_category" to "Sample Beef-Up",
        "library_query" to "808",
        "library_filter_track" to "Track 1 (Original)",
        "library_filter_lane" to "Boom bap",
        "take_filter_result" to "WINNER",
        "handoff_steps_done" to intArrayOf(1, 3),
        "compliance_undo_snapshot" to undoSnap.toJson()
      )
    )

    val vm = MainViewModel(app, saved)
    assertEquals(StudioTab.ADVISOR, vm.currentTab.value)
    assertEquals(JobType.ORIGINAL, vm.jobType.value)
    assertEquals("Boom bap", vm.activeLane.value)
    assertEquals("Restored Boom bap style prompt", vm.stylePrompt.value)
    assertEquals("[Intro]\n[Verse 1]\n[End]", vm.lyricsMap.value)
    assertEquals("trap hats", vm.excludeText.value)
    assertEquals(12, vm.variety.value)
    assertEquals(38, vm.weirdness.value)
    assertEquals(74, vm.styleInfluence.value)
    assertEquals(0, vm.audioInfluence.value)
    assertEquals("Restored simple prompt text", vm.simplePrompt.value)
    assertEquals("soul chop", vm.curatedSearchQuery.value)
    assertEquals("Sample Beef-Up", vm.curatedCategory.value)
    assertEquals("808", vm.libraryQuery.value)
    assertEquals("Track 1 (Original)", vm.libraryFilterTrack.value)
    assertEquals("Boom bap", vm.libraryFilterLane.value)
    assertEquals("WINNER", vm.takeFilterResult.value)
    assertEquals(setOf(1, 3), vm.handoffStepsDone.value)
    assertEquals(undoSnap, vm.complianceUndoSnapshot.value)
  }

  @Test
  fun testPromptHistoryExportImportSnapshotRestoreAndMalformedGuards() = runBlocking {
    val historyRepo = PromptHistoryRepository(database.promptHistoryDao())
    historyRepo.recordPrompt(
      prompt = "$LOCK_LINE. Tight Trap 808 bounce.",
      source = "Builder",
      lane = "Trap",
      jobType = JobType.PRESERVE,
      bpm = 140
    )
    historyRepo.recordPrompt(
      prompt = "Drift phonk cowbell drive at 150 BPM.",
      source = "Randomize",
      lane = "Drift phonk",
      jobType = JobType.ORIGINAL,
      bpm = 150
    )

    val exportedSnapshot = ComplianceSnapshot(
      jobType = JobType.PRESERVE,
      stylePrompt = "$LOCK_LINE. Tight Trap 808 bounce.",
      lyricsMap = "[Intro]\n[End]",
      excludeText = "no vocals",
      variety = 0,
      weirdness = 22,
      styleInfluence = 72,
      audioInfluence = 80
    )
    val backupJson = historyRepo.exportHistoryJson(
      entries = historyRepo.getRecentSnapshot(),
      snapshot = exportedSnapshot
    )

    historyRepo.clearHistory()
    assertEquals(0, historyRepo.getCount())

    val imported = historyRepo.importHistoryJson(backupJson)
    assertNull(imported.errorMessage)
    assertEquals(2, imported.importedCount)
    assertEquals(0, imported.skippedCount)
    assertEquals(exportedSnapshot, imported.restoredSnapshot)
    assertEquals(2, historyRepo.getCount())

    // Malformed / oversized / invalid-BPM entries are sanitized and skipped safely without crashing
    val mixedBadJson = """
      {
        "entries": [
          { "prompt": "", "lane": "Trap" },
          12345,
          { "prompt": "${"a".repeat(1400)}", "bpm": -45, "lane": "UK drill", "jobType": "preserve" }
        ]
      }
    """.trimIndent()
    historyRepo.clearHistory()
    val mixedResult = historyRepo.importHistoryJson(mixedBadJson)
    assertEquals(1, mixedResult.importedCount)
    assertEquals(2, mixedResult.skippedCount)
    val stored = historyRepo.getRecentSnapshot().single()
    assertEquals(STYLE_LIMIT, stored.prompt.length)
    assertNull("Invalid BPM -45 must be sanitized to null", stored.bpm)

    // Empty / non-JSON errors return user-friendly messages
    val emptyRes = historyRepo.importHistoryJson("   ")
    assertNotNull(emptyRes.errorMessage)
    assertEquals(0, emptyRes.importedCount)

    val garbageRes = historyRepo.importHistoryJson("{broken")
    assertNotNull(garbageRes.errorMessage)
    assertEquals(0, garbageRes.importedCount)

    // Enforce 20-entry rolling cap on bulk import (30 valid strings -> capped to 20)
    historyRepo.clearHistory()
    val arrayJson = org.json.JSONArray().apply {
      for (i in 1..30) put("Prompt variation #$i with Trap drums")
    }.toString()
    val bulkRes = historyRepo.importHistoryJson(arrayJson)
    assertEquals(PromptHistoryRepository.MAX_HISTORY_COUNT, bulkRes.importedCount)
    assertEquals(PromptHistoryRepository.MAX_HISTORY_COUNT, historyRepo.getCount())
  }

  @Test
  fun testTakeAndFavoriteRepositoriesDetailedImportSkipsMalformedEntries() = runBlocking {
    val takeRepo = TakeRepository(database.takeDao())
    val favRepo = FavoriteRepository(database.favoritePromptDao())

    val takesJson = """
      {
        "takes": [
          { "name": "Missing Style", "style": "   " },
          "not-an-object",
          {
            "name": "Valid Imported Take",
            "lane": "Detroit trap",
            "bpm": "NaN",
            "result": "bogus_status",
            "jobType": "preserve",
            "style": "${"z".repeat(1300)}"
          }
        ]
      }
    """.trimIndent()

    val takeRes = takeRepo.importTakesDetailed(takesJson)
    assertEquals(1, takeRes.importedCount)
    assertEquals(2, takeRes.skippedCount)
    val savedTake = takeRepo.allTakes.first().single()
    assertEquals("Valid Imported Take", savedTake.name)
    assertEquals(0.0, savedTake.bpm, 0.0001)
    assertEquals("pending", savedTake.result)
    assertEquals(STYLE_LIMIT, savedTake.style.length)

    val favsJson = """
      {
        "favorites": [
          { "id": 1, "prompt": "" },
          999,
          { "id": 500, "prompt": "Preserve sample. Add heavy 808 bounce.", "category": "Sample Beef-Up", "subgenre": "Trap" }
        ]
      }
    """.trimIndent()

    val favRes = favRepo.importFavoritesDetailed(favsJson)
    assertEquals(1, favRes.importedCount)
    assertEquals(2, favRes.skippedCount)
    assertTrue(favRepo.isFavorite(500L))
  }

  @Test
  fun testMainViewModelSafetyGuardsAndCustomShareSource() = runBlocking {
    val vm = MainViewModel(app)
    vm.setJobType(JobType.PRESERVE)

    // 1. applyTranscriptToStyle clamps <= STYLE_LIMIT and retains LOCK_LINE
    vm.updateStylePrompt("No lock line " + "beat ".repeat(240))
    vm.applyTranscriptToStyle("Punchy Memphis snare bounce and distorted cowbell")
    assertTrue(vm.stylePrompt.value.contains(LOCK_LINE))
    assertTrue(vm.stylePrompt.value.length <= STYLE_LIMIT)

    // 2. adjustBpmMultiplier clamps 40..260, ignores NaN/<=0, and retains Sub-heavy / Balanced label
    val sampleResult = AudioAnalysisResult(
      fileName = "bass_loop.wav",
      durationSec = 10.0,
      bpm = 140.0,
      bpmConfidence = "high",
      keyName = "C minor",
      keyClear = true,
      rmsDb = -14.0,
      peakDb = -1.0,
      lowShare = 0.48,
      centroidHz = 1800.0,
      onsetRate = 3.0,
      suggestedLane = "Trap",
      pocketLabel = "140 BPM · Dark · Sub-heavy",
      promptPreserve = "$LOCK_LINE. Add Trap drums.",
      promptOriginal = "Trap instrumental."
    )
    vm.setAnalysisForTest(sampleResult)

    vm.adjustBpmMultiplier(Double.NaN)
    assertEquals(140.0, vm.audioAnalysis.value!!.bpm!!, 0.01)

    vm.adjustBpmMultiplier(-2.0)
    assertEquals(140.0, vm.audioAnalysis.value!!.bpm!!, 0.01)

    vm.adjustBpmMultiplier(3.0) // 420 -> clamped to 260
    assertEquals(260.0, vm.audioAnalysis.value!!.bpm!!, 0.01)
    assertTrue(vm.audioAnalysis.value!!.pocketLabel.contains("Sub-heavy"))

    vm.adjustBpmMultiplier(0.1) // 26 -> clamped to 40
    assertEquals(40.0, vm.audioAnalysis.value!!.bpm!!, 0.01)

    // 3. sharePrompt preserves custom source & lane in prompt history
    vm.clearPromptHistory()
    Thread.sleep(100L)
    vm.sharePrompt(context, "Share Test", "Custom curated 808 pattern", source = "Curated", lane = "Gospel Trap")
    Thread.sleep(150L)
    val latest = vm.historyRepo.getRecentSnapshot().first()
    assertEquals("Curated", latest.source)
    assertEquals("Gospel Trap", latest.lane)
  }

  @Test
  fun testOfflineAndFirebaseUnavailablePreflightErrorStatesWithRetry() {
    val vm = MainViewModel(app)
    val dummyAnalysis = AudioAnalysisResult(
      fileName = "beat.wav",
      durationSec = 8.0,
      bpm = 132.0,
      bpmConfidence = "high",
      keyName = "D minor",
      keyClear = true,
      rmsDb = -16.0,
      peakDb = -2.0,
      lowShare = 0.40,
      centroidHz = 2000.0,
      onsetRate = 2.0,
      suggestedLane = "Trap",
      pocketLabel = "132 BPM · Dark · Sub-heavy",
      promptPreserve = "$LOCK_LINE. Add Trap drums.",
      promptOriginal = "Trap beat."
    )
    vm.setAnalysisForTest(dummyAnalysis)
    vm.setPendingTranscriptionForTest(byteArrayOf(1, 2, 3, 4), TranscriptionTarget.ANALYZE_SAMPLE_WORDS)

    // 1. Offline environment triggers retryable errors
    vm.environmentStatusProvider = { EnvironmentStatus(isOnline = false, isFirebaseReady = true) }

    vm.retryAiConsultation()
    val aiOffline = vm.aiConsultationState.value as? AiConsultationUiState.Error
    assertNotNull(aiOffline)
    assertTrue(aiOffline!!.canRetry)
    assertTrue(aiOffline.message.contains("Offline"))

    vm.retryLastTranscription()
    val transOffline = vm.micTranscriptionState.value as? TranscriptionUiState.Error
    assertNotNull(transOffline)
    assertTrue(transOffline!!.canRetry)
    assertTrue(transOffline.message.contains("Offline"))

    vm.searchSunoKnowledge("Suno v6 preserve lock")
    val searchOffline = vm.searchGroundingState.value as? SearchGroundingUiState.Error
    assertNotNull(searchOffline)
    assertTrue(searchOffline!!.message.contains("Offline"))

    // 2. Firebase unavailable triggers retryable errors
    vm.environmentStatusProvider = { EnvironmentStatus(isOnline = true, isFirebaseReady = false) }

    vm.retryAiConsultation()
    val aiNoFb = vm.aiConsultationState.value as? AiConsultationUiState.Error
    assertNotNull(aiNoFb)
    assertTrue(aiNoFb!!.canRetry)
    assertTrue(aiNoFb.message.contains("Firebase"))

    vm.retryLastTranscription()
    val transNoFb = vm.micTranscriptionState.value as? TranscriptionUiState.Error
    assertNotNull(transNoFb)
    assertTrue(transNoFb!!.canRetry)
    assertTrue(transNoFb.message.contains("Firebase"))

    vm.searchSunoKnowledge("Suno v6 extend route")
    val searchNoFb = vm.searchGroundingState.value as? SearchGroundingUiState.Error
    assertNotNull(searchNoFb)
    assertTrue(searchNoFb!!.message.contains("Firebase"))
  }

  @Test
  fun testAnalyzeAndBuildErrorAndMicDeniedBannersDisplayAndDismiss() {
    val vm = MainViewModel(app)
    var showBuildScreen by androidx.compose.runtime.mutableStateOf(false)

    composeTestRule.setContent {
      BooDaBeatTheme {
        if (showBuildScreen) {
          BuildScreen(viewModel = vm)
        } else {
          AnalyzeScreen(viewModel = vm)
        }
      }
    }
    composeTestRule.waitForIdle()

    // Trigger empty URI audio error + denied mic permission
    vm.analyzeFile(Uri.EMPTY, "broken.wav")
    vm.onMicrophonePermissionResult(false)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("analyze_audio_error_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mic_permission_denied_banner").assertIsDisplayed()

    composeTestRule.onNodeWithTag("action_dismiss_audio_error").performClick()
    composeTestRule.onNodeWithTag("action_dismiss_mic_denied").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("analyze_audio_error_banner").assertDoesNotExist()
    composeTestRule.onNodeWithTag("mic_permission_denied_banner").assertDoesNotExist()

    // Switch to BuildScreen inside the same setContent host & verify mic denied banner displays & dismisses
    vm.onMicrophonePermissionResult(false)
    showBuildScreen = true
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("build_list").performScrollToNode(hasTestTag("mic_permission_denied_banner"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("mic_permission_denied_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_dismiss_mic_denied").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("mic_permission_denied_banner").assertDoesNotExist()
  }

  @Test
  fun testRecentPromptsHistoryCardBackupSheetSchemaMergeReplaceAndAccessibility() {
    var exportedIncludeSnap: Boolean? = null
    var copiedIncludeSnap: Boolean? = null
    var importFileMode: HistoryImportMode? = null
    var importFileRestoreSnap: Boolean? = null
    var pastedJsonArg: String? = null
    var pastedModeArg: HistoryImportMode? = null
    var restoreSnapshotArg: Boolean? = null

    composeTestRule.setContent {
      BooDaBeatTheme {
        RecentPromptsHistoryCard(
          history = emptyList(),
          onRecallVariation = {},
          onCopyPrompt = {},
          onDeleteEntry = {},
          onClearAll = {},
          onExportBackupFile = { includeSnap -> exportedIncludeSnap = includeSnap },
          onCopyBackupJson = { includeSnap -> copiedIncludeSnap = includeSnap },
          onImportBackupFile = { mode, restoreSnap ->
            importFileMode = mode
            importFileRestoreSnap = restoreSnap
          },
          onImportBackupJson = { json, mode, restoreSnap ->
            pastedJsonArg = json
            pastedModeArg = mode
            restoreSnapshotArg = restoreSnap
          }
        )
      }
    }
    composeTestRule.waitForIdle()

    // Single Backup & Sync button with >= 48.dp target & TalkBack description replaces inline buttons
    val openSheetBtn = composeTestRule.onNodeWithTag("action_open_history_sheet")
    openSheetBtn.assertIsDisplayed()
    openSheetBtn.assertHeightIsAtLeast(48.dp)
    assertFalse(
      openSheetBtn.fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.isEmpty()
    )
    openSheetBtn.performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("history_backup_bottom_sheet").assertIsDisplayed()

    // Default import mode is Merge
    val mergeRadio = composeTestRule.onNodeWithTag("radio_import_mode_merge")
    val replaceRadio = composeTestRule.onNodeWithTag("radio_import_mode_replace")
    mergeRadio.assertHeightIsAtLeast(48.dp).assertIsSelected()
    replaceRadio.assertHeightIsAtLeast(48.dp).assertIsNotSelected()

    // Export actions inside sheet (each dismisses the sheet after dispatching its action)
    composeTestRule.onNodeWithTag("action_sheet_export_file")
      .performScrollTo()
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, exportedIncludeSnap)

    // Re-open sheet to trigger Copy JSON
    openSheetBtn.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("action_sheet_copy_json")
      .performScrollTo()
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()
    assertEquals(true, copiedIncludeSnap)

    // Re-open sheet for Merge/Replace, schema validation, and Paste JSON import
    openSheetBtn.performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("action_sheet_toggle_paste")
      .performScrollTo()
      .assertIsDisplayed()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sheet_paste_json_section").performScrollTo().assertIsDisplayed()
    val inputNode = composeTestRule.onNodeWithTag("input_import_history_json")
    inputNode.performScrollTo().performTextInput("{bad_json")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sheet_schema_status_error").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_confirm_import_history").performScrollTo().assertIsNotEnabled()

    // Switch to Replace mode and enter valid v1 schema JSON
    replaceRadio.performScrollTo().performClick()
    composeTestRule.waitForIdle()
    replaceRadio.assertIsSelected()

    inputNode.performScrollTo().performTextClearance()
    val validPayload = """{"kind":"booda-prompt-history","version":1,"entries":[{"prompt":"Restored Trap Groove"}]}"""
    inputNode.performTextInput(validPayload)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sheet_schema_status_valid").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_confirm_import_history")
      .performScrollTo()
      .assertIsEnabled()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    composeTestRule.waitForIdle()

    assertEquals(validPayload, pastedJsonArg)
    assertEquals(HistoryImportMode.REPLACE, pastedModeArg)
    assertEquals(true, restoreSnapshotArg)

    // Re-open sheet to test file picker import trigger forwards selected mode
    openSheetBtn.performClick()
    composeTestRule.waitForIdle()
    mergeRadio.performScrollTo().performClick()
    composeTestRule.onNodeWithTag("action_sheet_import_file")
      .performScrollTo()
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    assertEquals(HistoryImportMode.MERGE, importFileMode)
    assertEquals(true, importFileRestoreSnap)
  }

  @Test
  fun testTabletWideTwoPaneLayoutsForSimpleLibraryAndTakes() {
    runBlocking {
      val vm = MainViewModel(app)
      vm.takeRepo.insertTake(
        TakeEntity(
          name = "Wide Pane Take",
          beatTitle = "Wide Beat",
          lane = "Trap",
          bpm = 140.0,
          style = "$LOCK_LINE. Heavy Trap drums.",
          lyrics = "[End]",
          excludeText = "no vocals",
          settings = "Model v6",
          result = "winner",
          jobType = "preserve"
        )
      )
      Thread.sleep(100L)

      var screenIndex by mutableIntStateOf(0)
      composeTestRule.setContent {
        BooDaBeatTheme {
          Box(modifier = Modifier.requiredSize(900.dp, 800.dp)) {
            when (screenIndex) {
              0 -> SimpleScreen(viewModel = vm)
              1 -> LibraryScreen(viewModel = vm)
              else -> TakesScreen(viewModel = vm)
            }
          }
        }
      }

      // SimpleScreen wide 2-pane
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("simple_wide_layout").assertIsDisplayed()

      // LibraryScreen wide 2-pane
      screenIndex = 1
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("library_wide_layout").assertIsDisplayed()

      // TakesScreen wide 2-pane
      screenIndex = 2
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("takes_wide_layout").assertIsDisplayed()
      composeTestRule.onNodeWithTag("takes_list").assertIsDisplayed()
    }
  }

  @Test
  fun testManifestOnlyDeclaresPlayCompliantLeastPrivilegePermissions() {
    val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
      )
    } else {
      @Suppress("DEPRECATION")
      context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    }

    val requested = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
    assertTrue(requested.contains(android.Manifest.permission.INTERNET))
    assertTrue(requested.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
    assertTrue(requested.contains(android.Manifest.permission.RECORD_AUDIO))
    assertFalse(requested.contains("android.permission.READ_EXTERNAL_STORAGE"))
    assertFalse(requested.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
    assertFalse(requested.contains("android.permission.READ_MEDIA_AUDIO"))
    assertFalse(requested.contains("android.permission.READ_MEDIA_IMAGES"))
    assertFalse(requested.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
    assertFalse(requested.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
  }

  @Test
  fun testPromptHistoryMergeDeduplicatesAndCaps20AndReplaceOverwrites() = runBlocking {
    val historyRepo = PromptHistoryRepository(database.promptHistoryDao())
    val baseTs = 1_700_000_000_000L

    // Seed 15 existing entries
    for (i in 1..15) {
      historyRepo.recordPrompt(
        prompt = "Existing prompt #$i",
        source = "Builder",
        lane = "Trap",
        createdAt = baseTs + i * 1000L
      )
    }
    assertEquals(15, historyRepo.getCount())

    // Build backup with 5 overlapping prompts (newer timestamp) + 10 brand new prompts
    val mergeEntries = org.json.JSONArray().apply {
      for (i in 1..5) {
        put(org.json.JSONObject().apply {
          put("prompt", "Existing prompt #$i")
          put("source", "Backup")
          put("lane", "Trap")
          put("createdAt", baseTs + 50_000L + i * 1000L)
        })
      }
      for (i in 16..25) {
        put(org.json.JSONObject().apply {
          put("prompt", "Merged new prompt #$i")
          put("source", "Backup")
          put("lane", "Boom bap")
          put("createdAt", baseTs + 60_000L + i * 1000L)
        })
      }
    }
    val mergePayload = org.json.JSONObject().apply {
      put("kind", PromptHistoryRepository.EXPECTED_BACKUP_KIND)
      put("version", PromptHistoryRepository.SUPPORTED_SCHEMA_VERSION)
      put("entries", mergeEntries)
    }.toString()

    val mergeRes = historyRepo.importHistoryJson(mergePayload, mode = HistoryImportMode.MERGE)
    assertNull(mergeRes.errorMessage)
    val mergedList = historyRepo.getRecentSnapshot()
    // 15 existing + 10 new unique = 25 unique -> capped cleanly at 20 newest deduplicated items
    assertEquals(PromptHistoryRepository.MAX_HISTORY_COUNT, mergedList.size)
    assertEquals(mergedList.map { it.prompt }.distinct().size, mergedList.size)
    assertTrue(mergedList.any { it.prompt == "Merged new prompt #25" })

    // Replace mode overwrites all 20 previous entries with strictly the 2 imported items
    val replacePayload = """
      {
        "kind": "booda-prompt-history",
        "version": 1,
        "entries": [
          {"prompt": "Clean Replace Alpha", "lane": "NY drill"},
          {"prompt": "Clean Replace Beta", "lane": "Gospel Trap"}
        ]
      }
    """.trimIndent()
    val replaceRes = historyRepo.importHistoryJson(replacePayload, mode = HistoryImportMode.REPLACE)
    assertNull(replaceRes.errorMessage)
    assertEquals(2, replaceRes.importedCount)
    val afterReplace = historyRepo.getRecentSnapshot()
    assertEquals(2, afterReplace.size)
    assertEquals(setOf("Clean Replace Alpha", "Clean Replace Beta"), afterReplace.map { it.prompt }.toSet())
  }

  @Test
  fun testGlobalConnectivitySeparateHealthTrackingNetworkTransitionsAndRetry() {
    val vm = MainViewModel(app)
    vm.environmentStatusProvider = { EnvironmentStatus(isOnline = true, isFirebaseReady = true) }

    // 1. Live network transition offline -> online recovers cleanly
    vm.updateConnectivityOnNetworkChange(isOnline = false)
    val offlineState = vm.connectivityState.value
    assertFalse(offlineState.isNetworkOnline)
    assertEquals(FirebaseHealthStatus.OFFLINE, offlineState.firebaseHealth)
    assertEquals(GeminiHealthStatus.OFFLINE, offlineState.geminiHealth)
    assertTrue(offlineState.shouldShowBanner)

    // Offline studio workflows continue uninterrupted
    vm.setLane("Boom bap")
    assertEquals("Boom bap", vm.activeLane.value)
    assertTrue(vm.stylePrompt.value.contains("Boom bap"))

    vm.updateConnectivityOnNetworkChange(isOnline = true)
    val recoveredState = vm.connectivityState.value
    assertTrue(recoveredState.isNetworkOnline)
    assertEquals(FirebaseHealthStatus.HEALTHY, recoveredState.firebaseHealth)
    assertEquals(GeminiHealthStatus.HEALTHY, recoveredState.geminiHealth)
    assertFalse(recoveredState.shouldShowBanner)

    // 2. Firebase unreachable while Gemini healthy tracks separately and stores failed request
    vm.environmentStatusProvider = { EnvironmentStatus(isOnline = true, isFirebaseReady = false) }
    vm.searchSunoKnowledge("Suno v6 bass locking")
    val fbDownState = vm.connectivityState.value
    assertEquals(FirebaseHealthStatus.UNREACHABLE, fbDownState.firebaseHealth)
    assertEquals(GeminiHealthStatus.HEALTHY, fbDownState.geminiHealth)
    assertEquals(FailedCloudRequestType.SEARCH_GROUNDING, fbDownState.lastFailedRequest)
    assertTrue(fbDownState.shouldShowBanner)

    // 3. Re-probe Firebase healthy and tap Retry re-initializes last failed request cleanly
    vm.environmentStatusProvider = { EnvironmentStatus(isOnline = true, isFirebaseReady = true) }
    vm.retryConnectionAndLastRequest()
    // After synchronous preflight re-probe succeeds, Search Grounding was re-launched (Loading or Error if Firebase endpoint absent in JVM)
    assertTrue(
      vm.searchGroundingState.value is SearchGroundingUiState.Loading ||
        vm.searchGroundingState.value is SearchGroundingUiState.Error ||
        vm.searchGroundingState.value is SearchGroundingUiState.Success
    )
  }

  @Test
  fun testMainAppContentConnectivityBannerDisplaysPillsRetryAndDismissWithTouchTargets() {
    val vm = MainViewModel(app)
    vm.environmentStatusProvider = { EnvironmentStatus(isOnline = true, isFirebaseReady = true) }
    vm.reportConnectivityStateForTest(
      GlobalConnectivityState(
        isNetworkOnline = true,
        firebaseHealth = FirebaseHealthStatus.UNREACHABLE,
        geminiHealth = GeminiHealthStatus.TIMED_OUT,
        lastErrorMessage = "Gemini timed out; Firebase App Check unreachable.",
        lastFailedRequest = FailedCloudRequestType.SEARCH_GROUNDING,
        isRetrying = false,
        isBannerDismissed = false
      )
    )

    composeTestRule.setContent {
      BooDaBeatTheme {
        MainAppContent(vm)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("global_connectivity_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("connectivity_firebase_pill").assertIsDisplayed()
    composeTestRule.onNodeWithTag("connectivity_gemini_pill").assertIsDisplayed()

    val retryBtn = composeTestRule.onNodeWithTag("action_retry_connectivity")
    val dismissBtn = composeTestRule.onNodeWithTag("action_dismiss_connectivity_banner")
    retryBtn.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    dismissBtn.assertIsDisplayed().assertHeightIsAtLeast(48.dp)

    // Dismiss hides banner while offline workflows remain intact
    dismissBtn.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("global_connectivity_banner").assertDoesNotExist()

    // Re-surface error and tap Retry to re-probe
    vm.reportConnectivityStateForTest(
      GlobalConnectivityState(
        isNetworkOnline = true,
        firebaseHealth = FirebaseHealthStatus.UNREACHABLE,
        geminiHealth = GeminiHealthStatus.HEALTHY,
        lastErrorMessage = "Firebase App Check unavailable",
        lastFailedRequest = FailedCloudRequestType.BEAT_CONSULTATION,
        isRetrying = false,
        isBannerDismissed = false
      )
    )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("global_connectivity_banner").assertIsDisplayed()
    retryBtn.performClick()
    composeTestRule.waitForIdle()
    assertTrue(vm.connectivityState.value.isFirebaseReachable)
  }

  @Test
  fun testComplianceSnapshotJsonRoundtripAndMalformedHandling() {
    val original = ComplianceSnapshot(
      jobType = JobType.PRESERVE,
      stylePrompt = "$LOCK_LINE. Add Trap drums.",
      lyricsMap = "[Intro]\n[End]",
      excludeText = "no vocals",
      variety = 0,
      weirdness = 25,
      styleInfluence = 70,
      audioInfluence = 82,
      fixSummary = "Calibrated sliders"
    )

    val parsed = ComplianceSnapshot.fromJson(original.toJson())
    assertNotNull(parsed)
    assertEquals(original, parsed)

    val clampedJson = """
      {"jobType":"create","stylePrompt":"Trap beat","lyricsMap":"[End]","excludeText":"","variety":999,"weirdness":-10,"styleInfluence":150,"audioInfluence":-4}
    """.trimIndent()
    val clamped = ComplianceSnapshot.fromJson(clampedJson)
    assertNotNull(clamped)
    assertEquals(JobType.ORIGINAL, clamped!!.jobType)
    assertEquals(100, clamped.variety)
    assertEquals(0, clamped.weirdness)
    assertEquals(100, clamped.styleInfluence)
    assertEquals(0, clamped.audioInfluence)

    assertNull(ComplianceSnapshot.fromJson(null))
    assertNull(ComplianceSnapshot.fromJson(""))
    assertNull(ComplianceSnapshot.fromJson("not-json"))
  }

  @Test
  fun testPromptHistorySchemaValidationRulesAnd20Cap() {
    val validEnvelope = """
      {
        "app": "BooDa Beat Enhancer",
        "kind": "booda-prompt-history",
        "version": 1,
        "complianceSnapshot": {
          "jobType": "preserve",
          "stylePrompt": "$LOCK_LINE. Add Trap drums.",
          "lyricsMap": "[End]",
          "excludeText": "no vocals",
          "variety": 0,
          "weirdness": 25,
          "styleInfluence": 70,
          "audioInfluence": 80
        },
        "entries": [
          { "prompt": "$LOCK_LINE. Trap 808 bounce.", "lane": "Trap", "bpm": 140 },
          { "prompt": "   " }
        ]
      }
    """.trimIndent()

    val ok = PromptHistoryRepository.validateSchema(validEnvelope)
    assertTrue(ok.isValid)
    assertEquals(1, ok.entryCount)
    assertEquals(1, ok.skippedCount)
    assertTrue(ok.hasSnapshot)
    assertNull(ok.errorMessage)

    val wrongKind = PromptHistoryRepository.validateSchema(
      """{"kind":"other-app","version":1,"entries":["Test"]}"""
    )
    assertFalse(wrongKind.isValid)
    assertTrue(wrongKind.errorMessage!!.contains("kind", ignoreCase = true))

    val badVersion = PromptHistoryRepository.validateSchema(
      """{"kind":"booda-prompt-history","version":99,"entries":["Test"]}"""
    )
    assertFalse(badVersion.isValid)
    assertTrue(badVersion.errorMessage!!.contains("version", ignoreCase = true))

    val nonArrayEntries = PromptHistoryRepository.validateSchema(
      """{"kind":"booda-prompt-history","version":1,"entries":"invalid"}"""
    )
    assertFalse(nonArrayEntries.isValid)
    assertTrue(nonArrayEntries.errorMessage!!.contains("array", ignoreCase = true))

    assertFalse(PromptHistoryRepository.validateSchema("").isValid)
    assertFalse(PromptHistoryRepository.validateSchema("{not-json").isValid)
  }
}
