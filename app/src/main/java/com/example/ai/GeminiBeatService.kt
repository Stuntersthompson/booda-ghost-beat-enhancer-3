package com.example.ai

import android.util.Log
import com.example.audio.AudioAnalysisResult
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
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

data class AiBeatConsultation(
  val heardVocalText: String,
  val vocalConfidence: String, // "Clear", "Fragment", "None"
  val tacticalAdvice: String,
  val recommendedStylePrompt: String,
  val suggestedSettings: String,
  val suggestions: List<SunoPromptSuggestion> = emptyList(),
  val searchGroundingSummary: String? = null
)

sealed interface AiConsultationUiState {
  data object Idle : AiConsultationUiState
  data class Loading(val stage: String = "Analyzing beat with Gemini 3.8 Flash...") : AiConsultationUiState
  data class Success(val consultation: AiBeatConsultation) : AiConsultationUiState
  data class Error(val message: String, val canRetry: Boolean = true) : AiConsultationUiState
}

/**
 * Firebase AI Logic client powered by Gemini 3.8 Flash with structured JSON output
 * and optional multimodal WAV audio attachment (capped strictly under 20 MB).
 */
class GeminiBeatService {

  private companion object {
    const val TAG = "GeminiBeatService"
    const val MODEL_NAME = "gemini-3.8-flash"
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
      modelName = MODEL_NAME,
      generationConfig = generationConfig {
        temperature = 0.4f
        responseMimeType = "application/json"
        responseSchema = consultationSchema
      }
    )
  }

  /**
   * Executes real multimodal beat consultation + vocal transcription via Firebase AI Logic.
   * Surfaces timeouts and backend errors cleanly to the caller rather than hiding behind mock templates.
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
      - Tempo: ${analysis.bpm?.let { "%.1f".format(it) } ?: "Unknown"} BPM (confidence: ${analysis.bpmConfidence})
      - Musical Key: ${analysis.keyName ?: "Unread"} (clear: ${analysis.keyClear})
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
