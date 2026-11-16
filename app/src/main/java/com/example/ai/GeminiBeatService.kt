package com.example.ai

import android.util.Log
import com.example.audio.AudioAnalysisResult
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class SunoPromptSuggestion(
  val title: String,
  val prompt: String,
  val chars: Int,
  val focusNote: String
)

data class GroundedSource(
  val title: String,
  val uri: String
)

data class SearchGroundedAnswer(
  val query: String,
  val answer: String,
  val webSearchQueries: List<String> = emptyList(),
  val sources: List<GroundedSource> = emptyList(),
  val searchEntryPointHtml: String? = null
)

data class AiBeatConsultation(
  val heardVocalText: String,
  val vocalConfidence: String, // "Clear", "Fragment", "None"
  val tacticalAdvice: String,
  val recommendedStylePrompt: String,
  val suggestedSettings: String,
  val suggestions: List<SunoPromptSuggestion> = emptyList(),
  val searchGroundingSummary: String? = null,
  val groundedSources: List<GroundedSource> = emptyList(),
  val groundedQueries: List<String> = emptyList()
)

enum class TranscriptionTarget {
  ANALYZE_SAMPLE_WORDS,
  BUILD_LYRICS,
  BUILD_STYLE
}

sealed interface AiConsultationUiState {
  data object Idle : AiConsultationUiState
  data class Loading(val stage: String = "Analyzing beat with Gemini 3.8 Flash...") : AiConsultationUiState
  data class Success(val consultation: AiBeatConsultation) : AiConsultationUiState
  data class Error(val message: String, val canRetry: Boolean = true) : AiConsultationUiState
}

sealed interface TranscriptionUiState {
  data object Idle : TranscriptionUiState
  data class Recording(val target: TranscriptionTarget, val dbLevel: Float = -60f) : TranscriptionUiState
  data class Transcribing(val target: TranscriptionTarget) : TranscriptionUiState
  data class Success(val text: String, val target: TranscriptionTarget) : TranscriptionUiState
  data class Error(val message: String, val target: TranscriptionTarget, val canRetry: Boolean = true) : TranscriptionUiState
}

sealed interface SearchGroundingUiState {
  data object Idle : SearchGroundingUiState
  data class Loading(val query: String) : SearchGroundingUiState
  data class Success(val result: SearchGroundedAnswer) : SearchGroundingUiState
  data class Error(val message: String, val query: String) : SearchGroundingUiState
}

/**
 * Firebase AI Logic client routing:
 * - Structured beat consultation via gemini-3.8-flash
 * - Live microphone audio transcription via gemini-3.5-transcribe
 * - Live Google Search grounding via gemini-4-hydrogen + Tool.googleSearch()
 */
class GeminiBeatService {

  private companion object {
    const val TAG = "GeminiBeatService"
    const val MODEL_CONSULTATION = "gemini-3.8-flash"
    const val MODEL_TRANSCRIBE = "gemini-3.5-transcribe"
    const val MODEL_SEARCH_GROUNDING = "gemini-4-hydrogen"
    const val AI_TIMEOUT_MS = 60_000L
    const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024
  }

  private val consultationSchema: Schema by lazy {
    Schema.obj(
      properties = mapOf(
        "vocalText" to Schema.string(
          description = "Verbatim transcribed vocal phrases if audible, or 'No intelligible vocal phrases detected' if instrumental. Max 120 chars."
        ),
        "vocalConfidence" to Schema.enumeration(
          values = listOf("Clear", "Fragment", "None"),
          description = "Clear if full words audible, Fragment if chopped/partial vocals audible, None if instrumental."
        ),
        "tacticalAdvice" to Schema.string(
          description = "Actionable Suno v6 producer strategy tailored to this beat's BPM, key, low-end share, and mode."
        ),
        "recommendedStylePrompt" to Schema.string(
          description = "Primary Suno v6 Style of Music prompt strictly <= 200 characters, no artist/producer names."
        ),
        "suggestedSettings" to Schema.string(
          description = "Recommended Suno v6 route, Variety, Weirdness, Style Influence, and Audio Influence settings."
        ),
        "suggestions" to Schema.array(
          items = Schema.obj(
            properties = mapOf(
              "title" to Schema.string(description = "Short punchy variation label"),
              "prompt" to Schema.string(description = "Distinct Suno v6 style prompt <= 200 chars, no artist names"),
              "focusNote" to Schema.string(description = "Brief sonic objective for this variation")
            )
          ),
          description = "Exactly 4 distinct Suno v6 style prompt variations."
        ),
        "searchGrounding" to Schema.string(
          description = "Concise Suno v6 engineering summary note."
        )
      ),
      optionalProperties = listOf("searchGrounding")
    )
  }

