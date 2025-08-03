package com.example.diaryapp.ui.home

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.diaryapp.DiaryDatabase
import com.example.diaryapp.DiaryEntry
import com.example.diaryapp.R
import com.example.diaryapp.data.Note
import com.example.diaryapp.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RecycleBinActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecycleBinAdapter
    private val items = mutableListOf<RecycleBinItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_recycle_bin)
        
        // Apply theme to prevent flashing
        ThemeManager.applyTheme(this)

        // Set up modern header
        val backButton = findViewById<android.widget.ImageButton>(R.id.backButton)
        val toolbarTitle = findViewById<android.widget.TextView>(R.id.toolbarTitle)
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set title
        toolbarTitle.text = "Recycle Bin"

        recyclerView = findViewById(R.id.recycleBinRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = RecycleBinAdapter(items, ::onRestore, ::onDelete)
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            autoDeleteOldItems()
            loadRecycleBinItems()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_recycle_bin, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete_forever -> {
                // Show confirmation dialog before deleting all
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Empty Recycle Bin")
                    .setMessage("Are you sure you want to permanently delete all items in the recycle bin?")
                    .setPositiveButton("Delete") { _, _ ->
                        permanentlyDeleteAll()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh theme when returning to activity
        ThemeManager.applyTheme(this)
    }

    private fun loadRecycleBinItems() {
        lifecycleScope.launch {
            val db = DiaryDatabase.getDatabase(this@RecycleBinActivity)
            Log.d("RecycleBin", "Loading deleted notes and diaries...")
            val deletedNotes: List<Note> = withContext(Dispatchers.IO) {
                db.noteDao().getAllDeletedNotes().also { Log.d("RecycleBin", "Deleted notes: $it") }
            }
            val deletedDiaries: List<DiaryEntry> = withContext(Dispatchers.IO) {
                db.diaryEntryDao().getAllDeletedEntries().also { Log.d("RecycleBin", "Deleted diaries: $it") }
            }
            items.clear()
            items.addAll(deletedDiaries.map { diary -> RecycleBinItem.Diary(diary) })
            items.addAll(deletedNotes.map { note -> RecycleBinItem.Note(note) })
            adapter.notifyDataSetChanged()
        }
    }

    private fun onRestore(item: RecycleBinItem) {
        lifecycleScope.launch {
            val db = DiaryDatabase.getDatabase(this@RecycleBinActivity)
            when (item) {
                is RecycleBinItem.Diary -> {
                    val restored: DiaryEntry = item.entry.copy(deletedAt = null)
                    withContext(Dispatchers.IO) { db.diaryEntryDao().insertOrUpdate(restored) }
                }
                is RecycleBinItem.Note -> {
                    val restored: Note = item.note.copy(deletedAt = null)
                    withContext(Dispatchers.IO) { db.noteDao().insertOrReplace(restored) }
                }
            }
            Toast.makeText(this@RecycleBinActivity, "Restored!", Toast.LENGTH_SHORT).show()
            loadRecycleBinItems()
        }
    }

    private fun onDelete(item: RecycleBinItem) {
        lifecycleScope.launch {
            val db = DiaryDatabase.getDatabase(this@RecycleBinActivity)
            when (item) {
                is RecycleBinItem.Diary -> withContext(Dispatchers.IO) { db.diaryEntryDao().deleteEntryByDate(item.entry.date) }
                is RecycleBinItem.Note -> withContext(Dispatchers.IO) { db.noteDao().delete(item.note) }
            }
            Toast.makeText(this@RecycleBinActivity, "Deleted forever!", Toast.LENGTH_SHORT).show()
            loadRecycleBinItems()
        }
    }

    private suspend fun autoDeleteOldItems() {
        val db = DiaryDatabase.getDatabase(this@RecycleBinActivity)
        val now = System.currentTimeMillis()
        val sevenDaysMillis = TimeUnit.DAYS.toMillis(7)
        withContext(Dispatchers.IO) {
            val oldNotes = db.noteDao().getAllNotesSync().filter { it.deletedAt != null && now - it.deletedAt!! > sevenDaysMillis }
            val oldDiaries = db.diaryEntryDao().getAllEntries().filter { it.deletedAt != null && now - it.deletedAt!! > sevenDaysMillis }
            oldNotes.forEach { db.noteDao().delete(it) }
            oldDiaries.forEach { db.diaryEntryDao().deleteEntryByDate(it.date) }
        }
    }

    private fun permanentlyDeleteAll() {
        lifecycleScope.launch {
            val db = DiaryDatabase.getDatabase(this@RecycleBinActivity)
            withContext(Dispatchers.IO) {
                db.noteDao().deleteAllDeleted()
                db.diaryEntryDao().deleteAllDeleted()
            }
            Toast.makeText(this@RecycleBinActivity, "Recycle Bin emptied!", Toast.LENGTH_SHORT).show()
            loadRecycleBinItems()
        }
    }
} 