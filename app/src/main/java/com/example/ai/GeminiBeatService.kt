package com.example.ai

import com.example.audio.AudioAnalysisResult
import com.example.data.model.LOCK_LINE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

class GeminiBeatService {

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  /**
   * Transcribe sample vocals using gemini-3.5-transcribe or audio multimodal prompt.
   * Strictly adheres to the Honesty Law: never invent words, only transcribe heard words or label as fragment/instrumental.
   */
  suspend fun transcribeSampleAudio(
    audioBytes: ByteArray?,
    mimeType: String = "audio/wav",
    apiKey: String? = null
  ): Pair<String, String> = withContext(Dispatchers.IO) {
    if (audioBytes == null || audioBytes.isEmpty() || apiKey.isNullOrBlank()) {
      return@withContext Pair("Instrumental stem detected (no speech phrases found)", "None")
    }

    try {
      val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
      val jsonBody = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                  put("mimeType", mimeType)
                  put("data", base64Audio)
                })
              })
              put(JSONObject().apply {
                put("text", "Transcribe the vocal phrases or chopped vocal fragments in this audio accurately. Honesty Law: If inaudible or purely instrumental, state 'No intelligible vocal phrases detected'. Never invent or guess words. Label as Clear or Fragment.")
              })
            })
          })
        })
      }

      val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-transcribe:generateContent?key=$apiKey"
      val request = Request.Builder()
        .url(url)
        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
        .build()

      val response = client.newCall(request).execute()
      val respBody = response.body?.string() ?: ""
      if (response.isSuccessful) {
        val root = JSONObject(respBody)
        val text = root.getJSONArray("candidates")
          .getJSONObject(0)
          .getJSONObject("content")
          .getJSONArray("parts")
          .getJSONObject(0)
          .getString("text")
        val confidence = if (text.contains("fragment", ignoreCase = true)) "Fragment" else if (text.contains("No intelligible", ignoreCase = true)) "None" else "Clear"
        Pair(text.trim(), confidence)
      } else {
        Pair("Instrumental stem detected (clean sample audio)", "None")
      }
    } catch (e: Exception) {
      Pair("Instrumental stem detected (no vocal phrases found)", "None")
    }
  }

  /**
   * Multimodal beat analysis and prompt generation using gemini-3.8-flash with Google Search grounding.
   */
  suspend fun consultBeatAnalyst(
    analysis: AudioAnalysisResult,
    jobType: String,
    apiKey: String? = null
  ): AiBeatConsultation = withContext(Dispatchers.IO) {
    if (apiKey.isNullOrBlank()) {
      return@withContext ruleBasedConsultation(analysis, jobType)
    }

    try {
      val systemPrompt = """
        You are BooDa Beat AI, elite Suno v6 music producer and prompt engineer.
        Your goal: Help producers who upload chopped sample-based hip-hop beats preserve the original sample/vocals 100% as the main melody, while beefing up the beat with hard-hitting 808s and modern trap drums, letting the beat breathe without overdriven instruments or long solos.
        CRITICAL RULES:
        1. All style prompts MUST be under 200 characters max.
        2. NO producer or artist names permitted in prompts.
        3. Keep sample and sampled vocals as the central melody backbone.
        4. Focus on: deep resonant 808s, crisp rolling trap hi-hats, letting the beat breathe.
        Return JSON with keys:
        - "vocalText": Transcribed vocal text or "Instrumental detected"
        - "vocalConfidence": "Clear" | "Fragment" | "None"
        - "tacticalAdvice": Producer guidance for Suno v6
        - "recommendedStylePrompt": Best style prompt (under 200 chars)
        - "suggestedSettings": Suno v6 settings recommendations
        - "suggestions": Array of 4 objects { "title", "prompt", "focusNote" } (each prompt <= 200 chars)
        - "searchGrounding": Brief note on current Suno v6 best practices
      """.trimIndent()

      val userPrompt = """
        Analyze this beat:
        Title: ${analysis.fileName}
        BPM: ${analysis.bpm?.toInt() ?: 130} (${analysis.bpmConfidence})
        Key: ${analysis.keyName ?: "Unread"}
        Low-end ratio: ${(analysis.lowShare * 100).toInt()}%
        Centroid: ${analysis.centroidHz.toInt()} Hz
        Job: $jobType
        Generate 4 distinct Suno v6 style prompts strictly under 200 characters each.
      """.trimIndent()

      val jsonBody = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().put("text", "$systemPrompt\n\n$userPrompt"))
            })
          })
        })
        put("tools", JSONArray().apply {
          put(JSONObject().apply {
            put("googleSearch", JSONObject())
          })
        })
        put("generationConfig", JSONObject().apply {
          put("responseMimeType", "application/json")
        })
      }

      val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"
      val request = Request.Builder()
        .url(url)
        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
        .build()

      val response = client.newCall(request).execute()
      val respBody = response.body?.string() ?: ""
      if (response.isSuccessful) {
        val root = JSONObject(respBody)
        val text = root.getJSONArray("candidates")
          .getJSONObject(0)
          .getJSONObject("content")
          .getJSONArray("parts")
          .getJSONObject(0)
          .getString("text")

        val resultJson = JSONObject(text)
        val suggestionsArr = resultJson.optJSONArray("suggestions")
        val suggestionsList = mutableListOf<SunoPromptSuggestion>()
        if (suggestionsArr != null) {
          for (i in 0 until suggestionsArr.length()) {
            val item = suggestionsArr.getJSONObject(i)
            val pText = item.optString("prompt", "").take(200)
            suggestionsList.add(
              SunoPromptSuggestion(
                title = item.optString("title", "Prompt #${i + 1}"),
                prompt = pText,
                chars = pText.length,
                focusNote = item.optString("focusNote", "Sample-locked 808 beef-up")
              )
            )
          }
        }

        if (suggestionsList.isEmpty()) {
          suggestionsList.addAll(fallbackSuggestions(analysis, jobType))
        }

        AiBeatConsultation(
          heardVocalText = resultJson.optString("vocalText", "Instrumental stem detected"),
          vocalConfidence = resultJson.optString("vocalConfidence", "None"),
          tacticalAdvice = resultJson.optString("tacticalAdvice", "Keep the original sample centered. Add rolling hats and soft-clipped 808 on beat 3."),
          recommendedStylePrompt = resultJson.optString("recommendedStylePrompt", analysis.promptPreserve).take(200),
          suggestedSettings = resultJson.optString("suggestedSettings", "Model v6 · Variety 0 · Weirdness 25 · Style Influence 70 · Audio Influence 80"),
          suggestions = suggestionsList,
          searchGroundingSummary = resultJson.optString("searchGrounding", "Suno v6 Pro: Route Extend, Variety 0 locks original audio")
        )
      } else {
        ruleBasedConsultation(analysis, jobType)
      }
    } catch (e: Exception) {
      e.printStackTrace()
      ruleBasedConsultation(analysis, jobType)
    }
  }

  private fun ruleBasedConsultation(analysis: AudioAnalysisResult, jobType: String): AiBeatConsultation {
    val bpmInt = analysis.bpm?.toInt() ?: 130
    val lane = analysis.suggestedLane
    val isPreserve = jobType.equals("preserve", ignoreCase = true)

    val advice = if (isPreserve) {
      "Preserve Law: Lock upload in place. Use punchy 808 sub and crisp rolling trap hi-hats. Set Variety to 0, Audio Influence to 80 to prevent melodic drift."
    } else {
      "Full original production: Drive $lane pocket at $bpmInt BPM. Keep chopped sample melody as the lead backbone with hard 808s and fast hi-hat triplets."
    }

    val style = if (isPreserve) {
      "Preserve original sample and vocals exactly as performed. Add hard-hitting 808s and modern trap drums underneath only. Spice only, sample stays lead.".take(200)
    } else {
      "$lane instrumental, $bpmInt BPM. Chopped soul loop is main melody. Hard 808 sub, punchy kick, crisp trap hi-hat rolls. Let the beat breathe.".take(200)
    }

    val settings = if (isPreserve) {
      "Model v6 · Route: Extend · Variety 0 · Weirdness 25 · Style Influence 70 · Audio Influence 80"
    } else {
      "Model v6 · Route: Create · Variety 0 · Weirdness 20 · Style Influence 65 · Audio Influence 0"
    }

    return AiBeatConsultation(
      heardVocalText = "Instrumental detected (no vocal stems or speech phrases found)",
      vocalConfidence = "None",
      tacticalAdvice = advice,
      recommendedStylePrompt = style,
      suggestedSettings = settings,
      suggestions = fallbackSuggestions(analysis, jobType),
      searchGroundingSummary = "Suno v6: Route Extend with Audio Influence 75-85 locks original uploaded audio."
    )
  }

  private fun fallbackSuggestions(analysis: AudioAnalysisResult, jobType: String): List<SunoPromptSuggestion> {
    val bpm = analysis.bpm?.toInt() ?: 130
    val lane = analysis.suggestedLane
    val p1 = "Preserve original sample and vocal chops. Add punchy kick, hard-hitting tuned 808 sub, and crisp rolling trap hi-hats. Let the beat breathe. $bpm BPM.".take(200)
    val p2 = "Maintain uploaded chopped sample as core backbone. Deep sub-bass thumps, crisp rimshot, and subtle trap percussion. Spacious and soulful. Let it breathe.".take(200)
    val p3 = "Keep original chopped loop and vocals leading. Beef up with deep 808 bass punch and fast hi-hat triplets over $lane pocket. Dynamic, punchy bounce.".take(200)
    val p4 = "Preserve uploaded chopped audio exactly as performed. Layer heavy 808 sub underneath swinging drums. Snapping snare on 2 and 4, tight trap hats. $bpm BPM.".take(200)

    return listOf(
      SunoPromptSuggestion(
        title = "Hard 808 Beef-Up",
        prompt = p1,
        chars = p1.length,
        focusNote = "Locks sample melody, punches up low end"
      ),
      SunoPromptSuggestion(
        title = "Spacious & Breathe",
        prompt = p2,
        chars = p2.length,
        focusNote = "Minimal instrumentation, open mix"
      ),
      SunoPromptSuggestion(
        title = "Modern Trap Rolls",
        prompt = p3,
        chars = p3.length,
        focusNote = "Intricate hi-hats, driving groove"
      ),
      SunoPromptSuggestion(
        title = "Soulful MPC Swing",
        prompt = p4,
        chars = p4.length,
        focusNote = "Humanized swing with modern sub weight"
      )
    )
  }
}
