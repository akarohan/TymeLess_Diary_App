package com.example.diaryapp

data class ChecklistItem(
    val id: String = System.currentTimeMillis().toString(),
    var text: String = "",
    var isChecked: Boolean = false
) 