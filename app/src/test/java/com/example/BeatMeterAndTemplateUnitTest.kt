package com.example

import com.example.audio.AudioAnalysisResult
import com.example.audio.BeatIntensityCalculator
import com.example.data.model.JobType
import com.example.data.repository.PromptTemplateRepository
import org.junit.Assert.*
import org.junit.Test

class BeatMeterAndTemplateUnitTest {

  @Test
  fun beat_meter_complexity_and_intensity_scale_with_parameters() {
    val minimal = BeatIntensityCalculator.compute(
      variety = 0,
      weirdness = 0,
      styleInfluence = 15,
      audioInfluence = 0,
      stylePrompt = "Chill beat",
      lyricsMap = "[Intro]\n[End]",
      excludeText = "",
      activeLane = "Jazz rap / lo-fi",
      jobType = JobType.ORIGINAL,
      audioAnalysis = null
    )

    val maximal = BeatIntensityCalculator.compute(
      variety = 80,
      weirdness = 90,
      styleInfluence = 95,
      audioInfluence = 90,
      stylePrompt = "Aggressive rage trap hyper saw distorted 808 glides double-time hi-hat rolls layered textures crisp stereo punch",
      lyricsMap = "[Intro]\nLine 1\n[Build]\nLine 2\n[Drop]\nLine 3\n[Verse 1]\nLine 4\n[Bridge]\nLine 5\n[Drop 2]\nLine 6\n[Outro]\nLine 7\n[End]",
      excludeText = "muffled, muddy low-end, dull snare, clipping, flat mono, vocals",
      activeLane = "Rage",
      jobType = JobType.PRESERVE,
      audioAnalysis = null
    )

    assertEquals(12, minimal.bandTargets.size)
    assertEquals(12, maximal.bandTargets.size)
    assertFalse(minimal.audioDriven)
    assertTrue(
      "Maximal parameters should yield higher intensity (${maximal.intensityScore}) than minimal (${minimal.intensityScore})",
      maximal.intensityScore > minimal.intensityScore
    )
    assertTrue(
      "Maximal parameters should yield higher complexity (${maximal.complexityScore}) than minimal (${minimal.complexityScore})",
      maximal.complexityScore > minimal.complexityScore
    )
    assertEquals("MAXIMAL", maximal.tierLabel)
    assertTrue(minimal.bandTargets.all { it in 0.05f..1.0f })
    assertTrue(maximal.bandTargets.all { it in 0.05f..1.0f })
  }

  @Test
  fun beat_meter_blends_real_audio_analysis_when_present() {
    val analyzed = AudioAnalysisResult(
      fileName = "sub_heavy.wav",
      durationSec = 12.0,
      bpm = 144.0,
      bpmConfidence = "high",
      keyName = "F minor",
      keyClear = true,
      rmsDb = -10.5,
      peakDb = -1.2,
      lowShare = 0.72,
      centroidHz = 3600.0,
      onsetRate = 4.8,
      suggestedLane = "Trap",
      pocketLabel = "144 BPM · Bright · Sub-heavy",
      promptPreserve = "Preserve...",
      promptOriginal = "Original..."
    )

    val snapshot = BeatIntensityCalculator.compute(
      variety = 10,
      weirdness = 25,
      styleInfluence = 70,
      audioInfluence = 85,
      stylePrompt = "Preserve sample and add trap drums",
      lyricsMap = "[Intro]\n[Drop]\n[End]",
      excludeText = "muddy mix",
      activeLane = "Trap",
      jobType = JobType.PRESERVE,
      audioAnalysis = analyzed
    )

    assertTrue(snapshot.audioDriven)
    assertEquals(144.0, snapshot.effectiveBpm, 0.01)
    assertTrue(snapshot.summaryText.contains("analyzed audio"))
    assertTrue("Sub-heavy audio should energize low band 0", snapshot.bandTargets[0] > 0.55f)
  }

  @Test
  fun beat_meter_animated_sampler_produces_bounded_bands_without_overflow() {
    val snapshot = BeatIntensityCalculator.compute(
      variety = 25,
      weirdness = 40,
      styleInfluence = 75,
      audioInfluence = 80,
      stylePrompt = "Dark synthwave neon pulse 808",
      lyricsMap = "[Intro]\n[Drop]\n[End]",
      excludeText = "",
      activeLane = "Dark trap",
      jobType = JobType.ORIGINAL,
      audioAnalysis = null
    )
    val buffer = FloatArray(12)
    BeatIntensityCalculator.sampleAnimatedBands(
      snapshot = snapshot,
      timeSec = 1.25f,
      sensitivity = 2.0f,
      isPlayingOrLive = true,
      outBands = buffer
    )
    assertTrue(buffer.all { it in 0.05f..1.0f })
  }

  @Test
  fun starter_templates_include_lofi_and_dark_synthwave_and_filter_cleanly() {
    val starters = PromptTemplateRepository.starterTemplates(1_000_000L)
    assertTrue(starters.any { it.category == "Lo-Fi Hip Hop" })
    assertTrue(starters.any { it.category == "Dark Synthwave" })

    val darkFiltered = PromptTemplateRepository.filterTemplates(
      templates = starters,
      category = "Dark Synthwave",
      query = ""
    )
    assertEquals(2, darkFiltered.size)
    assertTrue(darkFiltered.all { it.category == "Dark Synthwave" })

    val searchMatched = PromptTemplateRepository.filterTemplates(
      templates = starters,
      category = "All",
      query = "rhodes vinyl"
    )
    assertEquals(1, searchMatched.size)
    assertEquals("Lo-Fi Hip Hop", searchMatched.first().category)
  }
}
