package com.example.data.repository

import com.example.data.db.FavoritePromptDao
import com.example.data.model.CuratedSunoPrompt
import com.example.data.model.FavoritePromptEntity
import com.example.data.model.STYLE_LIMIT
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
    return importFavoritesDetailed(jsonStr).importedCount
  }

  suspend fun importFavoritesDetailed(jsonStr: String): ImportBatchResult {
    val trimmed = jsonStr.trim()
    if (trimmed.isEmpty()) {
      return ImportBatchResult(0, 0, "Empty JSON input.")
    }
    var count = 0
    var skipped = 0
    return try {
      val jsonArray: JSONArray = when {
        trimmed.startsWith("{") -> {
          val envelope = JSONObject(trimmed)
          envelope.optJSONArray("favorites") ?: JSONArray()
        }
        trimmed.startsWith("[") -> JSONArray(trimmed)
        else -> return ImportBatchResult(0, 0, "Invalid JSON format.")
      }

      val limit = minOf(jsonArray.length(), 250)
      for (i in 0 until limit) {
        val item = jsonArray.opt(i)
        if (item is JSONObject) {
          val promptText = item.optString("prompt", "").ifEmpty { item.optString("text", "") }
            .trim()
            .take(STYLE_LIMIT)
          if (promptText.isNotBlank()) {
            val rawSaved = item.optLong("savedAt", System.currentTimeMillis())
            val entity = FavoritePromptEntity(
              id = item.optLong("id", System.currentTimeMillis() + i),
              prompt = promptText,
              category = item.optString("category", "Imported").trim().take(80).ifBlank { "Imported" },
              subgenre = item.optString("subgenre", "Custom").trim().take(80).ifBlank { "Custom" },
              bpmHint = item.optString("bpmHint", "").trim().take(40),
              chars = promptText.length,
              savedAt = if (rawSaved > 0L) rawSaved else System.currentTimeMillis()
            )
            favoriteDao.insertFavorite(entity)
            count++
          } else {
            skipped++
          }
        } else if (item is String && item.isNotBlank()) {
          val clean = item.trim().take(STYLE_LIMIT)
          val curatedMatch = CuratedPrompts.ALL_CURATED.find {
            it.id.toString() == clean || it.prompt.equals(clean, ignoreCase = true)
          }
          if (curatedMatch != null) {
            insertFavorite(curatedMatch)
            count++
          } else {
            val entity = FavoritePromptEntity(
              id = System.currentTimeMillis() + i,
              prompt = clean,
              category = "Imported",
              subgenre = "Custom",
              bpmHint = "",
              chars = clean.length,
              savedAt = System.currentTimeMillis()
            )
            favoriteDao.insertFavorite(entity)
            count++
          }
        } else {
          skipped++
        }
      }
      ImportBatchResult(
        importedCount = count,
        skippedCount = skipped,
        errorMessage = if (count == 0) "No valid favorite prompts found." else null
      )
    } catch (e: Exception) {
      e.printStackTrace()
      ImportBatchResult(count, skipped, "Malformed JSON: ${e.localizedMessage ?: "parse failed"}")
    }
  }
}
