package com.example.diaryapp

import androidx.room.*

@Dao
interface DiaryEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: DiaryEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DiaryEntry>)

    @Query("SELECT * FROM diary_entries WHERE date = :date")
    suspend fun getEntriesByDate(date: Long): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryByDate(date: Long): DiaryEntry?

    @Query("SELECT * FROM diary_entries ORDER BY date DESC")
    suspend fun getAllEntries(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE deletedAt IS NULL ORDER BY date DESC")
    suspend fun getAllNonDeletedEntries(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE deletedAt IS NOT NULL ORDER BY date DESC")
    suspend fun getAllDeletedEntries(): List<DiaryEntry>

    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM diary_entries WHERE date = :date")
    suspend fun deleteEntryByDate(date: Long)

    @Query("DELETE FROM diary_entries WHERE deletedAt IS NOT NULL")
    suspend fun deleteAllDeleted()
} 