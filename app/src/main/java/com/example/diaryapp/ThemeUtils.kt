package com.example.diaryapp

import android.app.Activity
import androidx.core.content.ContextCompat

object ThemeUtils {
    /**
     * Get the current theme color - always returns gray since theme color functionality is removed
     */
    fun getCurrentThemeColor(activity: Activity): Int {
        return ContextCompat.getColor(activity, R.color.greyback)
    }
} 