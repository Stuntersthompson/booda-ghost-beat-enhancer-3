package com.example.data.repository

import com.example.data.db.PromptHistoryDao
import com.example.data.model.ComplianceSnapshot
import com.example.data.model.JobType
import com.example.data.model.PromptHistoryEntity
import com.example.data.model.STYLE_LIMIT
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

enum class HistoryImportMode {
  MERGE,
  REPLACE
}

data class HistorySchemaValidationResult(
  val isValid: Boolean,
  val entryCount: Int = 0,
  val skippedCount: Int = 0,
  val hasSnapshot: Boolean = false,
  val errorMessage: String? = null,
  val parsedEntries: List<PromptHistoryEntity> = emptyList(),
  val parsedSnapshot: ComplianceSnapshot? = null
)

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
      put("kind", EXPECTED_BACKUP_KIND)
      put("version", SUPPORTED_SCHEMA_VERSION)
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

  fun validateHistorySchema(jsonStr: String): HistorySchemaValidationResult = validateSchema(jsonStr)

  suspend fun importHistoryJson(
    jsonStr: String,
    mode: HistoryImportMode = HistoryImportMode.MERGE
  ): HistoryImportResult {
    val validation = validateHistorySchema(jsonStr)
    if (!validation.isValid) {
      return HistoryImportResult(
        importedCount = 0,
        skippedCount = validation.skippedCount,
        restoredSnapshot = null,
        errorMessage = validation.errorMessage
      )
    }

    return when (mode) {
      HistoryImportMode.MERGE -> {
        if (validation.parsedEntries.isNotEmpty()) {
          dao.mergeHistoryAndKeepLatest(validation.parsedEntries, MAX_HISTORY_COUNT)
        }
        HistoryImportResult(
          importedCount = validation.parsedEntries.size,
          skippedCount = validation.skippedCount,
          restoredSnapshot = validation.parsedSnapshot
        )
      }
      HistoryImportMode.REPLACE -> {
        dao.replaceHistoryAndKeepLatest(validation.parsedEntries, MAX_HISTORY_COUNT)
        HistoryImportResult(
          importedCount = validation.parsedEntries.size,
          skippedCount = validation.skippedCount,
          restoredSnapshot = validation.parsedSnapshot
        )
      }
    }
  }

  companion object {
    const val MAX_HISTORY_COUNT = PromptHistoryDao.MAX_HISTORY_ENTRIES
    const val EXPECTED_BACKUP_KIND = "booda-prompt-history"
    const val SUPPORTED_SCHEMA_VERSION = 1

    fun validateSchema(jsonStr: String): HistorySchemaValidationResult {
      val trimmed = jsonStr.trim()
      if (trimmed.isEmpty()) {
        return HistorySchemaValidationResult(
          isValid = false,
          errorMessage = "JSON input is empty."
        )
      }

      return try {
        var restoredSnapshot: ComplianceSnapshot? = null
        val jsonArray: JSONArray = when {
          trimmed.startsWith("{") -> {
            val env = JSONObject(trimmed)

            val kind = env.optString("kind", "").trim()
            if (kind.isNotEmpty() && !kind.equals(EXPECTED_BACKUP_KIND, ignoreCase = true)) {
              return HistorySchemaValidationResult(
                isValid = false,
                errorMessage = "Schema error: Expected kind '$EXPECTED_BACKUP_KIND', found '$kind'."
              )
            }

            if (env.has("version") && !env.isNull("version")) {
              val version = env.optInt("version", -1)
              if (version != SUPPORTED_SCHEMA_VERSION) {
                return HistorySchemaValidationResult(
                  isValid = false,
                  errorMessage = "Schema error: Unsupported backup version '$version'."
                )
              }
            }

            if (env.has("complianceSnapshot") && !env.isNull("complianceSnapshot")) {
              val snapObj = env.optJSONObject("complianceSnapshot")
                ?: return HistorySchemaValidationResult(
                  isValid = false,
                  errorMessage = "Schema error: 'complianceSnapshot' must be a JSON object."
                )
              restoredSnapshot = ComplianceSnapshot.fromJson(snapObj.toString())
            }

            val hasEntriesKey = env.has("entries")
            val hasPromptsKey = env.has("prompts")
            when {
              hasEntriesKey -> env.optJSONArray("entries")
                ?: return HistorySchemaValidationResult(
                  isValid = false,
                  errorMessage = "Schema error: 'entries' must be a JSON array."
                )
              hasPromptsKey -> env.optJSONArray("prompts")
                ?: return HistorySchemaValidationResult(
                  isValid = false,
                  errorMessage = "Schema error: 'prompts' must be a JSON array."
                )
              restoredSnapshot != null -> JSONArray()
              else -> return HistorySchemaValidationResult(
                isValid = false,
                errorMessage = "Schema error: Backup object must contain an 'entries' array."
              )
            }
          }
          trimmed.startsWith("[") -> JSONArray(trimmed)
          else -> {
            return HistorySchemaValidationResult(
              isValid = false,
              errorMessage = "Invalid JSON format. Expected a history object or array."
            )
          }
        }

        val parsed = mutableListOf<PromptHistoryEntity>()
        var skipped = 0
        val maxItems = minOf(jsonArray.length(), 100)
        val baseTime = System.currentTimeMillis()

        for (i in 0 until maxItems) {
          when (val raw = jsonArray.opt(i)) {
            is JSONObject -> {
              val promptField = raw.optString("prompt", "").ifBlank { raw.optString("text", "") }
              val text = promptField.trim().take(STYLE_LIMIT)
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

        if (parsed.isEmpty() && restoredSnapshot == null) {
          return HistorySchemaValidationResult(
            isValid = false,
            skippedCount = skipped,
            errorMessage = "Schema error: No valid prompt variations found."
          )
        }

        val orderedCapped = parsed.sortedBy { it.createdAt }.takeLast(MAX_HISTORY_COUNT)
        HistorySchemaValidationResult(
          isValid = true,
          entryCount = orderedCapped.size,
          skippedCount = skipped,
          hasSnapshot = restoredSnapshot != null,
          parsedEntries = orderedCapped,
          parsedSnapshot = restoredSnapshot
        )
      } catch (e: Exception) {
        HistorySchemaValidationResult(
          isValid = false,
          errorMessage = "Malformed JSON: ${e.localizedMessage ?: "parse failed"}"
        )
      }
    }
  }
}
