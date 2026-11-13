package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.SUNO_STYLE_BOX_LIMIT
import com.example.data.model.TakeEntity
import com.example.data.repository.CuratedPrompts
import com.example.data.repository.PromptBankRepository
import com.example.data.repository.StudioPreferencesRepository
import com.example.ui.screens.LanesScreen
import com.example.ui.theme.BooDaBeatTheme
import com.example.viewmodel.MainViewModel
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
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun testAppNameResource() {
    val appName = context.getString(R.string.app_name)
    assertEquals("BooDa Beat", appName)
  }

  @Test
  fun testPromptBankRepositoryLoadsAllLanesAndPrompts() {
    val repo = PromptBankRepository(context)
    repo.ensureLoaded()

    assertEquals(316, repo.stylePrompts.size)
    assertEquals(75, repo.gospelPrompts.size)
    assertEquals(14, repo.slotTemplates.size)
    assertEquals(46, repo.lyricsBoxTemplates.size)
    assertEquals(3, repo.excludeBanks.size)
    assertEquals(2, repo.settingsCards.size)
    assertEquals(21, repo.verifiedLanes.size)
    assertEquals(20, repo.expansionLanes.size)
    assertEquals(41, repo.allLanes.size)
  }

  @Test
  fun testCurated150PromptsUnder200Characters() {
    val curated = CuratedPrompts.ALL_CURATED
    assertEquals(150, curated.size)

    val beefUpCount = curated.count { it.category == "Sample Beef-Up" }
    val gospelCount = curated.count { it.category == "Gospel Trap" }
    assertEquals(100, beefUpCount)
    assertEquals(50, gospelCount)

    curated.forEach { p ->
      assertTrue(
        "Prompt #${p.id} exceeds 200 characters limit: ${p.chars} chars (${p.prompt})",
        p.prompt.length <= SUNO_STYLE_BOX_LIMIT
      )
      assertTrue("Prompt #${p.id} should not be empty", p.prompt.isNotBlank())
    }
  }

  @Test
  fun testDynamic200CharPromptComposer() {
    val repo = PromptBankRepository(context)
    val composed = repo.composeSuno200CharPrompt(
      sampleType = "chopped soul sample and vocals",
      drumStyle = "punchy kick, hard-hitting 808 sub, and rolling trap hi-hats",
      vibe = "Let the beat breathe. Spacious and soulful",
      bpm = 135,
      isPreserve = true
    )
    assertTrue("Composed prompt must be <= 200 chars: ${composed.length}", composed.length <= SUNO_STYLE_BOX_LIMIT)
    assertTrue("Must contain preserve prefix", composed.contains("Preserve original"))
  }

  @Test
  fun testPreserveLyricsMapContainsLockLineAndEndsWithEndTag() {
    val repo = PromptBankRepository(context)
    repo.ensureLoaded()

    val map = repo.buildV6LyricsMap("Trap", JobType.PRESERVE)
    assertTrue("Preserve map must contain lock line", map.contains(LOCK_LINE))
    assertTrue("Preserve map must end with [End]", map.trim().endsWith("[End]"))
    assertTrue("Preserve map must be <= 5000 chars", map.length <= 5000)
  }

  @Test
  fun testRoomDatabaseTakeOperationsWithMetadata() = runBlocking {
    val dao = database.takeDao()

    val take = TakeEntity(
      beatTitle = "Sunday Service Choir Chop",
      bpm = 138.0,
      audioFilePath = "/storage/emulated/0/Music/choir_sample.wav",
      analysisMetadata = "{\"keyName\":\"F# minor\",\"keyClear\":true,\"lowShare\":0.48}",
      name = "Gospel Trap Winner",
      lane = "Gospel Trap",
      jobType = "preserve",
      style = "$LOCK_LINE. Add hard-banging 808s and rolling trap hi-hats.",
      excludeText = "Vocal-hallucination defense",
      lyrics = "[Intro]\n[Verse 1]\n[End]",
      settings = "Model v6 · Variety 0 · Weirdness 25 · Audio 80",
      result = "pending"
    )

    val id = dao.insertTake(take)
    assertTrue("Inserted take ID should be > 0", id > 0)

    val allTakes = dao.getAllTakes().first()
    assertEquals(1, allTakes.size)
    assertEquals("Sunday Service Choir Chop", allTakes[0].beatTitle)
    assertEquals(138.0, allTakes[0].bpm, 0.01)
    assertEquals("/storage/emulated/0/Music/choir_sample.wav", allTakes[0].audioFilePath)

    // Query by BPM range
    val bpmTakes = dao.getTakesByBpmRange(130.0, 140.0).first()
    assertEquals(1, bpmTakes.size)

    // Search by beat title
    val searchResults = dao.searchTakes("Choir").first()
    assertEquals(1, searchResults.size)

    // Update result to winner
    dao.updateResult(id, "winner")
    val winnerCount = dao.getWinnerCount()
    assertEquals(1, winnerCount)

    // Delete take
    dao.deleteTakeById(id)
    val afterDelete = dao.getAllTakes().first()
    assertEquals(0, afterDelete.size)
  }

  @Test
  fun testFavoritePromptDaoOperations() = runBlocking {
    val favDao = database.favoritePromptDao()

    val fav1 = com.example.data.model.FavoritePromptEntity(
      id = 1L,
      prompt = "Preserve original soul sample. Add heavy 808 sub.",
      category = "Sample Beef-Up",
      subgenre = "Chipmunk Soul",
      bpmHint = "88 BPM",
      chars = 52,
      savedAt = 1000L
    )

    val fav2 = com.example.data.model.FavoritePromptEntity(
      id = 101L,
      prompt = "Chopped gospel choir and B3 organ with 808s.",
      category = "Gospel Trap",
      subgenre = "Sanctuary Trap",
      bpmHint = "140 BPM",
      chars = 46,
      savedAt = 2000L
    )

    favDao.insertFavorite(fav1)
    favDao.insertFavorite(fav2)

    val allFavs = favDao.getAllFavorites().first()
    assertEquals(2, allFavs.size)
    // Ordered by savedAt DESC, so fav2 (savedAt=2000) is first
    assertEquals(101L, allFavs[0].id)
    assertEquals(1L, allFavs[1].id)

    // Check favorite status
    assertTrue(favDao.isFavorite(1L))
    assertTrue(favDao.isFavorite(101L))
    assertFalse(favDao.isFavorite(999L))

    // Rapid double-tap conflict test (OnConflictStrategy.REPLACE)
    val fav1Updated = fav1.copy(savedAt = 3000L)
    favDao.insertFavorite(fav1Updated)
    val count = favDao.getFavoritesCount()
    assertEquals("Duplicate IDs must not create extra rows", 2, count)

    // Delete favorite
    favDao.deleteFavoriteById(1L)
    assertFalse(favDao.isFavorite(1L))
    assertEquals(1, favDao.getFavoritesCount())
  }

  @Test
  fun testSearchFilterMultiWordAndLogic() {
    val repo = PromptBankRepository(context)
    val all = repo.curatedPrompts

    // Single term
    val gospel = repo.getCurated("All", "gospel")
    assertTrue(gospel.isNotEmpty())
    assertTrue("All results must contain 'gospel'", gospel.all {
      "${it.prompt} ${it.category} ${it.subgenre}".contains("gospel", ignoreCase = true)
    })

    // Multi-term AND query: "808 hats"
    val hatsAnd808 = repo.getCurated("All", "808 hats")
    assertTrue(hatsAnd808.isNotEmpty())
    assertTrue("All results must contain BOTH '808' and 'hats'", hatsAnd808.all {
      val text = "${it.prompt} ${it.category} ${it.subgenre} ${it.bpmHint}".lowercase()
      text.contains("808") && text.contains("hats")
    })

    // Case-insensitivity and whitespace trimming
    val lower = repo.getCurated("All", "drill")
    val upper = repo.getCurated("All", "   DRILL   ")
    assertEquals(lower.size, upper.size)

    // Category combined with query
    val gospelOnly = repo.getCurated("Gospel Trap", "organ")
    assertTrue(gospelOnly.all { it.category == "Gospel Trap" })

    // Zero match empty state test
    val none = repo.getCurated("All", "xyznonsensequery123")
    assertEquals(0, none.size)
  }

  @Test
  fun testFavoritesExportAndImportJson() = runBlocking {
    val favDao = database.favoritePromptDao()
    val favRepo = com.example.data.repository.FavoriteRepository(favDao)

    val item = com.example.data.model.CuratedSunoPrompt(
      id = 42,
      category = "Sample Beef-Up",
      subgenre = "Milwaukee Bounce",
      bpmHint = "100 BPM",
      prompt = "Keep original chopped sample as core backbone. Beef up with short punchy 808 stabs.",
      chars = 85
    )
    favRepo.insertFavorite(item)

    val favs = favDao.getAllFavorites().first()
    assertEquals(1, favs.size)

    val exportedJson = favRepo.exportFavoritesJson(favs)
    assertTrue("Exported JSON must contain prompt text", exportedJson.contains("Milwaukee Bounce"))

    // Clear and import back
    favDao.deleteFavoriteById(42L)
    assertEquals(0, favDao.getFavoritesCount())

    val importedCount = favRepo.importFavoritesJson(exportedJson)
    assertEquals(1, importedCount)
    assertTrue(favDao.isFavorite(42L))
  }

  @Test
  fun testTakesDualExportAndImport() = runBlocking {
    val takeDao = database.takeDao()
    val takeRepo = com.example.data.repository.TakeRepository(takeDao)

    val sampleTake = com.example.data.model.TakeEntity(
      id = 0,
      name = "Chiraq Drill Take 1",
      beatTitle = "Southside Heat",
      lane = "Drill",
      bpm = 144.0,
      audioFilePath = null,
      style = "Preserve the original sample and sampled vocals exactly as performed. Add sliding 808s and drill stutters.",
      lyrics = "[Intro] (sample solo)\n[Chorus 1] (drop)\n[End]",
      excludeText = "vocal chops, muddy reverb",
      settings = "Model v6 · Variety 0 · Weirdness 25 · Style 70 · Audio 80",
      result = "winner",
      nextChange = "Slightly shorter 808 glide time",
      jobType = "preserve",
      createdAt = System.currentTimeMillis()
    )

    val newId = takeRepo.insertTake(sampleTake)
    assertTrue(newId > 0)

    val allTakes = takeRepo.allTakes.first()
    assertEquals(1, allTakes.size)

    // Test Plain Text Export
    val plainText = takeRepo.exportTakesPlainText(allTakes)
    assertTrue(plainText.contains("BOODA BEAT ENHANCER"))
    assertTrue(plainText.contains("CHIRAQ DRILL TAKE 1"))
    assertTrue(plainText.contains("[STYLE OF MUSIC]"))
    assertTrue(plainText.contains("[LYRICS BOX / STRUCTURE MAP]"))

    // Test JSON Export
    val json = takeRepo.exportTakesJson(allTakes)
    assertTrue(json.contains("take-package-backup"))
    assertTrue(json.contains("Chiraq Drill Take 1"))

    // Clear DB
    takeRepo.deleteTake(newId)
    val emptyTakes = takeRepo.allTakes.first()
    assertEquals(0, emptyTakes.size)

    // Test JSON Import
    val importedCount = takeRepo.importTakesJson(json)
    assertEquals(1, importedCount)

    val restoredTakes = takeRepo.allTakes.first()
    assertEquals(1, restoredTakes.size)
    assertEquals("Chiraq Drill Take 1", restoredTakes[0].name)
    assertEquals("winner", restoredTakes[0].result)
  }

  @Test
  fun testTakesWinRateCalculation() = runBlocking {
    val takeDao = database.takeDao()
    val takeRepo = com.example.data.repository.TakeRepository(takeDao)

    // Clean start
    val initialTakes = takeRepo.allTakes.first()
    for (t in initialTakes) {
      takeRepo.deleteTake(t.id)
    }

    takeRepo.insertTake(com.example.data.model.TakeEntity(id = 0, name = "Take 1", beatTitle = "", lane = "Trap", bpm = 140.0, audioFilePath = null, style = "Style 1", lyrics = "[End]", excludeText = "", settings = "", result = "winner", nextChange = "", jobType = "preserve", createdAt = 1L))
    takeRepo.insertTake(com.example.data.model.TakeEntity(id = 0, name = "Take 2", beatTitle = "", lane = "Trap", bpm = 140.0, audioFilePath = null, style = "Style 2", lyrics = "[End]", excludeText = "", settings = "", result = "winner", nextChange = "", jobType = "preserve", createdAt = 2L))
    takeRepo.insertTake(com.example.data.model.TakeEntity(id = 0, name = "Take 3", beatTitle = "", lane = "Trap", bpm = 140.0, audioFilePath = null, style = "Style 3", lyrics = "[End]", excludeText = "", settings = "", result = "miss", nextChange = "", jobType = "preserve", createdAt = 3L))

    val (total, winners) = takeRepo.getStats()
    assertEquals(3, total)
    assertEquals(2, winners)
    val winRate = (winners * 100) / total
    assertEquals(66, winRate)
  }

  @Test
  fun testAll41LanesDrumsAndFrameworkComparison() {
    val repo = com.example.data.repository.PromptBankRepository(context)
    repo.ensureLoaded()

    assertEquals(41, repo.allLanes.size)
    assertEquals(21, repo.verifiedLanes.size)
    assertEquals(20, repo.expansionLanes.size)

    for (lane in repo.allLanes) {
      val drums = repo.laneDrums(lane)
      assertTrue("Lane '$lane' must have non-empty drum description", drums.isNotBlank())

      val map = repo.buildV6LyricsMap(lane, com.example.data.model.JobType.PRESERVE)
      assertTrue("Lane '$lane' lyrics map must contain lock line", map.contains(com.example.data.model.LOCK_LINE))
      assertTrue("Lane '$lane' lyrics map must end with [End]", map.trim().endsWith("[End]"))
      assertTrue("Lane '$lane' lyrics map must be <= 5000 chars", map.length <= 5000)
    }
  }

  @Test
  fun testStudioPreferencesSensitivityPersistenceAndReload() {
    val repo1 = StudioPreferencesRepository(context)
    // Default must be 1.0f
    assertEquals(1.0f, repo1.visualizerSensitivity.value, 0.001f)

    // Set custom sensitivity multiplier
    repo1.setVisualizerSensitivity(2.75f)
    assertEquals(2.75f, repo1.visualizerSensitivity.value, 0.001f)

    // Simulate app process recreation by creating a fresh instance against the same context
    val repo2 = StudioPreferencesRepository(context)
    assertEquals("Sensitivity must survive app reload", 2.75f, repo2.visualizerSensitivity.value, 0.001f)

    // Test Reset action
    repo2.resetVisualizerSensitivity()
    assertEquals(1.0f, repo2.visualizerSensitivity.value, 0.001f)

    // Verify reset was persisted
    val repo3 = StudioPreferencesRepository(context)
    assertEquals(1.0f, repo3.visualizerSensitivity.value, 0.001f)

    // Test out-of-bounds clamping
    repo3.setVisualizerSensitivity(10.0f)
    assertEquals("Should clamp high values to 4.0f", 4.0f, repo3.visualizerSensitivity.value, 0.001f)

    repo3.setVisualizerSensitivity(-2.0f)
    assertEquals("Should clamp low values to 0.25f", 0.25f, repo3.visualizerSensitivity.value, 0.001f)

    // Verify peakHoldEnabled default ON and persistence across re-instantiation
    assertTrue("peakHoldEnabled should default to true", repo3.peakHoldEnabled.value)
    repo3.setPeakHoldEnabled(false)
    assertFalse(repo3.peakHoldEnabled.value)

    val repo4 = StudioPreferencesRepository(context)
    assertFalse("peakHoldEnabled=false must survive reload", repo4.peakHoldEnabled.value)
    repo4.setPeakHoldEnabled(true)
    assertTrue(repo4.peakHoldEnabled.value)
  }

  @Test
  fun testAll41LanesTempoProfilesBpmMatchingAndComparisonDefaults() {
    val repo = PromptBankRepository(context)
    repo.ensureLoaded()

    // Confirm comparison default lanes both resolve cleanly in catalog + frameworks
    assertTrue("Memphis phonk / dark trap must be in allLanes", repo.allLanes.contains("Memphis phonk / dark trap"))
    assertTrue("Boom trap must be in allLanes", repo.allLanes.contains("Boom trap"))
    assertTrue(repo.laneDrums("Memphis phonk / dark trap").isNotBlank())
    assertTrue(repo.laneDrums("Boom trap").isNotBlank())

    // Every one of 41 lanes must produce valid min <= max tempo bounds
    for (lane in repo.allLanes) {
      val profile = repo.laneTempoProfile(lane)
      assertTrue("Lane '$lane' bpmMin=${profile.bpmMin} must be in 50..210", profile.bpmMin in 50..210)
      assertTrue("Lane '$lane' bpmMax=${profile.bpmMax} must be >= bpmMin", profile.bpmMax >= profile.bpmMin)
      assertTrue("Lane '$lane' regionTag must not be blank", profile.regionTag.isNotBlank())
    }

    // BPM matching at 88 BPM must surface slow/classic hip-hop lanes near the top
    val slowMatches = repo.rankLanesForBpm(88.0, limit = 5)
    assertEquals(5, slowMatches.size)
    assertTrue(
      "88 BPM should recommend Boom bap / West Coast / Soul / Lo-fi lanes",
      slowMatches.any { it.profile.bpmMin <= 95 }
    )

    // BPM brackets must partition properly
    assertTrue(repo.matchesBpmBracket("Trap", "Any BPM"))
    assertTrue(repo.matchesBpmBracket("Boom bap", "< 100"))
    assertTrue(repo.matchesBpmBracket("Detroit trap", "128+"))
  }

  @Test
  fun testMainViewModelBpmPresetAndTogglePersistence() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.MainViewModel(app)

    // Apply a 1-tap BPM-matched lane preset
    vm.applyBpmMatchedLanePreset("UK drill", 142.0)
    assertEquals("UK drill", vm.activeLane.value)
    assertTrue(
      "Style prompt should contain UK drill and 142 BPM",
      vm.stylePrompt.value.contains("UK drill", ignoreCase = true) &&
        vm.stylePrompt.value.contains("142 BPM")
    )
    assertTrue("Style prompt must stay within Suno limits", vm.stylePrompt.value.length <= 1000)
    assertTrue("Lyrics map must retain LOCK_LINE in PRESERVE mode", vm.lyricsMap.value.contains(LOCK_LINE))

    // Toggle peak-hold and adjust sensitivity
    vm.setPeakHoldEnabled(false)
    assertFalse(vm.peakHoldEnabled.value)
    vm.setVisualizerSensitivity(2.2f)
    assertEquals(2.2f, vm.visualizerSensitivity.value, 0.01f)
    vm.resetVisualizerSensitivity()
    assertEquals(1.0f, vm.visualizerSensitivity.value, 0.01f)
    vm.setPeakHoldEnabled(true)

    // Seeking with no loaded audio is a safe no-op
    vm.seekAudioToFraction(0.5f)
    assertEquals(0f, vm.playbackProgress.value, 0.001f)
  }

  @Test
  fun test41LanesGenreFamiliesAndMetadataSearch() {
    val repo = PromptBankRepository(context)
    repo.ensureLoaded()

    val concreteFamilies = PromptBankRepository.GENRE_FAMILIES.filter {
      it != PromptBankRepository.ALL_FAMILIES_LABEL
    }
    assertEquals(7, concreteFamilies.size)

    // Every one of the 41 lanes resolves to one of the 7 families and sums cleanly to 41
    val familyTotal = concreteFamilies.sumOf { family ->
      repo.filterLanes(query = "", familyFilter = family).size
    }
    assertEquals(41, familyTotal)

    for (lane in repo.allLanes) {
      val resolved = PromptBankRepository.resolveGenreFamily(lane)
      assertTrue(
        "Lane '$lane' resolved to unexpected family '$resolved'",
        concreteFamilies.contains(resolved)
      )
    }

    // Metadata search hits terms inside drum/framework profiles not present in the lane title
    val mpcSwingResults = repo.filterLanes(query = "MPC swing")
    assertTrue("Expected Boom bap from 'MPC swing' framework metadata", mpcSwingResults.contains("Boom bap"))

    val cowbellResults = repo.filterLanes(query = "cowbell")
    assertTrue(cowbellResults.contains("Memphis phonk / dark trap"))
    assertTrue(cowbellResults.contains("Drift phonk"))

    val fiveBeatResults = repo.filterLanes(query = "5-beat")
    assertTrue(fiveBeatResults.contains("Jersey club rap"))

    val logDrumResults = repo.filterLanes(query = "log-drum")
    assertTrue(logDrumResults.contains("Amapiano hip-hop"))
    assertTrue(logDrumResults.contains("Afro-drill"))

    // Combined Bank + BPM + Family intersection
    val verifiedDrillHighBpm = repo.filterLanes(
      query = "",
      bankFilter = "Verified",
      bpmBracket = "128+",
      familyFilter = "Drill & UK"
    )
    assertTrue(verifiedDrillHighBpm.containsAll(listOf("Chiraq drill", "UK drill", "NY drill")))
    assertFalse("NY sample drill is Expansion, not Verified", verifiedDrillHighBpm.contains("NY sample drill"))

    // Contradictory filter yields zero lanes
    val impossible = repo.filterLanes(
      query = "",
      bankFilter = "Verified",
      bpmBracket = "128+",
      familyFilter = "Boom Bap & Jazz"
    )
    assertTrue(impossible.isEmpty())
  }

  @Test
  fun testMainViewModelScrubEdgeCasesWithNoLoadedAudio() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    assertFalse(vm.hasPlayableAudio.value)
    assertFalse(vm.isScrubbingAudio.value)
    assertEquals(0f, vm.playbackProgress.value, 0.0001f)

    // Attempting drag scrub lifecycle with no audio loaded must remain a safe no-op
    vm.beginAudioScrub(0.35f)
    assertFalse("Should not enter scrubbing when durationMs == 0", vm.isScrubbingAudio.value)
    vm.previewAudioScrub(0.85f)
    vm.endAudioScrub(1.5f)
    assertFalse(vm.isScrubbingAudio.value)
    assertEquals(0f, vm.playbackProgress.value, 0.0001f)
  }

  @Test
  fun testLanesScreenSearchDebounceFiltersAndClearRecovery() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    composeTestRule.setContent {
      BooDaBeatTheme {
        LanesScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    // Initial state shows all 41 lanes
    composeTestRule.onNodeWithTag("lanes_result_count")
      .assertTextEquals("Showing 41 of 41 lanes")

    // Search by metadata ("cowbell") + verify ~150ms debounce applies
    composeTestRule.onNodeWithTag("input_lanes_search")
      .performTextInput("cowbell")
    Thread.sleep(220L)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("lanes_result_count")
      .assertTextEquals("Showing 2 of 41 lanes")
    composeTestRule.onNodeWithTag("action_clear_lane_filters")
      .assertIsDisplayed()

    // Combine with Verified bank -> only "Memphis phonk / dark trap" remains
    composeTestRule.onNodeWithTag("filter_bank_Verified").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lanes_result_count")
      .assertTextEquals("Showing 1 of 41 lanes")
    composeTestRule.onNodeWithTag("lanes_catalog_list")
      .performScrollToNode(hasTestTag("lane_item_memphis_phonk_/_dark_trap"))
    composeTestRule.onNodeWithTag("lane_item_memphis_phonk_/_dark_trap")
      .assertIsDisplayed()

    // Scroll header back to top, then switch family to Drill & UK -> 0 matches -> empty state card shown
    composeTestRule.onNodeWithTag("lanes_catalog_list")
      .performScrollToIndex(0)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("filter_family_drill_&_uk")
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lanes_result_count")
      .assertTextEquals("Showing 0 of 41 lanes")
    composeTestRule.onNodeWithTag("lanes_catalog_list")
      .performScrollToNode(hasTestTag("lanes_empty_state"))
    composeTestRule.onNodeWithTag("lanes_empty_state")
      .assertIsDisplayed()

    // Tap empty-state Clear all filters button -> restores all 41 lanes
    composeTestRule.onNodeWithTag("lanes_catalog_list")
      .performScrollToNode(hasTestTag("action_empty_clear_lane_filters"))
    composeTestRule.onNodeWithTag("action_empty_clear_lane_filters")
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lanes_catalog_list")
      .performScrollToIndex(0)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lanes_result_count")
      .assertTextEquals("Showing 41 of 41 lanes")
  }
}

