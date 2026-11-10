package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.FavoritePromptEntity
import com.example.data.model.TakeEntity

@Database(
  entities = [TakeEntity::class, FavoritePromptEntity::class],
  version = 3,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun takeDao(): TakeDao
  abstract fun favoritePromptDao(): FavoritePromptDao

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

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "booda_beat.db"
        )
          .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