  private val structuredModel by lazy {
    Firebase.ai.generativeModel(
      modelName = MODEL_CONSULTATION,
      generationConfig = generationConfig {
        temperature = 0.4f
        responseMimeType = "application/json"
        responseSchema = consultationSchema
      }
    )
  }

  private val transcriptionModel by lazy {
    Firebase.ai.generativeModel(
      modelName = MODEL_TRANSCRIBE
    )
  }

  private val searchGroundedModel by lazy {
    Firebase.ai.generativeModel(
      modelName = MODEL_SEARCH_GROUNDING,
      tools = listOf(Tool.googleSearch())
    )
  }

  /**
   * Executes real multimodal beat consultation via gemini-3.8-flash and enriches it
   * with live web-grounded Suno v6 notes via gemini-4-hydrogen (Tool.googleSearch).
   */
  suspend fun consultBeatAnalyst(
    analysis: AudioAnalysisResult,
    jobType: String,
    audioBytes: ByteArray? = null,
    audioMimeType: String = "audio/wav"
  ): AiBeatConsultation = withContext(Dispatchers.IO) {
    val promptInstruction = buildConsultationPrompt(analysis, jobType)
    val attachedAudio = audioBytes?.takeIf { it.isNotEmpty() && it.size <= MAX_INLINE_AUDIO_BYTES }

    val response = try {
      withTimeout(AI_TIMEOUT_MS) {
        structuredModel.generateContent(
          content {
            if (attachedAudio != null) {
              inlineData(attachedAudio, audioMimeType)
            }
            text(promptInstruction)
          }
        )
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini consultation timed out after ${AI_TIMEOUT_MS / 1000}s", e)
      throw e
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI Logic request failed", e)
      throw e
    }

    val rawJson = response.text?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("Gemini returned an empty response.")

    parseConsultationJson(rawJson, analysis, jobType)
  }

  /**
   * Transcribes live microphone PCM/WAV audio using the dedicated gemini-3.5-transcribe model.
   */
  suspend fun transcribeMicrophoneAudio(
    wavBytes: ByteArray,
    target: TranscriptionTarget,
    mimeType: String = "audio/wav"
  ): String = withContext(Dispatchers.IO) {
    if (wavBytes.isEmpty()) {
      throw IllegalArgumentException("No microphone audio captured.")
    }
    if (wavBytes.size > MAX_INLINE_AUDIO_BYTES) {
      throw IllegalArgumentException("Recorded audio exceeds 18 MB inline payload limit.")
    }

    val directive = when (target) {
      TranscriptionTarget.ANALYZE_SAMPLE_WORDS ->
        "Transcribe the spoken words or vocal hook in this microphone recording verbatim. " +
          "Do not invent or extrapolate missing words. If no speech is audible, respond with: No intelligible vocal phrases detected."
      TranscriptionTarget.BUILD_LYRICS ->
        "Transcribe this microphone recording verbatim as rhythmic lyrics or hook lines suitable for a song structure map. " +
          "Preserve exact phrasing without adding extra verses."
      TranscriptionTarget.BUILD_STYLE ->
        "Transcribe this spoken studio style instruction concisely as plain text without quotes or markdown."
    }

    val response = try {
      withTimeout(AI_TIMEOUT_MS) {
        transcriptionModel.generateContent(
          content {
            inlineData(wavBytes, mimeType)
            text(directive)
          }
        )
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Audio transcription timed out after ${AI_TIMEOUT_MS / 1000}s", e)
      throw e
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "gemini-3.5-transcribe failed", e)
      throw e
    }

    val transcript = response.text?.let { sanitizeTranscript(it, maxChars = if (target == TranscriptionTarget.ANALYZE_SAMPLE_WORDS) 120 else 500) }
    if (transcript.isNullOrBlank()) {
      throw IllegalStateException("Transcription returned empty text.")
    }
    transcript
  }

  /**
   * Executes live Google Search grounded research using gemini-4-hydrogen + Tool.googleSearch().
   */
  suspend fun researchWithGoogleSearch(query: String): SearchGroundedAnswer = withContext(Dispatchers.IO) {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) {
      throw IllegalArgumentException("Please enter a search query.")
    }

    val prompt = """
      You are an up-to-date music production and Suno AI studio engineering researcher.
      Use Google Search grounding to answer this question accurately with practical workflow guidance (no hallucinated features):
      "$cleanQuery"
    """.trimIndent()

    val response = try {
      withTimeout(AI_TIMEOUT_MS) {
        searchGroundedModel.generateContent(prompt)
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Search grounding timed out after ${AI_TIMEOUT_MS / 1000}s", e)
      throw e
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "gemini-4-hydrogen Search Grounding failed", e)
      throw e
    }

    extractGroundedAnswer(cleanQuery, response)
  }

