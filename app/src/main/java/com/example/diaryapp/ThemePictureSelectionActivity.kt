package com.example.diaryapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import androidx.activity.result.contract.ActivityResultContracts
import com.yalantis.ucrop.UCrop
import android.view.LayoutInflater
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.Gravity
import android.graphics.Color
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import com.example.diaryapp.databinding.ItemThemePictureBinding
import android.graphics.Bitmap
import android.graphics.Canvas

class ThemePictureSelectionActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var dayPreviewImageView: ImageView
    private lateinit var nightPreviewImageView: ImageView
    private lateinit var themeRecyclerView: RecyclerView
    private lateinit var applyButton: Button
    private lateinit var removeButton: Button
    private var selectedThemeIndex: Int = -1
    private var isNightMode: Boolean = false
    private lateinit var pickDayImageLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var pickNightImageLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var pickCustomImageLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var adapter: SectionedThemeAdapter
    
    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable?): Bitmap? {
        if (drawable == null) return null
        
        return try {
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1000
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1000
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            Log.e("ThemePicture", "Failed to convert drawable to bitmap", e)
            null
        }
    }

    data class ThemePicture(
        val name: String,
        val dayResourceId: Int,
        val nightResourceId: Int,
        val mode: ThemeMode = ThemeMode.DYNAMIC,
        val isCustom: Boolean = false
    )

    enum class ThemeMode {
        STATIC, DYNAMIC
    }

    // Predefined theme pictures with day and night versions
    private val dayNightThemes = listOf(
        ThemePicture("Mountains", R.drawable.bg_icemountain_optimized, R.drawable.bg_icemountain_night_optimized, ThemeMode.DYNAMIC),
        ThemePicture("Cherry Blossom", R.drawable.bg_mountain_blossom_optimized, R.drawable.bg_mountain_blossom_night_optimized, ThemeMode.DYNAMIC)
    )

    // Standalone themes that don't change with day/night mode
    private val standaloneThemes = listOf(
        ThemePicture("Batman vs Superman", R.drawable.batman_x_superman, R.drawable.batman_x_superman, ThemeMode.STATIC),
        ThemePicture("Cherry Blossom", R.drawable.cherry_blossom_1, R.drawable.cherry_blossom_1, ThemeMode.STATIC),
        ThemePicture("Couple", R.drawable.couple_1, R.drawable.couple_1, ThemeMode.STATIC),
        ThemePicture("Forest", R.drawable.forest_1, R.drawable.forest_1, ThemeMode.STATIC),
        ThemePicture("Tanjiro", R.drawable.tanjiro_1, R.drawable.tanjiro_1, ThemeMode.STATIC),
        ThemePicture("White Flower", R.drawable.white_flower_1, R.drawable.white_flower_1, ThemeMode.STATIC),
        ThemePicture("Yellow Flower", R.drawable.yellow_flower_1, R.drawable.yellow_flower_1, ThemeMode.STATIC),
        ThemePicture("Giyu Tomioka", R.drawable.giyu_tomioka_1, R.drawable.giyu_tomioka_1, ThemeMode.STATIC)
    )

    private val customThemes = listOf(
        ThemePicture("Custom", R.drawable.ic_add_white, R.drawable.ic_add_white, ThemeMode.STATIC, isCustom = true)
    )

    // Combined list for the adapter
    private val allThemes = dayNightThemes + standaloneThemes + customThemes

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before anything else!
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_picture_selection)

        // Set up modern header
        val backButton = findViewById<android.widget.ImageButton>(R.id.backButton)
        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set title
        toolbarTitle.text = "Theme Picture Selection"

        prefs = getEncryptedPrefs()
        isNightMode = ThemeManager.isNightMode(this)

        // Initialize selectedThemeIndex from preferences
        selectedThemeIndex = prefs.getInt("selected_theme_index", -1)

        // Initialize views
        dayPreviewImageView = findViewById(R.id.dayPreviewImageView)
        nightPreviewImageView = findViewById(R.id.nightPreviewImageView)
        themeRecyclerView = findViewById(R.id.themeRecyclerView)
        applyButton = findViewById(R.id.applyButton)
        removeButton = findViewById(R.id.removeButton)

        // Set up RecyclerView
        adapter = SectionedThemeAdapter(
            dayNightThemes, 
            standaloneThemes, 
            customThemes,
            onItemClick = { position ->
                selectedThemeIndex = position
                val themePicture = allThemes[position]
                updatePreviews()
                checkApplyButtonState()
            },
            onCustomThemeClick = {
                // Set the custom theme as selected
                selectedThemeIndex = allThemes.indexOfFirst { it.isCustom }
                // Clear previews first to remove any previous custom image
                clearPreviews()
                checkApplyButtonState()
                pickCustomImageLauncher.launch("image/*")
            }
        )
        
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        themeRecyclerView.layoutManager = layoutManager
        themeRecyclerView.adapter = adapter
        
        // Enable smooth scrolling
        themeRecyclerView.setHasFixedSize(true)
        themeRecyclerView.isNestedScrollingEnabled = true

        // Set up buttons
        applyButton.setOnClickListener {
            Log.d("ApplyButton", "Apply button clicked, selectedThemeIndex: $selectedThemeIndex")
            if (selectedThemeIndex >= 0 || hasCustomThemeImage()) {
                applyThemePicture()
            } else {
                Log.d("ApplyButton", "No theme selected and no custom theme image")
            }
        }

        removeButton.setOnClickListener {
            removeThemePicture()
        }

        // Check if user has a custom theme picture
        val currentThemePicUri = prefs.getString("theme_header_pic_uri", null)
        if (currentThemePicUri != null && File(currentThemePicUri).exists()) {
            removeButton.visibility = View.VISIBLE
        } else {
            removeButton.visibility = View.GONE
        }

        // Show current theme picture if exists
        showCurrentThemePicture()
        
        // Force clear previews if no theme is selected
        if (selectedThemeIndex < 0) {
            clearPreviews()
        }
        
        // Set up activity result launchers for custom images
        setupImageLaunchers()
        
        // Set up click listeners for preview images
        setupPreviewClickListeners()
        
        // Check if apply button should be enabled
        checkApplyButtonState()
    }

    override fun onResume() {
        super.onResume()
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isNight = themePrefs.getBoolean("is_night_mode", false)
            val applyButton = findViewById<androidx.appcompat.widget.AppCompatButton?>(R.id.applyButton)
    val removeButton = findViewById<androidx.appcompat.widget.AppCompatButton?>(R.id.removeButton)
        
        // Create rounded background drawables with 16dp corner radius
        if (isNight) {
            val whiteDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(ContextCompat.getColor(this@ThemePictureSelectionActivity, android.R.color.white))
            }
            applyButton?.setBackgroundDrawable(null) // Clear existing background first
            applyButton?.setBackgroundDrawable(whiteDrawable)
            applyButton?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            removeButton?.setBackgroundDrawable(null) // Clear existing background first
            removeButton?.setBackgroundDrawable(whiteDrawable)
            removeButton?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        } else {
            val blackDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(ContextCompat.getColor(this@ThemePictureSelectionActivity, android.R.color.black))
            }
            applyButton?.setBackgroundDrawable(null) // Clear existing background first
            applyButton?.setBackgroundDrawable(blackDrawable)
            applyButton?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            removeButton?.setBackgroundDrawable(null) // Clear existing background first
            removeButton?.setBackgroundDrawable(blackDrawable)
            removeButton?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    private fun updatePreviews() {
        // Check if we have a custom theme image even if no theme is selected
        val hasCustomTheme = hasCustomThemeImage()
        
        if (selectedThemeIndex >= 0) {
            val themePicture = allThemes[selectedThemeIndex]
            
            if (themePicture.isCustom) {
                // For custom theme, show the custom theme image
                updateCustomThemePreview()
            } else {
                // For predefined themes, show the drawable resources
                // Load day version
                Glide.with(this)
                    .load(themePicture.dayResourceId)
                    .centerCrop()
                    .into(dayPreviewImageView)

                // Load night version
                Glide.with(this)
                    .load(themePicture.nightResourceId)
                    .centerCrop()
                    .into(nightPreviewImageView)
            }
        } else if (hasCustomTheme) {
            // If no theme is selected but we have a custom theme image, show it
            updateCustomThemePreview()
        }
    }

    private fun applyThemePicture() {
        // Check if we have custom theme image available
        val hasCustomTheme = hasCustomThemeImage()
        
        Log.d("THEME_APPLY", "Starting applyThemePicture - selectedThemeIndex: $selectedThemeIndex, hasCustomTheme: $hasCustomTheme")
        
        // If no theme is selected and no custom theme image, return
        if (selectedThemeIndex < 0 && !hasCustomTheme) {
            Log.d("THEME_APPLY", "No theme selected and no custom theme image")
            return
        }

        // Determine the effective theme index
        val effectiveThemeIndex = if (selectedThemeIndex < 0) {
            // Find the custom theme index
            allThemes.indexOfFirst { it.isCustom }
        } else {
            selectedThemeIndex
        }
        
        Log.d("THEME_APPLY", "Effective theme index: $effectiveThemeIndex")
        val themePicture = allThemes[effectiveThemeIndex]
        
        try {
            Log.d("THEME_APPLY", "Applying theme: ${themePicture.name}, isCustom: ${themePicture.isCustom}")
            
            if (themePicture.isCustom) {
                // Handle custom theme as standalone (single image)
                val customThemeUri = prefs.getString("custom_theme_image_uri", null)
                Log.d("THEME_APPLY", "Custom theme URI: $customThemeUri")
                
                if (customThemeUri == null) {
                    Log.e("THEME_APPLY", "Custom theme URI is null")
                    return
                }
                
                // Save custom theme image
                val outputFile = File(filesDir, "theme_header_pic.jpg")
                File(customThemeUri).copyTo(outputFile, overwrite = true)
                
                // Save the file paths in preferences
                prefs.edit()
                    .putString("theme_header_pic_uri", outputFile.absolutePath)
                    .putString("custom_theme_image_uri", customThemeUri)
                    .putInt("selected_theme_index", effectiveThemeIndex)
                    .putBoolean("theme_just_applied", true) // Add flag to indicate theme was just applied
                    .apply()

                Log.d("THEME_APPLY", "Custom theme saved: ${outputFile.absolutePath}")
                Log.d("THEME_APPLY", "File exists: ${outputFile.exists()}")
                Log.d("THEME_APPLY", "File size: ${outputFile.length()} bytes")
                
                finish()
            } else {
                // Handle predefined themes - save both day and night versions
                val dayResourceId = themePicture.dayResourceId
                val nightResourceId = themePicture.nightResourceId
                
                // Save day version
                val dayBitmap = drawableToBitmap(ContextCompat.getDrawable(this, dayResourceId))
                val dayFile = File(filesDir, "theme_header_pic_day.jpg")
                if (dayBitmap != null) {
                    FileOutputStream(dayFile).use { outputStream ->
                        dayBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                        outputStream.flush()
                    }
                }
                
                // Save night version
                val nightBitmap = drawableToBitmap(ContextCompat.getDrawable(this, nightResourceId))
                val nightFile = File(filesDir, "theme_header_pic_night.jpg")
                if (nightBitmap != null) {
                    FileOutputStream(nightFile).use { outputStream ->
                        nightBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                        outputStream.flush()
                    }
                }
                
                // Copy the appropriate version based on current mode
                val currentFile = if (isNightMode) File(filesDir, "theme_header_pic_night.jpg") else File(filesDir, "theme_header_pic_day.jpg")
                val outputFile = File(filesDir, "theme_header_pic.jpg")
                currentFile.copyTo(outputFile, overwrite = true)
                
                // Save the file paths in preferences
                prefs.edit()
                    .putString("theme_header_pic_uri", outputFile.absolutePath)
                    .putString("theme_header_pic_day_uri", File(filesDir, "theme_header_pic_day.jpg").absolutePath)
                    .putString("theme_header_pic_night_uri", File(filesDir, "theme_header_pic_night.jpg").absolutePath)
                    .putInt("selected_theme_index", effectiveThemeIndex)
                    .putBoolean("theme_just_applied", true) // Add flag to indicate theme was just applied
                    .apply()

                Log.d("THEME_APPLY", "Theme saved: ${outputFile.absolutePath}")
                Log.d("THEME_APPLY", "File exists: ${outputFile.exists()}")
                Log.d("THEME_APPLY", "File size: ${outputFile.length()} bytes")


                
                finish()
            }
        } catch (e: Exception) {
            Log.e("ThemePicture", "Failed to apply theme picture", e)
        }
    }

    private fun removeThemePicture() {
        // Remove the theme picture files
        val themePicFile = File(filesDir, "theme_header_pic.jpg")
        if (themePicFile.exists()) {
            themePicFile.delete()
        }
        
        val themePicDayFile = File(filesDir, "theme_header_pic_day.jpg")
        if (themePicDayFile.exists()) {
            themePicDayFile.delete()
        }
        
        val themePicNightFile = File(filesDir, "theme_header_pic_night.jpg")
        if (themePicNightFile.exists()) {
            themePicNightFile.delete()
        }

        // Remove custom image files
        val customDayFile = File(filesDir, "custom_day_image.jpg")
        if (customDayFile.exists()) {
            customDayFile.delete()
        }
        
        val customNightFile = File(filesDir, "custom_night_image.jpg")
        if (customNightFile.exists()) {
            customNightFile.delete()
        }

        // Clear cache files
        clearThemePictureCache()

        // Clear all theme-related preferences
        prefs.edit()
            .remove("theme_header_pic_uri")
            .remove("theme_header_pic_day_uri")
            .remove("theme_header_pic_night_uri")
            .remove("selected_theme_index")
            .remove("custom_day_image_uri")
            .remove("custom_night_image_uri")
            .apply()


        removeButton.visibility = View.GONE
        finish()
    }

    private fun clearThemePictureCache() {
        // Clear cache files used for cropping and preview
        val cacheFiles = listOf(
            "custom_day_cropped.jpg",
            "custom_night_cropped.jpg", 
            "custom_day_recropped.jpg",
            "custom_night_recropped.jpg"
        )
        
        for (fileName in cacheFiles) {
            val cacheFile = File(cacheDir, fileName)
            if (cacheFile.exists()) {
                cacheFile.delete()
                Log.d("CacheCleanup", "Deleted cache file: $fileName")
            }
        }
        
        // Clear Glide cache for theme pictures
        try {
            Glide.get(this).clearMemory()
            Thread {
                Glide.get(this).clearDiskCache()
            }.start()
            Log.d("CacheCleanup", "Cleared Glide cache")
        } catch (e: Exception) {
            Log.e("CacheCleanup", "Failed to clear Glide cache", e)
        }
    }

    private fun showCurrentThemePicture() {
        // Check if we have custom images
        val dayImageUri = prefs.getString("custom_day_image_uri", null)
        val nightImageUri = prefs.getString("custom_night_image_uri", null)
        
        // Check if we have a selected theme
        val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
        
        Log.d("ThemePreview", "selectedThemeIndex: $selectedThemeIndex")
        Log.d("ThemePreview", "dayImageUri: $dayImageUri")
        Log.d("ThemePreview", "nightImageUri: $nightImageUri")
        
        // Only show images if we have a selected theme or custom images
        if (selectedThemeIndex >= 0 || (dayImageUri != null && File(dayImageUri).exists())) {
            Log.d("ThemePreview", "Showing images")
            if (dayImageUri != null && File(dayImageUri).exists()) {
                Glide.with(this)
                    .load(File(dayImageUri))
                    .centerCrop()
                    .into(dayPreviewImageView)
            } else if (selectedThemeIndex >= 0 && selectedThemeIndex < allThemes.size) {
                // Show predefined theme if one is selected
                val themePicture = allThemes[selectedThemeIndex]
                if (!themePicture.isCustom) {
                    Glide.with(this)
                        .load(themePicture.dayResourceId)
                        .centerCrop()
                        .into(dayPreviewImageView)
                }
            }
            
            if (nightImageUri != null && File(nightImageUri).exists()) {
                Glide.with(this)
                    .load(File(nightImageUri))
                    .centerCrop()
                    .into(nightPreviewImageView)
            } else if (selectedThemeIndex >= 0 && selectedThemeIndex < allThemes.size) {
                // Show predefined theme if one is selected
                val themePicture = allThemes[selectedThemeIndex]
                if (!themePicture.isCustom) {
                    Glide.with(this)
                        .load(themePicture.nightResourceId)
                        .centerCrop()
                        .into(nightPreviewImageView)
                }
            } else if (dayImageUri != null && File(dayImageUri).exists()) {
                // If no night image exists, show day image as placeholder
                Glide.with(this)
                    .load(File(dayImageUri))
                    .centerCrop()
                    .into(nightPreviewImageView)
            }
        } else {
            // Clear both previews if no theme is selected and no custom images
            Log.d("ThemePreview", "Clearing previews")
            dayPreviewImageView.setImageDrawable(null)
            nightPreviewImageView.setImageDrawable(null)
        }
    }

    private fun setupImageLaunchers() {
        pickDayImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val destFile = File(cacheDir, "custom_day_cropped.jpg")
                val destUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    destFile
                )
                
                // Launch UCrop for day image
                UCrop.of(it, destUri)
                    .withAspectRatio(16f, 9f)
                    .withMaxResultSize(1920, 1080)
                    .start(this)
            }
        }
        
        pickNightImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val destFile = File(cacheDir, "custom_night_cropped.jpg")
                val destUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    destFile
                )
                
                // Launch UCrop for night image
                UCrop.of(it, destUri)
                    .withAspectRatio(16f, 9f)
                    .withMaxResultSize(1920, 1080)
                    .start(this)
            }
        }
        
        pickCustomImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val destFile = File(cacheDir, "custom_theme_cropped.jpg")
                val destUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    destFile
                )
                
                // Launch UCrop for custom image
                UCrop.of(it, destUri)
                    .withAspectRatio(16f, 9f)
                    .withMaxResultSize(1920, 1080)
                    .start(this)
            }
        }
        
        // UCrop results will be handled in onActivityResult
    }
    

    

    
    private fun saveCustomDayImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "custom_day_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            
            prefs.edit().putString("custom_day_image_uri", file.absolutePath).apply()
            Log.d("ApplyButton", "Day image saved to: ${file.absolutePath}")
            updateCustomPreviews()
            
            // Add a small delay to ensure file operations complete
            dayPreviewImageView.post {
                checkApplyButtonState()
            }
            

        } catch (e: Exception) {
            Log.e("CustomImage", "Failed to save day image", e)
        }
    }
    
    private fun saveCustomNightImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "custom_night_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            
            prefs.edit().putString("custom_night_image_uri", file.absolutePath).apply()
            Log.d("ApplyButton", "Night image saved to: ${file.absolutePath}")
            updateCustomPreviews()
            
            // Add a small delay to ensure file operations complete
            nightPreviewImageView.post {
                checkApplyButtonState()
            }
            

        } catch (e: Exception) {
            Log.e("CustomImage", "Failed to save night image", e)
        }
    }
    
    private fun saveCustomThemeImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "custom_theme_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            
            prefs.edit().putString("custom_theme_image_uri", file.absolutePath).apply()
            Log.d("ApplyButton", "Custom theme image saved to: ${file.absolutePath}")
            
            // Force update the preview and button state
            runOnUiThread {
                // Clear any existing previews first
                dayPreviewImageView.setImageDrawable(null)
                nightPreviewImageView.setImageDrawable(null)
                
                // Update previews
                updateCustomThemePreview()
                checkApplyButtonState()
                
                // Ensure custom theme is selected
                if (selectedThemeIndex < 0) {
                    selectedThemeIndex = allThemes.indexOfFirst { it.isCustom }
                }
                
                Log.d("ApplyButton", "Custom theme image saved and preview updated")
            }
            

        } catch (e: Exception) {
            Log.e("CustomImage", "Failed to save custom theme image", e)
        }
    }
    
    private fun setupPreviewClickListeners() {
        dayPreviewImageView.setOnClickListener {
            val themePicture = if (selectedThemeIndex >= 0) allThemes[selectedThemeIndex] else null
            if (themePicture != null && themePicture.isCustom) {
                val dayImageUri = prefs.getString("custom_day_image_uri", null)
                if (dayImageUri != null && File(dayImageUri).exists()) {
                    // Re-crop the existing day image
                    val sourceUri = Uri.fromFile(File(dayImageUri))
                    val destFile = File(cacheDir, "custom_day_recropped.jpg")
                    val destUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        destFile
                    )
                    UCrop.of(sourceUri, destUri)
                        .withAspectRatio(16f, 9f)
                        .withMaxResultSize(1200, 675)
                        .start(this)
                } else {
                    // No existing day image, pick a new one
                    pickDayImageLauncher.launch("image/*")
                }
            }
            // else: do nothing for non-custom themes
        }
        
        nightPreviewImageView.setOnClickListener {
            val themePicture = if (selectedThemeIndex >= 0) allThemes[selectedThemeIndex] else null
            if (themePicture != null && themePicture.isCustom) {
                val nightImageUri = prefs.getString("custom_night_image_uri", null)
                if (nightImageUri != null && File(nightImageUri).exists()) {
                    // Re-crop the existing night image
                    val sourceUri = Uri.fromFile(File(nightImageUri))
                    val destFile = File(cacheDir, "custom_night_recropped.jpg")
                    val destUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        destFile
                    )
                    UCrop.of(sourceUri, destUri)
                        .withAspectRatio(16f, 9f)
                        .withMaxResultSize(1200, 675)
                        .start(this)
                } else {
                    // No existing night image, pick a new one
                    pickNightImageLauncher.launch("image/*")
                }
            }
            // else: do nothing for non-custom themes
        }
    }
    
    private fun checkApplyButtonState() {
        // Enable apply button if:
        // 1. A theme is selected from the grid, OR
        // 2. Custom theme image is available
        val hasSelectedTheme = selectedThemeIndex >= 0
        val hasCustomTheme = hasCustomThemeImage()
        
        val shouldEnable = hasSelectedTheme || hasCustomTheme
        applyButton.isEnabled = shouldEnable
        
        // Debug logging
        Log.d("ApplyButton", "hasSelectedTheme: $hasSelectedTheme, hasCustomTheme: $hasCustomTheme, shouldEnable: $shouldEnable")
    }
    
    private fun getCropOptions(): UCrop.Options {
        val options = UCrop.Options()
        
        // Set up basic crop options
        options.setHideBottomControls(false)
        options.setFreeStyleCropEnabled(true)
        options.setShowCropGrid(true)
        options.setShowCropFrame(true)
        
        // Set custom colors
        options.setStatusBarColor(resources.getColor(R.color.black, theme))
        options.setToolbarColor(resources.getColor(R.color.black, theme))
        options.setToolbarTitle("Edit Theme Picture")
        
        return options
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    private fun hasCustomDayAndNightImages(): Boolean {
        val dayImageUri = prefs.getString("custom_day_image_uri", null)
        val nightImageUri = prefs.getString("custom_night_image_uri", null)
        
        val dayExists = dayImageUri != null && File(dayImageUri).exists()
        val nightExists = nightImageUri != null && File(nightImageUri).exists()
        
        Log.d("ApplyButton", "Day image: $dayImageUri, exists: $dayExists")
        Log.d("ApplyButton", "Night image: $nightImageUri, exists: $nightExists")
        
        return dayExists && nightExists
    }
    
    private fun hasCustomThemeImage(): Boolean {
        val customThemeUri = prefs.getString("custom_theme_image_uri", null)
        val exists = customThemeUri != null && File(customThemeUri).exists()
        
        Log.d("ApplyButton", "Custom theme image: $customThemeUri, exists: $exists")
        
        return exists
    }
    
    private fun updateCustomPreviews() {
        val dayImageUri = prefs.getString("custom_day_image_uri", null)
        val nightImageUri = prefs.getString("custom_night_image_uri", null)
        
        if (dayImageUri != null && File(dayImageUri).exists()) {
            Glide.with(this)
                .load(File(dayImageUri))
                .centerCrop()
                .into(dayPreviewImageView)
        }
        
        if (nightImageUri != null && File(nightImageUri).exists()) {
            Glide.with(this)
                .load(File(nightImageUri))
                .centerCrop()
                .into(nightPreviewImageView)
        }
        
        // Update apply button state after updating previews
        checkApplyButtonState()
    }
    
    private fun updateCustomThemePreview() {
        val customThemeUri = prefs.getString("custom_theme_image_uri", null)
        
        Log.d("Preview", "Updating custom theme preview, URI: $customThemeUri")
        
        if (customThemeUri != null && File(customThemeUri).exists()) {
            // Clear existing images first
            dayPreviewImageView.setImageDrawable(null)
            nightPreviewImageView.setImageDrawable(null)
            
            // Show the same custom theme image in both day and night previews
            Glide.with(this)
                .load(File(customThemeUri))
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(dayPreviewImageView)
            
            Glide.with(this)
                .load(File(customThemeUri))
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(nightPreviewImageView)
            
            // Show both previews for custom themes
            nightPreviewImageView.visibility = View.VISIBLE
            
            Log.d("Preview", "Custom theme preview updated successfully")
        } else {
            Log.d("Preview", "Custom theme URI is null or file doesn't exist")
        }
        
        // Update apply button state after updating previews
        checkApplyButtonState()
    }

    private fun clearPreviews() {
        Log.d("ThemePreview", "Force clearing previews")
        dayPreviewImageView.setImageDrawable(null)
        nightPreviewImageView.setImageDrawable(null)
    }

    private fun getEncryptedPrefs(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            "diary_auth_prefs",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK) {
                val resultUri = UCrop.getOutput(data!!)
                if (resultUri != null) {
                    // Determine if this was day, night, or custom image based on the URI
                    val uriString = resultUri.toString()
                    if (uriString.contains("custom_day_cropped")) {
                        saveCustomDayImage(resultUri)
                    } else if (uriString.contains("custom_night_cropped")) {
                        saveCustomNightImage(resultUri)
                    } else if (uriString.contains("custom_theme_cropped")) {
                        saveCustomThemeImage(resultUri)
                    }
                }
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the crop operation, clear temporary cache
                clearTemporaryCache()
                Log.d("CropCancel", "User cancelled crop operation, cleared temporary cache")
            } else if (resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(data!!)

                // Clear cache files on crop error
                clearTemporaryCache()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear temporary cache files when activity is destroyed
        clearTemporaryCache()
    }

    private fun clearTemporaryCache() {
        // Only clear temporary crop files, not the saved custom images
        val tempCacheFiles = listOf(
            "custom_day_cropped.jpg",
            "custom_night_cropped.jpg", 
            "custom_day_recropped.jpg",
            "custom_night_recropped.jpg"
        )
        
        for (fileName in tempCacheFiles) {
            val cacheFile = File(cacheDir, fileName)
            if (cacheFile.exists()) {
                cacheFile.delete()
                Log.d("TempCacheCleanup", "Deleted temporary cache file: $fileName")
            }
        }
    }

    companion object {
        /**
         * Switch theme picture based on current day/night mode
         * This function should be called when the user changes theme mode
         */
        fun switchThemePicture(context: Context) {
            try {
                val prefs = context.getSharedPreferences("diary_auth_prefs", Context.MODE_PRIVATE)
                val isNightMode = ThemeManager.isNightMode(context)
                
                // Check if we have a theme picture set
                val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
                if (selectedThemeIndex < 0) return
                
                // Check if we have day and night versions saved
                val dayUri = prefs.getString("theme_header_pic_day_uri", null)
                val nightUri = prefs.getString("theme_header_pic_night_uri", null)
                
                if (dayUri != null && nightUri != null) {
                    val sourceFile = if (isNightMode) File(nightUri) else File(dayUri)
                    val outputFile = File(context.filesDir, "theme_header_pic.jpg")
                    
                    if (sourceFile.exists()) {
                        sourceFile.copyTo(outputFile, overwrite = true)
                        prefs.edit().putString("theme_header_pic_uri", outputFile.absolutePath).apply()
                        Log.d("ThemeSwitch", "Switched theme picture to ${if (isNightMode) "night" else "day"} mode")
                    }
                }
            } catch (e: Exception) {
                Log.e("ThemeSwitch", "Failed to switch theme picture", e)
            }
        }
    }
}

