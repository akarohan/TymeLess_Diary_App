package com.example.diaryapp

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import de.hdodenhof.circleimageview.CircleImageView

class SignupActivity : AppCompatActivity() {
    private lateinit var nameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var signupButton: TextView
    private lateinit var backButton: ImageButton
    private lateinit var profileImageView: CircleImageView
    
    private var selectedImageUri: Uri? = null
    private var tempImageFile: File? = null
    private lateinit var themeUpdateReceiver: BroadcastReceiver

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to take a photo", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            loadImageIntoView(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageFile?.let { file ->
                selectedImageUri = Uri.fromFile(file)
                loadImageIntoView(selectedImageUri!!)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Initialize views
        nameInput = findViewById(R.id.nameInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        signupButton = findViewById(R.id.signupButton)
        backButton = findViewById(R.id.backButton)
        profileImageView = findViewById(R.id.profileImageView)

        // Setup back button
        backButton.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Setup profile image click
        profileImageView.setOnClickListener {
            showImageSelectionDialog()
        }
        
        // Update button styling based on current theme
        updateButtonStyling()
        
        // Setup theme update receiver
        setupThemeUpdateReceiver()

        signupButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()
            
            if (name.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val prefs = getEncryptedPrefs()
            prefs.edit().putString("name", name)
                .putString("username", username)
                .putString("password_hash", hash(password))
                .putString("profile_picture_uri", selectedImageUri?.toString())
                .apply()
            Toast.makeText(this, "Registration successful! Please log in.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupThemeUpdateReceiver() {
        val filter = IntentFilter(ThemeManager.THEME_UPDATE_ACTION)
        themeUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ThemeManager.THEME_UPDATE_ACTION) {
                    updateButtonStyling()
                }
            }
        }
        registerReceiver(themeUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(themeUpdateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Select Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> openGallery()
                    2 -> { /* Cancel - do nothing */ }
                }
            }
            .show()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission")
                    .setMessage("Camera permission is needed to take a profile picture")
                    .setPositiveButton("Grant") { _, _ ->
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        try {
            tempImageFile = File(cacheDir, "temp_profile_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempImageFile!!
            )
            cameraLauncher.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        // Check for media permissions on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 100)
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
                return
            }
        }
        galleryLauncher.launch("image/*")
    }

    private fun loadImageIntoView(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Resize bitmap to reasonable size for profile picture
            val resizedBitmap = resizeBitmap(bitmap, 300, 300)
            profileImageView.setImageBitmap(resizedBitmap)
            
            Toast.makeText(this, "Profile picture selected!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()
        
        var finalWidth = maxWidth
        var finalHeight = maxHeight
        
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            100 -> { // READ_MEDIA_IMAGES
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    galleryLauncher.launch("image/*")
                } else {
                    Toast.makeText(this, "Gallery permission is required to select a photo", Toast.LENGTH_SHORT).show()
                }
            }
            101 -> { // READ_EXTERNAL_STORAGE
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    galleryLauncher.launch("image/*")
                } else {
                    Toast.makeText(this, "Gallery permission is required to select a photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateButtonStyling() {
        // Check if night mode is enabled using ThemeManager
        val isNightMode = ThemeManager.isNightMode(this)
        
        if (isNightMode) {
            // Night mode - use white background with black text
            signupButton.setBackgroundResource(R.drawable.white_button_background)
            signupButton.setTextColor(ContextCompat.getColor(this, R.color.black))
        } else {
            // Day mode - use black background with white text
            signupButton.setBackgroundResource(R.drawable.black_button_background)
            signupButton.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, AuthActivity::class.java)
        startActivity(intent)
        finish()
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

    private fun checkForAccountBackupAndPrompt(username: String) {
        val backupDir = filesDir.resolve("backups")
        if (backupDir.exists()) {
            val backupFiles = backupDir.listFiles { file -> file.name.endsWith(".zip") }
            if (!backupFiles.isNullOrEmpty()) {
                for (zipFile in backupFiles) {
                    // Look for notes.json inside the zip
                    val tempDir = cacheDir.resolve("backup_check")
                    tempDir.mkdirs()
                    try {
                        java.util.zip.ZipFile(zipFile).use { zip ->
                            val entry = zip.getEntry("notes.json")
                            if (entry != null) {
                                val input = zip.getInputStream(entry)
                                val notesJson = input.bufferedReader().use { it.readText() }
                                val obj = org.json.JSONObject(notesJson)
                                val backupUserKey = obj.optString("user_key", null)
                                val backupUsername = obj.optString("username", null)
                                val userKey = getEncryptedPrefs().getString("user_key", null)
                                if ((backupUsername == username) || (userKey != null && backupUserKey == userKey)) {
                                    // Always route to restore page
                                    val intent = Intent(this, RestoreDataActivity::class.java)
                                    intent.putExtra("backup_zip_path", zipFile.absolutePath)
                                    startActivity(intent)
                                    finish()
                                    return
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        Toast.makeText(this, "Registration successful! Please log in.", Toast.LENGTH_SHORT).show()
        goToLogin()
    }
} 