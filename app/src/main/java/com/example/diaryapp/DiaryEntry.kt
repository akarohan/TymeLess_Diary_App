package com.example.diaryapp

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.room.Index
import java.util.Date

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long, // Store as epoch millis for easier querying
    val htmlContent: String,
    val imagePaths: List<String>,
    val title: String? = null,
    @TypeConverters(DiaryTypeConverters::class)
    val audioList: List<AudioItem> = emptyList(),

    val mood: Int = 5, // Mood rating from -5 to 5, default neutral (5)
    val deletedAt: Long? = null // Timestamp in millis when deleted, null if not deleted
) 