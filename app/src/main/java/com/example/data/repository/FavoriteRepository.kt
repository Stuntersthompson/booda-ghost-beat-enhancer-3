package com.example.data.repository

import com.example.data.db.FavoritePromptDao
import com.example.data.model.CuratedSunoPrompt
import com.example.data.model.FavoritePromptEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class FavoriteRepository(private val favoriteDao: FavoritePromptDao) {

  val allFavorites: Flow<List<FavoritePromptEntity>> = favoriteDao.getAllFavorites()

  val favoriteIds: Flow<Set<Long>> = favoriteDao.getAllFavoriteIds().map { it.toSet() }

  val favoritesCount: Flow<Int> = favoriteDao.getFavoritesCountFlow()

  suspend fun insertFavorite(prompt: CuratedSunoPrompt) {
    val entity = FavoritePromptEntity(
      id = prompt.id.toLong(),
      prompt = prompt.prompt,
      category = prompt.category,
      subgenre = prompt.subgenre,
      bpmHint = prompt.bpmHint,
      chars = prompt.chars,
      savedAt = System.currentTimeMillis()
    )
    favoriteDao.insertFavorite(entity)
  }

  suspend fun insertFavoriteEntity(entity: FavoritePromptEntity) {
    favoriteDao.insertFavorite(entity)
  }

  suspend fun deleteFavorite(id: Long) {
    favoriteDao.deleteFavoriteById(id)
  }

  suspend fun isFavorite(id: Long): Boolean {
    return favoriteDao.isFavorite(id)
  }

  suspend fun getFavoriteById(id: Long): FavoritePromptEntity? {
    return favoriteDao.getFavoriteById(id)
  }

  suspend fun exportFavoritesJson(favorites: List<FavoritePromptEntity>): String {
    val jsonArray = JSONArray()
    for (f in favorites) {
      val obj = JSONObject().apply {
        put("id", f.id)
        put("prompt", f.prompt)
        put("category", f.category)
        put("subgenre", f.subgenre)
        put("bpmHint", f.bpmHint)
        put("chars", f.chars)
        put("savedAt", f.savedAt)
      }
      jsonArray.put(obj)
    }
    return jsonArray.toString(2)
  }

  suspend fun exportFavoritesPlainText(favorites: List<FavoritePromptEntity>): String {
    val sb = StringBuilder()
    sb.appendLine("=== BOODA BEAT ENHANCER - FAVORITE PROMPTS ===")
    sb.appendLine("Total Favorites: ${favorites.size}")
    sb.appendLine()
    favorites.forEachIndexed { index, f ->
      sb.appendLine("#${index + 1} [${f.category} · ${f.subgenre}] (${f.chars}c)")
      sb.appendLine(f.prompt)
      sb.appendLine()
    }
    return sb.toString()
  }

  suspend fun importFavoritesJson(jsonStr: String): Int {
    var count = 0
    try {
      val jsonArray = JSONArray(jsonStr)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val entity = FavoritePromptEntity(
          id = obj.optLong("id", System.currentTimeMillis() + i),
          prompt = obj.getString("prompt"),
          category = obj.optString("category", "Imported"),
          subgenre = obj.optString("subgenre", "Custom"),
          bpmHint = obj.optString("bpmHint", ""),
          chars = obj.optInt("chars", obj.getString("prompt").length),
          savedAt = obj.optLong("savedAt", System.currentTimeMillis())
        )
        favoriteDao.insertFavorite(entity)
        count++
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return count
  }
}
