package com.example.data.model

/**
 * Production role tag assigned to each lane layer in the 41-lane ProductionStudioGrid.
 */
enum class StudioLayerTag(
  val label: String,
  val promptPhrase: String
) {
  CANVAS_DRUMS("Canvas Drums", "canvas drum pocket"),
  EIGHT_OH_EIGHT("808 Sub", "tuned 808 sub bass"),
  SAMPLE_LOCK("Sample Lock", "untouched original sample backbone"),
  TOPS_HATS("Tops / Hats", "crisp hi-hat rolls and tops"),
  FX_RISER("FX / Riser", "riser sweeps and stop-time transitions"),
  CHOP_MELODY("Melodic Layer", "chopped melodic texture layer");

  fun next(): StudioLayerTag {
    val all = entries
    return all[(ordinal + 1) % all.size]
  }
}

/**
 * Interactive mixer & arrangement state for a single lane in the 41-lane ProductionStudioGrid.
 */
data class StudioLaneLayerState(
  val laneIndex: Int,
  val laneName: String,
  val bpmRange: String,
  val genreFamily: String,
  val regionTag: String,
  val drumTexture: String,
  val isVerified: Boolean,
  val isMuted: Boolean = false,
  val isSoloed: Boolean = false,
  val level: Float = 0.80f,
  val layerTag: StudioLayerTag = StudioLayerTag.CANVAS_DRUMS
) {
  val effectivePercent: Int get() = (level.coerceIn(0f, 1f) * 100f).toInt()

  fun isAudible(anySoloed: Boolean): Boolean {
    return if (anySoloed) {
      isSoloed && !isMuted && level > 0.01f
    } else {
      !isMuted && level > 0.01f
    }
  }
}

enum class WorkstationChipCategory(val label: String) {
  GENRE("Genre"),
  INSTRUMENT("Instrument & Texture")
}

data class WorkstationTagChip(
  val id: String,
  val label: String,
  val token: String,
  val category: WorkstationChipCategory
)

object SunoWorkstationCatalog {
  val genreChips: List<WorkstationTagChip> = listOf(
    WorkstationTagChip("genre_trap", "Trap", "Trap", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_uk_drill", "UK Drill", "UK drill", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_boom_bap", "Boom Bap", "Boom bap", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_gospel_trap", "Gospel Trap", "Gospel trap", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_detroit", "Detroit Trap", "Detroit trap", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_gfunk", "G-Funk", "G-funk", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_phonk", "Dark Phonk", "Dark phonk", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_trap_soul", "Trap Soul", "Trap soul", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_rage", "Rage", "Rage", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_pluggnb", "Pluggnb", "Pluggnb", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_amapiano", "Amapiano", "Amapiano hip-hop", WorkstationChipCategory.GENRE),
    WorkstationTagChip("genre_afro_trap", "Afro-Trap", "Afro-trap", WorkstationChipCategory.GENRE)
  )

  val instrumentChips: List<WorkstationTagChip> = listOf(
    WorkstationTagChip("inst_808_sub", "Tuned 808 Sub", "tuned 808 sub", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_808_glide", "Sliding 808 Glide", "sliding 808 glides", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_crisp_snare", "Crisp Snare", "crisp snapping snare", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_triplet_hats", "Triplet Hi-Hats", "triplet hi-hat rolls", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_b3_organ", "B3 Organ", "B3 Hammond organ", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_rhodes", "Filtered Rhodes", "filtered Rhodes chords", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_soul_vocal", "Chopped Soul Vocal", "chopped soul vocal texture", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_analog_brass", "Analog Brass", "warm analog brass swells", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_cowbell", "Cowbell Lead", "cowbell melody", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_vinyl", "Vinyl Crackle", "subtle vinyl crackle", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_tape_sat", "Tape Saturation", "warm tape saturation", WorkstationChipCategory.INSTRUMENT),
    WorkstationTagChip("inst_log_drum", "Log-Drum Bass", "percussive log-drum bass", WorkstationChipCategory.INSTRUMENT)
  )

  val allChips: List<WorkstationTagChip> = genreChips + instrumentChips
}

