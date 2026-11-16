package com.example.data.model

enum class ComplianceRuleId {
  PRESERVE_LOCK_LINE,
  LYRICS_END_CLOSURE,
  NEGATIVE_SEPARATION,
  PRESERVE_SLIDERS,
  CHARACTER_CAPS
}

data class ComplianceCheckResult(
  val id: ComplianceRuleId,
  val label: String,
  val passed: Boolean,
  val detail: String,
  val fixLabel: String? = null
)

data class ComplianceAuditReport(
  val checks: List<ComplianceCheckResult>,
  val passedCount: Int,
  val totalCount: Int
) {
  val allPassed: Boolean get() = passedCount == totalCount
}

data class ComplianceSnapshot(
  val jobType: JobType,
  val stylePrompt: String,
  val lyricsMap: String,
  val excludeText: String,
  val variety: Int,
  val weirdness: Int,
  val styleInfluence: Int,
  val audioInfluence: Int,
  val fixSummary: String = ""
)

/**
 * Pure domain auditor and auto-repair engine for Suno v6 production rules.
 */
object SunoComplianceAuditor {

  private val negativePhraseRegex = Regex(
    """\b(?:no\s+(?:vocals?|singing|drums?|808s?|bass|rap|melody|instruments?|hi-?hats?|snare|kicks?)|without\s+(?:vocals?|singing|drums?|bass|808s?)|exclude\s+[a-z0-9\s-]+|remove\s+(?:vocals?|drums?|bass|808s?))\b""",
    RegexOption.IGNORE_CASE
  )

  private val fallbackNegativeKeywords = listOf("no vocals", "no drums", "exclude", "without", "remove")

  fun findNegativeTermsInStyle(stylePrompt: String): List<String> {
    val phraseMatches = negativePhraseRegex.findAll(stylePrompt)
      .map { it.value.trim() }
      .filter { it.isNotEmpty() }
      .toList()
    if (phraseMatches.isNotEmpty()) return phraseMatches.distinct()

    return fallbackNegativeKeywords.filter { stylePrompt.contains(it, ignoreCase = true) }
  }

  fun audit(
    jobType: JobType,
    stylePrompt: String,
    lyricsMap: String,
    excludeText: String,
    variety: Int,
    audioInfluence: Int
  ): ComplianceAuditReport {
    val isPreserve = jobType == JobType.PRESERVE
    val hasLockLine = stylePrompt.contains(LOCK_LINE)
    val hasEnd = lyricsMap.isNotBlank() && lyricsMap.trim().endsWith("[End]")
    val foundNegatives = findNegativeTermsInStyle(stylePrompt)
    val hasNegatives = foundNegatives.isNotEmpty()
    val slidersValid = if (isPreserve) variety == 0 && audioInfluence in 75..85 else true
    val capsOk = stylePrompt.length <= STYLE_LIMIT &&
      lyricsMap.length <= LYRICS_LIMIT &&
      excludeText.length <= EXCLUDE_LIMIT

    val checks = listOf(
      ComplianceCheckResult(
        id = ComplianceRuleId.PRESERVE_LOCK_LINE,
        label = "Preserve Lock Line",
        passed = !isPreserve || hasLockLine,
        detail = if (isPreserve && !hasLockLine) {
          "FAIL: Missing '$LOCK_LINE' in Style box."
        } else {
          "PASS: Lock line intact or Create mode active."
        },
        fixLabel = if (isPreserve && !hasLockLine) "Insert Lock Line" else null
      ),
      ComplianceCheckResult(
        id = ComplianceRuleId.LYRICS_END_CLOSURE,
        label = "Lyrics Map Closure",
        passed = hasEnd,
        detail = if (!hasEnd) {
          "FAIL: Lyrics map must terminate with [End]."
        } else {
          "PASS: Map terminates cleanly with [End]."
        },
        fixLabel = if (!hasEnd) "Append [End]" else null
      ),
      ComplianceCheckResult(
        id = ComplianceRuleId.NEGATIVE_SEPARATION,
        label = "Negative Prompt Placement",
        passed = !hasNegatives,
        detail = if (hasNegatives) {
          "WARNING: Found '${foundNegatives.joinToString(", ")}' in Style box — move to Exclude box."
        } else {
          "PASS: Clean Style block without negative directives."
        },
        fixLabel = if (hasNegatives) "Move to Exclude" else null
      ),
      ComplianceCheckResult(
        id = ComplianceRuleId.PRESERVE_SLIDERS,
        label = "Preserve Slider Calibration",
        passed = slidersValid,
        detail = if (!slidersValid) {
          "FAIL: Preserve requires Variety 0 (now $variety) & Audio Influence 75-85 (now $audioInfluence)."
        } else if (isPreserve) {
          "PASS: Variety $variety · Audio Influence $audioInfluence% locked."
        } else {
          "PASS: Create mode sliders calibrated."
        },
        fixLabel = if (!slidersValid) "Calibrate Sliders" else null
      ),
      ComplianceCheckResult(
        id = ComplianceRuleId.CHARACTER_CAPS,
        label = "Character Caps Compliance",
        passed = capsOk,
        detail = "Style ${stylePrompt.length}/$STYLE_LIMIT · Lyrics ${lyricsMap.length}/$LYRICS_LIMIT · Exclude ${excludeText.length}/$EXCLUDE_LIMIT",
        fixLabel = if (!capsOk) "Trim to Limits" else null
      )
    )

    val passedCount = checks.count { it.passed }
    return ComplianceAuditReport(
      checks = checks,
      passedCount = passedCount,
      totalCount = checks.size
    )
  }