class SectionedThemeAdapter(
    private val dayNightThemes: List<ThemePictureSelectionActivity.ThemePicture>,
    private val standaloneThemes: List<ThemePictureSelectionActivity.ThemePicture>,
    private val customThemes: List<ThemePictureSelectionActivity.ThemePicture>,
    private val onItemClick: (Int) -> Unit,
    private val onCustomThemeClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_SECTION_HEADER = 0
        const val TYPE_THEME_SECTION = 1
    }

    private var selectedPosition: Int = -1

    // Combined list with section headers and theme sections
    private val items = mutableListOf<Any>().apply {
        add("Day/Night Themes")
        add(dayNightThemes)
        add("Standalone Themes")
        add(standaloneThemes)
        add("Custom Themes")
        add(customThemes)
    }

    init {
        // Debug logging
        Log.d("ThemeAdapter", "Day/Night Themes: ${dayNightThemes.map { it.name }}")
        Log.d("ThemeAdapter", "Standalone Themes: ${standaloneThemes.map { it.name }}")
        Log.d("ThemeAdapter", "Custom Themes: ${customThemes.map { it.name }}")
    }

    class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.sectionTitleTextView)
    }

    class ThemeSectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val themeRecyclerView: RecyclerView = view.findViewById(R.id.themeSectionRecyclerView)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is String -> TYPE_SECTION_HEADER
            is List<*> -> TYPE_THEME_SECTION
            else -> TYPE_THEME_SECTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_section_header, parent, false)
                SectionHeaderViewHolder(view)
            }
            TYPE_THEME_SECTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_theme_section, parent, false)
                ThemeSectionViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SectionHeaderViewHolder -> {
                val title = items[position] as String
                holder.titleTextView.text = title
            }
            is ThemeSectionViewHolder -> {
                val themes = items[position] as List<ThemePictureSelectionActivity.ThemePicture>
                setupThemeSection(holder.themeRecyclerView, themes)
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    private fun setupThemeSection(recyclerView: RecyclerView, themes: List<ThemePictureSelectionActivity.ThemePicture>) {
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context, LinearLayoutManager.HORIZONTAL, false)
        
        // Disable parent scroll when child is scrolling
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                return false
            }
            
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
        
        val themeCardAdapter = ThemeCardAdapter(
            onItemClick = { themePicture ->
                // Handle direct theme selection
                val globalIndex = getGlobalThemeIndex(themePicture)
                // We can't access the outer class from here, so we'll use a callback
                onItemClick(globalIndex)
            },
            onCustomThemeClick = onCustomThemeClick
        )
        recyclerView.adapter = themeCardAdapter
        themeCardAdapter.submitList(themes)
        
        // Update selection state if needed
        themeCardAdapter.setSelectedPosition(selectedPosition)
    }

    private fun getGlobalThemeIndex(themePicture: ThemePictureSelectionActivity.ThemePicture): Int {
        // Calculate global index based on which section this theme belongs to
        val allThemes = dayNightThemes + standaloneThemes + customThemes
        return allThemes.indexOf(themePicture)
    }

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }
}

