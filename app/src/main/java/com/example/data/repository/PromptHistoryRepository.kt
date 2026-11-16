package com.example.data.repository

import com.example.data.db.PromptHistoryDao
import com.example.data.model.JobType
import com.example.data.model.PromptHistoryEntity
import kotlinx.coroutines.flow.Flow

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
    val trimmed = prompt.trim()
    if (trimmed.isEmpty()) return null
    val entity = PromptHistoryEntity(
      prompt = trimmed,
      source = source.ifBlank { "Builder" },
      lane = lane.ifBlank { "Trap" },
      jobType = jobType.value,
      bpm = bpm,
      chars = trimmed.length,
      createdAt = createdAt
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

  companion object {
    const val MAX_HISTORY_COUNT = PromptHistoryDao.MAX_HISTORY_ENTRIES
  }
}
