package com.example.diaryapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.example.diaryapp.data.Note
import com.example.diaryapp.data.NoteDao
import com.example.diaryapp.data.StepCountData
import com.example.diaryapp.data.StepCountDao

@Database(entities = [DiaryEntry::class, Note::class, StepCountData::class], version = 14, exportSchema = false)
@TypeConverters(DiaryTypeConverters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun noteDao(): NoteDao
    abstract fun stepCountDao(): StepCountDao

    companion object {
        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        fun getDatabase(context: Context): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "diary_database"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add thumbnailPath column to notes table
                database.execSQL("ALTER TABLE notes ADD COLUMN thumbnailPath TEXT")
            }
        }
        
        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create step_count_data table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS step_count_data (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        stepCount INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """)
            }
        }
        
        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add location fields to diary_entries table
                database.execSQL("ALTER TABLE diary_entries ADD COLUMN latitude REAL")
                database.execSQL("ALTER TABLE diary_entries ADD COLUMN longitude REAL")
                database.execSQL("ALTER TABLE diary_entries ADD COLUMN locationName TEXT")
                database.execSQL("ALTER TABLE diary_entries ADD COLUMN address TEXT")
                
                // Add location fields to notes table
                database.execSQL("ALTER TABLE notes ADD COLUMN latitude REAL")
                database.execSQL("ALTER TABLE notes ADD COLUMN longitude REAL")
                database.execSQL("ALTER TABLE notes ADD COLUMN locationName TEXT")
                database.execSQL("ALTER TABLE notes ADD COLUMN address TEXT")
            }
        }
        
        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Remove mood column from diary_entries table
                // Note: SQLite doesn't support DROP COLUMN directly, so we'll create a new table
                database.execSQL("""
                    CREATE TABLE diary_entries_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        htmlContent TEXT NOT NULL,
                        imagePaths TEXT NOT NULL,
                        title TEXT,
                        audioList TEXT NOT NULL,
                        deletedAt INTEGER,
                        latitude REAL,
                        longitude REAL,
                        locationName TEXT,
                        address TEXT
                    )
                """)
                
                // Copy data from old table to new table (excluding mood column)
                database.execSQL("""
                    INSERT INTO diary_entries_new (id, date, htmlContent, imagePaths, title, audioList, deletedAt, latitude, longitude, locationName, address)
                    SELECT id, date, htmlContent, imagePaths, title, audioList, deletedAt, latitude, longitude, locationName, address
                    FROM diary_entries
                """)
                
                // Drop old table and rename new table
                database.execSQL("DROP TABLE diary_entries")
                database.execSQL("ALTER TABLE diary_entries_new RENAME TO diary_entries")
            }
        }
    }
} 