class ThemeCardAdapter(
    private val onItemClick: (ThemePictureSelectionActivity.ThemePicture) -> Unit,
    private val onCustomThemeClick: () -> Unit
) : ListAdapter<ThemePictureSelectionActivity.ThemePicture, ThemeCardAdapter.ThemeViewHolder>(DiffCallback()) {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val binding = ItemThemePictureBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThemeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    fun setSelectedPosition(position: Int) {
        val previousSelected = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousSelected)
        notifyItemChanged(selectedPosition)
    }

    inner class ThemeViewHolder(private val binding: ItemThemePictureBinding) : 
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val themePicture = getItem(position)
                    if (themePicture.isCustom) {
                        onCustomThemeClick()
                    } else {
                        onItemClick(themePicture)
                    }
                }
            }
        }

        fun bind(themePicture: ThemePictureSelectionActivity.ThemePicture, isSelected: Boolean) {
            // Check if this is a standalone theme (STATIC mode)
            if (themePicture.mode == ThemePictureSelectionActivity.ThemeMode.STATIC) {
                // For standalone themes, show only one image taking full width
                binding.nightThemeImageView.visibility = View.GONE
                
                // Load the single image
                Glide.with(binding.root.context)
                    .load(themePicture.dayResourceId)
                    .into(binding.dayThemeImageView)
            } else {
                // For day/night themes, show both images side by side
                binding.nightThemeImageView.visibility = View.VISIBLE
                
                // Load day image
                Glide.with(binding.root.context)
                    .load(themePicture.dayResourceId)
                    .into(binding.dayThemeImageView)

                // Load night image
                Glide.with(binding.root.context)
                    .load(themePicture.nightResourceId)
                    .into(binding.nightThemeImageView)
            }

            // Set the theme name
            binding.themeNameTextView.text = themePicture.name
            
            // Update selection state
            if (isSelected) {
                binding.themeCardView.strokeWidth = 4
                binding.themeCardView.strokeColor = ContextCompat.getColor(binding.root.context, R.color.greyback)
                binding.themeCardView.cardElevation = 8f
            } else {
                binding.themeCardView.strokeWidth = 2
                binding.themeCardView.strokeColor = ContextCompat.getColor(binding.root.context, R.color.greyback)
                binding.themeCardView.cardElevation = 4f
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ThemePictureSelectionActivity.ThemePicture>() {
        override fun areItemsTheSame(oldItem: ThemePictureSelectionActivity.ThemePicture, newItem: ThemePictureSelectionActivity.ThemePicture): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: ThemePictureSelectionActivity.ThemePicture, newItem: ThemePictureSelectionActivity.ThemePicture): Boolean {
            return oldItem == newItem
        }
    }
} 