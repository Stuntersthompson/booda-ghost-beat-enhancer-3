package com.example.data.repository

import com.example.data.db.TakeDao
import com.example.data.model.EXCLUDE_LIMIT
import com.example.data.model.JobType
import com.example.data.model.LYRICS_LIMIT
import com.example.data.model.STYLE_LIMIT
import com.example.data.model.TakeEntity
import kotlinx.coroutines.flow.Flow

data class ImportBatchResult(
  val importedCount: Int,
  val skippedCount: Int,
  val errorMessage: String? = null
)

class TakeRepository(private val takeDao: TakeDao) {
  val allTakes: Flow<List<TakeEntity>> = takeDao.getAllTakes()

  suspend fun insertTake(take: TakeEntity): Long {
    return takeDao.insertTake(take)
  }

  suspend fun updateResult(id: Long, result: String) {
    takeDao.updateResult(id, result)
  }

  suspend fun updateNextChange(id: Long, nextChange: String) {
    takeDao.updateNextChange(id, nextChange)
  }

  suspend fun updateName(id: Long, name: String) {
    takeDao.updateName(id, name)
  }

  suspend fun updateBeatTitle(id: Long, beatTitle: String) {
    takeDao.updateBeatTitle(id, beatTitle)
  }

  suspend fun duplicateTake(id: Long) {
    val original = takeDao.getTakeById(id) ?: return
    val duplicate = original.copy(
      id = 0,
      name = "${original.name} (Copy)",
      result = "pending",
      nextChange = "",
      createdAt = System.currentTimeMillis()
    )
    takeDao.insertTake(duplicate)
  }

  suspend fun deleteTake(id: Long) {
    takeDao.deleteTakeById(id)
  }

  fun searchTakes(query: String): Flow<List<TakeEntity>> {
    return takeDao.searchTakes(query)
  }

  fun getTakesByBpmRange(minBpm: Double, maxBpm: Double): Flow<List<TakeEntity>> {
    return takeDao.getTakesByBpmRange(minBpm, maxBpm)
  }

  suspend fun getStats(): Pair<Int, Int> {
    val total = takeDao.getTakesCount()
    val winners = takeDao.getWinnerCount()
    return Pair(total, winners)
  }

