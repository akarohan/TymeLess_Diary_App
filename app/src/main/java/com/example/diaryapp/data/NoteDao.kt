package com.example.diaryapp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotes(): LiveData<List<Note>>

    @Query("SELECT * FROM notes ORDER BY id DESC")
    suspend fun getAllNotesSync(): List<Note>

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(note: Note): Long

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun deleteAllDeleted()

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY id DESC")
    fun getAllNonDeletedNotes(): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY id DESC")
    suspend fun getAllNonDeletedNotesSync(): List<Note>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY id DESC")
    fun getAllNonDeletedNotesBlocking(): List<Note>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY id DESC")
    suspend fun getAllDeletedNotes(): List<Note>

    @Query("UPDATE notes SET content = :content WHERE id = :noteId")
    suspend fun updateNoteContent(noteId: Int, content: String)
} 