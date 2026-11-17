package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "prompt_templates",
  indices = [
    Index(value = ["category"]),
    Index(value = ["isPinned", "useCount", "lastUsedAt"])
  ]
)
data class PromptTemplateEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val category: String,
  val lane: String,
  val jobType: String = JobType.ORIGINAL.value,
  val bpmHint: String = "",
  val stylePrompt: String,
  val lyricsMap: String = "",
  val excludeText: String = "",
  val variety: Int = 0,
  val weirdness: Int = 25,
  val styleInfluence: Int = 70,
  val audioInfluence: Int = 80,
  val useCount: Int = 0,
  val isPinned: Boolean = false,
  val isStarter: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val lastUsedAt: Long = System.currentTimeMillis()
)
