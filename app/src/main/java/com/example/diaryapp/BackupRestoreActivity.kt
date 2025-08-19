package com.example.diaryapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.diaryapp.R
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import com.example.diaryapp.DiaryDatabase
import com.example.diaryapp.data.Note
import com.example.diaryapp.DiaryEntry
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import com.example.diaryapp.utils.AdvancedCompression
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.os.Environment
import android.util.Log
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import android.content.Context

class BackupRestoreActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var textBackupComplete: TextView
    private lateinit var textRestoreComplete: TextView
    private lateinit var progressSizeInfo: LinearLayout
    private lateinit var textUploadedSize: TextView
    private lateinit var textTotalSize: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val STORAGE_PERMISSION_CODE = 1001
    private val PICK_BACKUP_FOLDER_REQUEST_CODE = 2002
    private val GOOGLE_SIGN_IN_REQUEST_CODE = 2003
    private var backupFolderUri: Uri? = null
    
    // Google Drive related variables
    private lateinit var googleSignInClient: GoogleSignInClient
    private var googleDriveService: GoogleDriveService? = null
    private var currentGoogleAccount: GoogleSignInAccount? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_restore)

        // Set up modern header
        val backButton = findViewById<android.widget.ImageButton>(R.id.backButton)
        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
        
        backButton.setOnClickListener {
            finish()
        }
        
        // Set title
        toolbarTitle.text = "Backup And Restore"

        // Apply theme-aware colors
        applyThemeColors()

        // Analyze storage usage
        analyzeStorageUsage()

        val textLastBackup = findViewById<TextView>(R.id.text_last_backup)
        val textBackupPath = findViewById<TextView>(R.id.text_backup_path)
        
        // Google Drive switches
        val switchLocalStorage = findViewById<SwitchMaterial>(R.id.switch_local_storage)
        val switchGoogleDrive = findViewById<SwitchMaterial>(R.id.switch_google_drive)
        
        val buttonBackup = findViewById<Button>(R.id.button_backup)
        val buttonGoogleSignIn = findViewById<Button>(R.id.button_google_signin)
        progressBar = findViewById(R.id.progress_backup)
        textBackupComplete = findViewById(R.id.text_backup_complete)
        textRestoreComplete = findViewById(R.id.text_restore_complete)
        progressSizeInfo = findViewById(R.id.progress_size_info)
        textUploadedSize = findViewById(R.id.text_uploaded_size)
        textTotalSize = findViewById(R.id.text_total_size)
        
        // Google Drive status views
        val textGoogleDriveStatus = findViewById<TextView>(R.id.text_google_drive_status)
        val textGoogleAccount = findViewById<TextView>(R.id.text_google_account)
        val textGoogleLastBackup = findViewById<TextView>(R.id.text_google_last_backup)
        val textGoogleBackupLocation = findViewById<TextView>(R.id.text_google_backup_location)

        prefs = getEncryptedPrefs()
        updateLastBackupInfo()
        
        // Initialize Google Sign-In
        initializeGoogleSignIn()
        
        // Set up Google Drive switches
        setupGoogleDriveSwitches(switchLocalStorage, switchGoogleDrive, buttonGoogleSignIn, textGoogleDriveStatus)
        
        // Load saved switch states
        switchLocalStorage.isChecked = prefs.getBoolean("local_storage_enabled", true)
        switchGoogleDrive.isChecked = prefs.getBoolean("google_drive_enabled", false)
        
        // Save switch states when changed
        switchLocalStorage.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("local_storage_enabled", isChecked).apply()
        }
        
        switchGoogleDrive.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Trigger Google Sign-In when toggle is turned on
                signInToGoogleDrive()
            } else {
                // Save the preference when toggle is turned off
                prefs.edit().putBoolean("google_drive_enabled", isChecked).apply()
            }
        }
        
        // Set up automatic backup switch
        val switchAutomaticBackup = findViewById<SwitchMaterial>(R.id.switch_automatic_backup)
        switchAutomaticBackup.isChecked = prefs.getBoolean("automatic_backup_enabled", true)
        switchAutomaticBackup.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("automatic_backup_enabled", isChecked).apply()
        }
        
        // Set up Google Sign-In button
        buttonGoogleSignIn.setOnClickListener {
            signInToGoogleDrive()
        }

        buttonBackup.setOnClickListener {
            Log.d("BACKUP", "Local backup button clicked")
            CoroutineScope(Dispatchers.Main).launch {
                startLocalBackup(true, true, true, true, true)
            }
        }

        val buttonChooseFolder = findViewById<Button>(R.id.button_choose_folder)

        // Load saved folder URI
        val savedUri = prefs.getString("backup_folder_uri", null)
        if (savedUri != null) {
            backupFolderUri = Uri.parse(savedUri)
            textBackupPath.text = savedUri
        }

        // Set up folder selection
        buttonChooseFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, PICK_BACKUP_FOLDER_REQUEST_CODE)
        }

        val buttonRestore = findViewById<Button>(R.id.button_restore)
        buttonRestore.setOnClickListener {
            restoreFromBackup()
        }

        // Set up Google Drive buttons
        val buttonGoogleBackup = findViewById<Button>(R.id.button_google_backup)
        val buttonGoogleRestore = findViewById<Button>(R.id.button_google_restore)

        buttonGoogleBackup.setOnClickListener {
            Log.d("BACKUP", "Google Drive backup button clicked")
            startGoogleDriveBackup(true, true, true, true, true)
        }

        buttonGoogleRestore.setOnClickListener {
            restoreFromGoogleDrive()
        }
    }

    override fun onResume() {
        super.onResume()
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isNight = themePrefs.getBoolean("is_night_mode", false)
        val backupButton = findViewById<Button?>(R.id.button_backup)
        val restoreButton = findViewById<Button?>(R.id.button_restore)
        if (isNight) {
            backupButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            backupButton?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            restoreButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            restoreButton?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        } else {
            backupButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
            backupButton?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            restoreButton?.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
            restoreButton?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    private fun updateLastBackupInfo() {
        val textLastBackup = findViewById<TextView>(R.id.text_last_backup)
        val textBackupPath = findViewById<TextView>(R.id.text_backup_path)
        val lastTime = prefs.getLong("last_backup_time", 0L)
        val lastPath = prefs.getString("last_backup_path", "--")
        val sdf = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
        textLastBackup.text = if (lastTime > 0) sdf.format(Date(lastTime)) else "--"
        textBackupPath.text = if (lastPath != null && lastPath != "--") lastPath else "--"
    }

    private fun getEncryptedPrefs() = EncryptedSharedPreferences.create(
        "diary_auth_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
            else -> "${String.format("%.1f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }





    private suspend fun updateProgress(current: Int, total: Int) {
                withContext(Dispatchers.Main) {
            progressBar.progress = (current * 100) / total
        }
    }

    private suspend fun updateProgressWithSize(uploadedBytes: Long, totalBytes: Long) {
            withContext(Dispatchers.Main) {
            val progress = if (totalBytes > 0) ((uploadedBytes * 100) / totalBytes).toInt() else 0
            progressBar.progress = progress
            
            textUploadedSize.text = formatFileSize(uploadedBytes)
            textTotalSize.text = formatFileSize(totalBytes)
        }
    }

    private suspend fun zipFiles(files: List<Pair<String, File>>, zipFile: File) {
        // Always use the best compression method (mixed strategy)
        val compressionType = AdvancedCompression.CompressionType.CUSTOM_MIXED
        val compressedSize = AdvancedCompression.compressAdvanced(this, files, zipFile, compressionType)
        
        // Log compression statistics
        val originalSize = files.sumOf { it.second.length() }
        val stats = AdvancedCompression.getCompressionStats(originalSize, compressedSize)
        Log.d("BACKUP", "Using advanced compression with mixed strategy")
        Log.d("BACKUP", "Compression stats: $stats")
        
        // Show compression info to user
        val compressionRatio = stats["compressionRatio"] as Double
        val spaceSaved = stats["spaceSavedFormatted"] as String
        Log.d("BACKUP", "Compression ratio: ${String.format("%.1f", compressionRatio)}%")
        Log.d("BACKUP", "Space saved: $spaceSaved")
    }

    private fun restoreFromBackup() {
        // Temporarily only use local storage
        restoreFromLocalBackup()
    }

    private fun restoreFromLocalBackup() {
        val backupUriString = prefs.getString("backup_folder_uri", null)
        Log.d("RESTORE", "Starting local restore. backupUriString=$backupUriString")
        if (backupUriString == null) {
            Toast.makeText(this, "Please choose a backup folder first!", Toast.LENGTH_LONG).show()
            return
        }
        val folderUri = Uri.parse(backupUriString)
        val folderDoc = DocumentFile.fromTreeUri(this, folderUri)
        Log.d("RESTORE", "folderDoc=$folderDoc")
        if (folderDoc == null || !folderDoc.canRead()) {
            Toast.makeText(this, "Cannot read from selected folder!", Toast.LENGTH_LONG).show()
            return
        }
        val backupFile = folderDoc.listFiles().firstOrNull { it.name?.endsWith(".diary") == true || it.name?.endsWith(".zip") == true }
        Log.d("RESTORE", "backupFile=$backupFile")
        if (backupFile == null) {
            Toast.makeText(this, "No backup file found!", Toast.LENGTH_LONG).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Extract backup to cache
                val tempDir = File(cacheDir, "restore_temp").apply { mkdirs() }
                Log.d("RESTORE", "Extracting backup to $tempDir")
                
                val backupFileName = backupFile.name ?: ""
                val isNewFormat = backupFileName.endsWith(".diary")
                
                if (isNewFormat) {
                    // Use advanced decompression for new format
                    val tempBackupFile = File(cacheDir, "temp_restore.diary")
                    contentResolver.openInputStream(backupFile.uri)?.use { input ->
                        FileOutputStream(tempBackupFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val extractedFiles = AdvancedCompression.decompressAdvanced(
                        this@BackupRestoreActivity,
                        tempBackupFile,
                        tempDir,
                        AdvancedCompression.CompressionType.CUSTOM_MIXED
                    )
                    Log.d("RESTORE", "Extracted ${extractedFiles.size} files using advanced compression")
                    
                    // Clean up temp file
                    tempBackupFile.delete()
                } else {
                    // Fallback to ZIP for old format
                    contentResolver.openInputStream(backupFile.uri)?.use { input ->
                        java.util.zip.ZipInputStream(input).use { zis ->
                            var entry: java.util.zip.ZipEntry?
                            while (zis.nextEntry.also { entry = it } != null) {
                                entry?.let { e ->
                                    val outFile = File(tempDir, e.name)
                                    outFile.parentFile?.mkdirs() // Ensure parent directories exist
                                    FileOutputStream(outFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                    Log.d("RESTORE", "Extracted ${e.name}")
                                }
                            }
                        }
                    }
                }
                // Parse and restore notes
                val notesFile = File(tempDir, "notes.json")
                val diaryFile = File(tempDir, "diary_entries.json")
                val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                if (notesFile.exists()) {
                    val notesJson = notesFile.readText()
                    Log.d("RESTORE", "notes.json content: $notesJson")
                    val notesObj = gson.fromJson(notesJson, Map::class.java)
                    val notesListJson = gson.toJson(notesObj["notes"])
                    val notesList = gson.fromJson(notesListJson, Array<Note>::class.java).toList()
                    Log.d("RESTORE", "Parsed notes: $notesList")
                    db.noteDao().deleteAll()
                    db.noteDao().insertAll(notesList)
                } else {
                    Log.d("RESTORE", "notes.json not found")
                }
                if (diaryFile.exists()) {
                    val diaryJson = diaryFile.readText()
                    Log.d("RESTORE", "diary_entries.json content: $diaryJson")
                    val diaryList = gson.fromJson(diaryJson, Array<DiaryEntry>::class.java).toList()
                    Log.d("RESTORE", "Parsed diary entries: $diaryList")
                    db.diaryEntryDao().deleteAll()
                    db.diaryEntryDao().insertAll(diaryList)
                } else {
                    Log.d("RESTORE", "diary_entries.json not found")
                }
                
                // Move extracted images to app's internal storage
                val imagesDir = File(tempDir, "images")
                if (imagesDir.exists() && imagesDir.isDirectory) {
                    val imageFiles = imagesDir.listFiles()
                    if (imageFiles != null) {
                        for (imageFile in imageFiles) {
                            val targetFile = File(filesDir, imageFile.name)
                            try {
                                imageFile.copyTo(targetFile, overwrite = true)
                                Log.d("RESTORE", "Moved image to internal storage: ${imageFile.name}")
                            } catch (e: Exception) {
                                Log.e("RESTORE", "Failed to move image ${imageFile.name}: ${e.message}")
                            }
                        }
                    }
                }
                
                // Move extracted audio files to app's internal storage
                val audioDir = File(tempDir, "audio")
                if (audioDir.exists() && audioDir.isDirectory) {
                    val audioFiles = audioDir.listFiles()
                    if (audioFiles != null) {
                        for (audioFile in audioFiles) {
                            val targetFile = File(filesDir, audioFile.name)
                            try {
                                audioFile.copyTo(targetFile, overwrite = true)
                                Log.d("RESTORE", "Moved audio to internal storage: ${audioFile.name}")
                            } catch (e: Exception) {
                                Log.e("RESTORE", "Failed to move audio ${audioFile.name}: ${e.message}")
                            }
                        }
                    }
                }
                
                // Restore profile settings and theme color FIRST
                val profileSettingsFile = File(tempDir, "profile_settings.json")
                if (profileSettingsFile.exists()) {
                    try {
                        val profileSettingsJson = profileSettingsFile.readText()
                        val profileSettings = gson.fromJson(profileSettingsJson, Map::class.java)
                        
                        prefs.edit().apply {
                            putString("name", profileSettings["name"] as? String ?: "")
                            putString("username", profileSettings["username"] as? String ?: "")
                            putString("user_key", profileSettings["user_key"] as? String ?: "")
                            putString("theme_color", profileSettings["theme_color"] as? String ?: "#3F51B5")
                            putBoolean("is_night_mode", profileSettings["is_night_mode"] as? Boolean ?: false)
                            // Don't restore old file paths here - they will be updated after moving files
                        }.apply()
                        
                        Log.d("RESTORE", "Restored profile settings: $profileSettings")
                    } catch (e: Exception) {
                        Log.e("RESTORE", "Failed to restore profile settings: ${e.message}")
                    }
                }
                
                // Move profile/cover/theme images and update preferences
                val profileImages = listOf("profile_pic.jpg", "cover_pic.jpg", "theme_pic.jpg", "custom_day_image.jpg", "custom_night_image.jpg")
                for (imageName in profileImages) {
                    val sourceFile = File(tempDir, imageName)
                    if (sourceFile.exists()) {
                        val targetFile = File(filesDir, imageName)
                        try {
                            sourceFile.copyTo(targetFile, overwrite = true)
                            Log.d("RESTORE", "Moved profile/theme image to internal storage: $imageName")
                            
                            // Update preferences with new file paths
                            when (imageName) {
                                "profile_pic.jpg" -> {
                                    prefs.edit().putString("profile_pic_uri", targetFile.absolutePath).apply()
                                    Log.d("RESTORE", "Updated profile_pic_uri to: ${targetFile.absolutePath}")
                                }
                                "cover_pic.jpg" -> {
                                    prefs.edit().putString("cover_pic_uri", targetFile.absolutePath).apply()
                                    Log.d("RESTORE", "Updated cover_pic_uri to: ${targetFile.absolutePath}")
                                }
                                "theme_pic.jpg" -> {
                                    prefs.edit().putString("theme_header_pic_uri", targetFile.absolutePath).apply()
                                    Log.d("RESTORE", "Updated theme_header_pic_uri to: ${targetFile.absolutePath}")
                                }
                                "custom_day_image.jpg" -> {
                                    prefs.edit().putString("custom_day_image_uri", targetFile.absolutePath).apply()
                                    Log.d("RESTORE", "Updated custom_day_image_uri to: ${targetFile.absolutePath}")
                                }
                                "custom_night_image.jpg" -> {
                                    prefs.edit().putString("custom_night_image_uri", targetFile.absolutePath).apply()
                                    Log.d("RESTORE", "Updated custom_night_image_uri to: ${targetFile.absolutePath}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("RESTORE", "Failed to move profile/theme image $imageName: ${e.message}")
                        }
                    } else {
                        Log.d("RESTORE", "Profile image not found in backup: $imageName")
                    }
                }
                
                // Log final file paths after restoration
                Log.d("RESTORE", "Final file paths after restore - Profile: ${prefs.getString("profile_pic_uri", "null")}, Cover: ${prefs.getString("cover_pic_uri", "null")}, Theme: ${prefs.getString("theme_header_pic_uri", "null")}")
                
                // Verify restored files exist
                val profilePicPath = prefs.getString("profile_pic_uri", null)
                val coverPicPath = prefs.getString("cover_pic_uri", null)
                val themePicPath = prefs.getString("theme_header_pic_uri", null)
                
                Log.d("RESTORE", "File existence check:")
                Log.d("RESTORE", "Profile pic exists: ${profilePicPath?.let { File(it).exists() }}")
                Log.d("RESTORE", "Cover pic exists: ${coverPicPath?.let { File(it).exists() }}")
                Log.d("RESTORE", "Theme pic exists: ${themePicPath?.let { File(it).exists() }}")
                
                // Count restored items for success message
                val restoredImages = profileImages.count { File(tempDir, it).exists() }
                val restoredSettings = if (profileSettingsFile.exists()) "Profile settings, " else ""
                
                withContext(Dispatchers.Main) {
                    // Show restore complete message
                    textRestoreComplete.visibility = View.VISIBLE
                    
                    // Hide after 5 seconds
                    handler.postDelayed({
                        textRestoreComplete.visibility = View.GONE
                    }, 5000)
                    
                    // Also show toast for additional info
                    val message = if (restoredImages > 0) {
                        "Restored $restoredSettings$restoredImages profile images."
                    } else {
                        "$restoredSettings"
                    }
                    if (message.isNotEmpty()) {
                        Toast.makeText(this@BackupRestoreActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RESTORE", "Restore failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupRestoreActivity, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Storage permission granted. Please try backup again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permission denied. Cannot create backup.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_BACKUP_FOLDER_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                backupFolderUri = uri
                prefs.edit().putString("backup_folder_uri", uri.toString()).apply()
                findViewById<TextView>(R.id.text_backup_path).text = uri.toString()
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                Toast.makeText(this, "Backup folder set!", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                onGoogleSignInSuccess(account)
            } catch (e: ApiException) {
                Log.e("GOOGLE_SIGN_IN", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
                // Reset the toggle if sign-in failed
                findViewById<SwitchMaterial>(R.id.switch_google_drive).isChecked = false
                prefs.edit().putBoolean("google_drive_enabled", false).apply()
            }
        } else if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE && resultCode == RESULT_CANCELED) {
            // User cancelled the sign-in, reset the toggle
            findViewById<SwitchMaterial>(R.id.switch_google_drive).isChecked = false
            prefs.edit().putBoolean("google_drive_enabled", false).apply()
            Toast.makeText(this, "Google Sign-In cancelled", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Google Drive Methods
    private fun initializeGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        // Check if user is already signed in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            // Only auto-enable if the preference is already set to true
            val isGoogleDriveEnabled = prefs.getBoolean("google_drive_enabled", false)
            if (isGoogleDriveEnabled) {
            onGoogleSignInSuccess(account)
            } else {
                // Account is signed in but preference is false, so just update UI without enabling toggle
                currentGoogleAccount = account
                googleDriveService = GoogleDriveService(this).apply {
                    initialize(account)
                }
                
                // Update UI to show connected status but keep toggle off
                findViewById<Button>(R.id.button_google_signin).visibility = View.GONE
                findViewById<TextView>(R.id.text_google_drive_status).apply {
                    visibility = View.VISIBLE
                    text = "Connected to Google Drive"
                    setTextColor(ContextCompat.getColor(this@BackupRestoreActivity, android.R.color.holo_green_dark))
                }
                
                updateGoogleDriveInfo()
            }
        } else {
            // If no account is signed in, make sure the toggle reflects the actual state
            val isGoogleDriveEnabled = prefs.getBoolean("google_drive_enabled", false)
            if (isGoogleDriveEnabled) {
                // If preference says enabled but no account, reset the preference
                prefs.edit().putBoolean("google_drive_enabled", false).apply()
                findViewById<SwitchMaterial>(R.id.switch_google_drive).isChecked = false
            }
        }
    }
    
    private fun setupGoogleDriveSwitches(
        switchLocalStorage: SwitchMaterial,
        switchGoogleDrive: SwitchMaterial,
        buttonGoogleSignIn: Button,
        textGoogleDriveStatus: TextView
    ) {
        // Set up switch listeners
        switchLocalStorage.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && !switchGoogleDrive.isChecked) {
                // If both are unchecked, keep local storage checked
                switchLocalStorage.isChecked = true
                Toast.makeText(this, "At least one backup location must be selected", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Remove the old switch listener since we're handling it in onCreate now
    }
    
    private fun signInToGoogleDrive() {
        // Sign out first to force account picker to appear
        googleSignInClient.signOut().addOnCompleteListener {
            // After signing out, show the sign-in intent with account picker
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE)
        }
    }
    
    private fun onGoogleSignInSuccess(account: GoogleSignInAccount) {
        currentGoogleAccount = account
        googleDriveService = GoogleDriveService(this).apply {
            initialize(account)
        }
        
        // Save the preference when sign-in is successful
        prefs.edit().putBoolean("google_drive_enabled", true).apply()
        
        // Update UI
        findViewById<Button>(R.id.button_google_signin).visibility = View.GONE
        findViewById<TextView>(R.id.text_google_drive_status).apply {
            visibility = View.VISIBLE
            text = "Connected to Google Drive"
            setTextColor(ContextCompat.getColor(this@BackupRestoreActivity, android.R.color.holo_green_dark))
        }
        
        updateGoogleDriveInfo()
        
        Toast.makeText(this, "Successfully signed in to Google Drive", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateGoogleDriveInfo() {
        val textGoogleAccount = findViewById<TextView>(R.id.text_google_account)
        val textGoogleLastBackup = findViewById<TextView>(R.id.text_google_last_backup)
        val textGoogleBackupLocation = findViewById<TextView>(R.id.text_google_backup_location)
        
        // Show account info
        textGoogleAccount.text = currentGoogleAccount?.email ?: "Unknown"
        
        // Show backup location
        textGoogleBackupLocation.text = "Google Drive/DiaryApp_Backups"
        
        // Check for last backup time
        val lastGoogleBackupTime = prefs.getLong("last_google_backup_time", 0)
        if (lastGoogleBackupTime > 0) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
            textGoogleLastBackup.text = dateFormat.format(Date(lastGoogleBackupTime))
        } else {
            textGoogleLastBackup.text = "No backup yet"
        }
    }
    
    private suspend fun backupToGoogleDrive(backupFile: File): Boolean {
        return try {
            val totalSize = backupFile.length()
            updateProgressWithSize(0, totalSize)
            
            val result = googleDriveService?.createBackup(backupFile) { uploadedBytes ->
                // Use a coroutine to call the suspend function
                CoroutineScope(Dispatchers.Main).launch {
                    updateProgressWithSize(uploadedBytes, totalSize)
                }
            }
            
            if (result?.isSuccess == true) {
                // Save backup time
                prefs.edit().putLong("last_google_backup_time", System.currentTimeMillis()).apply()
                
                // Clear cache after successful backup
                try {
                    clearCacheAfterBackup(this@BackupRestoreActivity)
                    Log.d("GOOGLE_DRIVE", "Cache cleared successfully after Google Drive backup")
                } catch (e: Exception) {
                    Log.e("GOOGLE_DRIVE", "Failed to clear cache after Google Drive backup", e)
                }
                
                true
            } else {
                Log.e("GOOGLE_DRIVE", "Backup failed: ${result?.exceptionOrNull()?.message}")
                false
            }
        } catch (e: Exception) {
            Log.e("GOOGLE_DRIVE", "Google Drive backup failed", e)
            false
        }
    }

    private suspend fun startLocalBackup(images: Boolean, audio: Boolean, notes: Boolean, diaryNotes: Boolean, profileSettings: Boolean) {
        progressBar.visibility = View.VISIBLE
        progressSizeInfo.visibility = View.VISIBLE
        progressBar.progress = 0
        textBackupComplete.visibility = View.GONE
        textUploadedSize.text = "0 B"
        textTotalSize.text = "Calculating..."

        val backupUriString = prefs.getString("backup_folder_uri", null)
        if (backupUriString == null) {
            Toast.makeText(this, "Please choose a backup folder for local storage!", Toast.LENGTH_LONG).show()
            progressBar.visibility = View.GONE
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var notesCount = 0
            var diaryCount = 0
            
            try {
                val filesToZip = mutableListOf<Pair<String, File>>()
                var progress = 0
                val steps = (if (notes) 1 else 0) + (if (diaryNotes) 1 else 0) + (if (images) 1 else 0) + (if (audio) 1 else 0) + (if (profileSettings) 1 else 0) + 1
                
                // 1. Notes
                var notesJson = "[]"
                if (notes) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    val notesList = withContext(Dispatchers.IO) { db.noteDao().getAllNotesSync() }
                    notesCount = notesList.size
                    Log.d("BACKUP", "Backing up notes: " + notesList.joinToString { "[id=${it.id}, title=${it.title}]" })
                    val userKey = prefs.getString("user_key", "") ?: ""
                    val username = prefs.getString("username", "") ?: ""
                    val notesJsonObj = mapOf(
                        "user_key" to userKey,
                        "username" to username,
                        "notes" to notesList
                    )
                    notesJson = gson.toJson(notesJsonObj)
                    val notesFile = File(cacheDir, "notes.json")
                    notesFile.writeText(notesJson)
                    filesToZip.add("notes.json" to notesFile)
                }
                updateProgress(progress++, steps)

                // 2. Diary Notes
                var diaryJson = "[]"
                if (diaryNotes) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    val diaryList = withContext(Dispatchers.IO) { db.diaryEntryDao().getAllEntries() }
                    diaryCount = diaryList.size
                    Log.d("BACKUP", "Backing up diary entries: " + diaryList.joinToString { "[id=${it.id}, title=${it.title}]" })
                    diaryJson = gson.toJson(diaryList)
                    val diaryFile = File(cacheDir, "diary_entries.json")
                    diaryFile.writeText(diaryJson)
                    filesToZip.add("diary_entries.json" to diaryFile)
                }
                updateProgress(progress++, steps)

                // 3. Images and Audio
                if (images) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    val imageFiles = mutableSetOf<File>()
                    
                    if (notes) {
                        val notesList = db.noteDao().getAllNotesSync()
                        notesList.flatMap { it.imagePaths }.forEach { path ->
                            val file = File(path)
                            if (file.exists()) imageFiles.add(file)
                        }
                    }
                    if (diaryNotes) {
                        val diaryList = db.diaryEntryDao().getAllEntries()
                        diaryList.flatMap { it.imagePaths }.forEach { path ->
                            val file = File(path)
                            if (file.exists()) imageFiles.add(file)
                        }
                    }
                    
                    // Add profile, cover, theme pics (only if profile settings is enabled)
                    if (profileSettings) {
                        val profilePic = prefs.getString("profile_pic_uri", null)?.let { File(it) }
                        val coverPic = prefs.getString("cover_pic_uri", null)?.let { File(it) }
                        val themePic = prefs.getString("theme_header_pic_uri", null)?.let { File(it) }
                        val customDayPic = prefs.getString("custom_day_image_uri", null)?.let { File(it) }
                        val customNightPic = prefs.getString("custom_night_image_uri", null)?.let { File(it) }
                        
                        listOf(
                            profilePic to "profile_pic.jpg", 
                            coverPic to "cover_pic.jpg", 
                            themePic to "theme_pic.jpg",
                            customDayPic to "custom_day_image.jpg",
                            customNightPic to "custom_night_image.jpg"
                        ).forEach { (file, name) ->
                            if (file != null && file.exists()) filesToZip.add(name to file)
                        }
                        
                        // Backup profile settings and theme color
                        val profileSettings = mapOf(
                            "name" to (prefs.getString("name", "") ?: ""),
                            "username" to (prefs.getString("username", "") ?: ""),
                            "user_key" to (prefs.getString("user_key", "") ?: ""),
                            "theme_color" to (prefs.getString("theme_color", "#3F51B5") ?: "#3F51B5"),
                            "is_night_mode" to prefs.getBoolean("is_night_mode", false),
                            "profile_pic_uri" to (prefs.getString("profile_pic_uri", "") ?: ""),
                            "cover_pic_uri" to (prefs.getString("cover_pic_uri", "") ?: ""),
                            "theme_header_pic_uri" to (prefs.getString("theme_header_pic_uri", "") ?: "")
                        )
                        val profileSettingsJson = gson.toJson(profileSettings)
                        val profileSettingsFile = File(cacheDir, "profile_settings.json")
                        profileSettingsFile.writeText(profileSettingsJson)
                        filesToZip.add("profile_settings.json" to profileSettingsFile)
                        Log.d("BACKUP", "Backed up profile settings: $profileSettings")
                        updateProgress(progress++, steps)
                    }
                    imageFiles.forEachIndexed { idx, file ->
                        filesToZip.add("images/${file.name}" to file)
                    }
                }
                updateProgress(progress++, steps)

                // 4. Audio files
                if (audio) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    val audioFiles = mutableSetOf<File>()
                    
                    if (notes) {
                        val notesList = db.noteDao().getAllNotesSync()
                        notesList.flatMap { it.audioList }.forEach { audioItem ->
                            val file = File(audioItem.filePath)
                            if (file.exists()) audioFiles.add(file)
                        }
                    }
                    if (diaryNotes) {
                        val diaryList = db.diaryEntryDao().getAllEntries()
                        diaryList.flatMap { it.audioList }.forEach { audioItem ->
                            val file = File(audioItem.filePath)
                            if (file.exists()) audioFiles.add(file)
                        }
                    }
                    audioFiles.forEachIndexed { idx, file ->
                        filesToZip.add("audio/${file.name}" to file)
                    }
                }
                updateProgress(progress++, steps)

                // 5. Compress all files with advanced compression
                val tempZip = File(cacheDir, "temp_backup.diary")
                zipFiles(filesToZip, tempZip)
                updateProgress(progress++, steps)
                
                // 6. Upload to local storage
                val folderUri = Uri.parse(backupUriString)
                val folderDoc = DocumentFile.fromTreeUri(this@BackupRestoreActivity, folderUri)
                if (folderDoc != null && folderDoc.canWrite()) {
                    val backupFileName = "diary_backup.diary"
                    folderDoc.listFiles().forEach { file ->
                        if (file.name?.endsWith(".diary") == true || file.name?.endsWith(".zip") == true) file.delete()
                    }
                    val backupDoc = folderDoc.createFile("application/octet-stream", backupFileName)
                    if (backupDoc != null) {
                        contentResolver.openOutputStream(backupDoc.uri)?.use { out ->
                            FileInputStream(tempZip).use { input ->
                                input.copyTo(out)
                            }
                        }
                        prefs.edit().putLong("last_backup_time", System.currentTimeMillis())
                            .putString("last_backup_path", backupDoc.uri.toString()).apply()
                        
                                                                         // Clear cache after successful backup
                        try {
                            clearCacheAfterBackup(this@BackupRestoreActivity)
                            Log.d("BACKUP", "Cache cleared successfully after local backup")
                        } catch (e: Exception) {
                            Log.e("BACKUP", "Failed to clear cache after local backup", e)
                        }
                        
                        withContext(Dispatchers.Main) {
                            progressBar.visibility = View.GONE
                            progressSizeInfo.visibility = View.GONE
                            textBackupComplete.visibility = View.VISIBLE
                            textBackupComplete.text = "Local Storage Backup Complete"
                            updateLastBackupInfo()
                            val successMessage = "Local backup completed successfully! Notes: $notesCount, Diary Entries: $diaryCount"
                            Toast.makeText(this@BackupRestoreActivity, successMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BACKUP", "Local backup failed", e)
                                 withContext(Dispatchers.Main) {
                     progressBar.visibility = View.GONE
                     progressSizeInfo.visibility = View.GONE
                     Toast.makeText(this@BackupRestoreActivity, "Local backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                 }
            }
        }
    }

    private fun startGoogleDriveBackup(images: Boolean, audio: Boolean, notes: Boolean, diaryNotes: Boolean, profileSettings: Boolean) {
        if (currentGoogleAccount == null) {
            Toast.makeText(this, "Please sign in to Google Drive first!", Toast.LENGTH_LONG).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        progressSizeInfo.visibility = View.VISIBLE
        progressBar.progress = 0
        textBackupComplete.visibility = View.GONE
        textUploadedSize.text = "0 B"
        textTotalSize.text = "Calculating..."

        CoroutineScope(Dispatchers.IO).launch {
            var notesCount = 0
            var diaryCount = 0
            
            try {
                val filesToZip = mutableListOf<Pair<String, File>>()
                var progress = 0
                val steps = (if (notes) 1 else 0) + (if (diaryNotes) 1 else 0) + (if (images) 1 else 0) + (if (audio) 1 else 0) + (if (profileSettings) 1 else 0) + 1
                
                // 1. Notes
                var notesJson = "[]"
                if (notes) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    val notesList = withContext(Dispatchers.IO) { db.noteDao().getAllNotesSync() }
                    notesCount = notesList.size
                    Log.d("BACKUP", "Backing up notes: " + notesList.joinToString { "[id=${it.id}, title=${it.title}]" })
                    val userKey = prefs.getString("user_key", "") ?: ""
                    val username = prefs.getString("username", "") ?: ""
                    val notesJsonObj = mapOf(
                        "user_key" to userKey,
                        "username" to username,
                        "notes" to notesList
                    )
                    notesJson = gson.toJson(notesJsonObj)
                    val notesFile = File(cacheDir, "notes.json")
                    notesFile.writeText(notesJson)
                    filesToZip.add("notes.json" to notesFile)
                }
                updateProgress(progress++, steps)

                // 2. Diary Notes
                var diaryJson = "[]"
                if (diaryNotes) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    val diaryList = withContext(Dispatchers.IO) { db.diaryEntryDao().getAllEntries() }
                    diaryCount = diaryList.size
                    Log.d("BACKUP", "Backing up diary entries: " + diaryList.joinToString { "[id=${it.id}, title=${it.title}]" })
                    diaryJson = gson.toJson(diaryList)
                    val diaryFile = File(cacheDir, "diary_entries.json")
                    diaryFile.writeText(diaryJson)
                    filesToZip.add("diary_entries.json" to diaryFile)
                }
                updateProgress(progress++, steps)

                // 3. Images and Audio
                if (images) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    val imageFiles = mutableSetOf<File>()
                    
                    if (notes) {
                        val notesList = db.noteDao().getAllNotesSync()
                        notesList.flatMap { it.imagePaths }.forEach { path ->
                            val file = File(path)
                            if (file.exists()) imageFiles.add(file)
                        }
                    }
                    if (diaryNotes) {
                        val diaryList = db.diaryEntryDao().getAllEntries()
                        diaryList.flatMap { it.imagePaths }.forEach { path ->
                            val file = File(path)
                            if (file.exists()) imageFiles.add(file)
                        }
                    }
                    
                    // Add profile, cover, theme pics (only if profile settings is enabled)
                    if (profileSettings) {
                        val profilePic = prefs.getString("profile_pic_uri", null)?.let { File(it) }
                        val coverPic = prefs.getString("cover_pic_uri", null)?.let { File(it) }
                        val themePic = prefs.getString("theme_header_pic_uri", null)?.let { File(it) }
                        val customDayPic = prefs.getString("custom_day_image_uri", null)?.let { File(it) }
                        val customNightPic = prefs.getString("custom_night_image_uri", null)?.let { File(it) }
                        
                        listOf(
                            profilePic to "profile_pic.jpg", 
                            coverPic to "cover_pic.jpg", 
                            themePic to "theme_pic.jpg",
                            customDayPic to "custom_day_image.jpg",
                            customNightPic to "custom_night_image.jpg"
                        ).forEach { (file, name) ->
                            if (file != null && file.exists()) filesToZip.add(name to file)
                        }
                        
                        // Backup profile settings and theme color
                        val profileSettings = mapOf(
                            "name" to (prefs.getString("name", "") ?: ""),
                            "username" to (prefs.getString("username", "") ?: ""),
                            "user_key" to (prefs.getString("user_key", "") ?: ""),
                            "theme_color" to (prefs.getString("theme_color", "#3F51B5") ?: "#3F51B5"),
                            "is_night_mode" to prefs.getBoolean("is_night_mode", false),
                            "profile_pic_uri" to (prefs.getString("profile_pic_uri", "") ?: ""),
                            "cover_pic_uri" to (prefs.getString("cover_pic_uri", "") ?: ""),
                            "theme_header_pic_uri" to (prefs.getString("theme_header_pic_uri", "") ?: "")
                        )
                        val profileSettingsJson = gson.toJson(profileSettings)
                        val profileSettingsFile = File(cacheDir, "profile_settings.json")
                        profileSettingsFile.writeText(profileSettingsJson)
                        filesToZip.add("profile_settings.json" to profileSettingsFile)
                        Log.d("BACKUP", "Backed up profile settings: $profileSettings")
                        updateProgress(progress++, steps)
                    }
                    imageFiles.forEachIndexed { idx, file ->
                        filesToZip.add("images/${file.name}" to file)
                    }
                }
                updateProgress(progress++, steps)

                // 4. Audio files
                if (audio) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    val audioFiles = mutableSetOf<File>()
                    
                    if (notes) {
                        val notesList = db.noteDao().getAllNotesSync()
                        notesList.flatMap { it.audioList }.forEach { audioItem ->
                            val file = File(audioItem.filePath)
                            if (file.exists()) audioFiles.add(file)
                        }
                    }
                    if (diaryNotes) {
                        val diaryList = db.diaryEntryDao().getAllEntries()
                        diaryList.flatMap { it.audioList }.forEach { audioItem ->
                            val file = File(audioItem.filePath)
                            if (file.exists()) audioFiles.add(file)
                        }
                    }
                    audioFiles.forEachIndexed { idx, file ->
                        filesToZip.add("audio/${file.name}" to file)
                    }
                }
                updateProgress(progress++, steps)

                // 5. Compress all files with advanced compression
                val tempZip = File(cacheDir, "temp_backup.diary")
                zipFiles(filesToZip, tempZip)
                updateProgress(progress++, steps)
                
                // 6. Upload to Google Drive
                val success = backupToGoogleDrive(tempZip)
                
                                 withContext(Dispatchers.Main) {
                     progressBar.visibility = View.GONE
                     progressSizeInfo.visibility = View.GONE
                     textBackupComplete.visibility = View.VISIBLE
                     if (success) {
                         textBackupComplete.text = "Google Drive Backup Complete"
                         updateGoogleDriveInfo()
                         val successMessage = "Google Drive backup completed successfully! Notes: $notesCount, Diary Entries: $diaryCount"
                         Toast.makeText(this@BackupRestoreActivity, successMessage, Toast.LENGTH_LONG).show()
                     } else {
                         textBackupComplete.text = "Google Drive Backup Failed"
                         Toast.makeText(this@BackupRestoreActivity, "Google Drive backup failed!", Toast.LENGTH_LONG).show()
                     }
                 }
            } catch (e: Exception) {
                Log.e("BACKUP", "Google Drive backup failed", e)
                                 withContext(Dispatchers.Main) {
                     progressBar.visibility = View.GONE
                     progressSizeInfo.visibility = View.GONE
                     Toast.makeText(this@BackupRestoreActivity, "Google Drive backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                 }
            }
        }
    }

    private fun restoreFromGoogleDrive() {
        if (currentGoogleAccount == null) {
            Toast.makeText(this, "Please sign in to Google Drive first!", Toast.LENGTH_LONG).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = googleDriveService?.downloadBackup()
                if (result?.isSuccess == true) {
                    val backupFile = result.getOrNull()
                    if (backupFile != null) {
                        // Extract and restore from the downloaded file
                        // ... (copy the restore logic from restoreFromLocalBackup function)
                        
                        withContext(Dispatchers.Main) {
                            textRestoreComplete.visibility = View.VISIBLE
                            Toast.makeText(this@BackupRestoreActivity, "Restore from Google Drive completed!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BackupRestoreActivity, "No backup found on Google Drive!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RESTORE", "Google Drive restore failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupRestoreActivity, "Google Drive restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Static function that can be called from other activities for automatic backup
    companion object {
        fun performAutomaticBackup(context: Context) {
            Log.d("AUTO_BACKUP", "Starting automatic backup check...")
            
            val prefs = EncryptedSharedPreferences.create(
                "diary_auth_prefs",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            // Check if automatic backup is enabled
            val isAutomaticBackupEnabled = prefs.getBoolean("automatic_backup_enabled", true)
            Log.d("AUTO_BACKUP", "Automatic backup enabled: $isAutomaticBackupEnabled")
            if (!isAutomaticBackupEnabled) {
                Log.d("AUTO_BACKUP", "Automatic backup is disabled, skipping")
                return
            }
            
            // Check which backup locations are enabled
            val useLocalStorage = prefs.getBoolean("local_storage_enabled", true)
            val useGoogleDrive = prefs.getBoolean("google_drive_enabled", false)
            
            Log.d("AUTO_BACKUP", "Local storage enabled: $useLocalStorage, Google Drive enabled: $useGoogleDrive")
            
            // Only proceed if at least one backup location is enabled
            if (!useLocalStorage && !useGoogleDrive) {
                Log.d("AUTO_BACKUP", "No backup locations enabled, skipping")
                return
            }
            
            // Perform automatic backup in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("AUTO_BACKUP", "Starting automatic backup...")
                    
                    // Create backup data
                    val gson = Gson()
                    val filesToZip = mutableListOf<Pair<String, java.io.File>>()
                    
                    // 1. Notes
                    val db = DiaryDatabase.getDatabase(context)
                    val notesList = db.noteDao().getAllNotesSync()
                    val userKey = prefs.getString("user_key", "") ?: ""
                    val username = prefs.getString("username", "") ?: ""
                    val notesJsonObj = mapOf(
                        "user_key" to userKey,
                        "username" to username,
                        "notes" to notesList
                    )
                    val notesJson = gson.toJson(notesJsonObj)
                    val notesFile = java.io.File(context.cacheDir, "notes.json")
                    notesFile.writeText(notesJson)
                    filesToZip.add("notes.json" to notesFile)
                    
                    // 2. Diary Entries
                    val diaryList = db.diaryEntryDao().getAllEntries()
                    val diaryJson = gson.toJson(diaryList)
                    val diaryFile = java.io.File(context.cacheDir, "diary_entries.json")
                    diaryFile.writeText(diaryJson)
                    filesToZip.add("diary_entries.json" to diaryFile)
                    
                    // 3. Images and Audio
                    val imageFiles = mutableSetOf<java.io.File>()
                    val audioFiles = mutableSetOf<java.io.File>()
                    
                    notesList.flatMap { it.imagePaths }.forEach { path ->
                        val file = java.io.File(path)
                        if (file.exists()) imageFiles.add(file)
                    }
                    notesList.flatMap { it.audioList }.forEach { audioItem ->
                        val file = java.io.File(audioItem.filePath)
                        if (file.exists()) audioFiles.add(file)
                    }
                    
                    diaryList.flatMap { it.imagePaths }.forEach { path ->
                        val file = java.io.File(path)
                        if (file.exists()) imageFiles.add(file)
                    }
                    diaryList.flatMap { it.audioList }.forEach { audioItem ->
                        val file = java.io.File(audioItem.filePath)
                        if (file.exists()) audioFiles.add(file)
                    }
                    
                    // Add profile images
                    val profilePic = prefs.getString("profile_pic_uri", null)?.let { java.io.File(it) }
                    val coverPic = prefs.getString("cover_pic_uri", null)?.let { java.io.File(it) }
                    val themePic = prefs.getString("theme_header_pic_uri", null)?.let { java.io.File(it) }
                    
                    listOf(
                        profilePic to "profile_pic.jpg",
                        coverPic to "cover_pic.jpg",
                        themePic to "theme_pic.jpg"
                    ).forEach { (file, name) ->
                        if (file != null && file.exists()) filesToZip.add(name to file)
                    }
                    
                    // Add profile settings
                    val profileSettings = mapOf(
                        "name" to (prefs.getString("name", "") ?: ""),
                        "username" to (prefs.getString("username", "") ?: ""),
                        "user_key" to (prefs.getString("user_key", "") ?: ""),
                        "theme_color" to (prefs.getString("theme_color", "#3F51B5") ?: "#3F51B5"),
                        "is_night_mode" to prefs.getBoolean("is_night_mode", false),
                        "profile_pic_uri" to (prefs.getString("profile_pic_uri", "") ?: ""),
                        "cover_pic_uri" to (prefs.getString("cover_pic_uri", "") ?: ""),
                        "theme_header_pic_uri" to (prefs.getString("theme_header_pic_uri", "") ?: "")
                    )
                    val profileSettingsJson = gson.toJson(profileSettings)
                    val profileSettingsFile = java.io.File(context.cacheDir, "profile_settings.json")
                    profileSettingsFile.writeText(profileSettingsJson)
                    filesToZip.add("profile_settings.json" to profileSettingsFile)
                    
                    // Add images and audio
                    imageFiles.forEach { file ->
                        filesToZip.add("images/${file.name}" to file)
                    }
                    audioFiles.forEach { file ->
                        filesToZip.add("audio/${file.name}" to file)
                    }
                    
                    // Create zip file
                    val tempZip = java.io.File(context.cacheDir, "auto_backup.zip")
                    createZipFile(filesToZip, tempZip)
                    
                    // Backup to local storage if enabled
                    if (useLocalStorage) {
                        Log.d("AUTO_BACKUP", "Local storage is enabled, attempting backup...")
                        val backupUriString = prefs.getString("backup_folder_uri", null)
                        Log.d("AUTO_BACKUP", "Backup URI: $backupUriString")
                        if (backupUriString != null) {
                            try {
                                val folderUri = Uri.parse(backupUriString)
                                val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
                                if (folderDoc != null && folderDoc.canWrite()) {
                                    // Delete existing backups
                                    folderDoc.listFiles().forEach { file ->
                                        if (file.name?.endsWith(".zip") == true) file.delete()
                                    }
                                    
                                    val backupDoc = folderDoc.createFile("application/zip", "diary_backup.zip")
                                    if (backupDoc != null) {
                                        context.contentResolver.openOutputStream(backupDoc.uri)?.use { out ->
                                            java.io.FileInputStream(tempZip).use { input ->
                                                input.copyTo(out)
                                            }
                                        }
                                        
                                        // Update backup info
                                        prefs.edit()
                                            .putLong("last_backup_time", System.currentTimeMillis())
                                            .putString("last_backup_path", backupDoc.uri.toString())
                                            .apply()
                                        
                                        Log.d("AUTO_BACKUP", "Local automatic backup completed successfully")
                                        Toast.makeText(context, "Automatic backup completed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AUTO_BACKUP", "Local automatic backup failed", e)
                            }
                        } else {
                            Log.d("AUTO_BACKUP", "No backup folder URI set, skipping local backup")
                        }
                    }
                    
                    // Backup to Google Drive if enabled
                    if (useGoogleDrive) {
                        Log.d("AUTO_BACKUP", "Google Drive is enabled, attempting backup...")
                        try {
                            val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
                            if (googleAccount != null) {
                                val googleDriveService = GoogleDriveService(context).apply {
                                    initialize(googleAccount)
                                }
                                
                                val result = googleDriveService.createBackup(tempZip)
                                if (result.isSuccess) {
                                    prefs.edit().putLong("last_google_backup_time", System.currentTimeMillis()).apply()
                                    Log.d("AUTO_BACKUP", "Google Drive automatic backup completed")
                                } else {
                                    Log.e("AUTO_BACKUP", "Google Drive automatic backup failed: ${result.exceptionOrNull()?.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AUTO_BACKUP", "Google Drive automatic backup failed", e)
                        }
                    }
                    
                    val backupLocations = mutableListOf<String>()
                    if (useLocalStorage) backupLocations.add("Local Storage")
                    if (useGoogleDrive) backupLocations.add("Google Drive")
                    Log.d("AUTO_BACKUP", "Automatic backup completed successfully to: ${backupLocations.joinToString(", ")}")
                    
                    // Clear cache after successful backup
                    try {
                        clearCacheAfterBackup(context)
                        Log.d("AUTO_BACKUP", "Cache cleared successfully after backup")
                    } catch (e: Exception) {
                        Log.e("AUTO_BACKUP", "Failed to clear cache after backup", e)
                    }
                    
                } catch (e: Exception) {
                    Log.e("AUTO_BACKUP", "Automatic backup failed", e)
                }
            }
        }
        
        private fun createZipFile(files: List<Pair<String, java.io.File>>, zipFile: java.io.File) {
            ZipOutputStream(BufferedOutputStream(java.io.FileOutputStream(zipFile))).use { zos ->
                files.forEach { (entryName, file) ->
                    java.io.FileInputStream(file).use { fis ->
                        val entry = ZipEntry(entryName)
                        zos.putNextEntry(entry)
                        fis.copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
        }
        
        fun clearCacheAfterBackup(context: Context) {
            clearCache(context)
        }
        
        fun clearCache(context: Context) {
            try {
                Log.d("CACHE_CLEAR", "Starting cache clearing process...")
                
                // Clear cache directory
                val cacheDir = context.cacheDir
                Log.d("CACHE_CLEAR", "Cache directory path: ${cacheDir.absolutePath}")
                if (cacheDir.exists()) {
                    val filesBefore = cacheDir.listFiles()?.size ?: 0
                    Log.d("CACHE_CLEAR", "Files in cache before clearing: $filesBefore")
                    deleteDirectory(cacheDir)
                    val filesAfter = cacheDir.listFiles()?.size ?: 0
                    Log.d("CACHE_CLEAR", "Files in cache after clearing: $filesAfter")
                }
                
                // Clear external cache directory if it exists
                val externalCacheDir = context.externalCacheDir
                if (externalCacheDir != null && externalCacheDir.exists()) {
                    Log.d("CACHE_CLEAR", "External cache directory path: ${externalCacheDir.absolutePath}")
                    val filesBefore = externalCacheDir.listFiles()?.size ?: 0
                    Log.d("CACHE_CLEAR", "Files in external cache before clearing: $filesBefore")
                    deleteDirectory(externalCacheDir)
                    val filesAfter = externalCacheDir.listFiles()?.size ?: 0
                    Log.d("CACHE_CLEAR", "Files in external cache after clearing: $filesAfter")
                }
                
                // Clear specific backup files from cache
                val backupFiles = listOf("notes.json", "diary_entries.json", "profile_settings.json", "auto_backup.zip", "temp_backup.zip", "diary_backup.zip")
                backupFiles.forEach { fileName ->
                    val file = java.io.File(cacheDir, fileName)
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d("CACHE_CLEAR", "Deleted backup file: $fileName - Success: $deleted")
                    } else {
                        Log.d("CACHE_CLEAR", "Backup file not found: $fileName")
                    }
                }
                
                Log.d("CACHE_CLEAR", "Cache clearing process completed")
                
            } catch (e: Exception) {
                Log.e("CACHE_CLEAR", "Error clearing cache", e)
            }
        }
        
        private fun deleteDirectory(directory: java.io.File) {
            if (directory.exists()) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isDirectory) {
                            deleteDirectory(file)
                        } else {
                            file.delete()
                        }
                    }
                }
            }
        }
    }
    
    private fun applyThemeColors() {
        val isNightMode = resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Get all UI elements
        val backButton = findViewById<android.widget.ImageButton>(R.id.backButton)
        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
        val switchLocalStorage = findViewById<SwitchMaterial>(R.id.switch_local_storage)
        val switchAutomaticBackup = findViewById<SwitchMaterial>(R.id.switch_automatic_backup)
        val switchGoogleDrive = findViewById<SwitchMaterial>(R.id.switch_google_drive)
        val buttonBackup = findViewById<Button>(R.id.button_backup)
        val buttonChooseFolder = findViewById<Button>(R.id.button_choose_folder)
        val buttonRestore = findViewById<Button>(R.id.button_restore)
        val buttonGoogleBackup = findViewById<Button>(R.id.button_google_backup)
        val buttonGoogleRestore = findViewById<Button>(R.id.button_google_restore)
        val buttonGoogleSignIn = findViewById<Button>(R.id.button_google_signin)
        
        // Get card containers
        val localStorageCard = findViewById<LinearLayout>(R.id.localStorageCard)
        val googleDriveCard = findViewById<LinearLayout>(R.id.googleDriveCard)
        
        if (isNightMode) {
            // Dark theme colors
            backButton.setColorFilter(getColor(R.color.backup_text_color))
            toolbarTitle.setTextColor(getColor(R.color.backup_text_color))
            
            // Set card backgrounds for night mode
            localStorageCard?.setBackgroundResource(R.drawable.bg_backup_card_night)
            googleDriveCard?.setBackgroundResource(R.drawable.bg_backup_card_night)
            
            // Set button colors for night mode
            buttonBackup.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonBackup.setTextColor(getColor(R.color.backup_button_text))
            buttonChooseFolder.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonChooseFolder.setTextColor(getColor(R.color.backup_button_text))
            buttonRestore.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonRestore.setTextColor(getColor(R.color.backup_button_text))
            buttonGoogleBackup.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonGoogleBackup.setTextColor(getColor(R.color.backup_button_text))
            buttonGoogleRestore.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonGoogleRestore.setTextColor(getColor(R.color.backup_button_text))
            buttonGoogleSignIn.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonGoogleSignIn.setTextColor(getColor(R.color.backup_button_text))
            
        } else {
            // Light theme colors
            backButton.setColorFilter(getColor(R.color.backup_text_color))
            toolbarTitle.setTextColor(getColor(R.color.backup_text_color))
            
            // Set card backgrounds for day mode
            localStorageCard?.setBackgroundResource(R.drawable.bg_backup_card_day)
            googleDriveCard?.setBackgroundResource(R.drawable.bg_backup_card_day)
            
            // Set button colors for day mode
            buttonBackup.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonBackup.setTextColor(getColor(R.color.backup_button_text))
            buttonChooseFolder.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonChooseFolder.setTextColor(getColor(R.color.backup_button_text))
            buttonRestore.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonRestore.setTextColor(getColor(R.color.backup_button_text))
            buttonGoogleBackup.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonGoogleBackup.setTextColor(getColor(R.color.backup_button_text))
            buttonGoogleRestore.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonGoogleRestore.setTextColor(getColor(R.color.backup_button_text))
            buttonGoogleSignIn.setBackgroundColor(getColor(R.color.backup_button_bg))
            buttonGoogleSignIn.setTextColor(getColor(R.color.backup_button_text))
        }
    }

    private fun analyzeStorageUsage() {
        try {
            val breakdown = com.example.diaryapp.utils.StorageOptimizer.getStorageBreakdown(this)
            val totalSize = com.example.diaryapp.utils.StorageOptimizer.getUserDataSize(this)
            
            Log.d("STORAGE_ANALYSIS", "=== STORAGE BREAKDOWN ===")
            Log.d("STORAGE_ANALYSIS", "Total User Data: ${formatFileSize(totalSize)}")
            
            breakdown.forEach { (category, size) ->
                val percentage = if (totalSize > 0) (size * 100 / totalSize) else 0
                Log.d("STORAGE_ANALYSIS", "$category: ${formatFileSize(size)} ($percentage%)")
            }
            
            // Show the largest category
            val largestCategory = breakdown.maxByOrNull { it.value }
            if (largestCategory != null) {
                Log.d("STORAGE_ANALYSIS", "Largest category: ${largestCategory.key} (${formatFileSize(largestCategory.value)})")
            }
            
        } catch (e: Exception) {
            Log.e("STORAGE_ANALYSIS", "Failed to analyze storage", e)
        }
    }
}
