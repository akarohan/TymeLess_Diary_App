package com.example.diaryapp

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout

object ThemeManager {
    
    const val THEME_UPDATE_ACTION = "com.example.diaryapp.THEME_UPDATED"
    
    /**
     * Apply the current theme mode to an activity (without transition)
     */
    fun applyTheme(activity: Activity) {
        val themePrefs = activity.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isNight = themePrefs.getBoolean("is_night_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isNight) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
    
    /**
     * Apply the current theme mode to an activity with smooth transition
     */
    fun applyThemeWithTransition(activity: Activity) {
        val themePrefs = activity.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isNight = themePrefs.getBoolean("is_night_mode", false)
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val shouldBeNight = if (isNight) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        
        if (currentMode != shouldBeNight) {
            val fromNight = currentMode == AppCompatDelegate.MODE_NIGHT_YES
            applySmoothThemeTransition(activity, fromNight, isNight)
        }
    }
    
    /**
     * Toggle between day and night mode with smooth transition
     */
    fun toggleTheme(activity: Activity) {
        val themePrefs = activity.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val currentIsNight = themePrefs.getBoolean("is_night_mode", false)
        val newIsNight = !currentIsNight
        
        // Save the new state
        themePrefs.edit().putBoolean("is_night_mode", newIsNight).apply()
        
        // Apply smooth transition
        applySmoothThemeTransition(activity, currentIsNight, newIsNight)
    }
    
    /**
     * Apply smooth theme transition - SIMPLIFIED VERSION
     */
    fun applySmoothThemeTransition(activity: Activity, fromNight: Boolean, toNight: Boolean) {
        android.util.Log.d("ThemeManager", "=== THEME MANAGER START ===")
        android.util.Log.d("ThemeManager", "applySmoothThemeTransition called: fromNight=$fromNight, toNight=$toNight")
        
        try {
            // Save the theme preference immediately
            val themePrefs = activity.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            themePrefs.edit().putBoolean("is_night_mode", toNight).apply()
            android.util.Log.d("ThemeManager", "Theme preference saved successfully")
            
            // Set the night mode using AppCompatDelegate
            AppCompatDelegate.setDefaultNightMode(
                if (toNight) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            android.util.Log.d("ThemeManager", "AppCompatDelegate night mode set to: ${if (toNight) "NIGHT" else "DAY"}")
            
            // Recreate the activity to apply the new theme
            activity.recreate()
            android.util.Log.d("ThemeManager", "Activity recreated to apply new theme")
            
            android.util.Log.d("ThemeManager", "=== THEME MANAGER COMPLETED ===")
        } catch (e: Exception) {
            android.util.Log.e("ThemeManager", "Error in applySmoothThemeTransition", e)
        }
    }
    
    /**
     * Update HomeFragment background directly
     */
    private fun updateHomeFragmentBackground(activity: Activity, backgroundColor: Int) {
        try {
            // Find the current fragment
            val fragmentManager = (activity as androidx.appcompat.app.AppCompatActivity).supportFragmentManager
            val currentFragment = fragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
            
            if (currentFragment is com.example.diaryapp.ui.home.HomeFragment) {
                // Update the fragment's root view background
                currentFragment.view?.setBackgroundColor(backgroundColor)
                
                // Update SwipeRefreshLayout background
                val swipeRefreshLayout = currentFragment.view?.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
                swipeRefreshLayout?.setBackgroundColor(backgroundColor)
                
                // Force redraw
                currentFragment.view?.invalidate()
                swipeRefreshLayout?.invalidate()
                
                android.util.Log.d("ThemeManager", "HomeFragment background updated directly")
            }
        } catch (e: Exception) {
            android.util.Log.e("ThemeManager", "Error updating HomeFragment background", e)
        }
    }
    
    /**
     * Blend two colors based on progress - OPTIMIZED
     */
    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio
        val r = Color.red(from) * inverseRatio + Color.red(to) * ratio
        val g = Color.green(from) * inverseRatio + Color.green(to) * ratio
        val b = Color.blue(from) * inverseRatio + Color.blue(to) * ratio
        return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }
    
    /**
     * Check if night mode is currently enabled
     */
    fun isNightMode(context: Context): Boolean {
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        return themePrefs.getBoolean("is_night_mode", false)
    }
    
    /**
     * Apply theme transition to any activity
     */
    fun applyThemeTransition(activity: Activity, toNight: Boolean) {
        val currentIsNight = isNightMode(activity)
        if (currentIsNight != toNight) {
            applySmoothThemeTransition(activity, currentIsNight, toNight)
        }
    }
    
    /**
     * Force refresh edit text colors for an activity
     */
    fun refreshEditTextColors(activity: Activity) {
        val isNight = isNightMode(activity)
        val rootView = activity.window.decorView.findViewById<View>(android.R.id.content)
        
        // Find and update edit text colors
        updateEditTextColors(rootView, isNight)
    }
    
    /**
     * Update edit text colors based on current theme
     */
    private fun updateEditTextColors(view: View, isNight: Boolean) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateEditTextColors(view.getChildAt(i), isNight)
            }
        } else if (view is android.widget.EditText) {
            if (isEditTextView(view)) {
                val textColor = if (isNight) {
                    android.graphics.Color.parseColor("#FFFFFF") // White text in night mode
                } else {
                    android.graphics.Color.parseColor("#000000") // Black text in day mode
                }
                
                val hintColor = if (isNight) {
                    android.graphics.Color.parseColor("#AAAAAA") // Light gray hint in night mode
                } else {
                    android.graphics.Color.parseColor("#888888") // Dark gray hint in day mode
                }
                
                view.setTextColor(textColor)
                view.setHintTextColor(hintColor)
            }
        }
    }
    
    /**
     * Check if a view is an edit text element
     */
    private fun isEditTextView(view: View): Boolean {
        val viewId = view.id
        val viewClass = view.javaClass.simpleName
        
        // Check if it's an EditText
        if (viewClass.contains("EditText", ignoreCase = true)) {
            return true
        }
        
        // Check for specific edit text IDs
        val editTextIds = listOf(
            R.id.titleEditText,
            R.id.mainEditText
        )
        
        return editTextIds.contains(viewId)
    }
    

} 