package com.example.diaryapp.ui.home

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.diaryapp.*
import com.example.diaryapp.data.Note
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import com.example.diaryapp.R
import java.text.SimpleDateFormat
import android.graphics.Rect

class EditNoteActivity : AppCompatActivity() {
    private lateinit var titleEditText: EditText
    private lateinit var mainEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var btnGallery: ImageButton
    private lateinit var btnCamera: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var btnCheckbox: ImageButton
    private lateinit var imagesRecyclerView: RecyclerView
    private lateinit var imageBlockAdapter: ImageBlockAdapter
    private lateinit var audioRecyclerView: RecyclerView
    private lateinit var audioChipAdapter: AudioChipAdapter
    private lateinit var checklistRecyclerView: RecyclerView
    private lateinit var checklistAdapter: ChecklistAdapter
    private lateinit var toolbarTitle: TextView
    private lateinit var datePickerChip: Chip
    private lateinit var viewModel: NotesHomeViewModel
    private var imageUris = mutableListOf<Uri>()
    private var audioItems = mutableListOf<AudioItem>()
    private var checklistItems = mutableListOf<ChecklistItem>()
    private var audioRecorder: MediaRecorder? = null
    private var isRecording = false
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingIndex: Int? = null
    private var imageUri: Uri? = null
    private var noteDate: Long = System.currentTimeMillis()
    private val REQUEST_RECORD_AUDIO_PERMISSION = 2001
    private val REQUEST_CAMERA_PERMISSION = 2002
    private var isBoldActive = false
    private var isItalicActive = false
    private var isUnderlineActive = false
    private var isStrikethroughActive = false
    private var noteId: Int = 0
    private var noteType: String = "N"
    private var isSaving = false
    private var isImageAdding = false
    private var thumbnailUri: Uri? = null

