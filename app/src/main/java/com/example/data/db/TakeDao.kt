package com.example.data.db

import androidx.room.*
import com.example.data.model.TakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TakeDao {
  @Query("SELECT * FROM takes ORDER BY id DESC")
  fun getAllTakes(): Flow<List<TakeEntity>>

  @Query("SELECT * FROM takes WHERE id = :id LIMIT 1")
  suspend fun getTakeById(id: Long): TakeEntity?

  @Query("SELECT * FROM takes WHERE lane = :lane ORDER BY id DESC")
  fun getTakesByLane(lane: String): Flow<List<TakeEntity>>

  @Query("SELECT * FROM takes WHERE result = :result ORDER BY id DESC")
  fun getTakesByResult(result: String): Flow<List<TakeEntity>>

  @Query("SELECT * FROM takes WHERE bpm BETWEEN :minBpm AND :maxBpm ORDER BY id DESC")
  fun getTakesByBpmRange(minBpm: Double, maxBpm: Double): Flow<List<TakeEntity>>

  @Query("SELECT * FROM takes WHERE beatTitle LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' ORDER BY id DESC")
  fun searchTakes(query: String): Flow<List<TakeEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTake(take: TakeEntity): Long

  @Update
  suspend fun updateTake(take: TakeEntity)

  @Query("UPDATE takes SET result = :result WHERE id = :id")
  suspend fun updateResult(id: Long, result: String)

  @Query("UPDATE takes SET nextChange = :nextChange WHERE id = :id")
  suspend fun updateNextChange(id: Long, nextChange: String)

  @Query("UPDATE takes SET name = :name WHERE id = :id")
  suspend fun updateName(id: Long, name: String)

  @Query("UPDATE takes SET beatTitle = :beatTitle WHERE id = :id")
  suspend fun updateBeatTitle(id: Long, beatTitle: String)

  @Delete
  suspend fun deleteTake(take: TakeEntity)

  @Query("DELETE FROM takes WHERE id = :id")
  suspend fun deleteTakeById(id: Long)

  @Query("SELECT COUNT(*) FROM takes")
  suspend fun getTakesCount(): Int

  @Query("SELECT COUNT(*) FROM takes WHERE result = 'winner'")
  suspend fun getWinnerCount(): Int
}
