package com.example.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.ai.AiBeatConsultation
import com.example.ai.AiConsultationUiState
import com.example.ai.GeminiBeatService
import com.example.ai.SearchGroundingUiState
import com.example.ai.TranscriptionTarget
import com.example.ai.TranscriptionUiState
import com.example.ai.configureAppCheck
import com.example.audio.AudioAnalysisResult
import com.example.audio.AudioAnalyzer
import com.example.audio.AudioRecorder
import com.example.audio.BeatIntensityCalculator
import com.example.audio.BeatMeterSnapshot
import com.example.audio.StudioAudioPlayer
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.repository.FavoriteRepository
import com.example.data.repository.HistoryImportMode
import com.example.data.repository.HistorySchemaValidationResult
import com.example.data.repository.PromptBankRepository
import com.example.data.repository.PromptHistoryRepository
import com.example.data.repository.PromptTemplateRepository
import com.example.data.repository.StudioPreferencesRepository
import com.example.data.repository.TakeRepository
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class StudioTab(val title: String) {
  ANALYZE("Analyze"),
  STYLES("Styles"),
  LIBRARY("Library"),
  BUILD("Build"),
  SIMPLE("Simple"),
  LANES("Lanes"),
  TAKES("Takes"),
  ADVISOR("Advisor")
}

data class EnvironmentStatus(
  val isOnline: Boolean,
  val isFirebaseReady: Boolean
)