  /**
   * Fetches a focused Search-grounded Suno v6 pocket insight & citations for Beat Consultation.
   */
  suspend fun fetchGroundedBeatContext(
    analysis: AudioAnalysisResult,
    jobType: String
  ): SearchGroundedAnswer {
    val modeLabel = if (jobType.equals(JobType.PRESERVE.value, ignoreCase = true)) {
      "Extend route preserving sample"
    } else {
      "Create original instrumental"
    }
    val bpmInt = analysis.bpm?.toInt() ?: 130
    val keyLabel = analysis.keyName ?: "minor pocket"
    val query = "Suno AI v6 ${analysis.suggestedLane} $bpmInt BPM $keyLabel $modeLabel audio influence variety best settings"
    return researchWithGoogleSearch(query)
  }

  internal fun extractGroundedAnswer(
    query: String,
    response: GenerateContentResponse
  ): SearchGroundedAnswer {
    val answerText = response.text?.trim().orEmpty()
    if (answerText.isBlank()) {
      throw IllegalStateException("Search Grounding returned an empty response.")
    }
    val metadata = response.candidates.firstOrNull()?.groundingMetadata
    val webQueries = metadata?.webSearchQueries?.filter { it.isNotBlank() }.orEmpty()
    val rawSources = metadata?.groundingChunks?.mapNotNull { chunk ->
      val web = chunk.web
      val uri = web?.uri?.trim().orEmpty()
      if (uri.isNotBlank()) {
        GroundedSource(
          title = web?.title?.trim()?.takeIf { it.isNotEmpty() } ?: uri,
          uri = uri
        )
      } else null
    }.orEmpty()
    val entryHtml = metadata?.searchEntryPoint?.renderedContent?.takeIf { it.isNotBlank() }

    return buildSearchGroundedAnswer(
      query = query,
      answerText = answerText,
      webQueries = webQueries,
      rawSources = rawSources,
      searchEntryPointHtml = entryHtml
    )
  }

  internal fun buildSearchGroundedAnswer(
    query: String,
    answerText: String,
    webQueries: List<String>,
    rawSources: List<GroundedSource>,
    searchEntryPointHtml: String? = null
  ): SearchGroundedAnswer {
    val dedupedSources = rawSources
      .filter { it.uri.isNotBlank() }
      .distinctBy { it.uri }
    return SearchGroundedAnswer(
      query = query.trim(),
      answer = answerText.trim(),
      webSearchQueries = webQueries.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
      sources = dedupedSources,
      searchEntryPointHtml = searchEntryPointHtml
    )
  }

  internal fun sanitizeTranscript(rawText: String, maxChars: Int = 500): String {
    val cleaned = rawText
      .replace(Regex("\\s+"), " ")
      .trim()
      .trim('"')
    return if (cleaned.length <= maxChars) cleaned else cleaned.take(maxChars).trimEnd()
  }

  internal fun buildConsultationPrompt(analysis: AudioAnalysisResult, jobType: String): String {
    val isPreserve = jobType.equals(JobType.PRESERVE.value, ignoreCase = true)
    val modeDirective = if (isPreserve) {
      "Route is Extend (Preserve uploaded sample). Begin recommended prompts with '$LOCK_LINE.' where appropriate, and keep drums/808s underneath the sample."
    } else {
      "Route is Create (Original track inspired by tempo/pocket). Generate cohesive full-beat Suno v6 style prompts."
    }

    return """
      You are an elite Suno v6 hip-hop / sample-beat producer and audio engineer.
      Inspect the DSP telemetry below (and attached audio clip if provided) and return a structured JSON analysis.

      STRICT PRODUCTION LAWS:
      1. Honesty Law for vocalText: Only transcribe words you genuinely hear in the audio. Never invent, hallucinate, or guess lyric completions. If instrumental or unintelligible, return "No intelligible vocal phrases detected" and vocalConfidence = "None". Keep vocalText <= 120 characters.
      2. Every style prompt ("recommendedStylePrompt" and each suggestion "prompt") MUST be strictly <= 200 characters.
      3. NEVER include any real artist, rapper, singer, or producer names.
      4. NEVER put negative exclusion commands ("no vocals", "no drums", "without") inside Style prompts.
      5. $modeDirective

      DSP TELEMETRY:
      - Clip Name: ${analysis.fileName}
      - Duration: ${"%.1f".format(analysis.durationSec)}s
      - Tempo: ${analysis.bpm?.let { "%.1f".format(it) } ?: "Unknown"} BPM (confidence: ${analysis.bpmConfidence} / ${(analysis.bpmConfidenceScore * 100).toInt()}%)
      - Musical Key & Mode: ${analysis.keyName ?: "Unread"} (clear: ${analysis.keyClear}, confidence: ${(analysis.keyConfidenceScore * 100).toInt()}%)
      - RMS Loudness: ${"%.1f".format(analysis.rmsDb)} dBFS | Peak: ${"%.1f".format(analysis.peakDb)} dBFS
      - Sub-Bass Share (<160 Hz): ${(analysis.lowShare * 100).toInt()}%
      - Spectral Centroid: ${analysis.centroidHz.toInt()} Hz
      - Onset Density: ${analysis.onsetRate} hits/sec
      - Suggested Lane: ${analysis.suggestedLane}

      Provide 4 distinct Suno v6 prompt variations focusing on heavy tuned 808s, crisp trap/boom-bap pocket drums, and mix breathing room.
    """.trimIndent()
  }

