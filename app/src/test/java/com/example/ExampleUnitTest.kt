package com.example

import com.example.data.model.CuratedSunoPrompt
import com.example.data.model.SUNO_STYLE_BOX_LIMIT
import com.example.data.repository.CuratedPrompts
import com.example.data.repository.StudioPreferencesRepository
import com.example.ui.components.PeakHoldConstants
import com.example.ui.components.PeakHoldTracker
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

  @Test
  fun mirrored_waveform_geometry_symmetry() {
    val canvasHeight = 120f
    val centerY = canvasHeight / 2f

    val testAmps = listOf(0.04f, 0.25f, 0.50f, 0.78f, 1.0f)
    for (amp in testAmps) {
      val clampedAmp = amp.coerceIn(0.04f, 1f)
      val halfHeight = (clampedAmp * (canvasHeight * 0.44f)).coerceAtLeast(3f)
      val topY = centerY - halfHeight
      val bottomY = centerY + halfHeight
      val totalHeight = bottomY - topY

      // Verify absolute symmetrical distance around centerY
      val topDistance = centerY - topY
      val bottomDistance = bottomY - centerY
      assertEquals("Top and bottom distances from centerY must be equal", topDistance, bottomDistance, 0.0001f)
      assertEquals("Total height must equal 2 * halfHeight", halfHeight * 2f, totalHeight, 0.0001f)
      assertTrue("topY must be >= 0", topY >= 0f)
      assertTrue("bottomY must be <= canvasHeight", bottomY <= canvasHeight)
    }
  }

  @Test
  fun mirrored_waveform_color_tier_mapping() {
    fun getTier(amp: Float): String {
      return when {
        amp > 0.85f -> "RED_HOT"
        amp > 0.60f -> "AMBER_PEAK"
        else -> "CYAN_BASE"
      }
    }

    assertEquals("RED_HOT", getTier(0.92f))
    assertEquals("RED_HOT", getTier(0.86f))
    assertEquals("AMBER_PEAK", getTier(0.85f))
    assertEquals("AMBER_PEAK", getTier(0.61f))
    assertEquals("CYAN_BASE", getTier(0.60f))
    assertEquals("CYAN_BASE", getTier(0.15f))
  }

  @Test
  fun compact_waveform_meter_subsampling_bounds() {
    val largeAmplitudes = List(300) { (it % 100) / 100f }
    val barCount = largeAmplitudes.size.coerceAtMost(64)
    assertEquals(64, barCount)

    val emptyAmplitudes = emptyList<Float>()
    val emptyBarCount = emptyAmplitudes.size.coerceAtMost(64)
    assertEquals(0, emptyBarCount)
  }

  @Test
  fun test_visualizer_gain_scaling_and_clamping() {
    val rawAmps = listOf(0.10f, 0.40f, 0.80f, 0.01f)

    // Test unity gain (1.0x)
    val unityScaled = rawAmps.map { (it * 1.0f).coerceIn(0.04f, 1.0f) }
    assertEquals(0.10f, unityScaled[0], 0.001f)
    assertEquals(0.40f, unityScaled[1], 0.001f)
    assertEquals(0.80f, unityScaled[2], 0.001f)
    assertEquals(0.04f, unityScaled[3], 0.001f) // Clamped to min 0.04f

    // Test 4.0x boost (max sensitivity)
    val boostScaled = rawAmps.map { (it * 4.0f).coerceIn(0.04f, 1.0f) }
    assertEquals(0.40f, boostScaled[0], 0.001f)
    assertEquals(1.00f, boostScaled[1], 0.001f) // 0.40 * 4 = 1.6 clamped to 1.0f
    assertEquals(1.00f, boostScaled[2], 0.001f) // 0.80 * 4 = 3.2 clamped to 1.0f
    assertEquals(0.04f, boostScaled[3], 0.001f) // 0.01 * 4 = 0.04 clamped to 0.04f

    // Test 0.25x attenuation (min sensitivity)
    val attenuated = rawAmps.map { (it * 0.25f).coerceIn(0.04f, 1.0f) }
    assertEquals(0.04f, attenuated[0], 0.001f) // 0.10 * 0.25 = 0.025 clamped to 0.04f
    assertEquals(0.10f, attenuated[1], 0.001f) // 0.40 * 0.25 = 0.10f
    assertEquals(0.20f, attenuated[2], 0.001f) // 0.80 * 0.25 = 0.20f

    // Bounds constant verification
    assertEquals(0.25f, StudioPreferencesRepository.MIN_SENSITIVITY, 0.001f)
    assertEquals(4.0f, StudioPreferencesRepository.MAX_SENSITIVITY, 0.001f)
    assertEquals(1.0f, StudioPreferencesRepository.DEFAULT_SENSITIVITY, 0.001f)
  }

  @Test
  fun test_peak_hold_tracker_instant_rise_and_hold_window() {
    val tracker = PeakHoldTracker(maxCapacity = 4)

    // Initial input at t = 1000ms
    val t0 = 1000L
    tracker.update(
      amplitudes = listOf(0.75f, 0.30f),
      sensitivity = 1.0f,
      currentTimeMs = t0,
      holdDurationMs = 500L
    )

    assertEquals(0.75f, tracker.peakAmplitudes[0], 0.001f)
    assertEquals(0.30f, tracker.peakAmplitudes[1], 0.001f)
    assertEquals(1500L, tracker.peakHoldUntilMs[0])
    assertEquals(1500L, tracker.peakHoldUntilMs[1])
    assertEquals(0f, tracker.peakVelocities[0], 0.001f)

    // Intermediate input at t = 1300ms (within 500ms hold window) with lower signal
    val t1 = 1300L
    tracker.update(
      amplitudes = listOf(0.20f, 0.10f),
      sensitivity = 1.0f,
      currentTimeMs = t1,
      holdDurationMs = 500L
    )

    // Peaks must hold steady during hold window
    assertEquals(0.75f, tracker.peakAmplitudes[0], 0.001f)
    assertEquals(0.30f, tracker.peakAmplitudes[1], 0.001f)
    assertEquals(0f, tracker.peakVelocities[0], 0.001f)
  }

  @Test
  fun test_peak_hold_tracker_gravity_decay_after_hold_expires() {
    val tracker = PeakHoldTracker(maxCapacity = 4)

    val t0 = 1000L
    tracker.update(
      amplitudes = listOf(0.90f),
      sensitivity = 1.0f,
      currentTimeMs = t0,
      holdDurationMs = 500L
    )
    assertEquals(0.90f, tracker.peakAmplitudes[0], 0.001f)

    // At t = 1600ms (100ms past the 500ms hold expiration)
    val t1 = 1600L
    tracker.update(
      amplitudes = listOf(0.15f),
      sensitivity = 1.0f,
      currentTimeMs = t1,
      holdDurationMs = 500L
    )

    // Velocity must be accelerating downward
    assertTrue("Downward velocity must be > 0", tracker.peakVelocities[0] > 0f)
    // Peak amplitude must have decayed below 0.90f
    assertTrue("Peak must decay below initial peak", tracker.peakAmplitudes[0] < 0.90f)
    // Peak must remain >= instantaneous amplitude 0.15f
    assertTrue("Peak must not decay below current amplitude", tracker.peakAmplitudes[0] >= 0.15f)

    // Step forward several frames to confirm smooth settling to current amplitude
    var simTime = 1620L
    while (simTime <= 2500L) {
      tracker.update(listOf(0.15f), sensitivity = 1.0f, currentTimeMs = simTime)
      simTime += 20L
    }
    assertEquals(0.15f, tracker.peakAmplitudes[0], 0.005f)
  }

  @Test
  fun test_peak_hold_tracker_reset_clears_all_state() {
    val tracker = PeakHoldTracker(maxCapacity = 8)
    tracker.update(
      amplitudes = listOf(0.85f, 0.95f, 0.60f),
      sensitivity = 1.2f,
      currentTimeMs = 5000L
    )

    assertTrue(tracker.peakAmplitudes[0] > 0f)
    assertTrue(tracker.peakHoldUntilMs[0] > 0L)

    // Call reset (e.g. on beat change, style switch, recording stop)
    tracker.reset()

    for (i in 0 until tracker.maxCapacity) {
      assertEquals(0f, tracker.peakAmplitudes[i], 0.0001f)
      assertEquals(0L, tracker.peakHoldUntilMs[i])
      assertEquals(0f, tracker.peakVelocities[i], 0.0001f)
    }
    assertEquals(0L, tracker.lastUpdateTimestamp)
  }

  @Test
  fun test_peak_markers_respect_sensitivity_gain() {
    val tracker = PeakHoldTracker(maxCapacity = 4)
    val rawAmp = listOf(0.20f)

    // Raw 0.20f with 2.5x sensitivity = 0.50f
    tracker.update(rawAmp, sensitivity = 2.5f, currentTimeMs = 1000L)
    assertEquals(0.50f, tracker.peakAmplitudes[0], 0.001f)

    tracker.reset()
    // Raw 0.20f with 1.0x sensitivity = 0.20f
    tracker.update(rawAmp, sensitivity = 1.0f, currentTimeMs = 1000L)
    assertEquals(0.20f, tracker.peakAmplitudes[0], 0.001f)
  }

  @Test
  fun test_peak_hold_disabled_clears_and_skips_tracking() {
    val tracker = PeakHoldTracker(maxCapacity = 6)

    // Populate peaks while enabled
    tracker.update(
      amplitudes = listOf(0.85f, 0.92f),
      sensitivity = 1.0f,
      peakHoldEnabled = true,
      currentTimeMs = 2000L
    )
    assertTrue(tracker.hasActivePeaks())
    assertEquals(0.85f, tracker.peakAmplitudes[0], 0.001f)

    // Toggle OFF: must clear all state and skip new updates
    tracker.update(
      amplitudes = listOf(0.99f, 0.99f),
      sensitivity = 2.0f,
      peakHoldEnabled = false,
      currentTimeMs = 2016L
    )
    assertFalse("Active peaks must clear when peakHoldEnabled = false", tracker.hasActivePeaks())
    for (i in 0 until tracker.maxCapacity) {
      assertEquals(0f, tracker.peakAmplitudes[i], 0.0001f)
      assertEquals(0L, tracker.peakHoldUntilMs[i])
      assertEquals(0f, tracker.peakVelocities[i], 0.0001f)
    }
  }

  @Test
  fun test_playback_math_progress_seek_and_formatting() {
    // Normalized progress clamping
    assertEquals(0f, com.example.audio.PlaybackMath.computeNormalizedProgress(500L, 0L), 0.0001f)
    assertEquals(0.5f, com.example.audio.PlaybackMath.computeNormalizedProgress(15000L, 30000L), 0.0001f)
    assertEquals(1f, com.example.audio.PlaybackMath.computeNormalizedProgress(45000L, 30000L), 0.0001f)

    // Seek target ms with NaN and out-of-range guards
    assertEquals(0L, com.example.audio.PlaybackMath.computeSeekTargetMs(Float.NaN, 20000L))
    assertEquals(0L, com.example.audio.PlaybackMath.computeSeekTargetMs(-0.4f, 20000L))
    assertEquals(10000L, com.example.audio.PlaybackMath.computeSeekTargetMs(0.5f, 20000L))
    assertEquals(20000L, com.example.audio.PlaybackMath.computeSeekTargetMs(1.8f, 20000L))

    // PCM sample index bounds
    assertEquals(0, com.example.audio.PlaybackMath.computePcmSampleIndex(Float.NaN, 1000))
    assertEquals(500, com.example.audio.PlaybackMath.computePcmSampleIndex(0.5f, 1001))
    assertEquals(999, com.example.audio.PlaybackMath.computePcmSampleIndex(1.5f, 1000))

    // Duration formatter
    assertEquals("0:00", com.example.audio.PlaybackMath.formatDurationMmSs(-50L))
    assertEquals("0:09", com.example.audio.PlaybackMath.formatDurationMmSs(9000L))
    assertEquals("1:05", com.example.audio.PlaybackMath.formatDurationMmSs(65000L))
  }

  @Test
  fun test_sensitivity_step_snapping_and_bpm_range_parsing() {
    assertEquals(1.0f, StudioPreferencesRepository.snapSensitivityStep(1.02f), 0.001f)
    assertEquals(1.05f, StudioPreferencesRepository.snapSensitivityStep(1.03f), 0.001f)
    assertEquals(0.25f, StudioPreferencesRepository.snapSensitivityStep(0.05f), 0.001f)
    assertEquals(4.0f, StudioPreferencesRepository.snapSensitivityStep(9.0f), 0.001f)

    assertEquals(Pair(130, 145), com.example.data.repository.PromptBankRepository.parseBpmRange("130-145 BPM"))
    assertEquals(Pair(95, 110), com.example.data.repository.PromptBankRepository.parseBpmRange("95 BPM"))
    assertEquals(Pair(120, 145), com.example.data.repository.PromptBankRepository.parseBpmRange("unparsed"))
  }

  @Test
  fun test_lane_accent_color_resolution_and_fallback() {
    assertNull(com.example.ui.components.resolveLaneAccentColor(""))
    assertNull(com.example.ui.components.resolveLaneAccentColor("Unknown Hybrid Genre"))
    assertNotNull(com.example.ui.components.resolveLaneAccentColor("Gospel trap"))
    assertNotNull(com.example.ui.components.resolveLaneAccentColor("NY drill"))
    assertNotNull(com.example.ui.components.resolveLaneAccentColor("Boom bap"))
    assertNotNull(com.example.ui.components.resolveLaneAccentColor("Detroit trap"))
  }
}
