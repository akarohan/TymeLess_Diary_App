package com.example.diaryapp

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import jp.wasabeef.richeditor.RichEditor
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import android.widget.EditText
import android.widget.Button

import android.widget.TextView
import android.widget.NumberPicker
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentValues
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.chip.Chip
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import android.text.Html
import android.text.Spannable
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.StrikethroughSpan
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import java.io.File
import com.example.diaryapp.ImageBlockAdapter
import com.example.diaryapp.AudioChipAdapter
import com.example.diaryapp.AudioItem
import android.media.MediaPlayer
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.view.animation.AlphaAnimation
import android.content.Intent
import android.util.Log

class EditEntryActivity : AppCompatActivity() {
    private lateinit var richEditor: RichEditor
    private lateinit var db: DiaryDatabase
    private var entryDate: Long = 0L
    private lateinit var titleEditText: EditText
    private lateinit var saveButton: Button
    private var entryId: Int? = null
    private var isNewEntry: Boolean = true
    private lateinit var btnGallery: ImageButton
    private lateinit var btnCamera: ImageButton
    private var imageUri: Uri? = null
    private lateinit var imagesRecyclerView: RecyclerView
    private lateinit var imageBlockAdapter: ImageBlockAdapter
    private lateinit var mainEditText: EditText
    private var isBoldActive = false
    private var isItalicActive = false
    private var isUnderlineActive = false
    private var isStrikethroughActive = false
    private var wasManuallySaved = false
    private var audioRecorder: MediaRecorder? = null
    private var isRecording = false
    private val audioPaths = mutableListOf<String>()
    private val REQUEST_RECORD_AUDIO_PERMISSION = 2001
    private val REQUEST_CAMERA_PERMISSION = 2002
    private lateinit var audioRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var audioChipAdapter: AudioChipAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingIndex: Int? = null
    private lateinit var btnMic: ImageButton
    private val imageUris = mutableListOf<Uri>()
    private val audioItems = mutableListOf<AudioItem>()
    private var currentMood = 1 // 0 = sad, 1 = happy

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val filePath = copyUriToInternalStorage(it)
            if (filePath != null) {
                imageUris.add(Uri.fromFile(java.io.File(filePath)))
                imageBlockAdapter.notifyItemInserted(imageUris.size - 1)
            } else {
                Toast.makeText(this, "Failed to copy image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && imageUri != null) {
            try {
                imageUris.add(imageUri!!)
                imageBlockAdapter.notifyItemInserted(imageUris.size - 1)
            } catch (e: Exception) {
                Toast.makeText(this, "Error adding camera image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_entry)
        
        // Apply theme and refresh edit text colors
        ThemeManager.applyTheme(this)
        ThemeManager.refreshEditTextColors(this)
        
        mainEditText = findViewById(R.id.mainEditText)

        val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
        val datePickerChip = findViewById<Chip>(R.id.datePickerChip)

        titleEditText = findViewById(R.id.titleEditText)
        saveButton = findViewById(R.id.saveButton)
        db = DiaryDatabase.getDatabase(this)

        entryDate = intent.getLongExtra("entry_date", System.currentTimeMillis())
        entryId = null

        // Load existing entries for this date (if any)
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                db.diaryEntryDao().getEntriesByDate(entryDate)
            }
            if (entries.isNotEmpty()) {
                // Load the first entry for editing, or customize as needed
                val entry = entries[0]
                titleEditText.setText(entry.title ?: "")
                val plainText = if (entry.htmlContent != null) Html.fromHtml(entry.htmlContent ?: "", Html.FROM_HTML_MODE_LEGACY) else ""
                mainEditText.setText(plainText)
                entryId = entry.id
                imageUris.clear()
                audioItems.clear()
                imageUris.addAll(entry.imagePaths.map { android.net.Uri.parse(it) })
                audioItems.addAll(entry.audioList)
                imageBlockAdapter.notifyDataSetChanged()
                audioChipAdapter.notifyDataSetChanged()
                
                // Load mood value - handle both old 11-value system and new 2-value system
                val oldMood = entry.mood
                currentMood = when {
                    oldMood <= 1 -> oldMood // Already in new 2-value system (0 or 1)
                    oldMood <= 5 -> 0 // Old system: 0-5 = sad
                    else -> 1 // Old system: 6-10 = happy
                }

            } else {
                // Initialize with sad mood for new entries
                currentMood = 0
                
                titleEditText.setText("")
                mainEditText.setText("")
                entryId = null
                imageUris.clear()
                audioItems.clear()
                imageBlockAdapter.notifyDataSetChanged()
                audioChipAdapter.notifyDataSetChanged()
            }
        }

        // Format and set initial date on button
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = entryDate
        updateDateChipText(datePickerChip, calendar)

        datePickerChip.setOnClickListener {
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH)
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            val datePickerDialog = android.app.DatePickerDialog(
                this,
                { _, y, m, d ->
                    calendar.set(y, m, d)
                    entryDate = calendar.timeInMillis
                    updateDateChipText(datePickerChip, calendar)
                }, year, month, day)
            datePickerDialog.show()
        }

        wasManuallySaved = false // Reset when activity starts or loads a new entry
        saveButton.setOnClickListener {
            wasManuallySaved = true
            saveEntry(autoSave = false)
        }

        btnGallery = findViewById(R.id.btnGallery)
        btnCamera = findViewById(R.id.btnCamera)

        imagesRecyclerView = findViewById(R.id.imagesRecyclerView)
        imageBlockAdapter = ImageBlockAdapter(imageUris) { position ->
            android.util.Log.d("EditEntryActivity", "Delete callback triggered for position: $position")
            android.util.Log.d("EditEntryActivity", "Current imageUris size: ${imageUris.size}")
            if (position >= 0 && position < imageUris.size) {
                val removedUri = imageUris[position]
                android.util.Log.d("EditEntryActivity", "Removing URI: $removedUri")
                imageUris.removeAt(position)
                imageBlockAdapter.notifyItemRemoved(position)
                android.util.Log.d("EditEntryActivity", "Successfully removed image at position $position")
            } else {
                android.util.Log.e("EditEntryActivity", "Invalid position for deletion: $position, list size: ${imageUris.size}")
            }
        }
        imagesRecyclerView.layoutManager = GridLayoutManager(this, 2)
        imagesRecyclerView.adapter = imageBlockAdapter

        btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        btnCamera.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            } else {
                val photoUri = createImageUri()
                if (photoUri != null) {
                    imageUri = photoUri
                    cameraLauncher.launch(photoUri)
                } else {
                    Toast.makeText(this, "Failed to create image file. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val cardView = findViewById<androidx.cardview.widget.CardView>(R.id.cardView)
        cardView.setOnClickListener {
            mainEditText.requestFocus()
            // Force cursor to the start by resetting the HTML content
            val currentHtml = mainEditText.text.toString()
            mainEditText.setText("") // Clear first to reset cursor
            mainEditText.setText(currentHtml)
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(mainEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        setupFormatButtons()

        val btnUnderline = findViewById<TextView>(R.id.btnUnderline)
        btnUnderline.paintFlags = btnUnderline.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        val btnStrikethrough = findViewById<TextView>(R.id.btnStrikethrough)
        btnStrikethrough.paintFlags = btnStrikethrough.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

        btnMic = findViewById(R.id.btnMic)
        btnMic.setColorFilter(ContextCompat.getColor(this, R.color.black)) // Set initial color
        
        btnMic.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            } else {
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            }
        }

        audioRecyclerView = findViewById(R.id.audioRecyclerView)
        audioChipAdapter = AudioChipAdapter(audioItems,
            onPlayPause = { item, position -> handlePlayPause(item, position) },
            onDelete = { item, position -> handleDeleteAudio(item, position) }
        )
        audioRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        audioRecyclerView.adapter = audioChipAdapter



        // Set up back button
        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        // Refresh edit text colors in case theme changed while activity was in background
        ThemeManager.refreshEditTextColors(this)
    }

    override fun onPause() {
        super.onPause()
        if (!wasManuallySaved && shouldSaveEntry()) {
            saveEntry(autoSave = true)
        }
        wasManuallySaved = false // Reset for next time
    }

    private fun saveEntry(autoSave: Boolean = false) {
        val title = titleEditText.text.toString().trim()
        val content = mainEditText.text.toString().trim()
        if (title.isEmpty() && content.isEmpty()) {
            // Do not save empty entries
            return
        }
        val htmlContent = Html.toHtml(mainEditText.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        val imagePaths = imageUris.map { uri ->
            if (uri.scheme == "file") uri.path!! else uri.toString()
        }
        // currentMood is already defined in the mood toggle setup
        
        val entry = if (entryId != null) {
            DiaryEntry(
                id = entryId!!,
                date = entryDate,
                htmlContent = htmlContent,
                imagePaths = imagePaths,
                title = title,
                audioList = audioItems.toList(),
                mood = currentMood
            )
        } else {
            // Allow auto-save to create a new entry if not empty
            DiaryEntry(
                date = entryDate,
                htmlContent = htmlContent,
                imagePaths = imagePaths,
                title = title,
                audioList = audioItems.toList(),
                mood = currentMood
            )
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.diaryEntryDao().insertOrUpdate(entry)
            }
            if (entryId == null) {
                // If this was a new entry, fetch its ID after insert
                val entries = withContext(Dispatchers.IO) {
                    db.diaryEntryDao().getEntriesByDate(entryDate)
                }
                if (entries.isNotEmpty()) {
                    entryId = entries[0].id
                }
            }
            if (!autoSave) {
                android.widget.Toast.makeText(this@EditEntryActivity, "Entry saved", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun updateDateChipText(chip: Chip, calendar: java.util.Calendar) {
        val format = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
        chip.text = format.format(calendar.time)
    }



    private fun createImageUri(): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "new_image_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun copyUriToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return null
            }
            
            val file = java.io.File(filesDir, "image_${System.currentTimeMillis()}.jpg")
            val outputStream = java.io.FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun setupFormatButtons() {
        val btnBold = findViewById<TextView>(R.id.btnBold)
        val btnItalic = findViewById<TextView>(R.id.btnItalic)
        val btnUnderline = findViewById<TextView>(R.id.btnUnderline)
        val btnStrikethrough = findViewById<TextView>(R.id.btnStrikethrough)

        btnBold.setOnClickListener {
            val start = mainEditText.selectionStart
            val end = mainEditText.selectionEnd
            if (start == end) {
                isBoldActive = !isBoldActive
                updateButtonStyle(btnBold, isBoldActive)
            } else {
                toggleStyle(Typeface.BOLD, btnBold)
            }
        }
        btnItalic.setOnClickListener {
            val start = mainEditText.selectionStart
            val end = mainEditText.selectionEnd
            if (start == end) {
                isItalicActive = !isItalicActive
                updateButtonStyle(btnItalic, isItalicActive)
            } else {
                toggleStyle(Typeface.ITALIC, btnItalic)
            }
        }
        btnUnderline.setOnClickListener {
            val start = mainEditText.selectionStart
            val end = mainEditText.selectionEnd
            if (start == end) {
                isUnderlineActive = !isUnderlineActive
                updateButtonStyle(btnUnderline, isUnderlineActive)
            } else {
                toggleUnderline(btnUnderline)
            }
        }
        btnStrikethrough.setOnClickListener {
            val start = mainEditText.selectionStart
            val end = mainEditText.selectionEnd
            if (start == end) {
                isStrikethroughActive = !isStrikethroughActive
                updateButtonStyle(btnStrikethrough, isStrikethroughActive)
            } else {
                toggleStrikethrough(btnStrikethrough)
            }
        }

        // Add TextWatcher for formatting mode
        mainEditText.addTextChangedListener(object : android.text.TextWatcher {
            private var lastStart = 0
            private var lastCount = 0
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                lastStart = start
                lastCount = after
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (lastCount > 0 && s != null) {
                    val end = lastStart + lastCount
                    if (isBoldActive) {
                        s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), lastStart, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (isItalicActive) {
                        s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), lastStart, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (isUnderlineActive) {
                        s.setSpan(android.text.style.UnderlineSpan(), lastStart, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (isStrikethroughActive) {
                        s.setSpan(android.text.style.StrikethroughSpan(), lastStart, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        })
    }

    private fun toggleStyle(style: Int, button: TextView) {
        val start = mainEditText.selectionStart
        val end = mainEditText.selectionEnd
        if (start == end) return // No selection

        val spannable = mainEditText.text as Spannable
        val spans = spannable.getSpans(start, end, StyleSpan::class.java)
        var exists = false
        for (span in spans) {
            if (span.style == style) {
                spannable.removeSpan(span)
                exists = true
            }
        }
        if (!exists) {
            spannable.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        updateButtonStyle(button, !exists)
    }

    private fun toggleUnderline(button: TextView) {
        val start = mainEditText.selectionStart
        val end = mainEditText.selectionEnd
        if (start == end) return

        val spannable = mainEditText.text as Spannable
        val spans = spannable.getSpans(start, end, UnderlineSpan::class.java)
        var exists = false
        for (span in spans) {
            spannable.removeSpan(span)
            exists = true
        }
        if (!exists) {
            spannable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        updateButtonStyle(button, !exists)
    }

    private fun toggleStrikethrough(button: TextView) {
        val start = mainEditText.selectionStart
        val end = mainEditText.selectionEnd
        if (start == end) return

        val spannable = mainEditText.text as Spannable
        val spans = spannable.getSpans(start, end, StrikethroughSpan::class.java)
        var exists = false
        for (span in spans) {
            spannable.removeSpan(span)
            exists = true
        }
        if (!exists) {
            spannable.setSpan(StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        updateButtonStyle(button, !exists)
    }

    private fun updateButtonStyle(button: TextView, isActive: Boolean) {
        if (isActive) {
            if (button.id == R.id.btnStrikethrough) {
                button.background = ContextCompat.getDrawable(this, R.drawable.bius_last_button_active_background)
            } else {
                button.background = ContextCompat.getDrawable(this, R.drawable.bius_button_active_background)
            }
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
        } else {
            if (button.id == R.id.btnStrikethrough) {
                button.background = ContextCompat.getDrawable(this, R.drawable.bius_last_button_inactive_background)
            } else {
                button.background = ContextCompat.getDrawable(this, R.drawable.bius_button_inactive_background)
            }
            button.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun shouldSaveEntry(): Boolean {
        val title = titleEditText.text.toString().trim()
        val content = mainEditText.text.toString().trim()
        return title.isNotEmpty() || content.isNotEmpty()
    }

    private fun setMicButtonRecordingState(isRecording: Boolean) {
        if (isRecording) {
            btnMic.background.setTint(ContextCompat.getColor(this, R.color.black))
            btnMic.setImageResource(R.drawable.ic_stop_circle_red)
            btnMic.clearColorFilter() // No filter, icon is already colored
        } else {
            btnMic.background.setTint(ContextCompat.getColor(this, R.color.white))
            btnMic.setImageResource(R.drawable.ic_mic_filled)
            btnMic.setColorFilter(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun startRecording() {
        val audioFile = File(filesDir, "audio_${System.currentTimeMillis()}.3gp")
        audioRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFile.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
                setMicButtonRecordingState(true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        try {
            audioRecorder?.apply {
                stop()
                release()
            }
            audioRecorder = null
            isRecording = false
            setMicButtonRecordingState(false)
            // Save the audio file path
            val audioFile = File(filesDir, getLastAudioFileName())
            addAudioItem(audioFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLastAudioFileName(): String {
        val files = filesDir.listFiles { file -> file.name.startsWith("audio_") && file.name.endsWith(".3gp") }
        return files?.maxByOrNull { it.lastModified() }?.name ?: ""
    }

    private fun addAudioItem(filePath: String) {
        val duration = getAudioDurationFormatted(filePath)
        audioItems.add(AudioItem(filePath, duration))
        audioChipAdapter.notifyItemInserted(audioItems.size - 1)
        audioPaths.add(filePath)
    }

    private fun handlePlayPause(item: AudioItem, position: Int) {
        if (item.isPlaying) {
            stopAudioPlayback()
        } else {
            playAudio(item, position)
        }
    }

    private fun playAudio(item: AudioItem, position: Int) {
        // Check if the audio file exists
        val audioFile = File(item.filePath)
        if (!audioFile.exists()) {
            android.util.Log.e("EditEntryActivity", "Audio file does not exist: ${item.filePath}")
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            stopAudioPlayback()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(item.filePath)
                prepare()
                start()
                setOnCompletionListener {
                    audioItems[position] = item.copy(isPlaying = false)
                    audioChipAdapter.notifyItemChanged(position)
                    currentlyPlayingIndex = null
                }
            }
            audioItems[position] = item.copy(isPlaying = true)
            audioChipAdapter.notifyItemChanged(position)
            currentlyPlayingIndex = position
        } catch (e: Exception) {
            android.util.Log.e("EditEntryActivity", "Error playing audio: ${e.message}")
            Toast.makeText(this, "Error playing audio file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioPlayback() {
        currentlyPlayingIndex?.let { idx ->
            if (idx in audioItems.indices) {
                val item = audioItems[idx]
                if (item is AudioItem) {
                    audioItems[idx] = item.copy(isPlaying = false)
                    audioChipAdapter.notifyItemChanged(idx)
                }
            }
        }
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingIndex = null
    }

    private fun handleDeleteAudio(item: AudioItem, position: Int) {
        if (position >= 0 && position < audioItems.size) {
            audioItems.removeAt(position)
            audioChipAdapter.notifyItemRemoved(position)
        } else {
            android.util.Log.w("EditEntryActivity", "Attempted to remove audio at invalid position: $position")
        }
    }

    private fun getAudioDurationFormatted(filePath: String): String {
        return try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                android.util.Log.e("EditEntryActivity", "Audio file does not exist for duration check: $filePath")
                return "00:00"
            }
            
            val mp = MediaPlayer()
            mp.setDataSource(filePath)
            mp.prepare()
            val durationMs = mp.duration
            mp.release()
            val minutes = (durationMs / 1000 / 60)
            val seconds = ((durationMs / 1000) % 60)
            String.format("%02d:%02d", minutes, seconds)
        } catch (e: Exception) {
            android.util.Log.e("EditEntryActivity", "Error getting audio duration: ${e.message}")
            "00:00"
        }
    }











    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val photoUri = createImageUri()
                if (photoUri != null) {
                    imageUri = photoUri
                    cameraLauncher.launch(photoUri)
                } else {
                    Toast.makeText(this, "Failed to create image file. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Camera permission denied. Please enable camera access in settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getEncryptedPrefs() = androidx.security.crypto.EncryptedSharedPreferences.create(
        "diary_auth_prefs",
        androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC),
        this,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} 