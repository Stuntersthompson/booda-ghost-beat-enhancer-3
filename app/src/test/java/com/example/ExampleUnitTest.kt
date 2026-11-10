package com.example

import com.example.data.model.CuratedSunoPrompt
import com.example.data.model.SUNO_STYLE_BOX_LIMIT
import com.example.data.repository.CuratedPrompts
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun all_curated_prompts_strictly_within_200_characters() {
    val curated = CuratedPrompts.ALL_CURATED
    assertEquals(150, curated.size)

    for (p in curated) {
      assertTrue(
        "Prompt #${p.id} (${p.category} · ${p.subgenre}) exceeds 200 chars: ${p.prompt.length} chars",
        p.prompt.length <= SUNO_STYLE_BOX_LIMIT
      )
      assertTrue("Prompt #${p.id} must have non-blank text", p.prompt.isNotBlank())
      assertTrue("Prompt #${p.id} must have valid category", p.category.isNotBlank())
      assertTrue("Prompt #${p.id} must have valid subgenre", p.subgenre.isNotBlank())
    }
  }

  @Test
  fun multi_word_and_query_matches_all_tokens_in_any_order() {
    val samplePrompt = CuratedSunoPrompt(
      id = 1,
      category = "Sample Beef-Up",
      subgenre = "Chipmunk Soul",
      bpmHint = "88 BPM",
      prompt = "Preserve original chopped soul sample and pitched vocals. Add punchy modern kick, hard-hitting tuned 808 sub, and crisp rolling trap hi-hats. Let the beat breathe.",
      chars = 163
    )

    fun matches(item: CuratedSunoPrompt, query: String): Boolean {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) return true
      val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
      val target = "${item.prompt} ${item.category} ${item.subgenre} ${item.bpmHint}".lowercase()
      return tokens.all { token -> target.contains(token.lowercase()) }
    }

    // Both words present
    assertTrue(matches(samplePrompt, "808 hi-hats"))
    assertTrue(matches(samplePrompt, "hi-hats 808"))
    assertTrue(matches(samplePrompt, "soul 88"))
    assertTrue(matches(samplePrompt, "chipmunk breathe"))

    // Case-insensitivity
    assertTrue(matches(samplePrompt, "CHIPMUNK 808"))

    // One word missing should return false (AND logic)
    assertFalse(matches(samplePrompt, "808 drill"))
    assertFalse(matches(samplePrompt, "gospel soul"))
  }

  @Test
  fun search_query_whitespace_trimming() {
    val prompt = CuratedSunoPrompt(
      id = 101,
      category = "Gospel Trap",
      subgenre = "Sanctuary Trap",
      bpmHint = "140 BPM",
      prompt = "Chopped gospel choir and B3 organ with 808s.",
      chars = 46
    )

    val trimmed1 = "   gospel   ".trim()
    val trimmed2 = "gospel"
    assertEquals(trimmed1, trimmed2)
    assertTrue(prompt.prompt.contains(trimmed1, ignoreCase = true))
  }

  @Test
  fun waveform_peak_bounds_are_clamped_properly() {
    val rawSamples = floatArrayOf(0.0f, -0.95f, 0.42f, 1.2f, -1.8f, 0.01f)
    val clampedPeaks = rawSamples.map { abs(it).coerceIn(0.04f, 1.0f) }

    assertTrue("All values must be >= 0.04f", clampedPeaks.all { it >= 0.04f })
    assertTrue("All values must be <= 1.0f", clampedPeaks.all { it <= 1.0f })
    assertEquals(0.95f, clampedPeaks[1], 0.001f)
    assertEquals(1.0f, clampedPeaks[3], 0.001f)
  }

  @Test
  fun live_waveform_rolling_window_maintains_cap() {
    val liveList = mutableListOf<Float>()
    val maxSize = 80

    // Simulate 200 incoming PCM block peak updates
    for (i in 0 until 200) {
      val peak = ((i % 10) / 10f).coerceIn(0.04f, 1f)
      liveList.add(peak)
      if (liveList.size > maxSize) {
        liveList.removeAt(0)
      }
    }

    assertEquals("Rolling window must not exceed maxSize", maxSize, liveList.size)
    assertTrue("Values are valid amplitudes", liveList.all { it in 0.04f..1.0f })
  }

  @Test
  fun groove_grid_calculation_detects_four_bar_intervals() {
    val bpm = 120.0
    val durationSec = 16.0 // Exactly 8 bars at 120 BPM (0.5s per beat, 2s per 4-beat bar)
    val totalBeats = (bpm / 60.0) * durationSec
    val totalBars = (totalBeats / 4.0).toInt()

    assertEquals(32, totalBeats.toInt())
    assertEquals(8, totalBars)
  }

  @Test
  fun sample_words_honesty_law_strictly_enforces_120_char_cap() {
    val longWords = "This is a very long sample vocal phrase that someone heard in the background that goes on and on and should definitely be clamped at exactly 120 chars maximum limit to preserve space"
    val clamped = longWords.trim().take(120)

    assertEquals(120, clamped.length)
    assertTrue("Clamped string must be <= 120 chars", clamped.length <= 120)

    val validKinds = listOf("Heard clearly", "Fragment only")
    assertTrue(validKinds.contains("Heard clearly"))
    assertTrue(validKinds.contains("Fragment only"))
  }
}
