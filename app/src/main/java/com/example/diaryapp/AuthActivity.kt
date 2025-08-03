package com.example.diaryapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import android.widget.TextView
import android.widget.VideoView
import android.net.Uri
import android.util.AttributeSet
import android.view.TextureView
import android.view.Surface
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.widget.FrameLayout
import android.view.View
import android.widget.ImageView
import android.app.AlertDialog
import javax.crypto.AEADBadTagException
import android.content.SharedPreferences
import java.io.File
import java.io.FileReader
import org.json.JSONObject

class AuthActivity : AppCompatActivity() {
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var submitButton: Button
    private lateinit var signupButton: Button
    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // Initialize videoView first to avoid lateinit issues
        videoView = findViewById<VideoView>(R.id.loginVideoView)
        
        val prefs = getEncryptedPrefs()
        if (prefs == null) return // If prefs couldn't be loaded, wait for user action
        val customBgUri = prefs.getString("login_bg_uri", null)
        val customBgIsImage = prefs.getBoolean("login_bg_is_image", false)

        val imageView = findViewById<ImageView?>(R.id.loginBgImageView)
        if (customBgUri != null) {
            if (customBgIsImage && imageView != null) {
                imageView.setImageURI(Uri.parse(customBgUri))
                imageView.visibility = View.VISIBLE
                videoView.visibility = View.GONE
            } else {
                videoView.setVideoURI(Uri.parse(customBgUri))
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                }
                videoView.start()
                videoView.visibility = View.VISIBLE
                imageView?.visibility = View.GONE
            }
        } else {
            // Use a static image instead of video to save storage
            if (imageView != null) {
                imageView.setImageResource(R.drawable.bg_login_static)
                imageView.visibility = View.VISIBLE
                videoView.visibility = View.GONE
            } else {
                // Fallback to video only if image view doesn't exist
                videoView.setVideoURI(Uri.parse("android.resource://" + packageName + "/raw/login_page_into_anime"))
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                }
                videoView.start()
                videoView.visibility = View.VISIBLE
                imageView?.visibility = View.GONE
            }
        }

        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        submitButton = findViewById(R.id.submitButton)
        signupButton = findViewById(R.id.signupButton)
        
        // Initialize logo based on current theme
        updateAuthLogo()

        val savedUsername = prefs.getString("username", null)
        val savedPassword = prefs.getString("password_hash", null)
        val savedName = prefs.getString("name", null)

        submitButton.text = "Sign In"
        signupButton.text = "Sign Up"
        signupButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }
        submitButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (username == savedUsername && hash(password) == savedPassword) {
                val intent = Intent(this, PostAuthRouterActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        videoView.stopPlayback()
    }

    override fun onStop() {
        super.onStop()
        videoView.stopPlayback()
    }

    override fun onResume() {
        super.onResume()
        videoView.start()
    }

    private fun goToMain(name: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("user_name", name)
        startActivity(intent)
        finish()
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
            deleteSharedPreferences("diary_auth_prefs")
        } catch (_: Exception) {}
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun checkForAccountBackupAndPrompt(username: String) {
        val backupDir = File(filesDir, "backups")
        if (backupDir.exists()) {
            val backupFiles = backupDir.listFiles { file -> file.name.endsWith(".zip") }
            if (!backupFiles.isNullOrEmpty()) {
                for (zipFile in backupFiles) {
                    // Look for notes.json inside the zip
                    val tempDir = File(cacheDir, "backup_check")
                    tempDir.mkdirs()
                    try {
                        java.util.zip.ZipFile(zipFile).use { zip ->
                            val entry = zip.getEntry("notes.json")
                            if (entry != null) {
                                val input = zip.getInputStream(entry)
                                val notesJson = input.bufferedReader().use { it.readText() }
                                val obj = JSONObject(notesJson)
                                val backupUserKey = obj.optString("user_key", null)
                                val backupUsername = obj.optString("username", null)
                                val prefs = getEncryptedPrefs()
                                val userKey = prefs?.getString("user_key", null)
                                if ((backupUsername == username) || (userKey != null && backupUserKey == userKey)) {
                                    // Prompt user to restore
                                    runOnUiThread {
                                        AlertDialog.Builder(this)
                                            .setTitle("Restore Data")
                                            .setMessage("A backup was found for this account. Do you want to restore your previous notes and images?")
                                            .setPositiveButton("Restore") { _, _ ->
                                                val intent = Intent(this, RestoreDataActivity::class.java)
                                                intent.putExtra("backup_zip_path", zipFile.absolutePath)
                                                startActivity(intent)
                                                finish()
                                            }
                                            .setNegativeButton("Skip") { _, _ ->
                                                val intent = Intent(this, RestoreDataActivity::class.java)
                                                intent.putExtra("backup_zip_path", zipFile.absolutePath)
                                                intent.putExtra("skip_restore", true)
                                                startActivity(intent)
                                                finish()
                                            }
                                            .show()
                                    }
                                    return
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        // If no matching backup, proceed to main
        goToMain(username)
    }
    
    private fun updateAuthLogo() {
        val timelessLogo = findViewById<ImageView>(R.id.timelessLogo)
        if (timelessLogo != null) {
            // Use the new main logo
            val logoDrawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.tymeless_main_logo)
            timelessLogo.setImageDrawable(logoDrawable)
        }
    }
} 