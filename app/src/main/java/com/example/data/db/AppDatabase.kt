package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.FavoritePromptEntity
import com.example.data.model.PromptHistoryEntity
import com.example.data.model.PromptTemplateEntity
import com.example.data.model.TakeEntity

@Database(
  entities = [
    TakeEntity::class,
    FavoritePromptEntity::class,
    PromptHistoryEntity::class,
    PromptTemplateEntity::class
  ],
  version = 5,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun takeDao(): TakeDao
  abstract fun favoritePromptDao(): FavoritePromptDao
  abstract fun promptHistoryDao(): PromptHistoryDao
  abstract fun promptTemplateDao(): PromptTemplateDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        try {
          db.execSQL("ALTER TABLE takes ADD COLUMN beatTitle TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE takes ADD COLUMN bpm REAL NOT NULL DEFAULT 0.0")
          db.execSQL("ALTER TABLE takes ADD COLUMN audioFilePath TEXT DEFAULT NULL")
          db.execSQL("ALTER TABLE takes ADD COLUMN analysisMetadata TEXT NOT NULL DEFAULT '{}'")
        } catch (_: Exception) {}
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS favorite_prompts (
            id INTEGER NOT NULL PRIMARY KEY,
            prompt TEXT NOT NULL,
            category TEXT NOT NULL,
            subgenre TEXT NOT NULL,
            bpmHint TEXT NOT NULL,
            chars INTEGER NOT NULL,
            savedAt INTEGER NOT NULL
          )
        """.trimIndent())
      }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS prompt_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            prompt TEXT NOT NULL,
            source TEXT NOT NULL,
            lane TEXT NOT NULL,
            jobType TEXT NOT NULL,
            bpm INTEGER,
            chars INTEGER NOT NULL,
            createdAt INTEGER NOT NULL
          )
        """.trimIndent())
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_prompt_history_createdAt ON prompt_history(createdAt)"
        )
      }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS prompt_templates (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            category TEXT NOT NULL,
            lane TEXT NOT NULL,
            jobType TEXT NOT NULL,
            bpmHint TEXT NOT NULL,
            stylePrompt TEXT NOT NULL,
            lyricsMap TEXT NOT NULL,
            excludeText TEXT NOT NULL,
            variety INTEGER NOT NULL,
            weirdness INTEGER NOT NULL,
            styleInfluence INTEGER NOT NULL,
            audioInfluence INTEGER NOT NULL,
            useCount INTEGER NOT NULL,
            isPinned INTEGER NOT NULL,
            isStarter INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            lastUsedAt INTEGER NOT NULL
          )
        """.trimIndent())
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_prompt_templates_category ON prompt_templates(category)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_prompt_templates_isPinned_useCount_lastUsedAt ON prompt_templates(isPinned, useCount, lastUsedAt)"
        )
      }
    }

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "booda_beat.db"
        )
          .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
