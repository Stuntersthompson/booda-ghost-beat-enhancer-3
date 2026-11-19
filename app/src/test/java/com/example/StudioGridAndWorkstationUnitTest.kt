package com.example

import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.SIMPLE_WORKING_LIMIT
import com.example.data.model.STYLE_LIMIT
import com.example.data.model.StudioLaneLayerState
import com.example.data.model.StudioLayerTag
import com.example.data.model.SunoComplianceAuditor
import com.example.data.model.SunoWorkstationCatalog
import com.example.data.model.SunoWorkstationPromptEngine
import org.junit.Assert.*
import org.junit.Test

class StudioGridAndWorkstationUnitTest {

  @Test
  fun catalog_chips_contain_zero_artist_names_and_cover_genres_and_instruments() {
    assertTrue(SunoWorkstationCatalog.genreChips.size >= 8)
    assertTrue(SunoWorkstationCatalog.instrumentChips.size >= 8)
    SunoWorkstationCatalog.allChips.forEach { chip ->
      val detected = SunoComplianceAuditor.findArtistNamesInStyle(chip.token)
      assertTrue("Chip ${chip.label} must not contain artist names: $detected", detected.isEmpty())
    }
  }

  @Test
  fun smart_tag_toggle_adds_removes_and_preserves_lock_line() {
    val start = "$LOCK_LINE. Add Trap drums underneath only."
    val added = SunoWorkstationPromptEngine.toggleTagInStylePrompt(
      currentStyle = start,
      token = "B3 Hammond organ",
      preserveLockLine = true
    )
    assertTrue(added.added)
    assertNull(added.blockedReason)
    assertTrue(added.updatedPrompt.contains(LOCK_LINE))
    assertTrue(added.updatedPrompt.contains("B3 Hammond organ"))

    val removed = SunoWorkstationPromptEngine.toggleTagInStylePrompt(
      currentStyle = added.updatedPrompt,
      token = "B3 Hammond organ",
      preserveLockLine = true
    )
    assertTrue(removed.removed)
    assertFalse(removed.updatedPrompt.contains("B3 Hammond organ"))
    assertTrue(removed.updatedPrompt.contains(LOCK_LINE))
  }

  @Test
  fun smart_tag_toggle_enforces_1000_char_limit_without_corrupting_prompt() {
    val filler = "$LOCK_LINE. " + "clean punchy drum pocket ".repeat(37) // 989 chars
    assertTrue(filler.length <= STYLE_LIMIT)
    val outcome = SunoWorkstationPromptEngine.toggleTagInStylePrompt(
      currentStyle = filler,
      token = "chopped soul vocal texture and extra warm analog brass",
      preserveLockLine = true,
      maxChars = STYLE_LIMIT
    )
    assertFalse(outcome.added)
    assertNotNull("Exceeding 1000 chars must return explicit block reason", outcome.blockedReason)
    assertEquals("Blocked toggle must leave existing prompt untouched", filler, outcome.updatedPrompt)
  }

  @Test
  fun simple_prompt_chip_toggle_updates_style_header_and_preserves_song_structure() {
    val simple = "$LOCK_LINE. Add Trap drums underneath only.\n[Instrumental]\n[Intro]\n[End]"
    val added = SunoWorkstationPromptEngine.toggleTagInSimplePrompt(
      currentSimple = simple,
      token = "filtered Rhodes chords",
      preserveLockLine = true
    )
    assertTrue(added.added)
    assertTrue(added.updatedPrompt.contains("filtered Rhodes chords"))
    assertTrue(added.updatedPrompt.endsWith("[End]"))
    assertTrue(added.updatedPrompt.length <= SIMPLE_WORKING_LIMIT)

    val styleHeader = SunoWorkstationPromptEngine.extractSimpleStyleHeader(added.updatedPrompt)
    assertTrue(styleHeader.length <= STYLE_LIMIT)
    assertFalse("Style header must not include structure body", styleHeader.contains("[End]"))

    val toggledOff = SunoWorkstationPromptEngine.toggleTagInSimplePrompt(
      currentSimple = added.updatedPrompt,
      token = "filtered Rhodes chords",
      preserveLockLine = true
    )
    assertTrue(toggledOff.removed)
    assertFalse(toggledOff.updatedPrompt.contains("filtered Rhodes chords"))
    assertTrue(toggledOff.updatedPrompt.endsWith("[End]"))
  }

  @Test
  fun forty_one_lane_mix_synthesis_prioritizes_soloed_unmuted_layers_within_suno_rules() {
    val lanes = (1..41).map { idx ->
      StudioLaneLayerState(
        laneIndex = idx,
        laneName = "Lane $idx",
        bpmRange = "120-140 BPM",
        genreFamily = "Trap Core",
        regionTag = "Studio",
        drumTexture = "crisp kick and 808",
        isVerified = idx <= 21,
        isMuted = idx == 2,
        isSoloed = idx == 7,
        level = if (idx == 7) 0.95f else 0.70f,
        layerTag = if (idx == 7) StudioLayerTag.EIGHT_OH_EIGHT else StudioLayerTag.CANVAS_DRUMS
      )
    }

    val synthesized = SunoWorkstationPromptEngine.synthesizeBuildStyleFromLayers(
      layers = lanes,
      activeLane = "Lane 1",
      jobType = JobType.PRESERVE
    )
    assertTrue(synthesized.contains(LOCK_LINE))
    assertTrue(synthesized.contains("Lane 7"))
    assertTrue(synthesized.contains("808 Sub"))
    assertFalse(synthesized.contains("Lane 2"))
    assertTrue(synthesized.length <= STYLE_LIMIT)
    assertTrue(SunoComplianceAuditor.findArtistNamesInStyle(synthesized).isEmpty())
  }
}
