package com.example.diaryapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MountainsThemeActivity : AppCompatActivity() {
    private lateinit var adapter: MountainsThemeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mountains_theme)

        setupHeader()
        setupRecyclerView()
        setupBackButton()
    }

    private fun setupHeader() {
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        headerTitle.text = "Mountains"
    }

    private fun setupBackButton() {
        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = MountainsThemeAdapter { mountainTheme ->
            // Show popup with Remove and Apply options
            showThemeOptionsDialog(mountainTheme)
        }

        val mountainsRecyclerView = findViewById<RecyclerView>(R.id.mountainsRecyclerView)
        mountainsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MountainsThemeActivity, 2)
            adapter = this@MountainsThemeActivity.adapter
        }

        // Add mountain themes - only one card with both day and night
        val mountainThemes = listOf(
            MountainTheme("Icemountain", R.drawable.bg_icemountain_optimized),
            MountainTheme("Cherry Blossom", R.drawable.bg_mountain_blossom_optimized)
        )

        adapter.submitList(mountainThemes)
    }

    private fun showThemeOptionsDialog(mountainTheme: MountainTheme) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Theme Options")
            .setMessage("Do you want to apply or remove this theme?")
            .setPositiveButton("Apply") { _, _ ->
                // Apply the theme based on the selected mountain theme
                try {
                    val dayResourceId: Int
                    val nightResourceId: Int
                    
                    // Get the correct resource IDs based on the theme
                    when (mountainTheme.name) {
                        "Icemountain" -> {
                                        dayResourceId = R.drawable.bg_icemountain_optimized
            nightResourceId = R.drawable.bg_icemountain_night_optimized
                        }
                        "Cherry Blossom" -> {
                                        dayResourceId = R.drawable.bg_mountain_blossom_optimized
            nightResourceId = R.drawable.bg_mountain_blossom_night_optimized
                        }
                        else -> {
                            Toast.makeText(this, "Unknown theme", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                    }
                    
                    // Save day version
                    val dayFile = File(filesDir, "theme_header_pic_day.jpg")
                    val dayBitmap = android.graphics.BitmapFactory.decodeResource(resources, dayResourceId)
                    FileOutputStream(dayFile).use { outputStream ->
                        dayBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                    
                    // Save night version
                    val nightFile = File(filesDir, "theme_header_pic_night.jpg")
                    val nightBitmap = android.graphics.BitmapFactory.decodeResource(resources, nightResourceId)
                    FileOutputStream(nightFile).use { outputStream ->
                        nightBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                    
                    // Copy the appropriate version based on current mode
                    val isNightMode = ThemeManager.isNightMode(this)
                    val currentFile = if (isNightMode) File(filesDir, "theme_header_pic_night.jpg") else File(filesDir, "theme_header_pic_day.jpg")
                    val outputFile = File(filesDir, "theme_header_pic.jpg")
                    currentFile.copyTo(outputFile, overwrite = true)
                    
                    // Save the file paths in preferences
                    val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("theme_header_pic_uri", outputFile.absolutePath)
                        .putString("theme_header_pic_day_uri", File(filesDir, "theme_header_pic_day.jpg").absolutePath)
                        .putString("theme_header_pic_night_uri", File(filesDir, "theme_header_pic_night.jpg").absolutePath)
                        .putString("selected_theme_name", mountainTheme.name)
                        .putInt("selected_theme_index", 0) // Set a valid index so MainActivity detects it
                        .putBoolean("is_custom_theme", false)
                        .apply()
                    
                    // Show success message
                    Toast.makeText(this, "${mountainTheme.name} theme applied!", Toast.LENGTH_SHORT).show()
                    
                    // Refresh the theme immediately
                    refreshThemeInMainActivity()
                    
                    // Close the activity
                    finish()
                    
                } catch (e: Exception) {
                    Toast.makeText(this, "Error applying theme: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Remove") { _, _ ->
                // Remove the theme
                val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
                prefs.edit().apply {
                    putString("theme_header_pic_uri", null) // Clear custom theme
                    putString("theme_header_pic_day_uri", null)
                    putString("theme_header_pic_night_uri", null)
                    putString("selected_theme_name", null)
                    putInt("selected_theme_index", -1) // Clear the index
                    putInt("selected_theme_day_resource", 0)
                    putInt("selected_theme_night_resource", 0)
                    putBoolean("is_custom_theme", false)
                    apply()
                }
                
                // Show success message
                Toast.makeText(this, "${mountainTheme.name} theme removed!", Toast.LENGTH_SHORT).show()
                
                // Refresh the theme immediately
                refreshThemeInMainActivity()
                
                // Close the activity
                finish()
            }
            .setNeutralButton("Cancel") { _, _ ->
                // Dismiss the dialog
            }
            .create()
        dialog.show()
    }

    private fun refreshThemeInMainActivity() {
        // Send broadcast to refresh theme in MainActivity
        val intent = Intent(ThemeManager.THEME_UPDATE_ACTION)
        sendBroadcast(intent)
    }

    data class MountainTheme(
        val name: String,
        val imageResourceId: Int
    )

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MountainsThemeActivity::class.java)
            context.startActivity(intent)
        }
    }
} 