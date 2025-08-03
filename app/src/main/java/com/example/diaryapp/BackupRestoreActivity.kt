package com.example.diaryapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TextView
import android.widget.Button
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

class BackupRestoreActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var textBackupComplete: TextView
    private lateinit var textRestoreComplete: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val STORAGE_PERMISSION_CODE = 1001
    private val PICK_BACKUP_FOLDER_REQUEST_CODE = 2002
    private var backupFolderUri: Uri? = null

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

        val textLastBackup = findViewById<TextView>(R.id.text_last_backup)
        val textBackupPath = findViewById<TextView>(R.id.text_backup_path)
        val switchImages = findViewById<SwitchMaterial>(R.id.switch_images)
        val switchAudio = findViewById<SwitchMaterial>(R.id.switch_audio)
        val switchNotes = findViewById<SwitchMaterial>(R.id.switch_notes)
        val switchDiaryNotes = findViewById<SwitchMaterial>(R.id.switch_diary_notes)
        val switchProfileSettings = findViewById<SwitchMaterial>(R.id.switch_profile_settings)
        val buttonBackup = findViewById<Button>(R.id.button_backup)
        progressBar = findViewById(R.id.progress_backup)
        textBackupComplete = findViewById(R.id.text_backup_complete)
        textRestoreComplete = findViewById(R.id.text_restore_complete)

        prefs = getEncryptedPrefs()
        updateLastBackupInfo()

        // TODO: Load and display last backup time and backup path
        // TODO: Set up toggles and backup logic

        buttonBackup.setOnClickListener {
            Log.d("BACKUP", "Backup button clicked")
            startBackup(switchImages.isChecked, switchAudio.isChecked, switchNotes.isChecked, switchDiaryNotes.isChecked, switchProfileSettings.isChecked)
        }

        val buttonChooseFolder = findViewById<Button>(R.id.button_choose_folder)
        // Load saved folder URI
        val savedUri = prefs.getString("backup_folder_uri", null)
        if (savedUri != null) {
            backupFolderUri = Uri.parse(savedUri)
            textBackupPath.text = "Backup Folder: $backupFolderUri"
        }
        buttonChooseFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, PICK_BACKUP_FOLDER_REQUEST_CODE)
        }

        val buttonRestore = findViewById<Button>(R.id.button_restore)
        buttonRestore.setOnClickListener {
            restoreFromBackup()
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
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        textLastBackup.text = if (lastTime > 0) sdf.format(Date(lastTime)) else "--"
        textBackupPath.text = lastPath ?: "--"
    }

    private fun getEncryptedPrefs() = EncryptedSharedPreferences.create(
        "diary_auth_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun startBackup(images: Boolean, audio: Boolean, notes: Boolean, diaryNotes: Boolean, profileSettings: Boolean) {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        textBackupComplete.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            var notesCount = 0
            var diaryCount = 0
            val backupUriString = prefs.getString("backup_folder_uri", null)
            if (backupUriString == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupRestoreActivity, "Please choose a backup folder first!", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val folderUri = Uri.parse(backupUriString)
            val folderDoc = DocumentFile.fromTreeUri(this@BackupRestoreActivity, folderUri)
            if (folderDoc == null || !folderDoc.canWrite()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupRestoreActivity, "Cannot write to selected folder!", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val backupFileName = "diary_backup.zip"
            // Delete all existing .zip backups in the folder
            folderDoc.listFiles().forEach { file ->
                if (file.name?.endsWith(".zip") == true) file.delete()
            }
            val backupDoc = folderDoc.createFile("application/zip", backupFileName)
            if (backupDoc == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupRestoreActivity, "Failed to create backup file!", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            Log.d("BACKUP", "Backup file uri: ${backupDoc.uri}")
            val filesToZip = mutableListOf<Pair<String, File>>()
            var progress = 0
            val steps = (if (notes) 1 else 0) + (if (diaryNotes) 1 else 0) + (if (images) 1 else 0) + (if (audio) 1 else 0) + (if (profileSettings) 1 else 0) + 1 // +1 for zipping
            var backupSuccess = false
            try {
                // 1. Notes
                var notesJson = "[]"
                if (notes) {
                    val db = DiaryDatabase.getDatabase(this@BackupRestoreActivity)
                    // Fetch latest notes directly from DB in IO context
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
                    // Fetch latest diary entries directly from DB in IO context
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

                // 5. Zip all to temp file
                val tempZip = File(cacheDir, "temp_backup.zip")
                zipFiles(filesToZip, tempZip)
                updateProgress(progress++, steps)

                // 6. Write temp zip to SAF folder
                contentResolver.openOutputStream(backupDoc.uri)?.use { out ->
                    FileInputStream(tempZip).use { input ->
                        input.copyTo(out)
                    }
                }
                // 7. Save backup info
                prefs.edit().putLong("last_backup_time", System.currentTimeMillis())
                    .putString("last_backup_path", backupDoc.uri.toString()).apply()
                backupSuccess = true
            } catch (e: Exception) {
                Log.e("BACKUP", "Backup failed: ${e.message}")
            }
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                textBackupComplete.visibility = View.VISIBLE
                updateLastBackupInfo()
                if (backupSuccess) {
                    Log.d("BACKUP", "Backup created at: ${backupDoc.uri}, Notes: $notesCount, Diary Entries: $diaryCount")
                } else {
                    Log.e("BACKUP", "Backup failed or file not found!")
                }
                handler.postDelayed({
                    textBackupComplete.visibility = View.GONE
                }, 5000)
            }
        }
    }

    private suspend fun updateProgress(current: Int, total: Int) {
        withContext(Dispatchers.Main) {
            progressBar.progress = (current * 100) / total
        }
    }

    private fun zipFiles(files: List<Pair<String, File>>, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            files.forEach { (entryName, file) ->
                FileInputStream(file).use { fis ->
                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }

    private fun restoreFromBackup() {
        val backupUriString = prefs.getString("backup_folder_uri", null)
        Log.d("RESTORE", "Starting restore. backupUriString=$backupUriString")
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
        val backupFile = folderDoc.listFiles().firstOrNull { it.name?.endsWith(".zip") == true }
        Log.d("RESTORE", "backupFile=$backupFile")
        if (backupFile == null) {
            Toast.makeText(this, "No backup file found!", Toast.LENGTH_LONG).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Extract zip to cache
                val tempDir = File(cacheDir, "restore_temp").apply { mkdirs() }
                Log.d("RESTORE", "Extracting zip to $tempDir")
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
                findViewById<TextView>(R.id.text_backup_path).text = "Backup Folder: $uri"
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                Toast.makeText(this, "Backup folder set!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Debug function to check current profile settings
    private fun debugProfileSettings() {
        val profilePicUri = prefs.getString("profile_pic_uri", "null")
        val coverPicUri = prefs.getString("cover_pic_uri", "null")
        val themePicUri = prefs.getString("theme_header_pic_uri", "null")

        val isNightMode = prefs.getBoolean("is_night_mode", false)
        
        Log.d("DEBUG", "Current Profile Settings:")
        Log.d("DEBUG", "Profile Pic: $profilePicUri (exists: ${profilePicUri?.let { File(it).exists() }})")
        Log.d("DEBUG", "Cover Pic: $coverPicUri (exists: ${coverPicUri?.let { File(it).exists() }})")
        Log.d("DEBUG", "Theme Pic: $themePicUri (exists: ${themePicUri?.let { File(it).exists() }})")

        Log.d("DEBUG", "Night Mode: $isNightMode")
        
        Toast.makeText(this, "Check logs for profile settings debug info", Toast.LENGTH_SHORT).show()
    }
} 