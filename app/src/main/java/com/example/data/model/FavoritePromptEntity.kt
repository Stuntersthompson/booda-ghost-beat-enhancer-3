package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_prompts")
data class FavoritePromptEntity(
  @PrimaryKey
  val id: Long, // Matches CuratedSunoPrompt.id
  val prompt: String,
  val category: String,
  val subgenre: String = "",
  val bpmHint: String = "",
  val chars: Int = prompt.length,
  val savedAt: Long = System.currentTimeMillis()
)