class MainViewModel(
  application: Application,
  private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(application) {

  val bankRepo = PromptBankRepository(application)
  private val database = AppDatabase.getDatabase(application)
  val takeRepo = TakeRepository(database.takeDao())
  val favoriteRepo = FavoriteRepository(database.favoritePromptDao())
  val historyRepo = PromptHistoryRepository(database.promptHistoryDao())
  val promptTemplateRepo = PromptTemplateRepository(database.promptTemplateDao())
  val preferencesRepo = StudioPreferencesRepository(application)
  private val audioAnalyzer = AudioAnalyzer(application)
  private val audioRecorder = AudioRecorder()
  private val audioPlayer = StudioAudioPlayer(viewModelScope)
  private val geminiService = GeminiBeatService()

  internal var environmentStatusProvider: () -> EnvironmentStatus = { evaluateEnvironmentStatus() }

  private var analysisJob: Job? = null
  private var aiJob: Job? = null
  private var transcribeJob: Job? = null
  private var searchJob: Job? = null

  // Synced Waveform Playback State
  val isPlayingAudio: StateFlow<Boolean> = audioPlayer.isPlaying
  val playbackProgress: StateFlow<Float> = audioPlayer.progress
  val playbackPositionMs: StateFlow<Long> = audioPlayer.positionMs
  val playbackDurationMs: StateFlow<Long> = audioPlayer.durationMs
  val hasPlayableAudio: StateFlow<Boolean> = audioPlayer.hasAudio
  val isLoopingAudio: StateFlow<Boolean> = audioPlayer.isLooping
  val isScrubbingAudio: StateFlow<Boolean> = audioPlayer.isScrubbing

  // Studio Visualizer Preferences
  val visualizerSensitivity: StateFlow<Float> = preferencesRepo.visualizerSensitivity
  val peakHoldEnabled: StateFlow<Boolean> = preferencesRepo.peakHoldEnabled

  fun setVisualizerSensitivity(multiplier: Float) {
    preferencesRepo.setVisualizerSensitivity(multiplier)
  }

  fun resetVisualizerSensitivity() {
    preferencesRepo.resetVisualizerSensitivity()
  }

  fun setPeakHoldEnabled(enabled: Boolean) {
    preferencesRepo.setPeakHoldEnabled(enabled)
  }

  // Favorites System
  val allFavorites: StateFlow<List<FavoritePromptEntity>> = favoriteRepo.allFavorites
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val favoriteIds: StateFlow<Set<Long>> = favoriteRepo.favoriteIds
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

  val favoritesCount: StateFlow<Int> = favoriteRepo.favoritesCount
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  private val _lastDeletedFavorite = MutableStateFlow<FavoritePromptEntity?>(null)
  val lastDeletedFavorite: StateFlow<FavoritePromptEntity?> = _lastDeletedFavorite.asStateFlow()

  // Style Generator Search & Filter State
  private val _curatedSearchQuery = MutableStateFlow("")
  val curatedSearchQuery: StateFlow<String> = _curatedSearchQuery.asStateFlow()

  private val _curatedCategory = MutableStateFlow("All")
  val curatedCategory: StateFlow<String> = _curatedCategory.asStateFlow()

  // Room-backed Last 20 Generated Prompts History
  val recentPromptHistory: StateFlow<List<PromptHistoryEntity>> = historyRepo.recentPrompts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentPrompts: StateFlow<List<String>> = historyRepo.recentPrompts
    .map { entries -> entries.map { it.prompt } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Room-backed Categorized Suno Prompt Templates
  val promptTemplates: StateFlow<List<PromptTemplateEntity>> = promptTemplateRepo.allTemplates
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val templateCategories: StateFlow<List<String>> = promptTemplateRepo.categories
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _templateSearchQuery = MutableStateFlow(savedStateHandle.get<String>(KEY_TEMPLATE_QUERY) ?: "")
  val templateSearchQuery: StateFlow<String> = _templateSearchQuery.asStateFlow()

  private val _templateCategoryFilter = MutableStateFlow(savedStateHandle.get<String>(KEY_TEMPLATE_CATEGORY) ?: "All")
  val templateCategoryFilter: StateFlow<String> = _templateCategoryFilter.asStateFlow()

  // Navigation
  private val _currentTab = MutableStateFlow(StudioTab.BUILD)
  val currentTab: StateFlow<StudioTab> = _currentTab.asStateFlow()

  // Studio Workflow State
  private val _jobType = MutableStateFlow(JobType.PRESERVE)
  val jobType: StateFlow<JobType> = _jobType.asStateFlow()

  private val _activeLane = MutableStateFlow("Trap")
  val activeLane: StateFlow<String> = _activeLane.asStateFlow()

  // 3-Box Build Prompts
  private val _stylePrompt = MutableStateFlow("")
  val stylePrompt: StateFlow<String> = _stylePrompt.asStateFlow()

  private val _lyricsMap = MutableStateFlow("")
  val lyricsMap: StateFlow<String> = _lyricsMap.asStateFlow()

  private val _excludeText = MutableStateFlow("")
  val excludeText: StateFlow<String> = _excludeText.asStateFlow()

  // Suno v6 Settings
  private val _variety = MutableStateFlow(0)
  val variety: StateFlow<Int> = _variety.asStateFlow()

  private val _weirdness = MutableStateFlow(25)
  val weirdness: StateFlow<Int> = _weirdness.asStateFlow()

  private val _styleInfluence = MutableStateFlow(70)
  val styleInfluence: StateFlow<Int> = _styleInfluence.asStateFlow()

  private val _audioInfluence = MutableStateFlow(80)
  val audioInfluence: StateFlow<Int> = _audioInfluence.asStateFlow()

  // Simple Mode Prompt
  private val _simplePrompt = MutableStateFlow("")
  val simplePrompt: StateFlow<String> = _simplePrompt.asStateFlow()

  // Audio Analysis & Live Recording
  private val _audioAnalysis = MutableStateFlow<AudioAnalysisResult?>(null)
  val audioAnalysis: StateFlow<AudioAnalysisResult?> = _audioAnalysis.asStateFlow()

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _recordingPeak = MutableStateFlow(0f)
  val recordingPeak: StateFlow<Float> = _recordingPeak.asStateFlow()

  private val _recordingDb = MutableStateFlow(-60f)
  val recordingDb: StateFlow<Float> = _recordingDb.asStateFlow()

  private val rollingWaveformBuffer = FloatArray(80) { 0.05f }
  private val _liveWaveform = MutableStateFlow(FloatArray(80) { 0.05f })
  val liveWaveform: StateFlow<FloatArray> = _liveWaveform.asStateFlow()

  private val _isAnalyzing = MutableStateFlow(false)
  val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

  private val _aiConsultation = MutableStateFlow<AiBeatConsultation?>(null)
  val aiConsultation: StateFlow<AiBeatConsultation?> = _aiConsultation.asStateFlow()

  private val _aiConsultationState = MutableStateFlow<AiConsultationUiState>(AiConsultationUiState.Idle)
  val aiConsultationState: StateFlow<AiConsultationUiState> = _aiConsultationState.asStateFlow()

  private val _micTranscriptionState = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Idle)
  val micTranscriptionState: StateFlow<TranscriptionUiState> = _micTranscriptionState.asStateFlow()

  private val _searchGroundingState = MutableStateFlow<SearchGroundingUiState>(SearchGroundingUiState.Idle)
  val searchGroundingState: StateFlow<SearchGroundingUiState> = _searchGroundingState.asStateFlow()

  private val _connectivityState = MutableStateFlow(GlobalConnectivityState())
  val connectivityState: StateFlow<GlobalConnectivityState> = _connectivityState.asStateFlow()

  private var lastAnalyzedWavBytes: ByteArray? = null
  private var lastTranscriptionWavBytes: ByteArray? = null
  private var lastTranscriptionTarget: TranscriptionTarget = TranscriptionTarget.ANALYZE_SAMPLE_WORDS
  private var lastSearchQuery: String = ""

  private var connectivityManagerRef: ConnectivityManager? = null
  private var networkCallback: ConnectivityManager.NetworkCallback? = null

  private val _complianceUndoSnapshot = MutableStateFlow<ComplianceSnapshot?>(null)
  val complianceUndoSnapshot: StateFlow<ComplianceSnapshot?> = _complianceUndoSnapshot.asStateFlow()

  // Library & Search Filters
  private val _libraryQuery = MutableStateFlow("")
  val libraryQuery: StateFlow<String> = _libraryQuery.asStateFlow()

  private val _libraryFilterTrack = MutableStateFlow("All")
  val libraryFilterTrack: StateFlow<String> = _libraryFilterTrack.asStateFlow()

  private val _libraryFilterLane = MutableStateFlow("All")
  val libraryFilterLane: StateFlow<String> = _libraryFilterLane.asStateFlow()

  // Takes & Persistence
  val takesList: StateFlow<List<TakeEntity>> = takeRepo.allTakes
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val bankLoadError: StateFlow<String?> = bankRepo.loadError
  val playbackError: StateFlow<String?> = audioPlayer.playbackError

  private val _audioError = MutableStateFlow<String?>(null)
  val audioError: StateFlow<String?> = _audioError.asStateFlow()

  private val _micPermissionDenied = MutableStateFlow(false)
  val micPermissionDenied: StateFlow<Boolean> = _micPermissionDenied.asStateFlow()

  private val _takeFilterResult = MutableStateFlow("ALL")
  val takeFilterResult: StateFlow<String> = _takeFilterResult.asStateFlow()

  // Suno Handoff Checklist Tracking (Steps 1..5) — declared before init so restoreFromSavedState() never sees null
  private val _handoffStepsDone = MutableStateFlow<Set<Int>>(emptySet())
  val handoffStepsDone: StateFlow<Set<Int>> = _handoffStepsDone.asStateFlow()

  private data class MeterSliderParams(
    val variety: Int,
    val weirdness: Int,
    val styleInfluence: Int,
    val audioInfluence: Int,
    val jobType: JobType
  )

  private data class MeterContentParams(
    val stylePrompt: String,
    val lyricsMap: String,
    val excludeText: String,
    val activeLane: String,
    val audioAnalysis: AudioAnalysisResult?
  )

  val beatMeterSnapshot: StateFlow<BeatMeterSnapshot> = combine(
    combine(_variety, _weirdness, _styleInfluence, _audioInfluence, _jobType) { v, w, s, a, j ->
      MeterSliderParams(v, w, s, a, j)
    },
    combine(_stylePrompt, _lyricsMap, _excludeText, _activeLane, _audioAnalysis) { sp, lm, ex, lane, aa ->
      MeterContentParams(sp, lm, ex, lane, aa)
    }
  ) { sliders, content ->
    BeatIntensityCalculator.compute(
      variety = sliders.variety,
      weirdness = sliders.weirdness,
      styleInfluence = sliders.styleInfluence,
      audioInfluence = sliders.audioInfluence,
      stylePrompt = content.stylePrompt,
      lyricsMap = content.lyricsMap,
      excludeText = content.excludeText,
      activeLane = content.activeLane,
      jobType = sliders.jobType,
      audioAnalysis = content.audioAnalysis
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = BeatIntensityCalculator.compute(
      variety = 0,
      weirdness = 25,
      styleInfluence = 70,
      audioInfluence = 80,
      stylePrompt = "",
      lyricsMap = "",
      excludeText = "",
      activeLane = "Trap",
      jobType = JobType.PRESERVE,
      audioAnalysis = null
    )
  )

  init {
    bankRepo.ensureLoaded()
    if (savedStateHandle.contains(KEY_JOB_TYPE)) {
      restoreFromSavedState()
    } else {
      resetToPreserveDefaults()
    }
    viewModelScope.launch(Dispatchers.IO) {
      promptTemplateRepo.ensureSeeded()
    }
    refreshConnectivityFromEnvironment()
    registerNetworkCallbackSafely()
  }

  fun retryPromptBankLoad() {
    bankRepo.retryLoad()
    if (bankRepo.isLoaded && _lyricsMap.value.isBlank()) {
      resetToPreserveDefaults()
    }
  }

  fun dismissAudioError() {
    _audioError.value = null
    audioPlayer.clearPlaybackError()
  }

  fun onMicrophonePermissionResult(granted: Boolean) {
    _micPermissionDenied.value = !granted
  }

  fun dismissMicPermissionDenied() {
    _micPermissionDenied.value = false
  }

  fun setTab(tab: StudioTab) {
    _currentTab.value = tab
    savedStateHandle[KEY_TAB] = tab.name
  }

  fun setJobType(job: JobType) {
    _jobType.value = job
    savedStateHandle[KEY_JOB_TYPE] = job.value
    if (job == JobType.PRESERVE) {
      setVariety(0)
      setWeirdness(25)
      setStyleInfluence(70)
      setAudioInfluence(80)
      ensureLockLineInStyle()
    } else {
      setVariety(0)
      setWeirdness(20)
      setStyleInfluence(65)
      setAudioInfluence(0)
      removeLockLineFromStyle()
    }
    _lyricsMap.value = bankRepo.buildV6LyricsMap(_activeLane.value, job)
    savedStateHandle[KEY_LYRICS_MAP] = _lyricsMap.value
    updateSimplePrompt()
  }

  fun setLane(lane: String) {
    _activeLane.value = lane
    savedStateHandle[KEY_LANE] = lane
    val drums = bankRepo.laneDrums(lane)
    if (_jobType.value == JobType.PRESERVE) {
      _stylePrompt.value = "$LOCK_LINE. Add $lane drums underneath only ($drums), spice only, sample stays lead."
    } else {
      _stylePrompt.value = "$lane instrumental, $drums, high energy, crisp mix."
    }
    savedStateHandle[KEY_STYLE_PROMPT] = _stylePrompt.value
    _lyricsMap.value = bankRepo.buildV6LyricsMap(lane, _jobType.value)
    savedStateHandle[KEY_LYRICS_MAP] = _lyricsMap.value
    updateSimplePrompt()
  }

  fun updateStylePrompt(text: String) {
    _stylePrompt.value = text
    savedStateHandle[KEY_STYLE_PROMPT] = text
    resetHandoffSteps()
  }

  fun updateLyricsMap(text: String) {
    _lyricsMap.value = text
    savedStateHandle[KEY_LYRICS_MAP] = text
    resetHandoffSteps()
  }

  fun updateExcludeText(text: String) {
    _excludeText.value = text
    savedStateHandle[KEY_EXCLUDE_TEXT] = text
    resetHandoffSteps()
  }

  fun updateSimplePrompt(text: String? = null) {
    if (text != null) {
      _simplePrompt.value = text
    } else {
      val lane = _activeLane.value
      val drums = bankRepo.laneDrums(lane)
      if (_jobType.value == JobType.PRESERVE) {
        _simplePrompt.value = """$LOCK_LINE. Turn this uploaded beat into a hard-hitting $lane canvas remix. Add $drums underneath only. Keep original sample and vocals 100% leading.
[Instrumental]
[Intro] (uploaded sample plays solo exactly as performed, canvas drums out)
[Verse 1] (canvas drums in only — $drums, sample untouched)
[Build-Up] (canvas riser and snare roll build into the hook)
[Chorus 1] (drop on bar 1 — canvas drums and 808 at peak spice weight, sample untouched)
[Breakdown] (all canvas drums out, sample plays solo untouched)
[Chorus 2] (heaviest canvas drop with full drum bounce)
[Outro] (canvas drums fade, sample plays out alone to a tape stop finish)
[End]"""
      } else {
        _simplePrompt.value = """High-energy $lane instrumental, $drums, heavy 808 bass, crisp percussion.
[Instrumental]
[Intro] (melodic hook establishes mood)
[Verse 1] (full drum groove enters — $drums)
[Build-Up] (snare roll builds, riser climbs)
[Chorus 1] (explosive drop, maximum drum and bass weight)
[Breakdown] (drums drop out, melody alone)
[Chorus 2] (final heaviest drop with full percussion)
[Outro] (drums fade to clean finish)
[End]"""
      }
    }
    savedStateHandle[KEY_SIMPLE_PROMPT] = _simplePrompt.value
  }

  fun setVariety(v: Int) {
    val clamped = v.coerceIn(0, 100)
    _variety.value = clamped
    savedStateHandle[KEY_VARIETY] = clamped
  }

  fun setWeirdness(w: Int) {
    val clamped = w.coerceIn(0, 100)
    _weirdness.value = clamped
    savedStateHandle[KEY_WEIRDNESS] = clamped
  }

  fun setStyleInfluence(s: Int) {
    val clamped = s.coerceIn(0, 100)
    _styleInfluence.value = clamped
    savedStateHandle[KEY_STYLE_INFLUENCE] = clamped
  }

  fun setAudioInfluence(a: Int) {
    val clamped = a.coerceIn(0, 100)
    _audioInfluence.value = clamped
    savedStateHandle[KEY_AUDIO_INFLUENCE] = clamped
  }

  fun selectExcludeBank(name: String) {
    val bank = bankRepo.excludeBanks.find { it.name.equals(name, ignoreCase = true) }
    if (bank != null) {
      _excludeText.value = bank.text
      savedStateHandle[KEY_EXCLUDE_TEXT] = bank.text
    }
  }

  fun injectSlotTemplate() {
    val templates = bankRepo.slotTemplates
    if (templates.isEmpty()) return
    val tmpl = templates.random()
    val drums = bankRepo.laneDrums(_activeLane.value)
    val replaced = tmpl.text
      .replace("[DRUM MOVE]", drums)
      .replace("[808 MOVE]", "tuned rolling 808 sub")
      .replace("[TEXTURE]", "analog warmth texture")
      .replace("[GENRE]", _activeLane.value)
    _stylePrompt.value = replaced
    if (_jobType.value == JobType.PRESERVE) ensureLockLineInStyle()
    savedStateHandle[KEY_STYLE_PROMPT] = _stylePrompt.value
    recordGeneratedPrompt(_stylePrompt.value, source = "Slot Inject")
  }

  fun randomizeAll() {
    val lanePool = bankRepo.allLanes.ifEmpty { bankRepo.expansionLanes.map { it.name } }
    val lane = lanePool.random()
    _activeLane.value = lane
    savedStateHandle[KEY_LANE] = lane
    val drums = bankRepo.laneDrums(lane)
    if (_jobType.value == JobType.PRESERVE) {
      _stylePrompt.value = "$LOCK_LINE. Add $lane drums underneath only ($drums), spice only, sample stays lead."
      setAudioInfluence(Random.nextInt(75, 86))
      setWeirdness(Random.nextInt(20, 31))
      setStyleInfluence(Random.nextInt(65, 76))
      setVariety(0)
    } else {
      _stylePrompt.value = "$lane instrumental, $drums, crisp modern mix."
      setAudioInfluence(0)
      setWeirdness(Random.nextInt(15, 26))
      setStyleInfluence(Random.nextInt(60, 71))
      setVariety(0)
    }
    savedStateHandle[KEY_STYLE_PROMPT] = _stylePrompt.value
    _lyricsMap.value = bankRepo.buildV6LyricsMap(lane, _jobType.value)
    savedStateHandle[KEY_LYRICS_MAP] = _lyricsMap.value
    if (bankRepo.excludeBanks.isNotEmpty()) {
      _excludeText.value = bankRepo.excludeBanks.random().text
      savedStateHandle[KEY_EXCLUDE_TEXT] = _excludeText.value
    }
    updateSimplePrompt()
    recordGeneratedPrompt(_stylePrompt.value, source = "Randomize", lane = lane)
  }

  private fun ensureLockLineInStyle() {
    if (!_stylePrompt.value.contains(LOCK_LINE)) {
      val currentBody = _stylePrompt.value.trimStart()
      _stylePrompt.value = if (currentBody.isBlank()) {
        "$LOCK_LINE. Add ${_activeLane.value} drums underneath only."
      } else {
        val prefix = "$LOCK_LINE. "
        val budget = (STYLE_LIMIT - prefix.length).coerceAtLeast(0)
        val clippedBody = if (currentBody.length <= budget) currentBody else currentBody.take(budget).trimEnd()
        if (clippedBody.isEmpty()) LOCK_LINE else "$prefix$clippedBody"
      }
      savedStateHandle[KEY_STYLE_PROMPT] = _stylePrompt.value
    }
  }

  private fun removeLockLineFromStyle() {
    _stylePrompt.value = _stylePrompt.value
      .replace("$LOCK_LINE. ", "")
      .replace(LOCK_LINE, "")
      .trim()
    savedStateHandle[KEY_STYLE_PROMPT] = _stylePrompt.value
  }

  fun resetToPreserveDefaults() {
    _jobType.value = JobType.PRESERVE
    _activeLane.value = "Trap"
    val drums = bankRepo.laneDrums("Trap")
    _stylePrompt.value = "$LOCK_LINE. Add Trap drums underneath only ($drums), spice only, sample stays lead."
    _lyricsMap.value = bankRepo.buildV6LyricsMap("Trap", JobType.PRESERVE)
    if (bankRepo.excludeBanks.isNotEmpty()) {
      _excludeText.value = bankRepo.excludeBanks[0].text
    }
    _variety.value = 0
    _weirdness.value = 25
    _styleInfluence.value = 70
    _audioInfluence.value = 80
    updateSimplePrompt()
    persistAllCoreState()
  }

  // Audio Actions
  fun startRecording() {
    if (_isRecording.value) return
    _audioError.value = null
    _micPermissionDenied.value = false
    analysisJob?.cancel()
    aiJob?.cancel()
    audioPlayer.stop()
    val started = audioRecorder.startRecording { peak, db ->
      _recordingPeak.value = peak
      _recordingDb.value = db
    }
    if (!started) {
      _audioError.value = "Unable to access microphone. Check audio permissions or close other recording apps."
      return
    }
    _isRecording.value = true
    rollingWaveformBuffer.fill(0.05f)
    _liveWaveform.value = rollingWaveformBuffer.copyOf()
    analysisJob = viewModelScope.launch {
      try {
        val samples = audioRecorder.recordAudio(maxDurationSec = 15) { peak, db, blockPeaks ->
          _recordingPeak.value = peak
          _recordingDb.value = db
          val shift = blockPeaks.size.coerceAtMost(rollingWaveformBuffer.size)
          if (shift > 0) {
            val remaining = rollingWaveformBuffer.size - shift
            if (remaining > 0) {
              System.arraycopy(rollingWaveformBuffer, shift, rollingWaveformBuffer, 0, remaining)
            }
            for (k in 0 until shift) {
              rollingWaveformBuffer[remaining + k] = blockPeaks[k].coerceIn(0.04f, 1f)
            }
            _liveWaveform.value = rollingWaveformBuffer.copyOf()
          }
        }
        _isRecording.value = false
        if (samples.isNotEmpty()) {
          audioPlayer.loadPcm(samples, 44100)
          _isAnalyzing.value = true
          val result = audioAnalyzer.analyzeRecordedSamples("Live Mic Recording", samples)
          _audioAnalysis.value = result
          val wavBytes = audioAnalyzer.encodeWav16KhzMono(samples, 44100, maxSeconds = 15)
          lastAnalyzedWavBytes = wavBytes
          _isAnalyzing.value = false
          val analyzedPrompt = if (_jobType.value == JobType.PRESERVE) result.promptPreserve else result.promptOriginal
          recordGeneratedPrompt(
            prompt = analyzedPrompt,
            source = "Analyze",
            lane = result.suggestedLane,
            bpm = result.bpm?.toInt()
          )
          aiJob = launch {
            runAiBeatConsultation(result, wavBytes)
          }
        } else {
          _audioError.value = "No audio frames were captured from the microphone."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _audioError.value = e.localizedMessage ?: "Audio recording analysis failed."
      } finally {
        _isRecording.value = false
        _isAnalyzing.value = false
      }
    }
  }

  fun stopRecording() {
    audioRecorder.stopRecording()
    _isRecording.value = false
  }

  fun analyzeFile(uri: Uri, fileName: String) {
    if (uri == Uri.EMPTY) {
      _audioError.value = "No valid audio file URI was provided."
      return
    }
    _audioError.value = null
    analysisJob?.cancel()
    aiJob?.cancel()
    analysisJob = viewModelScope.launch {
      audioPlayer.stop()
      _isAnalyzing.value = true
      try {
        val analyzedClip = audioAnalyzer.analyzeAudioUriWithClip(uri, fileName)
        val result = analyzedClip.result
        _audioAnalysis.value = result
        lastAnalyzedWavBytes = analyzedClip.multimodalWavBytes
        val fallbackMs = (result.durationSec * 1000.0).toLong().coerceAtLeast(1000L)
        audioPlayer.loadUri(getApplication(), uri, fallbackMs)
        _isAnalyzing.value = false
        val analyzedPrompt = if (_jobType.value == JobType.PRESERVE) result.promptPreserve else result.promptOriginal
        recordGeneratedPrompt(
          prompt = analyzedPrompt,
          source = "Analyze",
          lane = result.suggestedLane,
          bpm = result.bpm?.toInt()
        )
        aiJob = launch {
          runAiBeatConsultation(result, analyzedClip.multimodalWavBytes)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _audioError.value = e.localizedMessage ?: "Could not decode selected audio file."
      } finally {
        _isAnalyzing.value = false
      }
    }
  }

  fun retryAiConsultation() {
    val result = _audioAnalysis.value ?: return
    aiJob?.cancel()
    aiJob = viewModelScope.launch {
      runAiBeatConsultation(result, lastAnalyzedWavBytes)
    }
  }

  private suspend fun runAiBeatConsultation(result: AudioAnalysisResult, wavBytes: ByteArray?) {
    val env = environmentStatusProvider()
    if (!env.isOnline) {
      recordOfflineOrFirebasePreflightFailure(env, FailedCloudRequestType.BEAT_CONSULTATION)
      _aiConsultationState.value = AiConsultationUiState.Error(
        message = "Offline — reconnect to consult Cloud AI. Local DSP, 41 lanes, and Prompt Builder still work offline.",
        canRetry = true
      )
      return
    }
    if (!env.isFirebaseReady) {
      recordOfflineOrFirebasePreflightFailure(env, FailedCloudRequestType.BEAT_CONSULTATION)
      _aiConsultationState.value = AiConsultationUiState.Error(
        message = "Firebase AI service unavailable. Verify configuration and tap Retry.",
        canRetry = true
      )
      return
    }

    _aiConsultationState.value = AiConsultationUiState.Loading(
      if (wavBytes != null && wavBytes.isNotEmpty()) {
        "Listening to audio & consulting Gemini 3.8 Flash + Search Grounding..."
      } else {
        "Analyzing DSP metrics with Gemini 3.8 Flash + Search Grounding..."
      }
    )
    try {
      val consultation = coroutineScope {
        val coreDeferred = async {
          geminiService.consultBeatAnalyst(
            analysis = result,
            jobType = _jobType.value.value,
            audioBytes = wavBytes
          )
        }
        val groundedDeferred = async {
          try {
            geminiService.fetchGroundedBeatContext(result, _jobType.value.value)
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            null
          }
        }
        val core = coreDeferred.await()
        val grounded = groundedDeferred.await()
        if (grounded != null) {
          core.copy(
            searchGroundingSummary = grounded.answer.take(260),
            groundedSources = grounded.sources,
            groundedQueries = grounded.webSearchQueries
          )
        } else {
          core
        }
      }
      markCloudServicesHealthy()
      _aiConsultation.value = consultation
      _aiConsultationState.value = AiConsultationUiState.Success(consultation)

      if ((consultation.vocalConfidence == "Clear" || consultation.vocalConfidence == "Fragment") &&
        _audioAnalysis.value?.sampleWordsText == null
      ) {
        val current = _audioAnalysis.value
        if (current != null && !consultation.heardVocalText.contains("No intelligible", ignoreCase = true)) {
          _audioAnalysis.value = current.copy(
            sampleWordsText = consultation.heardVocalText.take(120),
            sampleWordsKind = if (consultation.vocalConfidence == "Clear") "Heard clearly" else "Fragment only"
          )
        }
      }

      val analyzedPrompt = if (_jobType.value == JobType.PRESERVE) result.promptPreserve else result.promptOriginal
      consultation.suggestions.firstOrNull()?.let { firstSug ->
        if (firstSug.prompt != analyzedPrompt) {
          recordGeneratedPrompt(
            prompt = firstSug.prompt,
            source = "Analyze",
            lane = result.suggestedLane,
            bpm = result.bpm?.toInt()
          )
        }
      }
    } catch (e: TimeoutCancellationException) {
      classifyAndRecordCloudFailure(e, FailedCloudRequestType.BEAT_CONSULTATION)
      _aiConsultationState.value = AiConsultationUiState.Error(
        message = "Gemini consultation timed out after 60 seconds. Please retry.",
        canRetry = true
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      classifyAndRecordCloudFailure(e, FailedCloudRequestType.BEAT_CONSULTATION)
      _aiConsultationState.value = AiConsultationUiState.Error(
        message = e.localizedMessage ?: "Firebase AI Logic request failed. Verify network/App Check and retry.",
        canRetry = true
      )
    }
  }

  fun startMicTranscription(target: TranscriptionTarget) {
    if (_isRecording.value) return
    _audioError.value = null
    _micPermissionDenied.value = false
    transcribeJob?.cancel()
    audioPlayer.stop()
    val started = audioRecorder.startRecording { peak, db ->
      _recordingPeak.value = peak
      _recordingDb.value = db
      _micTranscriptionState.value = TranscriptionUiState.Recording(target, db)
    }
    if (!started) {
      _micTranscriptionState.value = TranscriptionUiState.Error(
        message = "Microphone unavailable or permission denied.",
        target = target,
        canRetry = false
      )
      return
    }
    _isRecording.value = true
    lastTranscriptionTarget = target
    _micTranscriptionState.value = TranscriptionUiState.Recording(target, -60f)

    transcribeJob = viewModelScope.launch {
      try {
        val samples = audioRecorder.recordAudio(maxDurationSec = 12) { peak, db, _ ->
          _recordingPeak.value = peak
          _recordingDb.value = db
          _micTranscriptionState.value = TranscriptionUiState.Recording(target, db)
        }
        _isRecording.value = false
        if (samples.isEmpty()) {
          _micTranscriptionState.value = TranscriptionUiState.Error(
            message = "No audio captured from microphone.",
            target = target,
            canRetry = true
          )
          return@launch
        }
        val wavBytes = audioAnalyzer.encodeWav16KhzMono(samples, 44100, maxSeconds = 12)
        lastTranscriptionWavBytes = wavBytes
        executeTranscription(wavBytes, target)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _micTranscriptionState.value = TranscriptionUiState.Error(
          message = e.localizedMessage ?: "Microphone capture failed.",
          target = target,
          canRetry = true
        )
      } finally {
        _isRecording.value = false
      }
    }
  }

  fun stopMicTranscription() {
    audioRecorder.stopRecording()
    _isRecording.value = false
  }

  fun retryLastTranscription() {
    val wavBytes = lastTranscriptionWavBytes ?: return
    val target = lastTranscriptionTarget
    transcribeJob?.cancel()
    transcribeJob = viewModelScope.launch {
      executeTranscription(wavBytes, target)
    }
  }

  fun dismissTranscriptionState() {
    if (!_isRecording.value) {
      _micTranscriptionState.value = TranscriptionUiState.Idle
    }
  }

  private suspend fun executeTranscription(wavBytes: ByteArray, target: TranscriptionTarget) {
    val env = environmentStatusProvider()
    if (!env.isOnline) {
      recordOfflineOrFirebasePreflightFailure(env, FailedCloudRequestType.MIC_TRANSCRIPTION)
      _micTranscriptionState.value = TranscriptionUiState.Error(
        message = "Offline — reconnect to transcribe audio with Gemini. Saved audio is kept for Retry.",
        target = target,
        canRetry = true
      )
      return
    }
    if (!env.isFirebaseReady) {
      recordOfflineOrFirebasePreflightFailure(env, FailedCloudRequestType.MIC_TRANSCRIPTION)
      _micTranscriptionState.value = TranscriptionUiState.Error(
        message = "Firebase AI service unavailable. Tap Retry when configured.",
        target = target,
        canRetry = true
      )
      return
    }

    _micTranscriptionState.value = TranscriptionUiState.Transcribing(target)
    try {
      val transcript = geminiService.transcribeMicrophoneAudio(wavBytes, target)
      markCloudServicesHealthy()
      _micTranscriptionState.value = TranscriptionUiState.Success(transcript, target)
      if (target == TranscriptionTarget.ANALYZE_SAMPLE_WORDS &&
        !transcript.contains("No intelligible", ignoreCase = true)
      ) {
        val kind = if (transcript.length > 18) "Heard clearly" else "Fragment only"
        setSampleWords(transcript.take(120), kind)
      }
    } catch (e: TimeoutCancellationException) {
      classifyAndRecordCloudFailure(e, FailedCloudRequestType.MIC_TRANSCRIPTION)
      _micTranscriptionState.value = TranscriptionUiState.Error(
        message = "gemini-3.5-transcribe timed out after 60s. Tap Retry.",
        target = target,
        canRetry = true
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      classifyAndRecordCloudFailure(e, FailedCloudRequestType.MIC_TRANSCRIPTION)
      _micTranscriptionState.value = TranscriptionUiState.Error(
        message = e.localizedMessage ?: "Audio transcription failed.",
        target = target,
        canRetry = true
      )
    }
  }

  fun applyTranscriptToLyrics(transcript: String, context: Context? = null) {
    val clean = transcript.trim()
    if (clean.isEmpty()) return
    val current = _lyricsMap.value.trimEnd()
    val hasEnd = current.endsWith("[End]")
    val body = if (hasEnd) current.removeSuffix("[End]").trimEnd() else current
    val inserted = buildString {
      if (body.isNotBlank()) {
        append(body)
        append("\n")
      }
      append("[Hook (Dictated)] ")
      append(clean)
      append("\n[End]")
    }.take(LYRICS_LIMIT)
    val finalMap = if (inserted.trimEnd().endsWith("[End]")) inserted else {
      inserted.take((LYRICS_LIMIT - 6).coerceAtLeast(0)).trimEnd() + "\n[End]"
    }
    updateLyricsMap(finalMap)
    if (context != null) {
      Toast.makeText(context, "Inserted transcribed hook into Lyrics Box", Toast.LENGTH_SHORT).show()
    }
  }

  fun applyTranscriptToStyle(transcript: String, context: Context? = null) {
    val clean = transcript.trim()
    if (clean.isEmpty()) return
    val current = _stylePrompt.value.trim()
    val combined = if (current.isBlank()) clean else "$current, $clean"
    val finalStyle = if (_jobType.value == JobType.PRESERVE && !combined.contains(LOCK_LINE)) {
      val prefix = "$LOCK_LINE. "
      val budget = (STYLE_LIMIT - prefix.length).coerceAtLeast(0)
      val body = if (combined.length <= budget) combined else combined.take(budget).trimEnd()
      if (body.isEmpty()) LOCK_LINE else "$prefix$body"
    } else {
      combined.take(STYLE_LIMIT)
    }
    updateStylePrompt(finalStyle)
    if (context != null) {
      Toast.makeText(context, "Added voice note to Style Prompt", Toast.LENGTH_SHORT).show()
    }
  }

  fun searchSunoKnowledge(query: String) {
    val clean = query.trim()
    if (clean.isEmpty()) return
    lastSearchQuery = clean
    searchJob?.cancel()
    val env = environmentStatusProvider()
    if (!env.isOnline) {
      recordOfflineOrFirebasePreflightFailure(env, FailedCloudRequestType.SEARCH_GROUNDING)
      _searchGroundingState.value = SearchGroundingUiState.Error(
        message = "Offline — reconnect to run Google Search Grounding, then tap Retry.",
        query = clean
      )
      return
    }
    if (!env.isFirebaseReady) {
      recordOfflineOrFirebasePreflightFailure(env, FailedCloudRequestType.SEARCH_GROUNDING)
      _searchGroundingState.value = SearchGroundingUiState.Error(
        message = "Firebase AI service unavailable. Tap Retry when configured.",
        query = clean
      )
      return
    }

    searchJob = viewModelScope.launch {
      _searchGroundingState.value = SearchGroundingUiState.Loading(clean)
      try {
        val result = geminiService.researchWithGoogleSearch(clean)
        markCloudServicesHealthy()
        _searchGroundingState.value = SearchGroundingUiState.Success(result)
      } catch (e: TimeoutCancellationException) {
        classifyAndRecordCloudFailure(e, FailedCloudRequestType.SEARCH_GROUNDING)
        _searchGroundingState.value = SearchGroundingUiState.Error(
          message = "Search grounding timed out after 60s. Tap Retry.",
          query = clean
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        classifyAndRecordCloudFailure(e, FailedCloudRequestType.SEARCH_GROUNDING)
        _searchGroundingState.value = SearchGroundingUiState.Error(
          message = e.localizedMessage ?: "Google Search grounding request failed.",
          query = clean
        )
      }
    }
  }

  fun clearSearchGrounding() {
    searchJob?.cancel()
    _searchGroundingState.value = SearchGroundingUiState.Idle
  }

  fun currentComplianceSnapshot(): ComplianceSnapshot {
    return ComplianceSnapshot(
      jobType = _jobType.value,
      stylePrompt = _stylePrompt.value,
      lyricsMap = _lyricsMap.value,
      excludeText = _excludeText.value,
      variety = _variety.value,
      weirdness = _weirdness.value,
      styleInfluence = _styleInfluence.value,
      audioInfluence = _audioInfluence.value
    )
  }

  fun autoFixComplianceRule(ruleId: ComplianceRuleId, context: Context? = null) {
    val before = currentComplianceSnapshot()
    val fixed = SunoComplianceAuditor.autoFixRule(ruleId, before, _activeLane.value)
    if (fixed == before) return
    val undoWithSummary = before.copy(fixSummary = fixed.fixSummary)
    _complianceUndoSnapshot.value = undoWithSummary
    savedStateHandle[KEY_COMPLIANCE_UNDO] = undoWithSummary.toJson()
    applyComplianceSnapshot(fixed)
    if (context != null && fixed.fixSummary.isNotBlank()) {
      Toast.makeText(context, fixed.fixSummary, Toast.LENGTH_SHORT).show()
    }
  }

  fun autoFixAllCompliance(context: Context? = null) {
    val before = currentComplianceSnapshot()
    val fixed = SunoComplianceAuditor.autoFixAll(before, _activeLane.value)
    if (fixed == before) return
    val undoWithSummary = before.copy(fixSummary = fixed.fixSummary)
    _complianceUndoSnapshot.value = undoWithSummary
    savedStateHandle[KEY_COMPLIANCE_UNDO] = undoWithSummary.toJson()
    applyComplianceSnapshot(fixed)
    if (context != null && fixed.fixSummary.isNotBlank()) {
      Toast.makeText(context, fixed.fixSummary, Toast.LENGTH_SHORT).show()
    }
  }

  fun undoLastComplianceFix(context: Context? = null) {
    val prev = _complianceUndoSnapshot.value ?: return
    applyComplianceSnapshot(prev)
    _complianceUndoSnapshot.value = null
    savedStateHandle.remove<String>(KEY_COMPLIANCE_UNDO)
    if (context != null) {
      Toast.makeText(context, "Reverted compliance auto-fix", Toast.LENGTH_SHORT).show()
    }
  }

  fun dismissComplianceUndo() {
    _complianceUndoSnapshot.value = null
    savedStateHandle.remove<String>(KEY_COMPLIANCE_UNDO)
  }

  private fun applyComplianceSnapshot(snapshot: ComplianceSnapshot) {
    _jobType.value = snapshot.jobType
    _stylePrompt.value = snapshot.stylePrompt
    _lyricsMap.value = snapshot.lyricsMap
    _excludeText.value = snapshot.excludeText
    _variety.value = snapshot.variety
    _weirdness.value = snapshot.weirdness
    _styleInfluence.value = snapshot.styleInfluence
    _audioInfluence.value = snapshot.audioInfluence
    updateSimplePrompt()
    resetHandoffSteps()
    persistAllCoreState()
  }

  fun loadTakeIntoBuild(take: TakeEntity, context: Context? = null) {
    val resolvedJobType = JobType.fromString(take.jobType)
    _jobType.value = resolvedJobType
    if (take.lane.isNotBlank() && bankRepo.allLanes.contains(take.lane)) {
      _activeLane.value = take.lane
    }
    _stylePrompt.value = take.style.take(STYLE_LIMIT)
    _excludeText.value = take.excludeText.take(EXCLUDE_LIMIT)
    _lyricsMap.value = take.lyrics.ifBlank { bankRepo.buildV6LyricsMap(_activeLane.value, resolvedJobType) }.take(LYRICS_LIMIT)
    if (resolvedJobType == JobType.PRESERVE) {
      _variety.value = 0
      _audioInfluence.value = 80
    } else {
      _variety.value = 0
      _audioInfluence.value = 0
    }
    updateSimplePrompt()
    resetHandoffSteps()
    setTab(StudioTab.BUILD)
    persistAllCoreState()
    if (context != null) {
      Toast.makeText(context, "Loaded take '${take.name}' into Build", Toast.LENGTH_SHORT).show()
    }
  }

  fun togglePlayPauseAudio() {
    if (_isRecording.value) return
    audioPlayer.togglePlayPause()
  }

  fun stopAudioPlayback() {
    audioPlayer.stop()
  }

  fun seekAudioToFraction(fraction: Float) {
    if (_isRecording.value) return
    audioPlayer.seekToFraction(fraction)
  }

  fun beginAudioScrub(initialFraction: Float) {
    if (_isRecording.value) return
    audioPlayer.beginScrub(initialFraction)
  }

  fun previewAudioScrub(fraction: Float) {
    if (_isRecording.value) return
    audioPlayer.previewScrub(fraction)
  }

  fun endAudioScrub(fraction: Float) {
    if (_isRecording.value) return
    audioPlayer.endScrub(fraction)
  }

  fun toggleAudioLoop() {
    audioPlayer.toggleLoop()
  }

  fun applyBpmMatchedLanePreset(lane: String, overrideBpm: Double? = null) {
    _activeLane.value = lane
    savedStateHandle[KEY_LANE] = lane
    val drums = bankRepo.laneDrums(lane)
    val analyzed = _audioAnalysis.value
    val bpmValue = overrideBpm ?: analyzed?.bpm
    val bpmTag = bpmValue?.toInt()?.let { " at $it BPM" } ?: ""
    val keyTag = analyzed?.keyName?.takeIf { it.isNotBlank() }?.let { " in $it" } ?: ""

    if (_jobType.value == JobType.PRESERVE) {
      _stylePrompt.value = "$LOCK_LINE. Add $lane drums underneath only ($drums)$bpmTag$keyTag, spice only, sample stays lead."
      setVariety(0)
      setWeirdness(25)
      setStyleInfluence(70)
      setAudioInfluence(80)
    } else {
      _stylePrompt.value = "$lane instrumental$bpmTag$keyTag, $drums, high energy, crisp mix."
      setVariety(0)
      setWeirdness(20)
      setStyleInfluence(65)
      setAudioInfluence(0)
    }
    savedStateHandle[KEY_STYLE_PROMPT] = _stylePrompt.value
    _lyricsMap.value = bankRepo.buildV6LyricsMap(lane, _jobType.value)
    savedStateHandle[KEY_LYRICS_MAP] = _lyricsMap.value
    updateSimplePrompt()
    resetHandoffSteps()
    recordGeneratedPrompt(
      prompt = _stylePrompt.value,
      source = "Preset",
      lane = lane,
      bpm = bpmValue?.toInt()
    )
  }

  fun adjustBpmMultiplier(multiplier: Double) {
    val current = _audioAnalysis.value ?: return
    if (multiplier.isNaN() || multiplier.isInfinite() || multiplier <= 0.0) return
    val curBpm = current.bpm ?: 130.0
    val newBpm = (curBpm * multiplier).coerceIn(40.0, 260.0)
    val roundedBpm = kotlin.math.round(newBpm * 10.0) / 10.0
    val lowTag = if (current.lowShare > 0.36) "Sub-heavy" else "Balanced"
    _audioAnalysis.value = current.copy(
      bpm = roundedBpm,
      pocketLabel = "${roundedBpm.toInt()} BPM · ${if (current.centroidHz > 2800) "Bright" else "Dark"} · $lowTag"
    )
  }

  fun setSampleWords(words: String?, kind: String = "Heard clearly") {
    val current = _audioAnalysis.value ?: return
    val trimmed = words?.trim()?.take(120)
    val updated = if (trimmed.isNullOrBlank()) {
      current.copy(sampleWordsText = null, sampleWordsKind = null)
    } else {
      current.copy(sampleWordsText = trimmed, sampleWordsKind = kind)
    }
    _audioAnalysis.value = updated
  }

  fun sendAnalysisToBuild() {
    val a = _audioAnalysis.value ?: return
    setLane(a.suggestedLane)
    if (_jobType.value == JobType.PRESERVE) {
      _stylePrompt.value = a.promptPreserve
    } else {
      _stylePrompt.value = a.promptOriginal
    }
    savedStateHandle[KEY_STYLE_PROMPT] = _stylePrompt.value
    recordGeneratedPrompt(
      prompt = _stylePrompt.value,
      source = "Send-to-Build",
      lane = a.suggestedLane,
      bpm = a.bpm?.toInt()
    )
    setTab(StudioTab.BUILD)
  }

  fun sendAnalysisToSimple() {
    val a = _audioAnalysis.value ?: return
    setLane(a.suggestedLane)
    _simplePrompt.value = if (_jobType.value == JobType.PRESERVE) {
      """${a.promptPreserve}
[Instrumental]
[Intro] (uploaded sample plays solo exactly as performed)
[Verse 1] (canvas drums enter under sample — ${bankRepo.laneDrums(a.suggestedLane)})
[Chorus 1] (full canvas drop at ${a.bpm?.toInt() ?: 130} BPM, 808 weight)
[Breakdown] (sample plays solo untouched)
[Chorus 2] (heaviest drop, full bounce)
[Outro] (canvas strips back to sample solo finish)
[End]"""
    } else {
      """${a.promptOriginal}
[Instrumental]
[Intro] (main melodic motif establishes atmosphere)
[Verse 1] (full groove enters at ${a.bpm?.toInt() ?: 130} BPM)
[Chorus 1] (maximum hook energy, punchy 808)
[Breakdown] (drums out, melody only)
[Chorus 2] (final heaviest drop)
[Outro] (fade to clean finish)
[End]"""
    }
    savedStateHandle[KEY_SIMPLE_PROMPT] = _simplePrompt.value
    setTab(StudioTab.SIMPLE)
  }

  // Takes & Persistence Actions
  fun saveCurrentTake(
    takeName: String,
    beatTitle: String = "",
    audioFilePath: String? = null,
    context: Context
  ) {
    viewModelScope.launch {
      val name = if (takeName.isNotBlank()) takeName else "${_activeLane.value} Take #${System.currentTimeMillis() % 1000}"
      val title = if (beatTitle.isNotBlank()) beatTitle else (_audioAnalysis.value?.fileName ?: name)
      val currentBpm = _audioAnalysis.value?.bpm ?: 0.0
      val settingsStr = "Model v6 · Route: ${if (_jobType.value == JobType.PRESERVE) "Extend" else "Create"} · Variety ${_variety.value} · Weirdness ${_weirdness.value} · Style ${_styleInfluence.value} · Audio ${_audioInfluence.value}"

      val metadataJson = org.json.JSONObject().apply {
        _audioAnalysis.value?.let { a ->
          put("keyName", a.keyName ?: "Unread")
          put("keyClear", a.keyClear)
          put("rmsDb", a.rmsDb)
          put("peakDb", a.peakDb)
          put("lowShare", a.lowShare)
          put("centroidHz", a.centroidHz)
          put("onsetRate", a.onsetRate)
          put("suggestedLane", a.suggestedLane)
        }
        _aiConsultation.value?.let { c ->
          put("vocalText", c.heardVocalText)
          put("vocalConfidence", c.vocalConfidence)
          put("tacticalAdvice", c.tacticalAdvice)
        }
      }.toString()

      val take = TakeEntity(
        beatTitle = title,
        bpm = currentBpm,
        audioFilePath = audioFilePath ?: _audioAnalysis.value?.fileName,
        analysisMetadata = metadataJson,
        name = name,
        lane = _activeLane.value,
        jobType = _jobType.value.value,
        style = _stylePrompt.value,
        excludeText = _excludeText.value,
        lyrics = _lyricsMap.value,
        settings = settingsStr,
        result = "pending"
      )
      takeRepo.insertTake(take)
      Toast.makeText(context, "Take saved to Logbook with audio metadata!", Toast.LENGTH_SHORT).show()
    }
  }

  fun updateTakeResult(id: Long, result: String) {
    viewModelScope.launch {
      takeRepo.updateResult(id, result)
    }
  }

  fun updateTakeNextChange(id: Long, nextChange: String) {
    viewModelScope.launch {
      takeRepo.updateNextChange(id, nextChange)
    }
  }

  fun duplicateTake(id: Long) {
    viewModelScope.launch {
      takeRepo.duplicateTake(id)
    }
  }

  fun deleteTake(id: Long) {
    viewModelScope.launch {
      takeRepo.deleteTake(id)
    }
  }

  fun setTakeFilterResult(filter: String) {
    _takeFilterResult.value = filter
    savedStateHandle[KEY_TAKE_FILTER] = filter
  }

  fun setLibraryQuery(q: String) {
    _libraryQuery.value = q
    savedStateHandle[KEY_LIBRARY_QUERY] = q
  }

  fun setLibraryFilterTrack(track: String) {
    _libraryFilterTrack.value = track
    savedStateHandle[KEY_LIBRARY_TRACK] = track
  }

  fun setLibraryFilterLane(lane: String) {
    _libraryFilterLane.value = lane
    savedStateHandle[KEY_LIBRARY_LANE] = lane
  }

  fun setTemplateSearchQuery(query: String) {
    _templateSearchQuery.value = query
    savedStateHandle[KEY_TEMPLATE_QUERY] = query
  }

  fun setTemplateCategoryFilter(category: String) {
    _templateCategoryFilter.value = category
    savedStateHandle[KEY_TEMPLATE_CATEGORY] = category
  }

  fun applyPromptTemplate(
    template: PromptTemplateEntity,
    context: Context? = null,
    switchToBuild: Boolean = true
  ) {
    val resolvedJob = JobType.fromString(template.jobType)
    _jobType.value = resolvedJob
    if (template.lane.isNotBlank()) {
      _activeLane.value = template.lane
    }
    _stylePrompt.value = template.stylePrompt.take(STYLE_LIMIT)
    _lyricsMap.value = template.lyricsMap.ifBlank {
      bankRepo.buildV6LyricsMap(_activeLane.value, resolvedJob)
    }.take(LYRICS_LIMIT)
    _excludeText.value = template.excludeText.take(EXCLUDE_LIMIT)
    _variety.value = template.variety.coerceIn(0, 100)
    _weirdness.value = template.weirdness.coerceIn(0, 100)
    _styleInfluence.value = template.styleInfluence.coerceIn(0, 100)
    _audioInfluence.value = template.audioInfluence.coerceIn(0, 100)
    _complianceUndoSnapshot.value = null
    savedStateHandle.remove<String>(KEY_COMPLIANCE_UNDO)
    updateSimplePrompt()
    resetHandoffSteps()
    persistAllCoreState()
    if (switchToBuild) {
      setTab(StudioTab.BUILD)
    }
    if (template.id > 0L) {
      viewModelScope.launch(Dispatchers.IO) {
        promptTemplateRepo.recordUsage(template.id)
      }
    }
    if (context != null) {
      Toast.makeText(context, "Applied template '${template.title}'", Toast.LENGTH_SHORT).show()
    }
  }

  fun applyStylePromptFromBank(prompt: StylePrompt, context: Context? = null) {
    val resolvedJob = when (prompt.track) {
      2 -> JobType.PRESERVE
      1 -> JobType.ORIGINAL
      else -> _jobType.value
    }
    _jobType.value = resolvedJob
    if (prompt.subgenre.isNotBlank() && bankRepo.allLanes.contains(prompt.subgenre)) {
      _activeLane.value = prompt.subgenre
    }
    val appliedStyle = if (resolvedJob == JobType.PRESERVE && !prompt.text.contains(LOCK_LINE)) {
      "$LOCK_LINE. ${prompt.text}"
    } else {
      prompt.text
    }
    _stylePrompt.value = appliedStyle.take(STYLE_LIMIT)
    _lyricsMap.value = bankRepo.buildV6LyricsMap(_activeLane.value, resolvedJob)
    if (resolvedJob == JobType.PRESERVE) {
      _audioInfluence.value = 80
    } else if (prompt.track == 1) {
      _audioInfluence.value = 0
    }
    updateSimplePrompt()
    resetHandoffSteps()
    persistAllCoreState()
    setTab(StudioTab.BUILD)
    if (context != null) {
      Toast.makeText(context, "Applied ${prompt.subgenre} prompt to Build", Toast.LENGTH_SHORT).show()
    }
  }

  fun saveCurrentAsTemplate(
    title: String,
    category: String,
    bpmHint: String = "",
    context: Context? = null
  ) {
    val cleanTitle = title.trim().ifEmpty { "${_activeLane.value} Preset" }
    val cleanCategory = category.trim().ifEmpty { _activeLane.value.ifBlank { "Custom" } }
    val resolvedBpm = bpmHint.trim().ifEmpty {
      _audioAnalysis.value?.bpm?.let { "${it.toInt()} BPM" } ?: ""
    }
    viewModelScope.launch(Dispatchers.IO) {
      promptTemplateRepo.saveTemplate(
        title = cleanTitle,
        category = cleanCategory,
        lane = _activeLane.value,
        jobType = _jobType.value,
        bpmHint = resolvedBpm,
        stylePrompt = _stylePrompt.value,
        lyricsMap = _lyricsMap.value,
        excludeText = _excludeText.value,
        variety = _variety.value,
        weirdness = _weirdness.value,
        styleInfluence = _styleInfluence.value,
        audioInfluence = _audioInfluence.value
      )
    }
    if (context != null) {
      Toast.makeText(context, "Saved '$cleanTitle' to Templates", Toast.LENGTH_SHORT).show()
    }
  }

  fun toggleTemplatePinned(template: PromptTemplateEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      promptTemplateRepo.setPinned(template.id, !template.isPinned)
    }
  }

  fun deletePromptTemplate(template: PromptTemplateEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      promptTemplateRepo.deleteById(template.id)
    }
  }

  fun restoreStarterTemplates(context: Context? = null) {
    viewModelScope.launch(Dispatchers.IO) {
      promptTemplateRepo.restoreStarterTemplates()
    }
    if (context != null) {
      Toast.makeText(context, "Restored starter Suno templates", Toast.LENGTH_SHORT).show()
    }
  }

  fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
  }

  fun copyFullBundle(context: Context) {
    val isPreserve = _jobType.value == JobType.PRESERVE
    val bundle = """=== BOODA BEAT ENHANCER · SUNO V6 BUNDLE ===
Route: ${if (isPreserve) "Extend (Upload Leading)" else "Create (Original Track)"}
Model: Model v6
Variety: ${_variety.value}
Weirdness: ${_weirdness.value}
Style Influence: ${_styleInfluence.value}
Audio Influence: ${_audioInfluence.value}

--- STYLE PROMPT (${_stylePrompt.value.length} / 1000 chars) ---
${_stylePrompt.value}

--- EXCLUDE STYLES (${_excludeText.value.length} / 1000 chars) ---
${_excludeText.value}

--- LYRICS MAP (${_lyricsMap.value.length} / 5000 chars) ---
${_lyricsMap.value}
=============================================""".trimIndent()
    copyToClipboard(context, "Suno Prompt Bundle", bundle)
  }

  fun setCuratedSearchQuery(q: String) {
    _curatedSearchQuery.value = q
    savedStateHandle[KEY_CURATED_QUERY] = q
  }

  fun setCuratedCategory(cat: String) {
    _curatedCategory.value = cat
    savedStateHandle[KEY_CURATED_CATEGORY] = cat
  }

  fun clearCuratedFilters() {
    _curatedSearchQuery.value = ""
    _curatedCategory.value = "All"
    savedStateHandle[KEY_CURATED_QUERY] = ""
    savedStateHandle[KEY_CURATED_CATEGORY] = "All"
  }

  fun toggleFavorite(prompt: CuratedSunoPrompt, onResult: ((isSaved: Boolean, removedEntity: FavoritePromptEntity?) -> Unit)? = null) {
    viewModelScope.launch {
      val isFav = favoriteIds.value.contains(prompt.id.toLong())
      if (isFav) {
        val existing = favoriteRepo.getFavoriteById(prompt.id.toLong())
        _lastDeletedFavorite.value = existing
        favoriteRepo.deleteFavorite(prompt.id.toLong())
        onResult?.invoke(false, existing)
      } else {
        favoriteRepo.insertFavorite(prompt)
        onResult?.invoke(true, null)
      }
    }
  }

  fun undoRemoveFavorite() {
    viewModelScope.launch {
      val removed = _lastDeletedFavorite.value ?: return@launch
      favoriteRepo.insertFavoriteEntity(removed)
      _lastDeletedFavorite.value = null
    }
  }

  fun recordGeneratedPrompt(
    prompt: String,
    source: String,
    lane: String = _activeLane.value,
    jobType: JobType = _jobType.value,
    bpm: Int? = _audioAnalysis.value?.bpm?.toInt()
  ) {
    if (prompt.isBlank()) return
    viewModelScope.launch {
      historyRepo.recordPrompt(
        prompt = prompt,
        source = source,
        lane = lane,
        jobType = jobType,
        bpm = bpm
      )
    }
  }

  fun addRecentPrompt(
    prompt: String,
    source: String = "Builder",
    lane: String = _activeLane.value
  ) {
    recordGeneratedPrompt(prompt = prompt, source = source, lane = lane)
  }

  fun recallPromptVariation(
    entry: PromptHistoryEntity,
    navigateToBuild: Boolean = false,
    context: Context? = null
  ) {
    val resolvedJobType = JobType.fromString(entry.jobType)
    _jobType.value = resolvedJobType
    if (entry.lane.isNotBlank() && bankRepo.allLanes.contains(entry.lane)) {
      _activeLane.value = entry.lane
    }
    _stylePrompt.value = entry.prompt
    _lyricsMap.value = bankRepo.buildV6LyricsMap(_activeLane.value, resolvedJobType)
    updateSimplePrompt()
    resetHandoffSteps()
    if (navigateToBuild) {
      setTab(StudioTab.BUILD)
    }
    persistAllCoreState()
    if (context != null) {
      Toast.makeText(
        context,
        "Loaded ${entry.source} variation (${entry.lane}) into Build",
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  fun deletePromptHistoryEntry(id: Long) {
    viewModelScope.launch {
      historyRepo.deleteEntry(id)
    }
  }

  fun clearPromptHistory() {
    viewModelScope.launch {
      historyRepo.clearHistory()
    }
  }

  fun copyPromptHistoryToClipboard(context: Context, includeSnapshot: Boolean = true) {
    viewModelScope.launch {
      val entries = historyRepo.getRecentSnapshot()
      val snapshot = if (includeSnapshot) currentComplianceSnapshot() else null
      if (entries.isEmpty() && snapshot == null) {
        Toast.makeText(context, "No prompt history entries to export.", Toast.LENGTH_SHORT).show()
        return@launch
      }
      val json = historyRepo.exportHistoryJson(entries, snapshot)
      copyToClipboard(context, "Prompt History JSON Backup", json)
    }
  }

  fun exportPromptHistoryToUri(context: Context, uri: Uri, includeSnapshot: Boolean = true) {
    viewModelScope.launch {
      val entries = historyRepo.getRecentSnapshot()
      val snapshot = if (includeSnapshot) currentComplianceSnapshot() else null
      val json = historyRepo.exportHistoryJson(entries, snapshot)
      val writeError: String? = withContext(Dispatchers.IO) {
        try {
          val stream = context.contentResolver.openOutputStream(uri)
          if (stream == null) {
            "Unable to open destination file."
          } else {
            stream.use { out ->
              out.write(json.toByteArray(Charsets.UTF_8))
              out.flush()
            }
            null
          }
        } catch (e: Exception) {
          e.localizedMessage ?: "Failed writing backup file."
        }
      }
      if (writeError == null) {
        Toast.makeText(context, "Saved ${entries.size} prompt(s) to JSON file", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(context, writeError, Toast.LENGTH_LONG).show()
      }
    }
  }

  fun validateHistoryBackupSchema(jsonStr: String): HistorySchemaValidationResult {
    return historyRepo.validateHistorySchema(jsonStr)
  }

  fun importPromptHistoryFromJson(
    context: Context,
    jsonStr: String,
    restoreSnapshot: Boolean = true,
    mode: HistoryImportMode = HistoryImportMode.MERGE
  ) {
    viewModelScope.launch {
      val result = historyRepo.importHistoryJson(jsonStr, mode = mode)
      if (restoreSnapshot && result.restoredSnapshot != null) {
        val before = currentComplianceSnapshot().copy(fixSummary = "Imported Backup Snapshot")
        _complianceUndoSnapshot.value = before
        savedStateHandle[KEY_COMPLIANCE_UNDO] = before.toJson()
        applyComplianceSnapshot(result.restoredSnapshot)
      }
      val modeWord = if (mode == HistoryImportMode.MERGE) "Merged" else "Replaced with"
      val msg = when {
        result.errorMessage != null && result.importedCount == 0 && result.restoredSnapshot == null ->
          result.errorMessage
        result.skippedCount > 0 ->
          "$modeWord ${result.importedCount} prompt(s) (${result.skippedCount} skipped)"
        result.restoredSnapshot != null && result.importedCount == 0 ->
          "Restored Studio Compliance Snapshot"
        result.restoredSnapshot != null ->
          "$modeWord ${result.importedCount} prompt(s) & restored snapshot"
        else ->
          "$modeWord ${result.importedCount} prompt(s) into Recent 20"
      }
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }

  fun importPromptHistoryFromUri(
    context: Context,
    uri: Uri,
    restoreSnapshot: Boolean = true,
    mode: HistoryImportMode = HistoryImportMode.MERGE
  ) {
    viewModelScope.launch {
      val readResult = withContext(Dispatchers.IO) {
        try {
          context.contentResolver.openInputStream(uri)?.use { stream ->
            val maxBytes = 2 * 1024 * 1024
            val bytes = stream.readNBytes(maxBytes)
            Pair(String(bytes, Charsets.UTF_8), null as String?)
          } ?: Pair("", "Unable to read selected JSON file.")
        } catch (e: Exception) {
          Pair("", e.localizedMessage ?: "Failed to open selected file.")
        }
      }
      val error = readResult.second
      if (error != null) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        return@launch
      }
      importPromptHistoryFromJson(context, readResult.first, restoreSnapshot, mode)
    }
  }

  fun resetHandoffSteps() {
    _handoffStepsDone.value = emptySet()
    savedStateHandle[KEY_HANDOFF_STEPS] = intArrayOf()
  }

  fun copyHandoffStep(context: Context, step: Int) {
    val isPreserve = _jobType.value == JobType.PRESERVE
    when (step) {
      1 -> {
        val routeText = if (isPreserve) "Route: Extend | Audio Influence: ${_audioInfluence.value}" else "Route: Create (Standard)"
        copyToClipboard(context, "Route Guidance", routeText)
      }
      2 -> copyToClipboard(context, "Style Prompt", _stylePrompt.value)
      3 -> copyToClipboard(context, "Exclude Styles", _excludeText.value)
      4 -> copyToClipboard(context, "Lyrics Map", _lyricsMap.value)
      5 -> {
        val settingsText = "Model v6 · Variety ${_variety.value} · Weirdness ${_weirdness.value} · Style Influence ${_styleInfluence.value} · Audio Influence ${_audioInfluence.value}"
        copyToClipboard(context, "Suno Settings", settingsText)
      }
    }
    val next = _handoffStepsDone.value + step
    _handoffStepsDone.value = next
    savedStateHandle[KEY_HANDOFF_STEPS] = next.toIntArray()
  }

  fun toggleHandoffStep(step: Int) {
    val current = _handoffStepsDone.value
    val next = if (current.contains(step)) current - step else current + step
    _handoffStepsDone.value = next
    savedStateHandle[KEY_HANDOFF_STEPS] = next.toIntArray()
  }

  fun loadFullPreservePackageFromAnalysis() {
    val a = _audioAnalysis.value ?: return
    _jobType.value = JobType.PRESERVE
    setLane(a.suggestedLane)
    _stylePrompt.value = a.promptPreserve
    _lyricsMap.value = bankRepo.buildV6LyricsMap(a.suggestedLane, JobType.PRESERVE)
    if (bankRepo.excludeBanks.isNotEmpty()) {
      _excludeText.value = bankRepo.excludeBanks[0].text
    }
    setVariety(0)
    setWeirdness(25)
    setStyleInfluence(70)
    setAudioInfluence(80)
    updateSimplePrompt()
    resetHandoffSteps()
    persistAllCoreState()
    recordGeneratedPrompt(
      prompt = a.promptPreserve,
      source = "Send-to-Build",
      lane = a.suggestedLane,
      jobType = JobType.PRESERVE,
      bpm = a.bpm?.toInt()
    )
    setTab(StudioTab.BUILD)
  }

  fun exportTakes(context: Context, asJson: Boolean = true) {
    viewModelScope.launch {
      val takes = takesList.value
      if (takes.isEmpty()) {
        Toast.makeText(context, "No takes logged to export!", Toast.LENGTH_SHORT).show()
        return@launch
      }
      val content = if (asJson) {
        takeRepo.exportTakesJson(takes)
      } else {
        takeRepo.exportTakesPlainText(takes)
      }
      val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TITLE, "BooDa Beat Takes Export")
        putExtra(Intent.EXTRA_TEXT, content)
        type = if (asJson) "application/json" else "text/plain"
      }
      val shareIntent = Intent.createChooser(sendIntent, "Export Production Takes")
      shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      launchExternalChooser(context, shareIntent, "No share target app available on this device.")
    }
  }

  fun importTakes(context: Context, jsonStr: String) {
    viewModelScope.launch {
      val res = takeRepo.importTakesDetailed(jsonStr)
      val msg = when {
        res.errorMessage != null && res.importedCount == 0 -> res.errorMessage
        res.skippedCount > 0 -> "Imported ${res.importedCount} take(s) (${res.skippedCount} skipped)"
        else -> "Imported ${res.importedCount} take(s) into Logbook"
      }
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }

  fun sharePrompt(
    context: Context,
    title: String,
    text: String,
    source: String = "Share",
    lane: String = _activeLane.value
  ) {
    addRecentPrompt(text, source = source, lane = lane)
    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TITLE, title)
      putExtra(Intent.EXTRA_SUBJECT, title)
      putExtra(Intent.EXTRA_TEXT, text)
      type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Suno Style Prompt")
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    launchExternalChooser(context, shareIntent, "No share app available to handle text.")
  }

  fun exportFavorites(context: Context, asJson: Boolean = true) {
    viewModelScope.launch {
      val favs = allFavorites.value
      if (favs.isEmpty()) {
        Toast.makeText(context, "No favorites to export yet!", Toast.LENGTH_SHORT).show()
        return@launch
      }
      val content = if (asJson) {
        favoriteRepo.exportFavoritesJson(favs)
      } else {
        favoriteRepo.exportFavoritesPlainText(favs)
      }
      val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TITLE, "BooDa Beat Favorites Export")
        putExtra(Intent.EXTRA_TEXT, content)
        type = if (asJson) "application/json" else "text/plain"
      }
      val shareIntent = Intent.createChooser(sendIntent, "Export Favorite Prompts")
      shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      launchExternalChooser(context, shareIntent, "No share app available to handle favorites export.")
    }
  }

  fun importFavorites(context: Context, jsonStr: String) {
    viewModelScope.launch {
      val res = favoriteRepo.importFavoritesDetailed(jsonStr)
      val msg = when {
        res.errorMessage != null && res.importedCount == 0 -> res.errorMessage
        res.skippedCount > 0 -> "Imported ${res.importedCount} favorite(s) (${res.skippedCount} skipped)"
        else -> "Imported ${res.importedCount} favorite prompt(s)"
      }
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }

  private fun launchExternalChooser(context: Context, intent: Intent, fallbackMsg: String) {
    try {
      context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(context, fallbackMsg, Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
      Toast.makeText(context, fallbackMsg, Toast.LENGTH_SHORT).show()
    }
  }

  private fun evaluateEnvironmentStatus(): EnvironmentStatus {
    val appCtx = getApplication<Application>()
    val isOnline = try {
      val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      val activeNet = cm?.activeNetwork
      val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
      caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
      true
    }
    val firebaseReady = try {
      if (FirebaseApp.getApps(appCtx).isEmpty()) {
        FirebaseApp.initializeApp(appCtx) != null
      } else {
        true
      }
    } catch (_: Exception) {
      false
    }
    return EnvironmentStatus(isOnline = isOnline, isFirebaseReady = firebaseReady)
  }

  private fun restoreFromSavedState() {
    val savedTabName = savedStateHandle.get<String>(KEY_TAB)
    _currentTab.value = savedTabName?.let {
      try { StudioTab.valueOf(it) } catch (_: Exception) { StudioTab.BUILD }
    } ?: StudioTab.BUILD

    val savedJob = savedStateHandle.get<String>(KEY_JOB_TYPE) ?: JobType.PRESERVE.value
    _jobType.value = JobType.fromString(savedJob)

    val savedLane = savedStateHandle.get<String>(KEY_LANE) ?: "Trap"
    _activeLane.value = savedLane

    _stylePrompt.value = savedStateHandle.get<String>(KEY_STYLE_PROMPT)
      ?: "$LOCK_LINE. Add $savedLane drums underneath only (${bankRepo.laneDrums(savedLane)}), spice only, sample stays lead."
    _lyricsMap.value = savedStateHandle.get<String>(KEY_LYRICS_MAP)
      ?: bankRepo.buildV6LyricsMap(savedLane, _jobType.value)
    _excludeText.value = savedStateHandle.get<String>(KEY_EXCLUDE_TEXT)
      ?: bankRepo.excludeBanks.firstOrNull()?.text.orEmpty()

    _variety.value = (savedStateHandle.get<Int>(KEY_VARIETY) ?: 0).coerceIn(0, 100)
    _weirdness.value = (savedStateHandle.get<Int>(KEY_WEIRDNESS) ?: 25).coerceIn(0, 100)
    _styleInfluence.value = (savedStateHandle.get<Int>(KEY_STYLE_INFLUENCE) ?: 70).coerceIn(0, 100)
    _audioInfluence.value = (savedStateHandle.get<Int>(KEY_AUDIO_INFLUENCE) ?: 80).coerceIn(0, 100)

    val savedSimple = savedStateHandle.get<String>(KEY_SIMPLE_PROMPT)
    if (savedSimple != null) {
      _simplePrompt.value = savedSimple
    } else {
      updateSimplePrompt()
    }

    _curatedSearchQuery.value = savedStateHandle.get<String>(KEY_CURATED_QUERY) ?: ""
    _curatedCategory.value = savedStateHandle.get<String>(KEY_CURATED_CATEGORY) ?: "All"
    _libraryQuery.value = savedStateHandle.get<String>(KEY_LIBRARY_QUERY) ?: ""
    _libraryFilterTrack.value = savedStateHandle.get<String>(KEY_LIBRARY_TRACK) ?: "All"
    _libraryFilterLane.value = savedStateHandle.get<String>(KEY_LIBRARY_LANE) ?: "All"
    _takeFilterResult.value = savedStateHandle.get<String>(KEY_TAKE_FILTER) ?: "ALL"
    _handoffStepsDone.value = savedStateHandle.get<IntArray>(KEY_HANDOFF_STEPS)?.toSet() ?: emptySet()
    _complianceUndoSnapshot.value = ComplianceSnapshot.fromJson(savedStateHandle.get<String>(KEY_COMPLIANCE_UNDO))
  }

  private fun persistAllCoreState() {
    savedStateHandle[KEY_TAB] = _currentTab.value.name
    savedStateHandle[KEY_JOB_TYPE] = _jobType.value.value
    savedStateHandle[KEY_LANE] = _activeLane.value
    savedStateHandle[KEY_STYLE_PROMPT] = _stylePrompt.value
    savedStateHandle[KEY_LYRICS_MAP] = _lyricsMap.value
    savedStateHandle[KEY_EXCLUDE_TEXT] = _excludeText.value
    savedStateHandle[KEY_VARIETY] = _variety.value
    savedStateHandle[KEY_WEIRDNESS] = _weirdness.value
    savedStateHandle[KEY_STYLE_INFLUENCE] = _styleInfluence.value
    savedStateHandle[KEY_AUDIO_INFLUENCE] = _audioInfluence.value
    savedStateHandle[KEY_SIMPLE_PROMPT] = _simplePrompt.value
  }

  internal fun setAnalysisForTest(result: AudioAnalysisResult) {
    _audioAnalysis.value = result
  }

  internal fun setPendingTranscriptionForTest(wavBytes: ByteArray, target: TranscriptionTarget) {
    lastTranscriptionWavBytes = wavBytes
    lastTranscriptionTarget = target
  }

  internal fun setPendingSearchQueryForTest(query: String) {
    lastSearchQuery = query
  }

  internal fun refreshConnectivityFromEnvironment() {
    val env = environmentStatusProvider()
    if (!env.isOnline) {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = false,
        firebaseHealth = FirebaseHealthStatus.OFFLINE,
        geminiHealth = GeminiHealthStatus.OFFLINE,
        lastErrorMessage = "Device offline — reconnect to reach Firebase & Gemini. Local studio tools stay active.",
        isRetrying = false
      )
    } else if (!env.isFirebaseReady) {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = true,
        firebaseHealth = FirebaseHealthStatus.UNREACHABLE,
        geminiHealth = GeminiHealthStatus.HEALTHY,
        lastErrorMessage = "Firebase service is unreachable. Local studio tools stay active.",
        isRetrying = false
      )
    } else {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = true,
        firebaseHealth = FirebaseHealthStatus.HEALTHY,
        geminiHealth = GeminiHealthStatus.HEALTHY,
        lastErrorMessage = null,
        isRetrying = false
      )
    }
  }

  private fun registerNetworkCallbackSafely() {
    try {
      val appCtx = getApplication<Application>()
      val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
      val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          updateConnectivityOnNetworkChange(isOnline = true)
        }

        override fun onLost(network: Network) {
          updateConnectivityOnNetworkChange(isOnline = false)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
          val hasNet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          updateConnectivityOnNetworkChange(isOnline = hasNet)
        }
      }
      cm.registerDefaultNetworkCallback(cb)
      connectivityManagerRef = cm
      networkCallback = cb
    } catch (_: Exception) {
      // Ignore on restricted environments
    }
  }

  internal fun updateConnectivityOnNetworkChange(isOnline: Boolean) {
    val current = _connectivityState.value
    if (!isOnline) {
      _connectivityState.value = current.copy(
        isNetworkOnline = false,
        firebaseHealth = FirebaseHealthStatus.OFFLINE,
        geminiHealth = GeminiHealthStatus.OFFLINE,
        lastErrorMessage = "Device offline — reconnect to use Cloud AI. Local DSP, 41 lanes, and Builder stay active.",
        isRetrying = false,
        isBannerDismissed = false
      )
    } else {
      val env = environmentStatusProvider()
      val nextFirebase = if (env.isFirebaseReady) {
        if (current.firebaseHealth == FirebaseHealthStatus.OFFLINE ||
          current.firebaseHealth == FirebaseHealthStatus.INITIALIZING
        ) {
          FirebaseHealthStatus.HEALTHY
        } else {
          current.firebaseHealth
        }
      } else {
        FirebaseHealthStatus.UNREACHABLE
      }
      val nextGemini = if (current.geminiHealth == GeminiHealthStatus.OFFLINE) {
        GeminiHealthStatus.HEALTHY
      } else {
        current.geminiHealth
      }
      val stillHasError = nextFirebase != FirebaseHealthStatus.HEALTHY || nextGemini != GeminiHealthStatus.HEALTHY
      _connectivityState.value = current.copy(
        isNetworkOnline = true,
        firebaseHealth = nextFirebase,
        geminiHealth = nextGemini,
        lastErrorMessage = if (stillHasError) current.lastErrorMessage else null,
        isRetrying = false
      )
    }
  }

  private fun recordOfflineOrFirebasePreflightFailure(
    env: EnvironmentStatus,
    failedRequest: FailedCloudRequestType
  ) {
    if (!env.isOnline) {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = false,
        firebaseHealth = FirebaseHealthStatus.OFFLINE,
        geminiHealth = GeminiHealthStatus.OFFLINE,
        lastErrorMessage = "Offline — reconnect for ${failedRequest.label}. Local tools stay usable.",
        lastFailedRequest = failedRequest,
        isRetrying = false,
        isBannerDismissed = false
      )
    } else if (!env.isFirebaseReady) {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = true,
        firebaseHealth = FirebaseHealthStatus.UNREACHABLE,
        geminiHealth = GeminiHealthStatus.HEALTHY,
        lastErrorMessage = "Firebase unavailable for ${failedRequest.label}. Local studio tools stay usable.",
        lastFailedRequest = failedRequest,
        isRetrying = false,
        isBannerDismissed = false
      )
    }
  }

  private fun classifyAndRecordCloudFailure(
    error: Throwable,
    failedRequest: FailedCloudRequestType
  ) {
    val msg = error.localizedMessage ?: error.message ?: ""
    val lower = msg.lowercase()
    val env = environmentStatusProvider()
    if (!env.isOnline) {
      recordOfflineOrFirebasePreflightFailure(env, failedRequest)
      return
    }
    if (error is TimeoutCancellationException) {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = true,
        firebaseHealth = if (env.isFirebaseReady) FirebaseHealthStatus.HEALTHY else FirebaseHealthStatus.UNREACHABLE,
        geminiHealth = GeminiHealthStatus.TIMED_OUT,
        lastErrorMessage = "Gemini timed out during ${failedRequest.label}. Local tools stay usable.",
        lastFailedRequest = failedRequest,
        isRetrying = false,
        isBannerDismissed = false
      )
    } else if (
      lower.contains("app check") ||
      lower.contains("appcheck") ||
      lower.contains("firebase") ||
      lower.contains("403") ||
      lower.contains("permission") ||
      lower.contains("unauthenticated")
    ) {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = true,
        firebaseHealth = FirebaseHealthStatus.UNREACHABLE,
        geminiHealth = GeminiHealthStatus.HEALTHY,
        lastErrorMessage = msg.ifBlank { "Firebase App Check/Auth unreachable. Tap Retry to re-probe." },
        lastFailedRequest = failedRequest,
        isRetrying = false,
        isBannerDismissed = false
      )
    } else {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = true,
        firebaseHealth = if (env.isFirebaseReady) FirebaseHealthStatus.HEALTHY else FirebaseHealthStatus.UNREACHABLE,
        geminiHealth = GeminiHealthStatus.UNREACHABLE,
        lastErrorMessage = msg.ifBlank { "Gemini unreachable during ${failedRequest.label}. Tap Retry." },
        lastFailedRequest = failedRequest,
        isRetrying = false,
        isBannerDismissed = false
      )
    }
  }

  private fun markCloudServicesHealthy() {
    _connectivityState.value = _connectivityState.value.copy(
      isNetworkOnline = true,
      firebaseHealth = FirebaseHealthStatus.HEALTHY,
      geminiHealth = GeminiHealthStatus.HEALTHY,
      lastErrorMessage = null,
      lastFailedRequest = null,
      isRetrying = false,
      isBannerDismissed = false
    )
  }

  fun dismissConnectivityBanner() {
    _connectivityState.value = _connectivityState.value.copy(isBannerDismissed = true)
  }

  fun retryConnectionAndLastRequest() {
    val pendingRequest = _connectivityState.value.lastFailedRequest
    _connectivityState.value = _connectivityState.value.copy(
      isRetrying = true,
      isBannerDismissed = false
    )

    var env = environmentStatusProvider()
    if (env.isOnline && !env.isFirebaseReady) {
      configureAppCheck(getApplication(), null)
      env = environmentStatusProvider()
    }

    if (!env.isOnline) {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = false,
        firebaseHealth = FirebaseHealthStatus.OFFLINE,
        geminiHealth = GeminiHealthStatus.OFFLINE,
        lastErrorMessage = "Still offline — reconnect and tap Retry. Local tools remain active.",
        isRetrying = false,
        isBannerDismissed = false
      )
      return
    }

    if (!env.isFirebaseReady) {
      _connectivityState.value = _connectivityState.value.copy(
        isNetworkOnline = true,
        firebaseHealth = FirebaseHealthStatus.UNREACHABLE,
        geminiHealth = GeminiHealthStatus.HEALTHY,
        lastErrorMessage = "Firebase still unreachable after re-probe. Tap Retry shortly.",
        isRetrying = false,
        isBannerDismissed = false
      )
      return
    }

    _connectivityState.value = _connectivityState.value.copy(
      isNetworkOnline = true,
      firebaseHealth = FirebaseHealthStatus.HEALTHY,
      geminiHealth = GeminiHealthStatus.HEALTHY,
      lastErrorMessage = null,
      isRetrying = false,
      isBannerDismissed = false
    )

    when (pendingRequest) {
      FailedCloudRequestType.BEAT_CONSULTATION -> retryAiConsultation()
      FailedCloudRequestType.MIC_TRANSCRIPTION -> retryLastTranscription()
      FailedCloudRequestType.SEARCH_GROUNDING -> {
        if (lastSearchQuery.isNotBlank()) {
          searchSunoKnowledge(lastSearchQuery)
        }
      }
      null -> Unit
    }
  }

  internal fun reportConnectivityStateForTest(state: GlobalConnectivityState) {
    _connectivityState.value = state
  }

  override fun onCleared() {
    super.onCleared()
    networkCallback?.let { cb ->
      try {
        connectivityManagerRef?.unregisterNetworkCallback(cb)
      } catch (_: Exception) {
      }
    }
    networkCallback = null
    connectivityManagerRef = null
    analysisJob?.cancel()
    aiJob?.cancel()
    transcribeJob?.cancel()
    searchJob?.cancel()
    audioRecorder.stopRecording()
    audioPlayer.release()
  }

  companion object {
    private const val KEY_TAB = "studio_tab"
    private const val KEY_JOB_TYPE = "job_type"
    private const val KEY_LANE = "active_lane"
    private const val KEY_STYLE_PROMPT = "style_prompt"
    private const val KEY_LYRICS_MAP = "lyrics_map"
    private const val KEY_EXCLUDE_TEXT = "exclude_text"
    private const val KEY_VARIETY = "variety"
    private const val KEY_WEIRDNESS = "weirdness"
    private const val KEY_STYLE_INFLUENCE = "style_influence"
    private const val KEY_AUDIO_INFLUENCE = "audio_influence"
    private const val KEY_SIMPLE_PROMPT = "simple_prompt"
    private const val KEY_CURATED_QUERY = "curated_search_query"
    private const val KEY_CURATED_CATEGORY = "curated_category"
    private const val KEY_LIBRARY_QUERY = "library_query"
    private const val KEY_LIBRARY_TRACK = "library_filter_track"
    private const val KEY_LIBRARY_LANE = "library_filter_lane"
    private const val KEY_TEMPLATE_QUERY = "template_search_query"
    private const val KEY_TEMPLATE_CATEGORY = "template_category_filter"
    private const val KEY_TAKE_FILTER = "take_filter_result"
    private const val KEY_HANDOFF_STEPS = "handoff_steps_done"
    private const val KEY_COMPLIANCE_UNDO = "compliance_undo_snapshot"
  }
}