  internal fun parseConsultationJson(
    jsonString: String,
    analysis: AudioAnalysisResult,
    jobType: String
  ): AiBeatConsultation {
    val root = JSONObject(jsonString)
    val isPreserve = jobType.equals(JobType.PRESERVE.value, ignoreCase = true)

    val rawVocalText = root.optString("vocalText", "No intelligible vocal phrases detected")
      .trim()
      .take(120)
      .ifBlank { "No intelligible vocal phrases detected" }

    val rawConfidence = root.optString("vocalConfidence", "None").trim()
    val vocalConfidence = when {
      rawConfidence.equals("Clear", ignoreCase = true) -> "Clear"
      rawConfidence.equals("Fragment", ignoreCase = true) -> "Fragment"
      else -> "None"
    }

    val tacticalAdvice = root.optString("tacticalAdvice", "")
      .trim()
      .ifBlank {
        if (isPreserve) {
          "Lock uploaded sample on Extend route (Variety 0, Audio Influence 80) and layer pocketed 808 + hi-hats underneath."
        } else {
          "Build around ${analysis.suggestedLane} pocket at ${analysis.bpm?.toInt() ?: 130} BPM with controlled low-end punch."
        }
      }

    val recommendedStyle = sanitizePrompt200(
      root.optString("recommendedStylePrompt", "")
        .ifBlank { if (isPreserve) analysis.promptPreserve else analysis.promptOriginal }
    )

    val suggestedSettings = root.optString("suggestedSettings", "")
      .trim()
      .ifBlank {
        if (isPreserve) {
          "Model v6 · Route: Extend · Variety 0 · Weirdness 25 · Style Influence 70 · Audio Influence 80"
        } else {
          "Model v6 · Route: Create · Variety 0 · Weirdness 20 · Style Influence 65 · Audio Influence 0"
        }
      }

    val suggestionsArr = root.optJSONArray("suggestions")
    val suggestionsList = mutableListOf<SunoPromptSuggestion>()
    if (suggestionsArr != null) {
      for (i in 0 until suggestionsArr.length()) {
        val item = suggestionsArr.optJSONObject(i) ?: continue
        val cleanPrompt = sanitizePrompt200(item.optString("prompt", ""))
        if (cleanPrompt.isNotBlank()) {
          suggestionsList.add(
            SunoPromptSuggestion(
              title = item.optString("title", "Variation #${i + 1}").trim().ifBlank { "Variation #${i + 1}" },
              prompt = cleanPrompt,
              chars = cleanPrompt.length,
              focusNote = item.optString("focusNote", "Sample-locked 808 pocket").trim()
            )
          )
        }
      }
    }

    val grounding = root.optString("searchGrounding", "").trim().takeIf { it.isNotEmpty() }

    return AiBeatConsultation(
      heardVocalText = rawVocalText,
      vocalConfidence = vocalConfidence,
      tacticalAdvice = tacticalAdvice,
      recommendedStylePrompt = recommendedStyle,
      suggestedSettings = suggestedSettings,
      suggestions = suggestionsList,
      searchGroundingSummary = grounding
    )
  }

  private fun sanitizePrompt200(raw: String): String {
    val normalized = raw.replace(Regex("\\s+"), " ").trim()
    if (normalized.length <= 200) return normalized
    val cut = normalized.substring(0, 200)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > 140) cut.substring(0, lastSpace).trimEnd(',', '.', ';') else cut.trim()
  }
}
