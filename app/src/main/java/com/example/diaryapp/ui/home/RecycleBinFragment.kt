package com.example.diaryapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.diaryapp.DiaryDatabase
import com.example.diaryapp.DiaryEntry
import com.example.diaryapp.R
import com.example.diaryapp.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ImageButton
import android.widget.TextView
import java.util.concurrent.TimeUnit
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class RecycleBinFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecycleBinAdapter
    private val items = mutableListOf<RecycleBinItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_recycle_bin, container, false)
        recyclerView = view.findViewById(R.id.recycleBinRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = RecycleBinAdapter(items, ::onRestore, ::onDelete)
        recyclerView.adapter = adapter

        // Remove old deleteForeverButton logic
        // Set has options menu for toolbar menu
        setHasOptionsMenu(true)

        // Auto-delete items older than 7 days
        lifecycleScope.launch {
            autoDeleteOldItems()
            loadRecycleBinItems()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Recycle Bin"
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_recycle_bin, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_forever -> {
                permanentlyDeleteAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadRecycleBinItems() {
        lifecycleScope.launch {
            val db = DiaryDatabase.getDatabase(requireContext())
            Log.d("RecycleBin", "Loading deleted notes and diaries...")
            val deletedNotes: List<Note> = withContext(Dispatchers.IO) {
                db.noteDao().getAllDeletedNotes().also { Log.d("RecycleBin", "Deleted notes: $it") }
            }
            val deletedDiaries: List<DiaryEntry> = withContext(Dispatchers.IO) {
                db.diaryEntryDao().getAllDeletedEntries().also { Log.d("RecycleBin", "Deleted diaries: $it") }
            }
            items.clear()
            // Always wrap as RecycleBinItem
            items.addAll(deletedDiaries.map { diary -> RecycleBinItem.Diary(diary) })
            items.addAll(deletedNotes.map { note -> RecycleBinItem.Note(note) })
            adapter.notifyDataSetChanged()
        }
    }

    private fun onRestore(item: RecycleBinItem) {
        lifecycleScope.launch {
            val db = DiaryDatabase.getDatabase(requireContext())
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
            Toast.makeText(requireContext(), "Restored!", Toast.LENGTH_SHORT).show()
            loadRecycleBinItems()
        }
    }

    private fun onDelete(item: RecycleBinItem) {
        lifecycleScope.launch {
            val db = DiaryDatabase.getDatabase(requireContext())
            when (item) {
                is RecycleBinItem.Diary -> withContext(Dispatchers.IO) { db.diaryEntryDao().deleteEntryByDate(item.entry.date) }
                is RecycleBinItem.Note -> withContext(Dispatchers.IO) { db.noteDao().delete(item.note) }
            }
            Toast.makeText(requireContext(), "Deleted forever!", Toast.LENGTH_SHORT).show()
            loadRecycleBinItems()
        }
    }

    private suspend fun autoDeleteOldItems() {
        val db = DiaryDatabase.getDatabase(requireContext())
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
            val db = DiaryDatabase.getDatabase(requireContext())
            withContext(Dispatchers.IO) {
                db.noteDao().deleteAllDeleted()
                db.diaryEntryDao().deleteAllDeleted()
            }
            Toast.makeText(requireContext(), "Recycle Bin emptied!", Toast.LENGTH_SHORT).show()
            loadRecycleBinItems()
        }
    }
} 