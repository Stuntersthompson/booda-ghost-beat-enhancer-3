package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiBeatConsultation
import com.example.ai.GeminiBeatService
import com.example.audio.AudioAnalysisResult
import com.example.audio.AudioAnalyzer
import com.example.audio.AudioRecorder
import com.example.audio.StudioAudioPlayer
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.repository.FavoriteRepository
import com.example.data.repository.PromptBankRepository
import com.example.data.repository.StudioPreferencesRepository
import com.example.data.repository.TakeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

  val bankRepo = PromptBankRepository(application)
  private val database = AppDatabase.getDatabase(application)
  val takeRepo = TakeRepository(database.takeDao())
  val favoriteRepo = FavoriteRepository(database.favoritePromptDao())
  val preferencesRepo = StudioPreferencesRepository(application)
  private val audioAnalyzer = AudioAnalyzer(application)
  private val audioRecorder = AudioRecorder()
  private val audioPlayer = StudioAudioPlayer(viewModelScope)
  private val geminiService = GeminiBeatService()

  // Synced Waveform Playback State
  val isPlayingAudio: StateFlow<Boolean> = audioPlayer.isPlaying
  val playbackProgress: StateFlow<Float> = audioPlayer.progress
  val playbackPositionMs: StateFlow<Long> = audioPlayer.positionMs
  val playbackDurationMs: StateFlow<Long> = audioPlayer.durationMs
  val hasPlayableAudio: StateFlow<Boolean> = audioPlayer.hasAudio
  val isLoopingAudio: StateFlow<Boolean> = audioPlayer.isLooping

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

  private val _recentPrompts = MutableStateFlow<List<String>>(emptyList())
  val recentPrompts: StateFlow<List<String>> = _recentPrompts.asStateFlow()

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

  private val _takeFilterResult = MutableStateFlow("ALL")
  val takeFilterResult: StateFlow<String> = _takeFilterResult.asStateFlow()

  init {
    bankRepo.ensureLoaded()
    resetToPreserveDefaults()
  }

  fun setTab(tab: StudioTab) {
    _currentTab.value = tab
  }

  fun setJobType(job: JobType) {
    _jobType.value = job
    if (job == JobType.PRESERVE) {
      _variety.value = 0
      _weirdness.value = 25
      _styleInfluence.value = 70
      _audioInfluence.value = 80
      ensureLockLineInStyle()
    } else {
      _variety.value = 0
      _weirdness.value = 20
      _styleInfluence.value = 65
      _audioInfluence.value = 0
      removeLockLineFromStyle()
    }
    _lyricsMap.value = bankRepo.buildV6LyricsMap(_activeLane.value, job)
    updateSimplePrompt()
  }

  fun setLane(lane: String) {
    _activeLane.value = lane
    val drums = bankRepo.laneDrums(lane)
    if (_jobType.value == JobType.PRESERVE) {
      _stylePrompt.value = "$LOCK_LINE. Add $lane drums underneath only ($drums), spice only, sample stays lead."
    } else {
      _stylePrompt.value = "$lane instrumental, $drums, high energy, crisp mix."
    }
    _lyricsMap.value = bankRepo.buildV6LyricsMap(lane, _jobType.value)
    updateSimplePrompt()
  }

  fun updateStylePrompt(text: String) {
    _stylePrompt.value = text
    resetHandoffSteps()
  }

  fun updateLyricsMap(text: String) {
    _lyricsMap.value = text
    resetHandoffSteps()
  }

  fun updateExcludeText(text: String) {
    _excludeText.value = text
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
  }

  fun setVariety(v: Int) { _variety.value = v }
  fun setWeirdness(w: Int) { _weirdness.value = w }
  fun setStyleInfluence(s: Int) { _styleInfluence.value = s }
  fun setAudioInfluence(a: Int) { _audioInfluence.value = a }

  fun selectExcludeBank(name: String) {
    val bank = bankRepo.excludeBanks.find { it.name.equals(name, ignoreCase = true) }
    if (bank != null) {
      _excludeText.value = bank.text
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
  }

  fun randomizeAll() {
    val lane = bankRepo.allLanes.random()
    _activeLane.value = lane
    val drums = bankRepo.laneDrums(lane)
    if (_jobType.value == JobType.PRESERVE) {
      _stylePrompt.value = "$LOCK_LINE. Add $lane drums underneath only ($drums), spice only, sample stays lead."
      _audioInfluence.value = Random.nextInt(75, 86)
      _weirdness.value = Random.nextInt(20, 31)
      _styleInfluence.value = Random.nextInt(65, 76)
      _variety.value = 0
    } else {
      _stylePrompt.value = "$lane instrumental, $drums, crisp modern mix."
      _audioInfluence.value = 0
      _weirdness.value = Random.nextInt(15, 26)
      _styleInfluence.value = Random.nextInt(60, 71)
      _variety.value = 0
    }
    _lyricsMap.value = bankRepo.buildV6LyricsMap(lane, _jobType.value)
    if (bankRepo.excludeBanks.isNotEmpty()) {
      _excludeText.value = bankRepo.excludeBanks.random().text
    }
    updateSimplePrompt()
  }

  private fun ensureLockLineInStyle() {
    if (!_stylePrompt.value.contains(LOCK_LINE)) {
      _stylePrompt.value = if (_stylePrompt.value.isBlank()) {
        "$LOCK_LINE. Add ${_activeLane.value} drums underneath only."
      } else {
        "$LOCK_LINE. ${_stylePrompt.value}"
      }
    }
  }

  private fun removeLockLineFromStyle() {
    _stylePrompt.value = _stylePrompt.value
      .replace("$LOCK_LINE. ", "")
      .replace(LOCK_LINE, "")
      .trim()
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
  }

  // Audio Actions
  fun startRecording() {
    if (_isRecording.value) return
    audioPlayer.stop()
    val started = audioRecorder.startRecording { peak, db ->
      _recordingPeak.value = peak
      _recordingDb.value = db
    }
    if (started) {
      _isRecording.value = true
      rollingWaveformBuffer.fill(0.05f)
      _liveWaveform.value = rollingWaveformBuffer.copyOf()
      viewModelScope.launch {
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
          _isAnalyzing.value = false
          // Consult AI
          val consultation = geminiService.consultBeatAnalyst(result, _jobType.value.value)
          _aiConsultation.value = consultation
        }
      }
    }
  }

  fun stopRecording() {
    audioRecorder.stopRecording()
    _isRecording.value = false
  }

  fun analyzeFile(uri: Uri, fileName: String) {
    viewModelScope.launch {
      audioPlayer.stop()
      _isAnalyzing.value = true
      val result = audioAnalyzer.analyzeAudioUri(uri, fileName)
      _audioAnalysis.value = result
      val fallbackMs = (result.durationSec * 1000.0).toLong().coerceAtLeast(1000L)
      audioPlayer.loadUri(getApplication(), uri, fallbackMs)
      _isAnalyzing.value = false
      val consultation = geminiService.consultBeatAnalyst(result, _jobType.value.value)
      _aiConsultation.value = consultation
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

  fun toggleAudioLoop() {
    audioPlayer.toggleLoop()
  }

  fun applyBpmMatchedLanePreset(lane: String, overrideBpm: Double? = null) {
    _activeLane.value = lane
    val drums = bankRepo.laneDrums(lane)
    val analyzed = _audioAnalysis.value
    val bpmValue = overrideBpm ?: analyzed?.bpm
    val bpmTag = bpmValue?.toInt()?.let { " at $it BPM" } ?: ""
    val keyTag = analyzed?.keyName?.takeIf { it.isNotBlank() }?.let { " in $it" } ?: ""

    if (_jobType.value == JobType.PRESERVE) {
      _stylePrompt.value = "$LOCK_LINE. Add $lane drums underneath only ($drums)$bpmTag$keyTag, spice only, sample stays lead."
      _variety.value = 0
      _weirdness.value = 25
      _styleInfluence.value = 70
      _audioInfluence.value = 80
    } else {
      _stylePrompt.value = "$lane instrumental$bpmTag$keyTag, $drums, high energy, crisp mix."
      _variety.value = 0
      _weirdness.value = 20
      _styleInfluence.value = 65
      _audioInfluence.value = 0
    }
    _lyricsMap.value = bankRepo.buildV6LyricsMap(lane, _jobType.value)
    updateSimplePrompt()
    resetHandoffSteps()
  }

  fun adjustBpmMultiplier(multiplier: Double) {
    val current = _audioAnalysis.value ?: return
    val curBpm = current.bpm ?: 130.0
    val newBpm = curBpm * multiplier
    _audioAnalysis.value = current.copy(
      bpm = kotlin.math.round(newBpm * 10) / 10.0,
      pocketLabel = "${newBpm.toInt()} BPM · ${if (current.centroidHz > 3000) "Bright" else "Dark"}"
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
    _currentTab.value = StudioTab.BUILD
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
    _currentTab.value = StudioTab.SIMPLE
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
  }

  fun setLibraryQuery(q: String) {
    _libraryQuery.value = q
  }

  fun setLibraryFilterTrack(track: String) {
    _libraryFilterTrack.value = track
  }

  fun setLibraryFilterLane(lane: String) {
    _libraryFilterLane.value = lane
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
  }

  fun setCuratedCategory(cat: String) {
    _curatedCategory.value = cat
  }

  fun clearCuratedFilters() {
    _curatedSearchQuery.value = ""
    _curatedCategory.value = "All"
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

  fun addRecentPrompt(prompt: String) {
    val current = _recentPrompts.value.toMutableList()
    current.remove(prompt)
    current.add(0, prompt)
    if (current.size > 15) {
      current.removeAt(current.size - 1)
    }
    _recentPrompts.value = current
  }

  // Suno Handoff Checklist Tracking (Steps 1..5)
  private val _handoffStepsDone = MutableStateFlow<Set<Int>>(emptySet())
  val handoffStepsDone: StateFlow<Set<Int>> = _handoffStepsDone.asStateFlow()

  fun resetHandoffSteps() {
    _handoffStepsDone.value = emptySet()
  }

  fun copyHandoffStep(context: Context, step: Int) {
    val isPreserve = _jobType.value == JobType.PRESERVE
    when (step) {
      1 -> { // Route
        val routeText = if (isPreserve) "Route: Extend | Audio Influence: ${_audioInfluence.value}" else "Route: Create (Standard)"
        copyToClipboard(context, "Route Guidance", routeText)
      }
      2 -> { // Style
        copyToClipboard(context, "Style Prompt", _stylePrompt.value)
      }
      3 -> { // Exclude
        copyToClipboard(context, "Exclude Styles", _excludeText.value)
      }
      4 -> { // Lyrics
        copyToClipboard(context, "Lyrics Map", _lyricsMap.value)
      }
      5 -> { // Settings
        val settingsText = "Model v6 · Variety ${_variety.value} · Weirdness ${_weirdness.value} · Style Influence ${_styleInfluence.value} · Audio Influence ${_audioInfluence.value}"
        copyToClipboard(context, "Suno Settings", settingsText)
      }
    }
    _handoffStepsDone.value = _handoffStepsDone.value + step
  }

  fun toggleHandoffStep(step: Int) {
    val current = _handoffStepsDone.value
    _handoffStepsDone.value = if (current.contains(step)) current - step else current + step
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
    _variety.value = 0
    _weirdness.value = 25
    _styleInfluence.value = 70
    _audioInfluence.value = 80
    updateSimplePrompt()
    resetHandoffSteps()
    _currentTab.value = StudioTab.BUILD
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
      context.startActivity(shareIntent)
    }
  }

  fun importTakes(context: Context, jsonStr: String) {
    viewModelScope.launch {
      val count = takeRepo.importTakesJson(jsonStr)
      Toast.makeText(context, "Imported $count take(s) into Logbook", Toast.LENGTH_SHORT).show()
    }
  }

  fun sharePrompt(context: Context, title: String, text: String) {
    addRecentPrompt(text)
    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TITLE, title)
      putExtra(Intent.EXTRA_SUBJECT, title)
      putExtra(Intent.EXTRA_TEXT, text)
      type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Suno Style Prompt")
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(shareIntent)
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
      context.startActivity(shareIntent)
    }
  }

  fun importFavorites(context: Context, jsonStr: String) {
    viewModelScope.launch {
      val count = favoriteRepo.importFavoritesJson(jsonStr)
      Toast.makeText(context, "Imported $count favorite prompt(s)", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onCleared() {
    super.onCleared()
    audioRecorder.stopRecording()
    audioPlayer.release()
  }
}