data class TagToggleOutcome(
  val updatedPrompt: String,
  val added: Boolean,
  val removed: Boolean,
  val blockedReason: String? = null
)

/**
 * Pure workstation engine handling smart Suno v6 chip toggling, 1,000-char caps,
 * artist-name hygiene, preservation lock preservation, and 41-lane mix synthesis.
 */
object SunoWorkstationPromptEngine {

  private fun tokenBoundaryRegex(token: String): Regex {
    return Regex(
      pattern = """(?:^|([,.\n;])\s*)${Regex.escape(token.trim())}(?=$|\s*[,.\n;])""",
      options = setOf(RegexOption.IGNORE_CASE)
    )
  }

  fun isTokenActive(prompt: String, token: String): Boolean {
    if (prompt.isBlank() || token.isBlank()) return false
    return tokenBoundaryRegex(token).containsMatchIn(prompt)
  }

  fun toggleTagInStylePrompt(
    currentStyle: String,
    token: String,
    preserveLockLine: Boolean,
    maxChars: Int = STYLE_LIMIT
  ): TagToggleOutcome {
    val cleanToken = SunoComplianceAuditor.stripArtistNames(token).trim()
    if (cleanToken.isEmpty()) {
      return TagToggleOutcome(updatedPrompt = currentStyle, added = false, removed = false)
    }

    val sanitizedBase = SunoComplianceAuditor.stripArtistNames(currentStyle)
    if (isTokenActive(sanitizedBase, cleanToken)) {
      val stripped = removeTokenPreservingLock(sanitizedBase, cleanToken, preserveLockLine)
      return TagToggleOutcome(
        updatedPrompt = stripped,
        added = false,
        removed = stripped != currentStyle
      )
    }

    val trimmed = sanitizedBase.trim()
    val candidate = when {
      trimmed.isEmpty() -> {
        if (preserveLockLine) "$LOCK_LINE. $cleanToken" else cleanToken
      }
      preserveLockLine && !trimmed.contains(LOCK_LINE) -> {
        "$LOCK_LINE. ${trimmed.trimEnd(',', '.', ' ')}, $cleanToken"
      }
      else -> {
        val body = trimmed.trimEnd(',', ' ')
        if (body.endsWith('.')) "$body $cleanToken" else "$body, $cleanToken"
      }
    }

    if (candidate.length > maxChars) {
      return TagToggleOutcome(
        updatedPrompt = currentStyle,
        added = false,
        removed = false,
        blockedReason = "Cannot add \"$cleanToken\": would reach ${candidate.length}/$maxChars chars in Suno v6 Style box."
      )
    }

    return TagToggleOutcome(
      updatedPrompt = candidate,
      added = true,
      removed = false
    )
  }

  fun extractSimpleStyleHeader(simplePrompt: String): String {
    val idx = simplePrompt.indexOf('\n')
    val bracketIdx = simplePrompt.indexOf('[')
    return when {
      bracketIdx == 0 -> ""
      bracketIdx > 0 -> simplePrompt.substring(0, bracketIdx).trimEnd('\n', '\r', ' ')
      idx >= 0 -> simplePrompt.substring(0, idx).trimEnd()
      else -> simplePrompt.trim()
    }
  }

  fun toggleTagInSimplePrompt(
    currentSimple: String,
    token: String,
    preserveLockLine: Boolean,
    styleHeaderCap: Int = STYLE_LIMIT,
    totalCap: Int = SIMPLE_WORKING_LIMIT
  ): TagToggleOutcome {
    val bracketIdx = currentSimple.indexOf('[')
    val styleHeader = if (bracketIdx >= 0) {
      currentSimple.substring(0, bracketIdx).trimEnd('\n', '\r')
    } else {
      currentSimple.trim()
    }
    val structureTail = if (bracketIdx >= 0) {
      currentSimple.substring(bracketIdx)
    } else {
      ""
    }

    val headerOutcome = toggleTagInStylePrompt(
      currentStyle = styleHeader,
      token = token,
      preserveLockLine = preserveLockLine,
      maxChars = styleHeaderCap
    )
    if (headerOutcome.blockedReason != null) {
      return headerOutcome.copy(updatedPrompt = currentSimple)
    }

    val rebuilt = if (structureTail.isBlank()) {
      headerOutcome.updatedPrompt
    } else if (headerOutcome.updatedPrompt.isBlank()) {
      structureTail
    } else {
      "${headerOutcome.updatedPrompt}\n$structureTail"
    }

    if (rebuilt.length > totalCap) {
      return TagToggleOutcome(
        updatedPrompt = currentSimple,
        added = false,
        removed = false,
        blockedReason = "Cannot add \"$token\": total Simple box would reach ${rebuilt.length}/$totalCap chars."
      )
    }

    return TagToggleOutcome(
      updatedPrompt = rebuilt,
      added = headerOutcome.added,
      removed = headerOutcome.removed,
      blockedReason = null
    )
  }

