package com.example.diaryapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile
import javax.crypto.AEADBadTagException
import android.os.Environment
import android.util.Log
import android.widget.Toast
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile

class PostAuthRouterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI, just routing
        val prefs = getEncryptedPrefs()
        val userKey = prefs?.getString("user_key", null)
        val username = prefs?.getString("username", null)
        Log.d("POST_AUTH", "userKey: $userKey, username: $username")
        var foundBackupUri: Uri? = null
        val backupUriString = prefs?.getString("backup_folder_uri", null)
        if (backupUriString == null && !pendingAfterFolderPick) {
            // Prompt user to pick a backup folder using a dialog to prevent window leak
            AlertDialog.Builder(this)
                .setTitle("Setup Backup Folder")
                .setMessage("To ensure your data is safe, please choose a folder for your backups.")
                .setPositiveButton("Choose Folder") { dialog, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    startActivityForResult(intent, PICK_BACKUP_FOLDER_REQUEST_CODE)
                    pendingAfterFolderPick = true
                    dialog.dismiss()
                }
                .setNegativeButton("Later") { dialog, _ ->
                    dialog.dismiss()
                    // No backup folder, proceed to main activity
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .setCancelable(false)
                .show()
            return
        }
        if (backupUriString != null) {
            val folderUri = Uri.parse(backupUriString)
            val folderDoc = DocumentFile.fromTreeUri(this, folderUri)
            if (folderDoc != null && folderDoc.exists() && folderDoc.isDirectory) {
                val files = folderDoc.listFiles()
                val zipFiles = files.filter { it.name?.endsWith(".zip") == true }
                Log.d("POST_AUTH", "Backup folder exists: ${folderDoc.uri}, zip files: ${zipFiles.size}")
                for (file in zipFiles) {
                    if (file.name?.endsWith(".zip") == true) {
                        try {
                            val zipInput = contentResolver.openInputStream(file.uri)
                            if (zipInput != null) {
                                java.util.zip.ZipInputStream(zipInput).use { zipStream ->
                                    var entry = zipStream.nextEntry
                                    while (entry != null) {
                                        if (entry.name == "notes.json") {
                                            val notesJson = zipStream.bufferedReader().use { it.readText() }
                                            val obj = org.json.JSONObject(notesJson)
                                            val backupUserKey = obj.optString("user_key", null)
                                            val backupUsername = obj.optString("username", null)
                                            Log.d("POST_AUTH", "Checking backup: $backupUserKey, $backupUsername")
                                            if ((backupUsername == username) || (userKey != null && backupUserKey == userKey)) {
                                                foundBackupUri = file.uri
                                                Log.d("POST_AUTH", "Matching backup found!")
                                                break
                                            }
                                        }
                                        entry = zipStream.nextEntry
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("POST_AUTH", "Error reading zip: ${e.message}")
                        }
                    }
                    if (foundBackupUri != null) break
                }
            }
        } else {
            Log.d("POST_AUTH", "No backup folder set in preferences")
        }
        if (foundBackupUri != null) {
            // Check if user has already been prompted for restore
            val hasBeenPromptedForRestore = prefs?.getBoolean("has_been_prompted_for_restore", false) ?: false
            
            if (!hasBeenPromptedForRestore) {
                Log.d("POST_AUTH", "Launching RestoreDataActivity with backup: $foundBackupUri")
                val intent = Intent(this, RestoreDataActivity::class.java)
                intent.putExtra("backup_zip_uri", foundBackupUri.toString())
                startActivity(intent)
            } else {
                Log.d("POST_AUTH", "User already prompted for restore, going to MainActivity")
                startActivity(Intent(this, MainActivity::class.java))
            }
        } else {
            Log.d("POST_AUTH", "No matching backup found, launching MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun getEncryptedPrefs(): android.content.SharedPreferences? {
        return try {
            EncryptedSharedPreferences.create(
                "diary_auth_prefs",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: AEADBadTagException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private val PICK_BACKUP_FOLDER_REQUEST_CODE = 2002
    private var pendingAfterFolderPick: Boolean = false

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_BACKUP_FOLDER_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                val prefs = getEncryptedPrefs()
                prefs?.edit()?.putString("backup_folder_uri", uri.toString())?.apply()
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                Toast.makeText(this, "Backup folder set!", Toast.LENGTH_SHORT).show()
                // Now re-run the backup check
                recreate()
            }
        }
    }
} 