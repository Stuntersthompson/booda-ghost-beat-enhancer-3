package com.example.data.model

import kotlin.math.max

data class LaneWinStat(
  val lane: String,
  val total: Int,
  val winners: Int,
  val winRatePercent: Int
)

data class ModeWinStat(
  val label: String,
  val total: Int,
  val winners: Int,
  val winRatePercent: Int
)

data class TakeAnalyticsSummary(
  val totalTakes: Int,
  val winnerCount: Int,
  val missCount: Int,
  val pendingCount: Int,
  val overallWinRate: Int,
  val preserveStats: ModeWinStat,
  val originalStats: ModeWinStat,
  val topLanes: List<LaneWinStat>
)

enum class DiffOp {
  UNCHANGED,
  ADDED,
  REMOVED
}

data class DiffToken(
  val text: String,
  val op: DiffOp
)

data class TakeSliderDelta(
  val label: String,
  val valueA: String,
  val valueB: String,
  val changed: Boolean
)

data class TakeComparisonResult(
  val takeA: TakeEntity,
  val takeB: TakeEntity,
  val styleDiffTokens: List<DiffToken>,
  val sliderDeltas: List<TakeSliderDelta>,
  val unchangedWordCount: Int,
  val addedWordCount: Int,
  val removedWordCount: Int
)

/**
 * Pure analytics and word-level A/B prompt diff calculator for Production Logbook takes.
 */
object TakeAnalytics {

  fun summarize(takes: List<TakeEntity>): TakeAnalyticsSummary {
    val total = takes.size
    val winners = takes.count { it.result.equals("winner", ignoreCase = true) }
    val misses = takes.count { it.result.equals("miss", ignoreCase = true) }
    val pending = total - winners - misses
    val overallRate = if (total > 0) (winners * 100) / total else 0

    val preserveTakes = takes.filter { it.jobType.equals(JobType.PRESERVE.value, ignoreCase = true) }
    val preserveWinners = preserveTakes.count { it.result.equals("winner", ignoreCase = true) }
    val preserveStats = ModeWinStat(
      label = "Preserve",
      total = preserveTakes.size,
      winners = preserveWinners,
      winRatePercent = if (preserveTakes.isNotEmpty()) (preserveWinners * 100) / preserveTakes.size else 0
    )

    val originalTakes = takes.filter { !it.jobType.equals(JobType.PRESERVE.value, ignoreCase = true) }
    val originalWinners = originalTakes.count { it.result.equals("winner", ignoreCase = true) }
    val originalStats = ModeWinStat(
      label = "Original",
      total = originalTakes.size,
      winners = originalWinners,
      winRatePercent = if (originalTakes.isNotEmpty()) (originalWinners * 100) / originalTakes.size else 0
    )

    val laneStats = takes
      .groupBy { it.lane.ifBlank { "General" } }
      .map { (lane, group) ->
        val w = group.count { it.result.equals("winner", ignoreCase = true) }
        LaneWinStat(
          lane = lane,
          total = group.size,
          winners = w,
          winRatePercent = if (group.isNotEmpty()) (w * 100) / group.size else 0
        )
      }
      .sortedWith(
        compareByDescending<LaneWinStat> { it.winRatePercent }
          .thenByDescending { it.winners }
          .thenByDescending { it.total }
      )
      .take(4)

    return TakeAnalyticsSummary(
      totalTakes = total,
      winnerCount = winners,
      missCount = misses,
      pendingCount = pending,
      overallWinRate = overallRate,
      preserveStats = preserveStats,
      originalStats = originalStats,
      topLanes = laneStats
    )
  }

  /**
   * Computes a deterministic word-level LCS diff between two takes' Style Prompts and compares sliders.
   */
  fun compareTakes(takeA: TakeEntity, takeB: TakeEntity): TakeComparisonResult {
    val tokens = computeWordDiff(takeA.style, takeB.style)
    val unchanged = tokens.count { it.op == DiffOp.UNCHANGED }
    val added = tokens.count { it.op == DiffOp.ADDED }
    val removed = tokens.count { it.op == DiffOp.REMOVED }

    val mapA = parseSettingsMap(takeA.settings)
    val mapB = parseSettingsMap(takeB.settings)

    val deltas = listOf(
      buildDelta("Result", takeA.result.uppercase(), takeB.result.uppercase()),
      buildDelta("Lane", takeA.lane, takeB.lane),
      buildDelta("Mode", takeA.jobType.uppercase(), takeB.jobType.uppercase()),
      buildDelta(
        "BPM",
        if (takeA.bpm > 0) takeA.bpm.toInt().toString() else "N/A",
        if (takeB.bpm > 0) takeB.bpm.toInt().toString() else "N/A"
      ),
      buildDelta("Variety", mapA["Variety"] ?: "0", mapB["Variety"] ?: "0"),
      buildDelta("Weirdness", mapA["Weirdness"] ?: "25", mapB["Weirdness"] ?: "25"),
      buildDelta("Style Infl.", mapA["Style"] ?: "70", mapB["Style"] ?: "70"),
      buildDelta("Audio Infl.", mapA["Audio"] ?: "80", mapB["Audio"] ?: "80")
    )

    return TakeComparisonResult(
      takeA = takeA,
      takeB = takeB,
      styleDiffTokens = tokens,
      sliderDeltas = deltas,
      unchangedWordCount = unchanged,
      addedWordCount = added,
      removedWordCount = removed
    )
  }

  internal fun computeWordDiff(textA: String, textB: String): List<DiffToken> {
    val wordsA = textA.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val wordsB = textB.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val n = wordsA.size
    val m = wordsB.size

    if (n == 0) return wordsB.map { DiffToken(it, DiffOp.ADDED) }
    if (m == 0) return wordsA.map { DiffToken(it, DiffOp.REMOVED) }

    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
      for (j in m - 1 downTo 0) {
        dp[i][j] = if (wordsA[i] == wordsB[j]) {
          1 + dp[i + 1][j + 1]
        } else {
          max(dp[i + 1][j], dp[i][j + 1])
        }
      }
    }

    val result = ArrayList<DiffToken>(n + m)
    var i = 0
    var j = 0
    while (i < n && j < m) {
      when {
        wordsA[i] == wordsB[j] -> {
          result.add(DiffToken(wordsA[i], DiffOp.UNCHANGED))
          i++
          j++
        }
        dp[i + 1][j] >= dp[i][j + 1] -> {
          result.add(DiffToken(wordsA[i], DiffOp.REMOVED))
          i++
        }
        else -> {
          result.add(DiffToken(wordsB[j], DiffOp.ADDED))
          j++
        }
      }
    }
    while (i < n) {
      result.add(DiffToken(wordsA[i], DiffOp.REMOVED))
      i++
    }
    while (j < m) {
      result.add(DiffToken(wordsB[j], DiffOp.ADDED))
      j++
    }
    return result
  }

  private fun parseSettingsMap(settings: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    settings.split("·").map { it.trim() }.forEach { part ->
      val tokens = part.split(" ").filter { it.isNotBlank() }
      if (tokens.size >= 2) {
        val key = tokens.first()
        val value = tokens.last()
        result[key] = value
      }
    }
    return result
  }

  private fun buildDelta(label: String, a: String, b: String): TakeSliderDelta {
    return TakeSliderDelta(
      label = label,
      valueA = a,
      valueB = b,
      changed = !a.equals(b, ignoreCase = true)
    )
  }
}
