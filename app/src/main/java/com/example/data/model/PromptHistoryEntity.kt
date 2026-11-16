package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "prompt_history",
  indices = [Index(value = ["createdAt"])]
)
data class PromptHistoryEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val prompt: String,
  val source: String,
  val lane: String,
  val jobType: String = JobType.PRESERVE.value,
  val bpm: Int? = null,
  val chars: Int = prompt.length,
  val createdAt: Long = System.currentTimeMillis()
)