  suspend fun exportTakesJson(takes: List<TakeEntity>): String {
    val envelope = org.json.JSONObject().apply {
      put("app", "BooDa Beat Enhancer")
      put("kind", "take-package-backup")
      put("version", 1)
      put("exported_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.format(java.util.Date()))
      put("count", takes.size)
      val jsonArray = org.json.JSONArray()
      for (t in takes) {
        val obj = org.json.JSONObject().apply {
          put("id", t.id)
          put("name", t.name)
          put("beatTitle", t.beatTitle)
          put("lane", t.lane)
          put("bpm", t.bpm)
          put("audioFilePath", t.audioFilePath ?: "")
          put("style", t.style)
          put("lyrics", t.lyrics)
          put("excludeText", t.excludeText)
          put("settings", t.settings)
          put("result", t.result)
          put("nextChange", t.nextChange)
          put("jobType", t.jobType)
          put("createdAt", t.createdAt)
        }
        jsonArray.put(obj)
      }
      put("takes", jsonArray)
    }
    return envelope.toString(2)
  }

  suspend fun exportTakesPlainText(takes: List<TakeEntity>): String {
    val sb = StringBuilder()
    sb.appendLine("==================================================")
    sb.appendLine("BOODA BEAT ENHANCER — SUNO V6 PRODUCTION LOGBOOK")
    sb.appendLine("Total Takes Exported: ${takes.size}")
    sb.appendLine("==================================================")
    sb.appendLine()

    takes.forEachIndexed { idx, t ->
      sb.appendLine("--- TAKE #${idx + 1}: ${t.name.uppercase()} ---")
      sb.appendLine("Beat: ${if (t.beatTitle.isNotBlank()) t.beatTitle else t.name}")
      sb.appendLine("Lane: ${t.lane} | Job: ${t.jobType.uppercase()} | BPM: ${if (t.bpm > 0) t.bpm.toInt().toString() else "N/A"}")
      sb.appendLine("Status: ${t.result.uppercase()} | Settings: ${t.settings}")
      if (t.nextChange.isNotBlank()) {
        sb.appendLine("Next Change: ${t.nextChange}")
      }
      sb.appendLine()
      sb.appendLine("[STYLE OF MUSIC]")
      sb.appendLine(t.style)
      sb.appendLine()
      if (t.excludeText.isNotBlank()) {
        sb.appendLine("[EXCLUDE STYLES]")
        sb.appendLine(t.excludeText)
        sb.appendLine()
      }
      sb.appendLine("[LYRICS BOX / STRUCTURE MAP]")
      sb.appendLine(t.lyrics)
      sb.appendLine()
      sb.appendLine("--------------------------------------------------")
      sb.appendLine()
    }
    return sb.toString()
  }

  suspend fun importTakesJson(jsonStr: String): Int {
    return importTakesDetailed(jsonStr).importedCount
  }

  suspend fun importTakesDetailed(jsonStr: String): ImportBatchResult {
    val trimmed = jsonStr.trim()
    if (trimmed.isEmpty()) {
      return ImportBatchResult(0, 0, "Empty JSON input.")
    }
    var count = 0
    var skipped = 0
    return try {
      val jsonArray = when {
        trimmed.startsWith("{") -> {
          val env = org.json.JSONObject(trimmed)
          env.optJSONArray("takes") ?: org.json.JSONArray()
        }
        trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
        else -> return ImportBatchResult(0, 0, "Invalid JSON format.")
      }

      val limit = minOf(jsonArray.length(), 250)
      for (i in 0 until limit) {
        val item = jsonArray.optJSONObject(i)
        if (item == null) {
          skipped++
          continue
        }
        val styleText = item.optString("style", "").trim().take(STYLE_LIMIT)
        if (styleText.isBlank()) {
          skipped++
          continue
        }

        val rawBpm = item.optDouble("bpm", 0.0)
        val safeBpm = if (rawBpm.isNaN() || rawBpm.isInfinite() || rawBpm < 0.0) 0.0 else rawBpm.coerceAtMost(300.0)

        val rawResult = item.optString("result", "pending").trim().lowercase()
        val normalizedResult = when (rawResult) {
          "winner", "miss", "pending" -> rawResult
          else -> "pending"
        }

        val normalizedJob = JobType.fromString(item.optString("jobType", "preserve")).value
        val rawCreatedAt = item.optLong("createdAt", System.currentTimeMillis())

        val entity = TakeEntity(
          id = 0,
          name = item.optString("name", "Imported Take").trim().take(120).ifBlank { "Imported Take" },
          beatTitle = item.optString("beatTitle", "").trim().take(120),
          lane = item.optString("lane", "Trap").trim().take(80).ifBlank { "Trap" },
          bpm = safeBpm,
          audioFilePath = item.optString("audioFilePath", "").trim().take(260).ifEmpty { null },
          style = styleText,
          lyrics = item.optString("lyrics", "").take(LYRICS_LIMIT),
          excludeText = item.optString("excludeText", item.optString("exclude", "")).take(EXCLUDE_LIMIT),
          settings = item.optString("settings", "").take(400),
          result = normalizedResult,
          nextChange = item.optString("nextChange", "").take(400),
          jobType = normalizedJob,
          createdAt = if (rawCreatedAt > 0L) rawCreatedAt else System.currentTimeMillis()
        )
        takeDao.insertTake(entity)
        count++
      }
      ImportBatchResult(
        importedCount = count,
        skippedCount = skipped,
        errorMessage = if (count == 0) "No valid takes with a Style prompt found." else null
      )
    } catch (e: Exception) {
      e.printStackTrace()
      ImportBatchResult(count, skipped, "Malformed JSON: ${e.localizedMessage ?: "parse failed"}")
    }
  }
}
