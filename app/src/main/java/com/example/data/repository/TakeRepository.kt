package com.example.data.repository

import com.example.data.db.TakeDao
import com.example.data.model.TakeEntity
import kotlinx.coroutines.flow.Flow

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
}
