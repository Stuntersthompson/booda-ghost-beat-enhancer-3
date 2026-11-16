package com.example.data.repository

import com.example.data.db.PromptHistoryDao
import com.example.data.model.ComplianceSnapshot
import com.example.data.model.JobType
import com.example.data.model.PromptHistoryEntity
import com.example.data.model.STYLE_LIMIT
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

data class HistoryImportResult(
  val importedCount: Int,
  val skippedCount: Int,
  val restoredSnapshot: ComplianceSnapshot? = null,
  val errorMessage: String? = null
)

class PromptHistoryRepository(private val dao: PromptHistoryDao) {

  val recentPrompts: Flow<List<PromptHistoryEntity>> =
    dao.getRecentPrompts(MAX_HISTORY_COUNT)

  suspend fun recordPrompt(
    prompt: String,
    source: String,
    lane: String,
    jobType: JobType = JobType.PRESERVE,
    bpm: Int? = null,
    createdAt: Long = System.currentTimeMillis()
  ): Long? {
    val trimmed = prompt.trim().take(STYLE_LIMIT)
    if (trimmed.isEmpty()) return null
    val entity = PromptHistoryEntity(
      prompt = trimmed,
      source = source.trim().take(60).ifBlank { "Builder" },
      lane = lane.trim().take(80).ifBlank { "Trap" },
      jobType = jobType.value,
      bpm = bpm?.coerceIn(30, 300),
      chars = trimmed.length,
      createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis()
    )
    return dao.saveAndKeepLatest(entity, MAX_HISTORY_COUNT)
  }

  suspend fun getRecentSnapshot(): List<PromptHistoryEntity> {
    return dao.getRecentPromptsOnce(MAX_HISTORY_COUNT)
  }

  suspend fun deleteEntry(id: Long) {
    dao.deleteById(id)
  }

  suspend fun clearHistory() {
    dao.clearAll()
  }

  suspend fun getCount(): Int {
    return dao.getCount()
  }

  fun exportHistoryJson(
    entries: List<PromptHistoryEntity>,
    snapshot: ComplianceSnapshot? = null
  ): String {
    val capped = entries.take(MAX_HISTORY_COUNT)
    val envelope = JSONObject().apply {
      put("app", "BooDa Beat Enhancer")
      put("kind", "booda-prompt-history")
      put("version", 1)
      put("exported_at", System.currentTimeMillis())
      put("count", capped.size)
      if (snapshot != null) {
        put("complianceSnapshot", JSONObject(snapshot.toJson()))
      }
      val arr = JSONArray()
      for (e in capped) {
        val obj = JSONObject().apply {
          put("prompt", e.prompt)
          put("source", e.source)
          put("lane", e.lane)
          put("jobType", e.jobType)
          if (e.bpm != null) put("bpm", e.bpm)
          put("chars", e.chars)
          put("createdAt", e.createdAt)
        }
        arr.put(obj)
      }
      put("entries", arr)
    }
    return envelope.toString(2)
  }

  suspend fun importHistoryJson(jsonStr: String): HistoryImportResult {
    val trimmed = jsonStr.trim()
    if (trimmed.isEmpty()) {
      return HistoryImportResult(0, 0, errorMessage = "Clipboard or file is empty.")
    }

    return try {
      var restoredSnapshot: ComplianceSnapshot? = null
      val jsonArray: JSONArray = when {
        trimmed.startsWith("{") -> {
          val env = JSONObject(trimmed)
          restoredSnapshot = env.optJSONObject("complianceSnapshot")?.let {
            ComplianceSnapshot.fromJson(it.toString())
          }
          env.optJSONArray("entries")
            ?: env.optJSONArray("prompts")
            ?: JSONArray()
        }
        trimmed.startsWith("[") -> JSONArray(trimmed)
        else -> {
          return HistoryImportResult(
            importedCount = 0,
            skippedCount = 0,
            errorMessage = "Invalid JSON format. Expected a prompt history object or array."
          )
        }
      }

      val parsed = mutableListOf<PromptHistoryEntity>()
      var skipped = 0
      val maxItems = minOf(jsonArray.length(), 100)
      val baseTime = System.currentTimeMillis()

      for (i in 0 until maxItems) {
        val raw = jsonArray.opt(i)
        when (raw) {
          is JSONObject -> {
            val text = raw.optString("prompt", "").trim().take(STYLE_LIMIT)
            if (text.isBlank()) {
              skipped++
            } else {
              val resolvedJob = JobType.fromString(raw.optString("jobType", "preserve"))
              val rawBpm = if (raw.has("bpm") && !raw.isNull("bpm")) raw.optInt("bpm", -1) else -1
              val validBpm = rawBpm.takeIf { it in 30..300 }
              val rawTs = raw.optLong("createdAt", baseTime + i)
              parsed.add(
                PromptHistoryEntity(
                  prompt = text,
                  source = raw.optString("source", "Backup").trim().take(60).ifBlank { "Backup" },
                  lane = raw.optString("lane", "Trap").trim().take(80).ifBlank { "Trap" },
                  jobType = resolvedJob.value,
                  bpm = validBpm,
                  chars = text.length,
                  createdAt = if (rawTs > 0L) rawTs else (baseTime + i)
                )
              )
            }
          }
          is String -> {
            val text = raw.trim().take(STYLE_LIMIT)
            if (text.isBlank()) {
              skipped++
            } else {
              parsed.add(
                PromptHistoryEntity(
                  prompt = text,
                  source = "Backup",
                  lane = "Trap",
                  jobType = JobType.PRESERVE.value,
                  bpm = null,
                  chars = text.length,
                  createdAt = baseTime + i
                )
              )
            }
          }
          else -> skipped++
        }
      }

      val ordered = parsed.sortedBy { it.createdAt }
      for (entity in ordered) {
        dao.saveAndKeepLatest(entity, MAX_HISTORY_COUNT)
      }

      HistoryImportResult(
        importedCount = ordered.size,
        skippedCount = skipped,
        restoredSnapshot = restoredSnapshot,
        errorMessage = if (ordered.isEmpty() && restoredSnapshot == null) {
          "No valid prompt entries found in JSON."
        } else null
      )
    } catch (e: Exception) {
      HistoryImportResult(
        importedCount = 0,
        skippedCount = 0,
        errorMessage = "Malformed JSON: ${e.localizedMessage ?: "parse failed"}"
      )
    }
  }

  companion object {
    const val MAX_HISTORY_COUNT = PromptHistoryDao.MAX_HISTORY_ENTRIES
  }
}