    // Thumbnail section UI elements
    private lateinit var thumbnailCardView: androidx.cardview.widget.CardView
    private lateinit var thumbnailImageView: ImageView
    private lateinit var thumbnailPlaceholder: LinearLayout
    private lateinit var btnAddThumbnail: Button
    private lateinit var btnRemoveThumbnail: Button

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val filePath = copyUriToInternalStorage(it)
            if (filePath != null) {
                imageUris.add(Uri.fromFile(File(filePath)))
                imageBlockAdapter.notifyItemInserted(imageUris.size - 1)
                autoSaveNote()
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && imageUri != null) {
            val filePath = copyUriToInternalStorage(imageUri!!)
            if (filePath != null) {
                imageUris.add(Uri.fromFile(File(filePath)))
                imageBlockAdapter.notifyItemInserted(imageUris.size - 1)
                autoSaveNote()
            }
        }
    }

    private val thumbnailLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Log.d("EditNoteActivity", "Thumbnail selected: $uri")
            val filePath = copyUriToInternalStorage(it)
            if (filePath != null) {
                thumbnailUri = Uri.fromFile(File(filePath))
                Log.d("EditNoteActivity", "Thumbnail saved to: $filePath")
                updateThumbnailDisplay()
                autoSaveNote()
            } else {
                Log.e("EditNoteActivity", "Failed to copy thumbnail to internal storage")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_note)
        
        // Apply theme
        isSaving = false // Reset save flag on activity start
        
        // Initialize database
        viewModel = ViewModelProvider(this)[NotesHomeViewModel::class.java]
        
        // Initialize views
        titleEditText = findViewById(R.id.titleEditText)
        mainEditText = findViewById(R.id.mainEditText)
        saveButton = findViewById(R.id.saveButton)
        btnGallery = findViewById(R.id.btnGallery)
        btnCamera = findViewById(R.id.btnCamera)
        btnMic = findViewById(R.id.btnMic)
        btnCheckbox = findViewById(R.id.btnCheckbox)
        imagesRecyclerView = findViewById(R.id.imagesRecyclerView)
        audioRecyclerView = findViewById(R.id.audioRecyclerView)
        checklistRecyclerView = findViewById(R.id.checklistRecyclerView)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        datePickerChip = findViewById(R.id.datePickerChip)
        
        // Initialize thumbnail section
        thumbnailCardView = findViewById(R.id.thumbnailCardView)
        thumbnailImageView = findViewById(R.id.thumbnailImageView)
        thumbnailPlaceholder = findViewById(R.id.thumbnailPlaceholder)
        btnAddThumbnail = findViewById(R.id.btnAddThumbnail)
        btnRemoveThumbnail = findViewById(R.id.btnRemoveThumbnail)
        
        // Set up thumbnail section
        setupThumbnailSection()
        
        // Set up keyboard handling
        setupKeyboardHandling()
        
        // Get note ID from intent
        noteId = intent.getIntExtra("note_id", 0)
        
        // Get note type from intent (for new notes)
        val intentNoteType = intent.getStringExtra("note_type")
        if (intentNoteType != null) {
            noteType = intentNoteType
            Log.d("EditNoteActivity", "Note type set from intent: $noteType")
            // updateNoteTypeIndicator() // Removed as per edit hint
        }
        
        // Set up RecyclerViews
        setupRecyclerViews()
        
        // Set up buttons
        setupButtons()
        setupFormatButtons()
        
        // Set up date picker
        setupDatePicker()
        
        // Load content after all views are initialized
        if (noteId != 0) {
            // Editing existing note, load everything from database to get the latest changes
            loadContentFromDatabase()
        } else {
            // FIXED: For new notes, ensure we save immediately when checklist is added
            Log.d("CHECKLIST_DEBUG", "New note created - will save immediately when checklist is added")
        }
        
        // Checkbox button is now handled in setupButtons()
    }

    private fun setupRecyclerViews() {
        // Set up image RecyclerView
        imageBlockAdapter = ImageBlockAdapter(imageUris) { position ->
            imageUris.removeAt(position)
            imageBlockAdapter.notifyItemRemoved(position)
            autoSaveNote()
        }
        imagesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@EditNoteActivity, 3)
            adapter = imageBlockAdapter
        }
        
        // Set up audio RecyclerView with proper callback functions
        audioChipAdapter = AudioChipAdapter(
            audioItems,
            onPlayPause = { audioItem, position ->
                // Handle play/pause logic here
                handleAudioPlayPause(audioItem, position)
            },
            onDelete = { audioItem, position ->
                audioItems.removeAt(position)
                audioChipAdapter.notifyItemRemoved(position)
                autoSaveNote()
            }
        )
        audioRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@EditNoteActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = audioChipAdapter
        }
        
        // Set up checklist RecyclerView
        checklistAdapter = ChecklistAdapter(checklistItems) {
            // FIXED: Save immediately when checklist items change
            autoSaveNote()
            Log.d("CHECKLIST_DEBUG", "Checklist item changed - auto saving")
            
            // Check if all checklist items are removed and disable checklist mode
            if (checklistItems.isEmpty()) {
                Log.d("CHECKLIST_DEBUG", "All checklist items removed - disabling checklist mode")
                disableChecklistMode()
            }
        }
        checklistRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@EditNoteActivity)
            adapter = checklistAdapter
        }
        
        // Set up drag and drop for checklist
        val itemTouchHelper = ItemTouchHelper(ChecklistItemTouchHelper(checklistAdapter))
        itemTouchHelper.attachToRecyclerView(checklistRecyclerView)
        
        checklistAdapter.setOnStartDragListener(object : ChecklistAdapter.OnStartDragListener {
            override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
                itemTouchHelper.startDrag(viewHolder)
            }
        })
        
        // Don't initialize with empty item - let loadContent handle this
    }

    private fun setupThumbnailSection() {
        // Show thumbnail section only for A notes
        if (noteType == "A") {
            thumbnailCardView.visibility = View.VISIBLE
            updateThumbnailDisplay()
        } else {
            thumbnailCardView.visibility = View.GONE
        }
        
        // Set up thumbnail buttons
        btnAddThumbnail.setOnClickListener {
            thumbnailLauncher.launch("image/*")
        }
        
        btnRemoveThumbnail.setOnClickListener {
            thumbnailUri = null
            updateThumbnailDisplay()
            autoSaveNote()
        }
        
        // Make the thumbnail area clickable
        thumbnailImageView.setOnClickListener {
            thumbnailLauncher.launch("image/*")
        }
        
        thumbnailPlaceholder.setOnClickListener {
            thumbnailLauncher.launch("image/*")
        }
    }

    private fun setupKeyboardHandling() {
        // Get the root view for keyboard detection
        val rootView = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootConstraintLayout)
        
        // Set up focus change listeners to scroll to focused view and hide thumbnail
        titleEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Hide thumbnail when title is focused
                thumbnailCardView.visibility = View.GONE
                titleEditText.post {
                    findViewById<ScrollView>(R.id.editorScrollView).smoothScrollTo(0, titleEditText.top)
                }
            }
        }
        
        mainEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Hide thumbnail when main content is focused
                thumbnailCardView.visibility = View.GONE
                mainEditText.post {
                    findViewById<ScrollView>(R.id.editorScrollView).smoothScrollTo(0, mainEditText.top - 100)
                }
            }
        }
        
        // Add global layout listener to detect keyboard visibility
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom
            
            if (keypadHeight > screenHeight * 0.15) {
                // Keyboard is visible - hide thumbnail and ensure bottom bar is accessible
                thumbnailCardView.visibility = View.GONE
                
                // Ensure the bottom bar is visible above keyboard
                val bottomBarContainer = findViewById<LinearLayout>(R.id.bottomBarContainer)
                bottomBarContainer.post {
                    bottomBarContainer.bringToFront()
                }
                
                // Adjust scroll to ensure content is visible
                val scrollView = findViewById<ScrollView>(R.id.editorScrollView)
                if (mainEditText.hasFocus()) {
                    scrollView.post {
                        scrollView.smoothScrollTo(0, mainEditText.top - 200)
                    }
                }
            } else {
                // Keyboard is hidden - show thumbnail for A notes
                if (noteType == "A") {
                    thumbnailCardView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateThumbnailDisplay() {
        Log.d("EditNoteActivity", "updateThumbnailDisplay called, thumbnailUri: $thumbnailUri")
        
        if (thumbnailUri != null) {
            try {
                Log.d("EditNoteActivity", "Loading thumbnail: $thumbnailUri")
                
                // Handle file-based URIs (from internal storage)
                if (thumbnailUri!!.scheme == "file") {
                    val file = File(thumbnailUri!!.path ?: "")
                    Log.d("EditNoteActivity", "Loading from file: ${file.absolutePath}")
                    Log.d("EditNoteActivity", "File exists: ${file.exists()}")
                    
                    if (file.exists()) {
                        val inputStream = file.inputStream()
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        
                        if (bitmap != null) {
                            thumbnailImageView.setImageBitmap(bitmap)
                            Log.d("EditNoteActivity", "Thumbnail loaded successfully from file")
                        } else {
                            Log.e("EditNoteActivity", "Failed to decode bitmap from file")
                            thumbnailImageView.setImageURI(thumbnailUri)
                        }
                    } else {
                        Log.e("EditNoteActivity", "Thumbnail file does not exist")
                        thumbnailImageView.setImageURI(thumbnailUri)
                    }
                } else {
                    // Handle content URIs (from gallery)
                    val inputStream = contentResolver.openInputStream(thumbnailUri!!)
                    if (inputStream != null) {
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        
                        if (bitmap != null) {
                            thumbnailImageView.setImageBitmap(bitmap)
                            Log.d("EditNoteActivity", "Thumbnail loaded successfully from content URI")
                        } else {
                            Log.e("EditNoteActivity", "Failed to decode bitmap from content URI")
                            thumbnailImageView.setImageURI(thumbnailUri)
                        }
                    } else {
                        Log.e("EditNoteActivity", "Failed to open input stream for content URI")
                        thumbnailImageView.setImageURI(thumbnailUri)
                    }
                }
                
                thumbnailPlaceholder.visibility = View.GONE
                btnRemoveThumbnail.visibility = View.VISIBLE
                btnAddThumbnail.text = "Change Thumbnail"
                btnAddThumbnail.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
                
            } catch (e: Exception) {
                Log.e("EditNoteActivity", "Error loading thumbnail", e)
                thumbnailPlaceholder.visibility = View.VISIBLE
                btnRemoveThumbnail.visibility = View.GONE
                btnAddThumbnail.text = "Add Thumbnail"
                btnAddThumbnail.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            }
        } else {
            Log.d("EditNoteActivity", "No thumbnail URI, showing placeholder")
            thumbnailImageView.setImageResource(R.drawable.ic_image_placeholder)
            thumbnailPlaceholder.visibility = View.VISIBLE
            btnRemoveThumbnail.visibility = View.GONE
            btnAddThumbnail.text = "Add Thumbnail"
            btnAddThumbnail.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        }
    }

    private fun handleAudioPlayPause(audioItem: AudioItem, position: Int) {
        // Implementation for audio play/pause functionality
        // This is a placeholder - you can implement the actual audio playback logic here
        Log.d("EditNoteActivity", "Play/pause audio at position: $position")
    }

    private fun setupButtons() {
        // Back button
        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
        
        // Gallery button
        btnGallery.setOnClickListener {
            if (checkStoragePermission()) {
                galleryLauncher.launch("image/*")
            }
        }
        
        // Camera button
        btnCamera.setOnClickListener {
            if (checkCameraPermission()) {
                takePhoto()
            }
        }
        
        // Microphone button
        btnMic.setOnClickListener {
            if (checkAudioPermission()) {
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            }
        }
        
        // Checkbox button - toggle checklist mode
        btnCheckbox.setOnClickListener {
            toggleChecklistMode()
        }
        
        // Save button
        saveButton.setOnClickListener {
            isSaving = false // Reset flag so manual save always runs
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                autoSaveNote()
                setResult(Activity.RESULT_OK)
                finish()
            }, 300)
        }
    }

    private fun setupDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = noteDate
        updateDateChipText(datePickerChip, calendar)
        
        datePickerChip.setOnClickListener {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val datePickerDialog = DatePickerDialog(
                this,
                { _, y, m, d ->
                    calendar.set(y, m, d)
                    noteDate = calendar.timeInMillis
                    updateDateChipText(datePickerChip, calendar)
                }, year, month, day)
            datePickerDialog.show()
        }
    }

    private fun toggleChecklistMode() {
        if (checklistRecyclerView.visibility == View.VISIBLE) {
            // Checklist mode is active, add a new item
            if (checklistItems.isEmpty()) {
                // For new notes, create initial note first
                if (noteId == 0) {
                    val initialNote = Note(
                        id = 0,
                        title = "Untitled",
                        content = "CHECKLIST:",
                        imagePaths = emptyList(),
                        audioList = emptyList(),
                        thumbnailPath = null,
                        noteType = noteType
                    )
                    
                    lifecycleScope.launch {
                        try {
                            val newId = viewModel.insertOrReplace(initialNote)
                            noteId = newId.toInt()
                            Log.d("CHECKLIST_DEBUG", "Initial note created with ID: $noteId")
                            
                            // Now add the first checklist item
                            checklistAdapter.addItem()
                            
                            // Force save after a delay to ensure everything is saved
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                forceSaveNote()
                            }, 300)
                        } catch (e: Exception) {
                            Log.e("EditNoteActivity", "Error creating initial note", e)
                        }
                    }
                } else {
                    // For existing notes, just add an item if empty
                    checklistAdapter.addItem()
                }
            } else {
                // Add another item to existing checklist
                checklistAdapter.addItem()
            }
        } else {
            // Enable checklist mode
            checklistRecyclerView.visibility = View.VISIBLE
            mainEditText.visibility = View.VISIBLE
            btnCheckbox.setImageResource(R.drawable.ic_checkbox)
            
            // Add first item if checklist is empty
            if (checklistItems.isEmpty()) {
                checklistAdapter.addItem()
            }
        }
    }

    private fun disableChecklistMode() {
        // Hide checklist mode
        checklistRecyclerView.visibility = View.GONE
        mainEditText.visibility = View.VISIBLE
        btnCheckbox.setImageResource(R.drawable.ic_checkbox)
        
        // Clear checklist items
        checklistItems.clear()
        checklistAdapter.notifyDataSetChanged()
        
        // Force save to remove checklist content from database
        forceSaveNote()
        
        Log.d("CHECKLIST_DEBUG", "Checklist mode disabled")
    }
    
    private fun addChecklistItem() {
        // Show checklist RecyclerView but keep main EditText visible
        checklistRecyclerView.visibility = View.VISIBLE
        mainEditText.visibility = View.VISIBLE
        
        // Add new checklist item at the bottom
        checklistAdapter.addItem()
        
        // Scroll to the bottom to show the new item
        checklistRecyclerView.post {
            checklistRecyclerView.smoothScrollToPosition(checklistItems.size - 1)
        }
        
        autoSaveNote()
    }
    


    private fun loadContentFromDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val note = viewModel.getNoteById(noteId)
                withContext(Dispatchers.Main) {
                    note?.let { 
                        // Set note type from loaded note
                        noteType = it.noteType
                        Log.d("EditNoteActivity", "Note type loaded from database: $noteType")
                        
                        // updateNoteTypeIndicator() // Removed as per edit hint
                        
                        // Load thumbnail if exists
                        if (it.thumbnailPath != null) {
                            try {
                                Log.d("EditNoteActivity", "Attempting to load thumbnail: ${it.thumbnailPath}")
                                val thumbnailFile = File(filesDir, it.thumbnailPath)
                                Log.d("EditNoteActivity", "Thumbnail file path: ${thumbnailFile.absolutePath}")
                                Log.d("EditNoteActivity", "Thumbnail file exists: ${thumbnailFile.exists()}")
                                
                                if (thumbnailFile.exists()) {
                                    thumbnailUri = Uri.fromFile(thumbnailFile)
                                    Log.d("EditNoteActivity", "Thumbnail URI set: $thumbnailUri")
                                } else {
                                    Log.e("EditNoteActivity", "Thumbnail file does not exist: ${thumbnailFile.absolutePath}")
                                }
                            } catch (e: Exception) {
                                Log.e("EditNoteActivity", "Error loading thumbnail", e)
                            }
                        } else {
                            Log.d("EditNoteActivity", "No thumbnail path found in note")
                        }
                        
                        // Update thumbnail section visibility and display
                        if (noteType == "A") {
                            thumbnailCardView.visibility = View.VISIBLE
                            Log.d("EditNoteActivity", "Showing thumbnail section for A note")
                            updateThumbnailDisplay()
                        } else {
                            thumbnailCardView.visibility = View.GONE
                            Log.d("EditNoteActivity", "Hiding thumbnail section for non-A note")
                        }
                        
                        titleEditText.setText(it.title)
                        loadContent(it.content)
                    }
                }
            } catch (e: Exception) {
                Log.e("EditNoteActivity", "Error loading note from database", e)
            }
        }
    }

    private fun loadContent(content: String) {
        Log.d("CHECKLIST_DEBUG", "=== LOADING ===")
        Log.d("CHECKLIST_DEBUG", "Raw content: $content")
        
        try {
            // Clean up any old format data first
            val cleanedContent = cleanOldFormatData(content)
            Log.d("CHECKLIST_DEBUG", "Cleaned content: $cleanedContent")
            
            // Check if content contains checklist items
            if (cleanedContent.contains("CHECKLIST:")) {
                Log.d("CHECKLIST_DEBUG", "Found checklist content")
                
                // Parse checklist content to extract checklist items
                checklistItems.clear()
                
                // Extract checklist items from JSON format with error handling
                val checklistMatch = Regex("CHECKLIST:(.+)").find(cleanedContent)
                if (checklistMatch != null) {
                    val checklistData = checklistMatch.groupValues[1]
                    Log.d("CHECKLIST_DEBUG", "Checklist data: '$checklistData'")
                    
                    if (checklistData.isNotEmpty()) {
                        val items = checklistData.split("|")
                        Log.d("CHECKLIST_DEBUG", "Found ${items.size} checklist items to load")
                        
                        for (item in items) {
                            try {
                                if (item.isNotEmpty()) {
                                    val parts = item.split(":", limit = 2)
                                    if (parts.size == 2) {
                                        val text = parts[0].replace("\\:", ":").replace("\\|", "|")
                                        val isChecked = parts[1] == "1"
                                        
                                        // FIXED: Load all items, including empty ones
                                        checklistItems.add(ChecklistItem(text = text.trim(), isChecked = isChecked))
                                        Log.d("CHECKLIST_DEBUG", "Loaded checklist item: '$text', checked: $isChecked")
                                    } else {
                                        Log.w("CHECKLIST_DEBUG", "Invalid checklist item format: $item")
                                    }
                                } else {
                                    // Handle completely empty items
                                    checklistItems.add(ChecklistItem(text = "", isChecked = false))
                                    Log.d("CHECKLIST_DEBUG", "Loaded empty checklist item")
                                }
                            } catch (e: Exception) {
                                Log.e("CHECKLIST_DEBUG", "Error parsing checklist item: $item", e)
                            }
                        }
                    }
                }
                
                Log.d("CHECKLIST_DEBUG", "Total checklist items loaded: ${checklistItems.size}")
                
                // Always show checklist mode if CHECKLIST: is found, even if no items
                checklistRecyclerView.visibility = View.VISIBLE
                mainEditText.visibility = View.VISIBLE
                btnCheckbox.setImageResource(R.drawable.ic_checkbox)
                
                // Load remaining content as regular text (remove checklist parts and clean up)
                val textContent = cleanedContent.replace(Regex("CHECKLIST:.+"), "").trim()
                // Remove any trailing newlines that might be left
                val cleanTextContent = textContent.replace(Regex("\n+$"), "").trim()
                mainEditText.setText(cleanTextContent)
                
                // Notify adapter that data has changed
                checklistAdapter.notifyDataSetChanged()
                
                // If no items were loaded but checklist mode is active, add an empty item
                if (checklistItems.isEmpty()) {
                    Log.d("CHECKLIST_DEBUG", "No checklist items found, adding empty item")
                    checklistAdapter.addItem()
                }
            } else {
                Log.d("EditNoteActivity", "No checklist content found")
                // Regular text content - hide checklist mode
                checklistRecyclerView.visibility = View.GONE
                mainEditText.visibility = View.VISIBLE
                btnCheckbox.setImageResource(R.drawable.ic_checkbox)
                mainEditText.setText(cleanedContent)
            }
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error loading content", e)
            // Fallback to regular text content
            mainEditText.setText(content)
            checklistRecyclerView.visibility = View.GONE
        }
    }
    
    private fun cleanOldFormatData(content: String): String {
        // Remove any old HTML format with checkbox symbols
        var cleanedContent = content
        
        // Remove old HTML format: <span style='margin-right: 8px;'>☐</span><span style='...'>text</span>
        val oldHtmlPattern = Regex("<span style='margin-right: 8px;'>([☐☑])</span><span style='[^']*'>([^<]*)</span>")
        cleanedContent = oldHtmlPattern.replace(cleanedContent) { matchResult ->
            val checkboxSymbol = matchResult.groupValues[1]
            val text = matchResult.groupValues[2]
            val isChecked = if (checkboxSymbol == "☑") "1" else "0"
            "CHECKLIST:$text:$isChecked"
        }
        
        // Remove any standalone checkbox symbols
        cleanedContent = cleanedContent.replace("☐", "").replace("☑", "")
        
        // Clean up any remaining HTML tags
        cleanedContent = cleanedContent.replace(Regex("<[^>]*>"), "")
        
        return cleanedContent
    }

    override fun onPause() {
        super.onPause()
        // Force save when leaving the activity to ensure checklist items are saved
        if (!isSaving && !isImageAdding) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                forceSaveNote()
            }, 100)
        }
    }
    
    // Helper function to check if note should be saved
    private fun shouldSaveNote(title: String, content: String, hasImages: Boolean, hasAudio: Boolean, hasChecklist: Boolean): Boolean {
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()
        
        // Don't save if title is empty, "Untitled", or content is empty and no media
        if (trimmedTitle.isEmpty() || trimmedTitle == "Untitled") {
            return false
        }
        
        // Don't save if content is empty and no images, audio, or checklist
        if (trimmedContent.isEmpty() && !hasImages && !hasAudio && !hasChecklist) {
            return false
        }
        
        return true
    }

    private fun forceSaveNote() {
        // Force save regardless of saving flag to ensure checklist items are saved
        try {
            val title = titleEditText.text.toString().trim()
            val content = mainEditText.text.toString()
            
            val imagePathsToSave = imageUris.map { uri -> 
                when {
                    uri.scheme == "file" -> {
                        val file = File(uri.path ?: "")
                        if (file.absolutePath.startsWith(filesDir.absolutePath)) {
                            file.name
                        } else {
                            uri.path ?: uri.toString()
                        }
                    }
                    else -> uri.toString()
                }
            }
            
            // FIXED: Handle thumbnail for A notes
            val thumbnailPathToSave = when {
                noteType == "A" && imagePathsToSave.isNotEmpty() -> {
                    // For A notes, use the first image as thumbnail
                    imagePathsToSave.first()
                }
                thumbnailUri != null -> {
                    // Use existing thumbnail
                    val file = File(thumbnailUri!!.path ?: "")
                    if (file.absolutePath.startsWith(filesDir.absolutePath)) {
                        file.name
                    } else {
                        thumbnailUri!!.path ?: thumbnailUri.toString()
                    }
                }
                else -> null
            }
            
            // FIXED: Always save all checklist items, including empty ones
            val checklistContent = if (checklistItems.isNotEmpty()) {
                val checklistJson = checklistItems.joinToString("|") { item ->
                    val text = item.text.trim()
                    val cleanText = text.replace(":", "\\:").replace("|", "\\|")
                    "$cleanText:${if (item.isChecked) "1" else "0"}"
                }
                "CHECKLIST:$checklistJson"
            } else ""
            
            Log.d("CHECKLIST_DEBUG", "=== FORCE SAVING ===")
            Log.d("CHECKLIST_DEBUG", "Checklist items count: ${checklistItems.size}")
            Log.d("CHECKLIST_DEBUG", "Checklist items: ${checklistItems.map { "'${it.text}' (${it.isChecked})" }}")
            Log.d("CHECKLIST_DEBUG", "Checklist content: $checklistContent")
            
            // FIXED: Always include checklist content if checklist mode was activated
            val finalContent = if (checklistRecyclerView.visibility == View.VISIBLE) {
                if (checklistContent.isNotEmpty()) {
                    if (content.isNotEmpty()) "$content\n\n$checklistContent" else checklistContent
                } else {
                    // Even if no items, preserve checklist mode
                    if (content.isNotEmpty()) "$content\n\nCHECKLIST:" else "CHECKLIST:"
                }
            } else {
                content
            }
            
            Log.d("CHECKLIST_DEBUG", "Final content to force save: $finalContent")
            
            // Check if note should be saved
            val hasImages = imageUris.isNotEmpty()
            val hasAudio = audioItems.isNotEmpty()
            val hasChecklist = checklistRecyclerView.visibility == View.VISIBLE
            
            if (shouldSaveNote(title, finalContent, hasImages, hasAudio, hasChecklist)) {
                val note = Note(
                    id = noteId,
                    title = title,
                    content = finalContent,
                    imagePaths = imagePathsToSave,
                    audioList = audioItems,
                    thumbnailPath = thumbnailPathToSave,
                    noteType = noteType
                )
                
                lifecycleScope.launch {
                    try {
                        val newId = viewModel.insertOrReplace(note)
                        if (noteId == 0) noteId = newId.toInt()
                        Log.d("CHECKLIST_DEBUG", "Note force saved successfully with ID: $noteId")
                    } catch (e: Exception) {
                        Log.e("EditNoteActivity", "Error force saving note to database", e)
                    }
                }
            } else {
                Log.d("EditNoteActivity", "Note not saved - empty or untitled")
            }
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error in forceSaveNote", e)
        }
    }

    private fun autoSaveNote() {
        if (isSaving) return
        isSaving = true
        
        try {
            val title = titleEditText.text.toString().trim()
            val content = mainEditText.text.toString()
            
            val imagePathsToSave = imageUris.map { uri -> 
                when {
                    uri.scheme == "file" -> {
                        val file = File(uri.path ?: "")
                        if (file.absolutePath.startsWith(filesDir.absolutePath)) {
                            file.name
                        } else {
                            uri.path ?: uri.toString()
                        }
                    }
                    else -> uri.toString()
                }
            }
            
            // FIXED: Handle thumbnail for A notes
            val thumbnailPathToSave = when {
                noteType == "A" && imagePathsToSave.isNotEmpty() -> {
                    // For A notes, use the first image as thumbnail
                    imagePathsToSave.first()
                }
                thumbnailUri != null -> {
                    // Use existing thumbnail
                    val file = File(thumbnailUri!!.path ?: "")
                    if (file.absolutePath.startsWith(filesDir.absolutePath)) {
                        file.name
                    } else {
                        thumbnailUri!!.path ?: thumbnailUri.toString()
                    }
                }
                else -> null
            }
            
            // FIXED: Always save all checklist items, including empty ones
            val checklistContent = if (checklistItems.isNotEmpty()) {
                val checklistJson = checklistItems.joinToString("|") { item ->
                    val text = item.text.trim()
                    val cleanText = text.replace(":", "\\:").replace("|", "\\|")
                    "$cleanText:${if (item.isChecked) "1" else "0"}"
                }
                "CHECKLIST:$checklistJson"
            } else ""
            
            Log.d("CHECKLIST_DEBUG", "=== SAVING ===")
            Log.d("CHECKLIST_DEBUG", "Checklist items count: ${checklistItems.size}")
            Log.d("CHECKLIST_DEBUG", "Checklist items: ${checklistItems.map { "'${it.text}' (${it.isChecked})" }}")
            Log.d("CHECKLIST_DEBUG", "Checklist content: $checklistContent")
            
            // FIXED: Always include checklist content if checklist mode was activated
            val finalContent = if (checklistRecyclerView.visibility == View.VISIBLE) {
                if (checklistContent.isNotEmpty()) {
                    if (content.isNotEmpty()) "$content\n\n$checklistContent" else checklistContent
                } else {
                    // Even if no items, preserve checklist mode
                    if (content.isNotEmpty()) "$content\n\nCHECKLIST:" else "CHECKLIST:"
                }
            } else {
                content
            }
            
            Log.d("CHECKLIST_DEBUG", "Final content to save: $finalContent")
            
            // Check if note should be saved
            val hasImages = imageUris.isNotEmpty()
            val hasAudio = audioItems.isNotEmpty()
            val hasChecklist = checklistRecyclerView.visibility == View.VISIBLE
            
            if (shouldSaveNote(title, finalContent, hasImages, hasAudio, hasChecklist)) {
                val note = Note(
                    id = noteId,
                    title = title,
                    content = finalContent,
                    imagePaths = imagePathsToSave,
                    audioList = audioItems,
                    thumbnailPath = thumbnailPathToSave,
                    noteType = noteType
                )
                
                lifecycleScope.launch {
                    try {
                        val newId = viewModel.insertOrReplace(note)
                        if (noteId == 0) noteId = newId.toInt()
                        Log.d("CHECKLIST_DEBUG", "Note saved successfully with ID: $noteId")
                    } catch (e: Exception) {
                        Log.e("EditNoteActivity", "Error saving note to database", e)
                        // Retry once after a short delay
                        delay(100)
                        try {
                            val retryId = viewModel.insertOrReplace(note)
                            if (noteId == 0) noteId = retryId.toInt()
                            Log.d("CHECKLIST_DEBUG", "Note saved successfully on retry with ID: $noteId")
                        } catch (retryException: Exception) {
                            Log.e("EditNoteActivity", "Error saving note on retry", retryException)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error in autoSaveNote", e)
        } finally {
            isSaving = false
        }
    }

    override fun onResume() {
        super.onResume()
    }
    
    private fun updateDateChipText(chip: Chip, calendar: Calendar) {
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        chip.text = dateFormat.format(calendar.time)
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
                toggleStyle(Typeface.ITALIC, btnUnderline) // Using italic as placeholder for underline
            }
        }
        btnStrikethrough.setOnClickListener {
            val start = mainEditText.selectionStart
            val end = mainEditText.selectionEnd
            if (start == end) {
                isStrikethroughActive = !isStrikethroughActive
                updateButtonStyle(btnStrikethrough, isStrikethroughActive)
            } else {
                toggleStyle(Typeface.ITALIC, btnStrikethrough) // Using italic as placeholder for strikethrough
            }
        }
    }

    private fun updateButtonStyle(button: TextView, isActive: Boolean) {
        if (isActive) {
            button.setBackgroundResource(R.drawable.bg_audio_chip_pill)
            button.setTextColor(resources.getColor(android.R.color.white, null))
        } else {
            button.setBackgroundResource(android.R.color.transparent)
            button.setTextColor(resources.getColor(android.R.color.black, null))
        }
    }

    private fun toggleStyle(style: Int, button: TextView) {
        // Implementation for applying styles to selected text
        // This is a simplified version - you might want to implement rich text editing
        updateButtonStyle(button, true)
    }

    private fun checkStoragePermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1001)
            return false
        }
        return true
    }

    private fun checkCameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return false
        }
        return true
    }

    private fun checkAudioPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            return false
        }
        return true
    }

    private fun takePhoto() {
        val photoFile = File.createTempFile("photo_${System.currentTimeMillis()}", ".jpg", filesDir)
        imageUri = Uri.fromFile(photoFile)
        cameraLauncher.launch(imageUri!!)
    }

    private fun startRecording() {
        try {
            val audioFile = File.createTempFile("audio_${System.currentTimeMillis()}", ".mp3", filesDir)
            audioRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            btnMic.setImageResource(R.drawable.ic_stop_circle_red)
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error starting recording", e)
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
            btnMic.setImageResource(R.drawable.ic_mic_filled)
            
            // Add the recorded audio to the list
            val audioFile = File(filesDir, "audio_${System.currentTimeMillis()}.mp3")
            val audioItem = AudioItem(audioFile.absolutePath, "Recorded Audio")
            audioItems.add(audioItem)
            audioChipAdapter.notifyItemInserted(audioItems.size - 1)
            autoSaveNote()
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error stopping recording", e)
        }
    }

    private fun copyUriToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, fileName)
            
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("EditNoteActivity", "Error copying file", e)
            null
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        // Debug: Log current checklist items before saving
        Log.d("CHECKLIST_DEBUG", "=== ON BACK PRESSED ===")
        Log.d("CHECKLIST_DEBUG", "Checklist items before save: ${checklistItems.map { "'${it.text}' (${it.isChecked})" }}")
        Log.d("CHECKLIST_DEBUG", "Checklist visibility: ${checklistRecyclerView.visibility == View.VISIBLE}")
        
        autoSaveNote()
        super.onBackPressed()
    }

    // Removed updateNoteTypeIndicator() function
} 