  /**
   * Synthesizes a valid <= 1,000-char Suno v6 Style prompt from the active/soloed layers in the 41-lane ProductionStudioGrid.
   */
  fun synthesizeBuildStyleFromLayers(
    layers: List<StudioLaneLayerState>,
    activeLane: String,
    jobType: JobType
  ): String {
    val anySoloed = layers.any { it.isSoloed }
    val audible = layers
      .filter { it.isAudible(anySoloed) }
      .sortedWith(
        compareByDescending<StudioLaneLayerState> { it.isSoloed }
          .thenByDescending { it.laneName.equals(activeLane, ignoreCase = true) }
          .thenByDescending { it.level }
      )

    val prioritized = if (audible.isNotEmpty()) {
      audible.take(4)
    } else {
      layers.filter { it.laneName.equals(activeLane, ignoreCase = true) }.ifEmpty { layers.take(1) }
    }

    val leadLayer = prioritized.firstOrNull()
    val leadLaneName = leadLayer?.laneName ?: activeLane
    val layerSummary = prioritized.joinToString("; ") { layer ->
      val weightLabel = when {
        layer.isSoloed -> "soloed lead"
        layer.effectivePercent >= 75 -> "primary"
        layer.effectivePercent >= 40 -> "support"
        else -> "subtle"
      }
      "${layer.laneName} [${layer.layerTag.label} · ${layer.effectivePercent}% $weightLabel: ${layer.drumTexture}]"
    }

    val raw = if (jobType == JobType.PRESERVE) {
      "$LOCK_LINE. Add $leadLaneName canvas arrangement underneath only. Active layer stack: $layerSummary. Spice only, sample stays lead."
    } else {
      "$leadLaneName original production build. Active layer stack: $layerSummary. Punchy mix, clean headroom."
    }

    val cleaned = SunoComplianceAuditor.stripArtistNames(raw)
    return if (cleaned.length <= STYLE_LIMIT) {
      cleaned
    } else {
      val cut = cleaned.substring(0, STYLE_LIMIT)
      val lastSpace = cut.lastIndexOf(' ')
      if (lastSpace > (STYLE_LIMIT * 0.7).toInt()) cut.substring(0, lastSpace).trimEnd(',', ';', ' ') else cut.trimEnd()
    }
  }

  private fun removeTokenPreservingLock(
    prompt: String,
    token: String,
    preserveLockLine: Boolean
  ): String {
    val lockPlaceholder = "__BOODA_LOCK_LINE_GUARD__"
    val working = if (preserveLockLine && prompt.contains(LOCK_LINE)) {
      prompt.replace(LOCK_LINE, lockPlaceholder)
    } else {
      prompt
    }

    val removed = tokenBoundaryRegex(token).replace(working) { match ->
      val separator = match.groupValues.getOrNull(1).orEmpty()
      if (separator == ".") "." else ""
    }

    var normalized = removed
      .replace(Regex(",\\s*,+"), ",")
      .replace(Regex("\\.\\s*,+"), ".")
      .replace(Regex("\\s{2,}"), " ")
      .replace(Regex("\\s+([,.])"), "$1")
      .trim()
      .trimStart(',', ' ')
      .trimEnd(',', ' ')

    if (preserveLockLine && prompt.contains(LOCK_LINE)) {
      normalized = normalized.replace(lockPlaceholder, LOCK_LINE)
    }
    return normalized
  }
}
