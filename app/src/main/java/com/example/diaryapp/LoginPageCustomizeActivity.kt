package com.example.diaryapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import androidx.core.content.ContextCompat
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class LoginPageCustomizeActivity : AppCompatActivity() {
    private lateinit var pickMediaButton: Button
    private lateinit var saveButton: Button
    private lateinit var removeButton: Button
    private lateinit var previewImage: ImageView
    private lateinit var previewVideo: VideoView
    private lateinit var currentBackgroundContainer: View
    private var selectedUri: Uri? = null
    private var isImage: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_page_customize)

        // Set up modern header
        val backButton = findViewById<android.widget.ImageButton>(R.id.backButton)
        val toolbarTitle = findViewById<android.widget.TextView>(R.id.toolbarTitle)
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set title
        toolbarTitle.text = "Customise Login Page"

        pickMediaButton = findViewById(R.id.pickMediaButton)
        saveButton = findViewById(R.id.saveButton)
        removeButton = findViewById(R.id.removeButton)
        previewImage = findViewById(R.id.previewImage)
        previewVideo = findViewById(R.id.previewVideo)
        currentBackgroundContainer = findViewById(R.id.currentBackgroundContainer)

        // Show current login background if exists
        showCurrentLoginBackground()

        pickMediaButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/* video/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            startActivityForResult(Intent.createChooser(intent, "Select Media"), 1001)
        }

        saveButton.setOnClickListener {
            if (selectedUri != null) {
                val prefs = getEncryptedPrefs()
                prefs.edit().putString("login_bg_uri", selectedUri.toString())
                    .putBoolean("login_bg_is_image", isImage)
                    .apply()
                Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Please select a media file first.", Toast.LENGTH_SHORT).show()
            }
        }

        removeButton.setOnClickListener {
            val prefs = getEncryptedPrefs()
            prefs.edit()
                .remove("login_bg_uri")
                .remove("login_bg_is_image")
                .apply()
            
            // Clear current background display
            currentBackgroundContainer.visibility = View.GONE
            previewImage.visibility = View.GONE
            previewVideo.visibility = View.GONE
            
            // Show default purple background message
            val defaultBackgroundMessage = findViewById<android.widget.TextView>(R.id.defaultBackgroundMessage)
            defaultBackgroundMessage.visibility = View.VISIBLE
            
            // Disable remove button since no background exists
            removeButton.isEnabled = false
            
            Toast.makeText(this, "Login background removed! Default purple background will be used.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isNight = themePrefs.getBoolean("is_night_mode", false)
        val saveButton = findViewById<Button?>(R.id.saveButton)
        val removeButton = findViewById<Button?>(R.id.removeButton)
        
        if (isNight) {
            saveButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            saveButton?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            removeButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            removeButton?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        } else {
            saveButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
            saveButton?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            removeButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
            removeButton?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 1001) {
            val uri = data?.data ?: return
            val type = contentResolver.getType(uri) ?: ""
            if (type.startsWith("image")) {
                // Launch cropper with aspect ratio matching the device's screen
                val destUri = Uri.fromFile(File(cacheDir, "cropped_login_bg.jpg"))
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                UCrop.of(uri, destUri)
                    .withAspectRatio(screenWidth.toFloat(), screenHeight.toFloat())
                    .withMaxResultSize(screenWidth, screenHeight)
                    .start(this)
            } else if (type.startsWith("video")) {
                showVideo(uri)
            }
        } else if (resultCode == Activity.RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                showImage(resultUri)
            }
        }
    }

    private fun showImage(uri: Uri) {
        // Update the current background display with the new image
        currentBackgroundContainer.visibility = View.VISIBLE
        
        // Show the new image in the current background preview
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(previewImage)
        previewImage.visibility = View.VISIBLE
        previewVideo.visibility = View.GONE
        
        // Hide default background message
        val defaultBackgroundMessage = findViewById<android.widget.TextView>(R.id.defaultBackgroundMessage)
        defaultBackgroundMessage.visibility = View.GONE
        
        // Enable remove button since we now have a background
        removeButton.isEnabled = true
        
        selectedUri = uri
        isImage = true
    }

    private fun showVideo(uri: Uri) {
        // Update the current background display with the new video
        currentBackgroundContainer.visibility = View.VISIBLE
        
        // Copy video to internal storage for reliable playback
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        val outFile = File(filesDir, "login_bg_video.mp4")
        val outputStream = FileOutputStream(outFile)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        val fileUri = Uri.fromFile(outFile)
        
        // Show the new video in the current background preview
        previewVideo.setVideoURI(fileUri)
        previewVideo.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
        }
        previewVideo.start()
        previewVideo.visibility = View.VISIBLE
        previewImage.visibility = View.GONE
        
        // Hide default background message
        val defaultBackgroundMessage = findViewById<android.widget.TextView>(R.id.defaultBackgroundMessage)
        defaultBackgroundMessage.visibility = View.GONE
        
        // Enable remove button since we now have a background
        removeButton.isEnabled = true
        
        selectedUri = fileUri
        isImage = false
    }



    private fun showCurrentLoginBackground() {
        val prefs = getEncryptedPrefs()
        val currentBgUri = prefs.getString("login_bg_uri", null)
        val currentBgIsImage = prefs.getBoolean("login_bg_is_image", false)
        
        android.util.Log.d("LoginBackground", "Current BG URI: $currentBgUri")
        android.util.Log.d("LoginBackground", "Is Image: $currentBgIsImage")
        
        if (currentBgUri != null) {
            // Check if it's a content URI or file path
            val isContentUri = currentBgUri.startsWith("content://")
            android.util.Log.d("LoginBackground", "Is content URI: $isContentUri")
            
            if (isContentUri) {
                // Handle content URI directly
                android.util.Log.d("LoginBackground", "Handling content URI")
                showBackgroundFromUri(Uri.parse(currentBgUri), currentBgIsImage)
            } else {
                // Handle file path - check if it's a file:// URI
                val isFileUri = currentBgUri.startsWith("file://")
                android.util.Log.d("LoginBackground", "Is file URI: $isFileUri")
                
                if (isFileUri) {
                    // Handle file:// URI
                    val filePath = currentBgUri.substring(7) // Remove "file://" prefix
                    val file = File(filePath)
                    android.util.Log.d("LoginBackground", "File exists: ${file.exists()}")
                    android.util.Log.d("LoginBackground", "File path: ${file.absolutePath}")
                    
                    if (file.exists()) {
                        showBackgroundFromUri(Uri.parse(currentBgUri), currentBgIsImage)
                    } else {
                        // No current background, show default message
                        currentBackgroundContainer.visibility = View.GONE
                        previewImage.visibility = View.GONE
                        previewVideo.visibility = View.GONE
                        
                        val defaultBackgroundMessage = findViewById<android.widget.TextView>(R.id.defaultBackgroundMessage)
                        defaultBackgroundMessage.visibility = View.VISIBLE
                        
                        // Disable remove button when no background exists
                        removeButton.isEnabled = false
                        android.util.Log.d("LoginBackground", "No background found, showing default message")
                    }
                } else {
                    // Handle regular file path
                    val file = File(currentBgUri)
                    android.util.Log.d("LoginBackground", "File exists: ${file.exists()}")
                    android.util.Log.d("LoginBackground", "File path: ${file.absolutePath}")
                    
                    if (file.exists()) {
                        showBackgroundFromUri(Uri.parse(currentBgUri), currentBgIsImage)
                    } else {
                        // No current background, show default message
                        currentBackgroundContainer.visibility = View.GONE
                        previewImage.visibility = View.GONE
                        previewVideo.visibility = View.GONE
                        
                        val defaultBackgroundMessage = findViewById<android.widget.TextView>(R.id.defaultBackgroundMessage)
                        defaultBackgroundMessage.visibility = View.VISIBLE
                        
                        // Disable remove button when no background exists
                        removeButton.isEnabled = false
                        android.util.Log.d("LoginBackground", "No background found, showing default message")
                    }
                }
            }
        } else {
            // No current background, show default message
            currentBackgroundContainer.visibility = View.GONE
            previewImage.visibility = View.GONE
            previewVideo.visibility = View.GONE
            
            val defaultBackgroundMessage = findViewById<android.widget.TextView>(R.id.defaultBackgroundMessage)
            defaultBackgroundMessage.visibility = View.VISIBLE
            
            // Disable remove button when no background exists
            removeButton.isEnabled = false
            android.util.Log.d("LoginBackground", "No background found, showing default message")
        }
    }

    private fun showBackgroundFromUri(uri: Uri, isImage: Boolean) {
        // Show current background
        currentBackgroundContainer.visibility = View.VISIBLE
        
        if (isImage) {
            // Show current image
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(previewImage)
            previewImage.visibility = View.VISIBLE
            previewVideo.visibility = View.GONE
        } else {
            // Show current video
            previewVideo.setVideoURI(uri)
            previewVideo.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
            }
            previewVideo.start()
            previewVideo.visibility = View.VISIBLE
            previewImage.visibility = View.GONE
        }
        
        // Hide default background message
        val defaultBackgroundMessage = findViewById<android.widget.TextView>(R.id.defaultBackgroundMessage)
        defaultBackgroundMessage.visibility = View.GONE
        
        // Enable remove button
        removeButton.isEnabled = true
        android.util.Log.d("LoginBackground", "Background found and displayed successfully")
    }

    private fun getEncryptedPrefs() = EncryptedSharedPreferences.create(
        "diary_auth_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} 