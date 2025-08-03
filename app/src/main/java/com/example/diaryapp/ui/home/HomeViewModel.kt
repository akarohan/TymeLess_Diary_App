package com.example.diaryapp.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diaryapp.DiaryDatabase
import com.example.diaryapp.DiaryEntry
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _entries = MutableLiveData<List<DiaryEntry>>()
    val entries: LiveData<List<DiaryEntry>> = _entries

    private val db = DiaryDatabase.getDatabase(application)

    fun loadEntries() {
        viewModelScope.launch {
            _entries.postValue(db.diaryEntryDao().getAllNonDeletedEntries())
        }
    }
}