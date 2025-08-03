package com.example.diaryapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.content.SharedPreferences
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.diaryapp.DiaryDatabase
import com.example.diaryapp.data.Note
import com.google.gson.Gson
import org.json.JSONObject
import java.util.zip.ZipFile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import android.net.Uri

class RestoreDataActivity : AppCompatActivity() {
    private val gson = Gson()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restore_data)

        val backupEntries = findViewById<TextView>(R.id.backup_entries)
        val backupNotes = findViewById<TextView>(R.id.backup_notes)
        val backupOthers = findViewById<TextView>(R.id.backup_others)
        val btnRestore = findViewById<Button>(R.id.btn_restore)
        val btnSkip = findViewById<Button>(R.id.btn_skip)

        val backupUriString = intent.getStringExtra("backup_zip_uri")
        if (backupUriString != null) {
            val backupUri = Uri.parse(backupUriString)
            // Show backup summary
            var dateStr = "--"
            var notesCount = 0
            var diaryCount = 0
            try {
                contentResolver.openInputStream(backupUri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zipStream ->
                        var entry = zipStream.nextEntry
                        while (entry != null) {
                            if (entry.name == "notes.json") {
                                val notesJson = zipStream.bufferedReader().readText()
                                val obj = org.json.JSONObject(notesJson)
                                val notesArr = obj.optJSONArray("notes")
                                notesCount = notesArr?.length() ?: 0
                            } else if (entry.name == "diary_entries.json") {
                                val diaryJson = zipStream.bufferedReader().readText()
                                val arr = gson.fromJson(diaryJson, Array<com.example.diaryapp.DiaryEntry>::class.java)
                                diaryCount = arr?.size ?: 0
                            }
                            entry = zipStream.nextEntry
                        }
                    }
                }
            } catch (_: Exception) {}
            backupEntries.text = "Diary Entries: $diaryCount"
            backupNotes.text = "Notes: $notesCount"
            backupOthers.text = "Profile Settings: Theme Color, Profile Pic, Cover Pic, Theme Pic"
        }

        btnRestore.setOnClickListener {
            btnRestore.isClickable = false
            btnRestore.isFocusable = false
            val backupUriString = intent.getStringExtra("backup_zip_uri")
            if (backupUriString != null) {
                val backupUri = Uri.parse(backupUriString)
                CoroutineScope(Dispatchers.IO).launch {
                    var success = false
                    var notesRestored = 0
                    var diaryRestored = 0
                    try {
                        val db = DiaryDatabase.getDatabase(this@RestoreDataActivity)
                        contentResolver.openInputStream(backupUri)?.use { input ->
                            java.util.zip.ZipInputStream(input).use { zipStream ->
                                var entry = zipStream.nextEntry
                                while (entry != null) {
                                    if (entry.name == "notes.json") {
                                        val notesJson = zipStream.bufferedReader().readText()
                                        val obj = org.json.JSONObject(notesJson)
                                        val notesArr = obj.optJSONArray("notes")
                                        if (notesArr != null) {
                                            notesRestored = notesArr.length()
                                            for (i in 0 until notesArr.length()) {
                                                val noteObj = notesArr.getJSONObject(i)
                                                val note = gson.fromJson(noteObj.toString(), com.example.diaryapp.data.Note::class.java)
                                                db.noteDao().insertOrReplace(note)
                                            }
                                        }
                                    } else if (entry.name == "diary_entries.json") {
                                        val diaryJson = zipStream.bufferedReader().readText()
                                        val arr = gson.fromJson(diaryJson, Array<com.example.diaryapp.DiaryEntry>::class.java)
                                        if (arr != null) {
                                            diaryRestored = arr.size
                                            for (entryObj in arr) {
                                                db.diaryEntryDao().insertOrUpdate(entryObj)
                                            }
                                        }
                                    } else if (entry.name.startsWith("images/")) {
                                        // Extract image files
                                        val imageName = entry.name.substringAfter("images/")
                                        val imageFile = File(filesDir, imageName)
                                        try {
                                            FileOutputStream(imageFile).use { output ->
                                                zipStream.copyTo(output)
                                            }
                                            Log.d("RESTORE", "Extracted image: $imageName")
                                        } catch (e: Exception) {
                                            Log.e("RESTORE", "Failed to extract image $imageName: ${e.message}")
                                        }
                                    } else if (entry.name.startsWith("audio/")) {
                                        // Extract audio files
                                        val audioName = entry.name.substringAfter("audio/")
                                        val audioFile = File(filesDir, audioName)
                                        try {
                                            FileOutputStream(audioFile).use { output ->
                                                zipStream.copyTo(output)
                                            }
                                            Log.d("RESTORE", "Extracted audio: $audioName")
                                        } catch (e: Exception) {
                                            Log.e("RESTORE", "Failed to extract audio $audioName: ${e.message}")
                                        }
                                    } else if (entry.name in listOf("profile_pic.jpg", "cover_pic.jpg", "theme_pic.jpg")) {
                                        // Extract profile/cover/theme images
                                        val imageFile = File(filesDir, entry.name)
                                        try {
                                            FileOutputStream(imageFile).use { output ->
                                                zipStream.copyTo(output)
                                            }
                                            Log.d("RESTORE", "Extracted profile/theme image: ${entry.name}")
                                            
                                            // Update preferences with new file paths
                                            val prefs = getEncryptedPrefs()
                                            when (entry.name) {
                                                "profile_pic.jpg" -> {
                                                    prefs?.edit()?.putString("profile_pic_uri", imageFile.absolutePath)?.apply()
                                                    Log.d("RESTORE", "Updated profile_pic_uri to: ${imageFile.absolutePath}")
                                                }
                                                "cover_pic.jpg" -> {
                                                    prefs?.edit()?.putString("cover_pic_uri", imageFile.absolutePath)?.apply()
                                                    Log.d("RESTORE", "Updated cover_pic_uri to: ${imageFile.absolutePath}")
                                                }
                                                "theme_pic.jpg" -> {
                                                    prefs?.edit()?.putString("theme_header_pic_uri", imageFile.absolutePath)?.apply()
                                                    Log.d("RESTORE", "Updated theme_header_pic_uri to: ${imageFile.absolutePath}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("RESTORE", "Failed to extract profile/theme image ${entry.name}: ${e.message}")
                                        }
                                    } else if (entry.name == "profile_settings.json") {
                                        // Extract and restore profile settings
                                        try {
                                            val profileSettingsJson = zipStream.bufferedReader().readText()
                                            val profileSettings = gson.fromJson(profileSettingsJson, Map::class.java)
                                            val prefs = getEncryptedPrefs()
                                            
                                            prefs?.edit()?.apply {
                                                putString("name", profileSettings["name"] as? String ?: "")
                                                putString("username", profileSettings["username"] as? String ?: "")
                                                putString("user_key", profileSettings["user_key"] as? String ?: "")
                                                putString("theme_color", profileSettings["theme_color"] as? String ?: "#3F51B5")
                                                putBoolean("is_night_mode", profileSettings["is_night_mode"] as? Boolean ?: false)
                                            }?.apply()
                                            
                                            Log.d("RESTORE", "Restored profile settings: $profileSettings")
                                        } catch (e: Exception) {
                                            Log.e("RESTORE", "Failed to restore profile settings: ${e.message}")
                                        }
                                    }
                                    entry = zipStream.nextEntry
                                }
                            }
                        }
                        success = true
                        
                        // Verify restored profile files exist
                        val profilePicPath = getEncryptedPrefs()?.getString("profile_pic_uri", null)
                        val coverPicPath = getEncryptedPrefs()?.getString("cover_pic_uri", null)
                        val themePicPath = getEncryptedPrefs()?.getString("theme_header_pic_uri", null)
                        
                        Log.d("RESTORE", "File existence check after restore:")
                        Log.d("RESTORE", "Profile pic exists: ${profilePicPath?.let { File(it).exists() }}")
                        Log.d("RESTORE", "Cover pic exists: ${coverPicPath?.let { File(it).exists() }}")
                        Log.d("RESTORE", "Theme pic exists: ${themePicPath?.let { File(it).exists() }}")
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Log.d("RESTORE", "Restore complete! Notes: $notesRestored, Diary Entries: $diaryRestored")
                            // Mark that user has been prompted for restore
                            getEncryptedPrefs()?.edit()?.putBoolean("has_been_prompted_for_restore", true)?.apply()
                        } else {
                            Log.e("RESTORE", "Restore failed.")
                        }
                        goToMain()
                    }
                }
            } else {
                goToMain()
            }
        }
        btnSkip.setOnClickListener {
            btnSkip.isClickable = false
            btnSkip.isFocusable = false
            // Mark that user has been prompted for restore
            getEncryptedPrefs()?.edit()?.putBoolean("has_been_prompted_for_restore", true)?.apply()
            
            // Delete backup file if path is provided
            if (backupUriString != null) {
                try {
                    val file = java.io.File(backupUriString)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            goToMain()
        }
    }

    private fun getEncryptedPrefs(): SharedPreferences? {
        return try {
            EncryptedSharedPreferences.create(
                "diary_auth_prefs",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: AEADBadTagException) {
            showDecryptionErrorDialog()
            null
        } catch (e: Exception) {
            showDecryptionErrorDialog()
            null
        }
    }

    private fun showDecryptionErrorDialog() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Secure Data Error")
                .setMessage("Your secure preferences could not be decrypted. This can happen after reinstalling the app or restoring a backup. You may need to reset your app preferences.")
                .setPositiveButton("Reset Preferences") { _, _ ->
                    clearEncryptedPrefsAndRestart()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun clearEncryptedPrefsAndRestart() {
        try {
            val prefs = EncryptedSharedPreferences.create(
                "diary_auth_prefs",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit().clear().apply()
        } catch (_: Exception) {}
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
} 