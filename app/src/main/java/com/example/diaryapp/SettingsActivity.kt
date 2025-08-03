package com.example.diaryapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import android.net.Uri
import android.content.Intent
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import android.widget.TextView
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable

class SettingsActivity : AppCompatActivity() {
    private lateinit var usernameInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var saveButton: Button
    private lateinit var profileImageView: de.hdodenhof.circleimageview.CircleImageView
    private lateinit var pickProfileImageLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var takeProfileImageLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri>
    private var cameraImageUri: android.net.Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Set up modern header
        val backButton = findViewById<android.widget.ImageButton>(R.id.backButton)
        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set title
        toolbarTitle.text = "Account Settings"

        usernameInput = findViewById(R.id.usernameInput)
        nameInput = findViewById(R.id.nameInput)
        passwordInput = findViewById(R.id.passwordInput)
        saveButton = findViewById(R.id.saveButton)
        profileImageView = findViewById(R.id.profileImageView)
        
        // Set up image launchers
        setupImageLaunchers()
        
        // Set up profile image click listener
        setupProfileImageClick()
        
        // Load current profile image
        loadProfileImage()

        val prefs = getEncryptedPrefs()
        usernameInput.setText(prefs.getString("username", ""))
        nameInput.setText(prefs.getString("name", ""))
        // Log loaded values
        val loadedUsername = prefs.getString("username", "")
        val loadedName = prefs.getString("name", "")
        Log.d("SettingsLoad", "Loaded username: $loadedUsername, name: $loadedName")

        saveButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val name = nameInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (username.isEmpty()) {
                return@setOnClickListener
            }
            val editor = prefs.edit()
            editor.putString("username", username)
            editor.putString("name", name)
            if (password.isNotEmpty()) {
                editor.putString("password_hash", hash(password))
            }
            editor.apply()
            Log.d("SettingsSave", "Saved username: $username, name: $name")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Set button colors based on current theme
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isNight = themePrefs.getBoolean("is_night_mode", false)
        val saveButton = findViewById<Button>(R.id.saveButton)
        
        if (isNight) {
            // Night mode: white button with black text
            saveButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            saveButton.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        } else {
            // Day mode: black button with white text
            saveButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
            saveButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
        
        // Reload profile image in case it was updated elsewhere
        loadProfileImage()
    }

    private fun getEncryptedPrefs() = EncryptedSharedPreferences.create(
        "diary_auth_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == com.yalantis.ucrop.UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK) {
                data?.let { intent ->
                    val resultUri = com.yalantis.ucrop.UCrop.getOutput(intent)
                    resultUri?.let { handleImageSelection(it) }
                }
            } else if (resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR) {
                data?.let { intent ->
                    val cropError = com.yalantis.ucrop.UCrop.getError(intent)
                    Log.e("SettingsActivity", "Crop error: ${cropError?.message}")
                    Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setupImageLaunchers() {
        pickProfileImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { startCrop(it) }
        }
        
        takeProfileImageLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraImageUri?.let { startCrop(it) }
            }
        }
    }
    
    private fun setupProfileImageClick() {
        profileImageView.setOnClickListener {
            showImageSelectionDialog()
        }
    }
    
    private fun showImageSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCameraForProfileImage()
                    1 -> pickProfileImageLauncher.launch("image/*")
                }
            }
            .show()
    }
    
    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_profile_${System.currentTimeMillis()}.jpg"))
        
        com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f) // Square aspect ratio for profile picture
            .withMaxResultSize(512, 512) // Max size for profile picture
            .start(this)
    }
    
    private fun launchCameraForProfileImage() {
        val file = File(filesDir, "profile_image.jpg")
        cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        takeProfileImageLauncher.launch(cameraImageUri)
    }
    
    private fun handleImageSelection(uri: Uri) {
        try {
            // Save the image to internal storage
            val file = File(filesDir, "profile_pic.jpg")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // Save the file path in preferences
            val prefs = getEncryptedPrefs()
            prefs.edit().putString("profile_pic_uri", file.absolutePath).apply()
            
            // Load the image into the profile image view
            loadProfileImage()
            
            Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error saving profile image", e)
            Toast.makeText(this, "Failed to update profile picture", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadProfileImage() {
        val prefs = getEncryptedPrefs()
        val profilePicUri = prefs.getString("profile_pic_uri", null)
        
        if (profilePicUri != null && File(profilePicUri).exists()) {
            Glide.with(this)
                .load(Uri.fromFile(File(profilePicUri)))
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_user_placeholder)
                .error(R.drawable.ic_user_placeholder)
                .into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.ic_user_placeholder)
        }
    }
} 