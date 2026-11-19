package com.example

import com.example.audio.AudioAnalysisResult
import com.example.audio.BeatIntensityCalculator
import com.example.data.model.ComplianceRuleId
import com.example.data.model.ComplianceSnapshot
import com.example.data.model.EXCLUDE_LIMIT
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.LYRICS_LIMIT
import com.example.data.model.STYLE_LIMIT
import com.example.data.model.SunoComplianceAuditor
import org.junit.Assert.*
import org.junit.Test

class FullAuditUpgradeUnitTest {

  @Test
  fun beat_meter_reports_honest_parameter_estimate_when_audio_unmeasured() {
    val unreadAudio = AudioAnalysisResult(
      fileName = "corrupt.wav",
      durationSec = 0.0,
      bpm = null,
      bpmConfidence = "unread",
      keyName = null,
      keyClear = false,
      rmsDb = -80.0,
      peakDb = -80.0,
      lowShare = 0.0,
      centroidHz = 0.0,
      onsetRate = 0.0,
      suggestedLane = "Trap",
      pocketLabel = "Unread · No decodable audio",
      promptPreserve = "$LOCK_LINE. Add Trap drums.",
      promptOriginal = "Trap instrumental.",
      isAudioMeasured = false,
      isSpectralMeasured = false,
      measuredSpectrumBands = emptyList(),
      analysisNote = "Decode failed"
    )

    val snapshot = BeatIntensityCalculator.compute(
      variety = 0,
      weirdness = 25,
      styleInfluence = 70,
      audioInfluence = 80,
      stylePrompt = "$LOCK_LINE. Add Trap drums.",
      lyricsMap = "[Intro]\n[End]",
      excludeText = "no vocals",
      activeLane = "Trap",
      jobType = JobType.PRESERVE,
      audioAnalysis = unreadAudio
    )

    assertFalse(snapshot.audioDriven)
    assertFalse(snapshot.isMeasuredAudioSpectrum)
    assertTrue(snapshot.summaryText.contains("parameter estimate"))
    assertTrue(snapshot.summaryText.contains("no audio measured"))
  }

  @Test
  fun beat_meter_uses_real_12_band_fft_spectrum_when_measured() {
    val realSpectrum = listOf(0.92f, 0.84f, 0.71f, 0.50f, 0.44f, 0.39f, 0.36f, 0.31f, 0.25f, 0.20f, 0.14f, 0.10f)
    val measured = AudioAnalysisResult(
      fileName = "real_fft.wav",
      durationSec = 10.0,
      bpm = 136.0,
      bpmConfidence = "high",
      keyName = "G minor",
      keyClear = true,
      rmsDb = -11.0,
      peakDb = -0.4,
      lowShare = 0.55,
      centroidHz = 2600.0,
      onsetRate = 3.5,
      suggestedLane = "Trap",
      pocketLabel = "136 BPM · Balanced · Sub-heavy",
      promptPreserve = "$LOCK_LINE. Add Trap drums.",
      promptOriginal = "Trap instrumental, 136 BPM.",
      isAudioMeasured = true,
      isSpectralMeasured = true,
      measuredSpectrumBands = realSpectrum
    )

    val snapshot = BeatIntensityCalculator.compute(
      variety = 0,
      weirdness = 25,
      styleInfluence = 70,
      audioInfluence = 80,
      stylePrompt = "$LOCK_LINE. Add Trap drums.",
      lyricsMap = "[Intro]\n[End]",
      excludeText = "",
      activeLane = "Trap",
      jobType = JobType.PRESERVE,
      audioAnalysis = measured
    )

    assertTrue(snapshot.audioDriven)
    assertTrue(snapshot.isMeasuredAudioSpectrum)
    assertTrue(snapshot.summaryText.contains("FFT spectrum"))
    assertEquals(12, snapshot.bandTargets.size)
    assertTrue("Sub 32Hz band should reflect high FFT energy", snapshot.bandTargets[0] > 0.75f)
    assertTrue("Sub 32Hz energy should exceed 16kHz air band", snapshot.bandTargets[0] > snapshot.bandTargets[11])
  }

  @Test
  fun suno_compliance_auditor_detects_and_strips_artist_names_while_keeping_5_rule_contract() {
    val dirtyStyle = "Metro Boomin type beat in the style of Travis Scott with heavy distorted 808 and dark bells"
    val detected = SunoComplianceAuditor.findArtistNamesInStyle(dirtyStyle)
    assertTrue(detected.any { it.equals("Metro Boomin", ignoreCase = true) })
    assertTrue(detected.any { it.equals("Travis Scott", ignoreCase = true) })

    val cleaned = SunoComplianceAuditor.stripArtistNames(dirtyStyle)
    assertTrue(SunoComplianceAuditor.findArtistNamesInStyle(cleaned).isEmpty())
    assertFalse(cleaned.contains("type beat", ignoreCase = true))
    assertFalse(cleaned.contains("in the style of", ignoreCase = true))
    assertTrue("Sonic descriptors should stay intact: $cleaned", cleaned.contains("808") && cleaned.contains("dark bells"))

    val report = SunoComplianceAuditor.audit(
      jobType = JobType.PRESERVE,
      stylePrompt = dirtyStyle,
      lyricsMap = "[Intro]\n[End]",
      excludeText = "muddy mix",
      variety = 0,
      audioInfluence = 80
    )
    assertEquals("Core rule count contract must remain 5", 5, report.totalCount)
    assertFalse(report.noArtistNames)
    assertFalse(report.allPassed)

    val fixed = SunoComplianceAuditor.autoFixAll(
      snapshot = ComplianceSnapshot(
        jobType = JobType.PRESERVE,
        stylePrompt = dirtyStyle,
        lyricsMap = "[Intro]",
        excludeText = "muddy",
        variety = 20,
        weirdness = 25,
        styleInfluence = 70,
        audioInfluence = 40
      ),
      activeLane = "Dark trap"
    )

    val postReport = SunoComplianceAuditor.audit(
      jobType = fixed.jobType,
      stylePrompt = fixed.stylePrompt,
      lyricsMap = fixed.lyricsMap,
      excludeText = fixed.excludeText,
      variety = fixed.variety,
      audioInfluence = fixed.audioInfluence
    )
    assertEquals(5, postReport.passedCount)
    assertEquals(5, postReport.totalCount)
    assertTrue(postReport.allPassed)
    assertTrue(postReport.noArtistNames)
    assertTrue(fixed.stylePrompt.startsWith(LOCK_LINE))
    assertTrue(fixed.lyricsMap.endsWith("[End]"))
    assertTrue(fixed.stylePrompt.length <= STYLE_LIMIT)
    assertTrue(fixed.lyricsMap.length <= LYRICS_LIMIT)
    assertTrue(fixed.excludeText.length <= EXCLUDE_LIMIT)
    assertEquals(1000, STYLE_LIMIT)
    assertEquals(5000, LYRICS_LIMIT)
    assertEquals(1000, EXCLUDE_LIMIT)
  }

  @Test
  fun suno_compliance_individual_rule_enum_count_matches_auditor_checks() {
    val report = SunoComplianceAuditor.audit(
      jobType = JobType.ORIGINAL,
      stylePrompt = "Crisp boom bap vinyl drums",
      lyricsMap = "[Intro]\n[End]",
      excludeText = "",
      variety = 0,
      audioInfluence = 0
    )
    assertEquals(ComplianceRuleId.entries.size, report.checks.size)
    assertEquals(5, report.passedCount)
    assertTrue(report.allPassed)
  }
}
