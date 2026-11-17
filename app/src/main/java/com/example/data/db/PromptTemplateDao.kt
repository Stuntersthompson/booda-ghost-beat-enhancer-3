package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PromptTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptTemplateDao {

  @Query(
    """
    SELECT * FROM prompt_templates
    ORDER BY isPinned DESC, useCount DESC, lastUsedAt DESC, id ASC
    """
  )
  fun observeAllTemplates(): Flow<List<PromptTemplateEntity>>

  @Query(
    """
    SELECT * FROM prompt_templates
    WHERE category = :category
    ORDER BY isPinned DESC, useCount DESC, lastUsedAt DESC, id ASC
    """
  )
  fun observeByCategory(category: String): Flow<List<PromptTemplateEntity>>

  @Query("SELECT DISTINCT category FROM prompt_templates ORDER BY category ASC")
  fun observeCategories(): Flow<List<String>>

  @Query("SELECT COUNT(*) FROM prompt_templates")
  suspend fun getCount(): Int

  @Query("SELECT * FROM prompt_templates WHERE title = :title AND category = :category LIMIT 1")
  suspend fun findByTitleAndCategory(title: String, category: String): PromptTemplateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTemplate(template: PromptTemplateEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(templates: List<PromptTemplateEntity>)

  @Update
  suspend fun updateTemplate(template: PromptTemplateEntity)

  @Query("UPDATE prompt_templates SET useCount = useCount + 1, lastUsedAt = :usedAt WHERE id = :id")
  suspend fun incrementUsage(id: Long, usedAt: Long)

  @Query("UPDATE prompt_templates SET isPinned = :pinned WHERE id = :id")
  suspend fun setPinned(id: Long, pinned: Boolean)

  @Query("DELETE FROM prompt_templates WHERE id = :id")
  suspend fun deleteById(id: Long)
}