  fun autoFixRule(
    ruleId: ComplianceRuleId,
    snapshot: ComplianceSnapshot,
    activeLane: String
  ): ComplianceSnapshot {
    return when (ruleId) {
      ComplianceRuleId.PRESERVE_LOCK_LINE -> {
        if (snapshot.jobType != JobType.PRESERVE || snapshot.stylePrompt.contains(LOCK_LINE)) {
          snapshot
        } else {
          val updated = if (snapshot.stylePrompt.isBlank()) {
            "$LOCK_LINE. Add $activeLane drums underneath only."
          } else {
            "$LOCK_LINE. ${snapshot.stylePrompt.trimStart()}"
          }
          snapshot.copy(
            stylePrompt = updated,
            fixSummary = "Inserted Preserve Lock Line"
          )
        }
      }

      ComplianceRuleId.LYRICS_END_CLOSURE -> {
        val trimmed = snapshot.lyricsMap.trimEnd()
        val updated = when {
          trimmed.isBlank() -> "[Instrumental]\n[Intro] (sample solo)\n[Verse 1]\n[Chorus 1]\n[End]"
          trimmed.endsWith("[End]") -> snapshot.lyricsMap
          else -> "$trimmed\n[End]"
        }
        snapshot.copy(
          lyricsMap = updated,
          fixSummary = "Appended [End] tag"
        )
      }

      ComplianceRuleId.NEGATIVE_SEPARATION -> {
        val extracted = findNegativeTermsInStyle(snapshot.stylePrompt)
        if (extracted.isEmpty()) return snapshot

        var cleanedStyle = snapshot.stylePrompt
        for (term in extracted) {
          cleanedStyle = cleanedStyle.replace(term, "", ignoreCase = true)
        }
        cleanedStyle = cleanedStyle
          .replace(Regex(",\\s*,+"), ",")
          .replace(Regex("\\s{2,}"), " ")
          .replace(Regex("\\s+([,.])"), "$1")
          .trim()
          .trimStart(',', ' ')
          .trimEnd(',', ' ')

        val existingExcludes = snapshot.excludeText
          .split(",")
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .toMutableList()
        for (term in extracted) {
          val normalized = term
            .removePrefix("without ")
            .removePrefix("exclude ")
            .removePrefix("remove ")
            .trim()
            .ifBlank { term }
          if (existingExcludes.none { it.equals(normalized, ignoreCase = true) }) {
            existingExcludes.add(normalized)
          }
        }

        snapshot.copy(
          stylePrompt = cleanedStyle,
          excludeText = existingExcludes.joinToString(", "),
          fixSummary = "Moved ${extracted.size} negative directive(s) to Exclude"
        )
      }

      ComplianceRuleId.PRESERVE_SLIDERS -> {
        if (snapshot.jobType != JobType.PRESERVE) return snapshot
        val fixedAudio = if (snapshot.audioInfluence in 75..85) snapshot.audioInfluence else 80
        snapshot.copy(
          variety = 0,
          audioInfluence = fixedAudio,
          fixSummary = "Set Variety 0 & Audio Influence $fixedAudio%"
        )
      }

      ComplianceRuleId.CHARACTER_CAPS -> {
        val fixedStyle = trimAtWordBoundary(snapshot.stylePrompt, STYLE_LIMIT)
        val fixedExclude = trimAtWordBoundary(snapshot.excludeText, EXCLUDE_LIMIT)
        val fixedLyrics = if (snapshot.lyricsMap.length <= LYRICS_LIMIT) {
          snapshot.lyricsMap
        } else {
          val endSuffix = "\n[End]"
          val bodyMax = (LYRICS_LIMIT - endSuffix.length).coerceAtLeast(0)
          trimAtWordBoundary(snapshot.lyricsMap, bodyMax) + endSuffix
        }
        snapshot.copy(
          stylePrompt = fixedStyle,
          excludeText = fixedExclude,
          lyricsMap = fixedLyrics,
          fixSummary = "Trimmed prompts to Suno v6 caps"
        )
      }
    }
  }

  fun autoFixAll(
    snapshot: ComplianceSnapshot,
    activeLane: String
  ): ComplianceSnapshot {
    val order = listOf(
      ComplianceRuleId.NEGATIVE_SEPARATION,
      ComplianceRuleId.PRESERVE_LOCK_LINE,
      ComplianceRuleId.LYRICS_END_CLOSURE,
      ComplianceRuleId.PRESERVE_SLIDERS,
      ComplianceRuleId.CHARACTER_CAPS
    )
    var current = snapshot
    val applied = mutableListOf<String>()
    for (rule in order) {
      val next = autoFixRule(rule, current, activeLane)
      if (next != current) {
        if (next.fixSummary.isNotBlank()) applied.add(next.fixSummary)
        current = next
      }
    }
    return if (applied.isEmpty()) {
      current
    } else {
      current.copy(fixSummary = "Auto-fixed ${applied.size} rule(s)")
    }
  }

  private fun trimAtWordBoundary(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    val cut = text.substring(0, maxChars)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > (maxChars * 0.7).toInt()) cut.substring(0, lastSpace).trimEnd() else cut.trimEnd()
  }
}
