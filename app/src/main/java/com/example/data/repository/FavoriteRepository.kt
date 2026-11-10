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
    val envelope = JSONObject().apply {
      put("app", "BooDa Beat Enhancer")
      put("kind", "prompt-favorites")
      put("version", 1)
      put("exported_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.format(java.util.Date()))
      put("count", favorites.size)
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
      put("favorites", jsonArray)
    }
    return envelope.toString(2)
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
      val trimmed = jsonStr.trim()
      val jsonArray: JSONArray = if (trimmed.startsWith("{")) {
        val envelope = JSONObject(trimmed)
        envelope.optJSONArray("favorites") ?: JSONArray()
      } else if (trimmed.startsWith("[")) {
        JSONArray(trimmed)
      } else {
        return 0
      }

      for (i in 0 until jsonArray.length()) {
        val item = jsonArray.get(i)
        if (item is JSONObject) {
          val promptText = item.optString("prompt", "").ifEmpty { item.optString("text", "") }
          if (promptText.isNotBlank()) {
            val entity = FavoritePromptEntity(
              id = item.optLong("id", System.currentTimeMillis() + i),
              prompt = promptText,
              category = item.optString("category", "Imported"),
              subgenre = item.optString("subgenre", "Custom"),
              bpmHint = item.optString("bpmHint", ""),
              chars = item.optInt("chars", promptText.length),
              savedAt = item.optLong("savedAt", System.currentTimeMillis())
            )
            favoriteDao.insertFavorite(entity)
            count++
          }
        } else if (item is String && item.isNotBlank()) {
          // Check if string matches a curated prompt ID or text
          val curatedMatch = CuratedPrompts.ALL_CURATED.find { 
            it.id.toString() == item || it.prompt.equals(item, ignoreCase = true) 
          }
          if (curatedMatch != null) {
            insertFavorite(curatedMatch)
            count++
          } else {
            val entity = FavoritePromptEntity(
              id = System.currentTimeMillis() + i,
              prompt = item,
              category = "Imported",
              subgenre = "Custom",
              bpmHint = "",
              chars = item.length,
              savedAt = System.currentTimeMillis()
            )
            favoriteDao.insertFavorite(entity)
            count++
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return count
  }
}
