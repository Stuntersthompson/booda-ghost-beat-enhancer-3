package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

const val LOCK_LINE = "Preserve the original sample and sampled vocals exactly as performed"
const val STYLE_LIMIT = 1000
const val SUNO_STYLE_BOX_LIMIT = 200
const val EXCLUDE_LIMIT = 1000
const val LYRICS_LIMIT = 5000
const val SIMPLE_WORKING_LIMIT = 3000

enum class JobType(val value: String) {
  ORIGINAL("original"),
  PRESERVE("preserve");

  companion object {
    fun fromString(str: String): JobType =
      if (str.equals("preserve", ignoreCase = true)) PRESERVE else ORIGINAL
  }
}

data class StylePrompt(
  val id: Int,
  val track: Int,
  val applicability: String,
  val subgenre: String,
  val text: String,
  val chars: Int
)

data class GospelPrompt(
  val id: Int,
  val group: String,
  val applicability: String,
  val text: String,
  val chars: Int
)

data class SlotTemplate(
  val id: Int,
  val applicability: String,
  val text: String,
  val slots: List<String>,
  val chars: Int
)

data class LyricsTemplate(
  val id: Int,
  val topic: String,
  val variant: String, // "A" (ORIGINAL) or "B" (PRESERVE)
  val applicability: String,
  val text: String,
  val chars: Int
)

data class ExcludeBank(
  val id: String,
  val name: String,
  val text: String,
  val chars: Int
)

data class SettingsCard(
  val id: String,
  val title: String,
  val route: String,
  val model: String,
  val variety: Int,
  val weirdness: Int,
  val styleInfluence: Int,
  val audioInfluence: Int,
  val notes: String
)

data class FrameworkProfile(
  val name: String,
  val layout: String,
  val build: String,
  val drop: String,
  val breakdown: String,
  val signature: String
)

data class ExpansionLane(
  val name: String,
  val region: String,
  val styleHint: String,
  val tempoRange: String,
  val drumTexture: String,
  val bassProfile: String
)

data class RemixTemplate(
  val id: String,
  val lane: String,
  val family: String,
  val desc: String,
  val prompt: String,
  val chars: Int
)

data class CuratedSunoPrompt(
  val id: Int,
  val category: String, // "Sample Beef-Up", "Gospel Trap", "Era & Soul Chop", "Drill & Modern Stutter"
  val subgenre: String,
  val bpmHint: String,
  val prompt: String,
  val chars: Int
)

@Entity(tableName = "takes")
data class TakeEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val beatTitle: String = "",
  val bpm: Double = 0.0,
  val audioFilePath: String? = null,
  val analysisMetadata: String = "{}",
  val name: String,
  val lane: String,
  val jobType: String, // "original" or "preserve"
  val style: String,
  val excludeText: String,
  val lyrics: String,
  val settings: String,
  val result: String = "pending", // "pending", "winner", "miss"
  val nextChange: String = "",
  val createdAt: Long = System.currentTimeMillis()
)
