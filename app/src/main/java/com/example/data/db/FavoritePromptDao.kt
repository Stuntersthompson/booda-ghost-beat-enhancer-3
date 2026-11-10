package com.example.data.db

import androidx.room.*
import com.example.data.model.FavoritePromptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritePromptDao {
  @Query("SELECT * FROM favorite_prompts ORDER BY savedAt DESC")
  fun getAllFavorites(): Flow<List<FavoritePromptEntity>>

  @Query("SELECT id FROM favorite_prompts")
  fun getAllFavoriteIds(): Flow<List<Long>>

  @Query("SELECT * FROM favorite_prompts WHERE id = :id LIMIT 1")
  suspend fun getFavoriteById(id: Long): FavoritePromptEntity?

  @Query("SELECT EXISTS(SELECT 1 FROM favorite_prompts WHERE id = :id)")
  suspend fun isFavorite(id: Long): Boolean

  @Query("SELECT EXISTS(SELECT 1 FROM favorite_prompts WHERE id = :id)")
  fun isFavoriteFlow(id: Long): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFavorite(favorite: FavoritePromptEntity): Long

  @Query("DELETE FROM favorite_prompts WHERE id = :id")
  suspend fun deleteFavoriteById(id: Long)

  @Query("SELECT COUNT(*) FROM favorite_prompts")
  fun getFavoritesCountFlow(): Flow<Int>

  @Query("SELECT COUNT(*) FROM favorite_prompts")
  suspend fun getFavoritesCount(): Int

  @Query("DELETE FROM favorite_prompts")
  suspend fun clearAllFavorites()
}
