package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.PromptHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptHistoryDao {
  @Query("SELECT * FROM prompt_history ORDER BY createdAt DESC, id DESC LIMIT :limit")
  fun getRecentPrompts(limit: Int = MAX_HISTORY_ENTRIES): Flow<List<PromptHistoryEntity>>

  @Query("SELECT * FROM prompt_history ORDER BY createdAt DESC, id DESC LIMIT :limit")
  suspend fun getRecentPromptsOnce(limit: Int = MAX_HISTORY_ENTRIES): List<PromptHistoryEntity>

  @Query("DELETE FROM prompt_history WHERE prompt = :prompt")
  suspend fun deleteByPromptText(prompt: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPrompt(entry: PromptHistoryEntity): Long

  @Query("DELETE FROM prompt_history WHERE id NOT IN (SELECT id FROM prompt_history ORDER BY createdAt DESC, id DESC LIMIT :maxCount)")
  suspend fun pruneOldestBeyond(maxCount: Int = MAX_HISTORY_ENTRIES)

  @Transaction
  suspend fun saveAndKeepLatest(
    entry: PromptHistoryEntity,
    maxCount: Int = MAX_HISTORY_ENTRIES
  ): Long {
    deleteByPromptText(entry.prompt)
    val insertedId = insertPrompt(entry)
    pruneOldestBeyond(maxCount)
    return insertedId
  }

  @Transaction
  suspend fun mergeHistoryAndKeepLatest(
    entries: List<PromptHistoryEntity>,
    maxCount: Int = MAX_HISTORY_ENTRIES
  ) {
    val ordered = entries.sortedBy { it.createdAt }
    for (entry in ordered) {
      deleteByPromptText(entry.prompt)
      insertPrompt(entry)
    }
    pruneOldestBeyond(maxCount)
  }

  @Transaction
  suspend fun replaceHistoryAndKeepLatest(
    entries: List<PromptHistoryEntity>,
    maxCount: Int = MAX_HISTORY_ENTRIES
  ) {
    clearAll()
    val ordered = entries.sortedBy { it.createdAt }
    for (entry in ordered) {
      deleteByPromptText(entry.prompt)
      insertPrompt(entry)
    }
    pruneOldestBeyond(maxCount)
  }

  @Query("DELETE FROM prompt_history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM prompt_history")
  suspend fun clearAll()

  @Query("SELECT COUNT(*) FROM prompt_history")
  suspend fun getCount(): Int

  companion object {
    const val MAX_HISTORY_ENTRIES = 20
  }
}
