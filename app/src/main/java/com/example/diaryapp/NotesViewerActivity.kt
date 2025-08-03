package com.example.diaryapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.diaryapp.databinding.ActivityNotesViewerBinding
import com.example.diaryapp.ThemeManager
import com.example.diaryapp.data.Note
import com.example.diaryapp.DiaryDatabase
import com.example.diaryapp.ui.home.EditNoteActivity
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.*

class NotesViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotesViewerBinding
    private lateinit var imageAdapter: GalleryImageAdapter
    private var currentNote: Note? = null
    private var noteId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNotesViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set status bar appearance based on theme
        setupStatusBar()

        // Get note data from intent
        noteId = intent.getIntExtra("note_id", -1)
        if (noteId == -1) {
            finish()
            return
        }

        // Load note from database
        loadNote(noteId)

        // Setup toolbar
        setupToolbar()

        // Setup image gallery
        setupImageGallery()
    }

    private fun setupStatusBar() {
        val isDarkTheme = ThemeManager.isNightMode(this)
        
        if (isDarkTheme) {
            // Dark theme - light status bar content
            window.statusBarColor = android.graphics.Color.BLACK
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and 
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        } else {
            // Light theme - dark status bar content
            window.statusBarColor = android.graphics.Color.WHITE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or 
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    private fun setupToolbar() {
        // Set dynamic colors for toolbar buttons
        updateToolbarColors()
        
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.editButton.setOnClickListener {
            currentNote?.let { note ->
                val intent = Intent(this, EditNoteActivity::class.java)
                intent.putExtra("note_id", note.id)
                intent.putExtra("note_title", note.title)
                intent.putExtra("note_content", note.content)
                intent.putExtra("note_type", note.noteType)
                startActivity(intent)
                finish() // Close viewer when going to edit
            }
        }
    }
    
    private fun updateToolbarColors() {
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val textColor = typedValue.data
        binding.backButton.setColorFilter(textColor)
        binding.editButton.setColorFilter(textColor)
    }

    private fun setupImageGallery() {
        imageAdapter = GalleryImageAdapter()
        binding.imageGallery.apply {
            layoutManager = LinearLayoutManager(this@NotesViewerActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
            
            // Add scroll listener to update dot indicators
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    updateImageIndicator()
                }
                
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        updateImageIndicator()
                    }
                }
            })
        }
    }
    
    private fun updateImageIndicator() {
        val layoutManager = binding.imageGallery.layoutManager as LinearLayoutManager
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        
        // Update dot indicators
        for (i in 0 until binding.imageIndicator.childCount) {
            val dot = binding.imageIndicator.getChildAt(i) as ImageView
            if (i == firstVisible) {
                dot.setImageResource(R.drawable.circle_indicator_active)
            } else {
                dot.setImageResource(R.drawable.circle_indicator_inactive)
            }
        }
    }
    
    private fun setupImageIndicators(count: Int) {
        binding.imageIndicator.removeAllViews()
        
        for (i in 0 until count) {
            val dot = ImageView(this)
            val params = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.dot_size),
                resources.getDimensionPixelSize(R.dimen.dot_size)
            )
            params.marginEnd = resources.getDimensionPixelSize(R.dimen.dot_margin)
            dot.layoutParams = params
            
            if (i == 0) {
                dot.setImageResource(R.drawable.circle_indicator_active)
            } else {
                dot.setImageResource(R.drawable.circle_indicator_inactive)
            }
            
            binding.imageIndicator.addView(dot)
        }
    }

    private fun loadNote(noteId: Int) {
        // Load note from database using coroutines
        CoroutineScope(Dispatchers.IO).launch {
            val db = DiaryDatabase.getDatabase(this@NotesViewerActivity)
            val note = db.noteDao().getNoteById(noteId)
            
            withContext(Dispatchers.Main) {
                if (note != null) {
                    currentNote = note
                    displayNote(note)
                } else {
                    finish()
                }
            }
        }
    }

    private fun displayNote(note: Note) {
        // Set title
        binding.noteTitle.text = note.title

        // Load images first
        if (note.imagePaths.isNotEmpty()) {
            binding.imageGallery.visibility = View.VISIBLE
            binding.imageIndicator.visibility = View.VISIBLE
            imageAdapter.updateImages(note.imagePaths)
            setupImageIndicators(note.imagePaths.size)
        } else {
            binding.imageGallery.visibility = View.GONE
            binding.imageIndicator.visibility = View.GONE
        }

        // Display content with native Android checkboxes
        displayContentWithNativeCheckboxes(note.content)

        // Setup note type indicator
        setupNoteTypeIndicator(note.noteType)
    }

    private fun displayContentWithNativeCheckboxes(content: String) {
        Log.d("CHECKLIST_DEBUG", "=== NOTES VIEWER ===")
        Log.d("CHECKLIST_DEBUG", "Raw content: $content")
        
        // Clear the container
        binding.noteContentContainer.removeAllViews()
        
        // Clean up any old format data first (same as EditNoteActivity)
        val cleanedContent = cleanOldFormatData(content)
        Log.d("CHECKLIST_DEBUG", "Cleaned content: $cleanedContent")
        
        // Check if content contains checklist items
        if (cleanedContent.contains("CHECKLIST:")) {
            Log.d("CHECKLIST_DEBUG", "Found checklist content in viewer")
            // Parse checklist content to extract checklist items
            val checklistMatch = Regex("CHECKLIST:(.+)").find(cleanedContent)
            var hasCheckboxes = false
            var hasRegularText = false
            
            // Extract regular text content (remove checklist parts and clean HTML)
            var textContent = cleanedContent.replace(Regex("CHECKLIST:.+"), "").trim()
            // Clean any remaining HTML tags
            textContent = textContent.replace(Regex("<[^>]*>"), "").trim()
            
            // Add regular text first
            if (textContent.isNotEmpty()) {
                hasRegularText = true
                val textView = TextView(this)
                textView.text = textContent
                textView.setTextColor(ContextCompat.getColor(this, R.color.text))
                textView.textSize = 16f
                
                val textParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                textParams.topMargin = 8
                textParams.bottomMargin = 8
                textView.layoutParams = textParams
                
                binding.noteContentContainer.addView(textView)
            }
            
            // Add checklist items
            if (checklistMatch != null) {
                val checklistData = checklistMatch.groupValues[1]
                Log.d("CHECKLIST_DEBUG", "Checklist data in viewer: '$checklistData'")
                
                if (checklistData.isNotEmpty()) {
                    hasCheckboxes = true
                    val items = checklistData.split("|")
                    Log.d("CHECKLIST_DEBUG", "Found ${items.size} checklist items")
                    
                    for (item in items) {
                        try {
                            if (item.isNotEmpty()) {
                                val parts = item.split(":", limit = 2)
                                if (parts.size == 2) {
                                    val text = parts[0].replace("\\:", ":").replace("\\|", "|")
                                    val isChecked = parts[1] == "1"
                                    
                                    // FIXED: Display the actual text, even if empty
                                    val checkBox = android.widget.CheckBox(this)
                                    checkBox.text = text.trim()
                                    checkBox.isChecked = isChecked
                                    checkBox.isEnabled = true
                                    checkBox.setTextColor(ContextCompat.getColor(this, R.color.text))
                                    checkBox.textSize = 16f
                                    
                                    // FIXED: Add strikethrough for checked items
                                    if (isChecked) {
                                        checkBox.paintFlags = checkBox.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                                        checkBox.setTextColor(android.graphics.Color.GRAY)
                                    }
                                    
                                    // Add listener to update the note content when checkbox state changes
                                    checkBox.setOnCheckedChangeListener { _, newCheckedState ->
                                        // Update strikethrough and color when checkbox state changes
                                        if (newCheckedState) {
                                            checkBox.paintFlags = checkBox.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                                            checkBox.setTextColor(android.graphics.Color.GRAY)
                                        } else {
                                            checkBox.paintFlags = checkBox.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                                            checkBox.setTextColor(ContextCompat.getColor(this, R.color.text))
                                        }
                                        updateCheckboxInContent(item, newCheckedState)
                                    }
                                    
                                    // Add margin for better spacing
                                    val checkboxParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    checkboxParams.topMargin = 8
                                    checkboxParams.bottomMargin = 8
                                    checkBox.layoutParams = checkboxParams
                                    
                                    binding.noteContentContainer.addView(checkBox)
                                    Log.d("CHECKLIST_DEBUG", "Added checkbox in viewer: '$text', checked: $isChecked")
                                } else {
                                    Log.w("CHECKLIST_DEBUG", "Invalid checklist item format in viewer: $item")
                                }
                            } else {
                                // Handle completely empty items - show as empty checkbox
                                val checkBox = android.widget.CheckBox(this)
                                checkBox.text = ""
                                checkBox.isChecked = false
                                checkBox.isEnabled = true
                                checkBox.setTextColor(ContextCompat.getColor(this, R.color.text))
                                checkBox.textSize = 16f
                                
                                // Add margin for better spacing
                                val checkboxParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                checkboxParams.topMargin = 8
                                checkboxParams.bottomMargin = 8
                                checkBox.layoutParams = checkboxParams
                                
                                binding.noteContentContainer.addView(checkBox)
                                Log.d("CHECKLIST_DEBUG", "Added empty checkbox in viewer")
                            }
                        } catch (e: Exception) {
                            Log.e("CHECKLIST_DEBUG", "Error parsing checklist item in viewer: $item", e)
                        }
                    }
                } else {
                    Log.d("CHECKLIST_DEBUG", "Empty checklist data in viewer - showing empty checklist message")
                    // Show a message that this is an empty checklist
                    val emptyTextView = TextView(this)
                    emptyTextView.text = "Empty checklist"
                    emptyTextView.setTextColor(ContextCompat.getColor(this, R.color.text))
                    emptyTextView.textSize = 14f
                    emptyTextView.alpha = 0.6f
                    
                    val emptyParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    emptyParams.topMargin = 8
                    emptyParams.bottomMargin = 8
                    emptyTextView.layoutParams = emptyParams
                    
                    binding.noteContentContainer.addView(emptyTextView)
                }
            } else {
                Log.d("CHECKLIST_DEBUG", "No checklist match found - showing empty checklist message")
                // Show a message that this is an empty checklist
                val emptyTextView = TextView(this)
                emptyTextView.text = "Empty checklist"
                emptyTextView.setTextColor(ContextCompat.getColor(this, R.color.text))
                emptyTextView.textSize = 14f
                emptyTextView.alpha = 0.6f
                
                val emptyParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                emptyParams.topMargin = 8
                emptyParams.bottomMargin = 8
                emptyTextView.layoutParams = emptyParams
                
                binding.noteContentContainer.addView(emptyTextView)
            }
            
            // Always show the container when we have checkboxes or checklist mode, never show the regular content view
            if (hasCheckboxes || hasRegularText || cleanedContent.contains("CHECKLIST:")) {
                binding.noteContentContainer.visibility = View.VISIBLE
                binding.noteContent.visibility = View.GONE
            } else {
                binding.noteContentContainer.visibility = View.GONE
                binding.noteContent.visibility = View.VISIBLE
                // Clean any HTML tags from the content before displaying
                val cleanContent = content.replace(Regex("<[^>]*>"), "").trim()
                binding.noteContent.text = cleanContent
            }
        } else {
            // No checkboxes, show regular text content
            binding.noteContentContainer.visibility = View.GONE
            binding.noteContent.visibility = View.VISIBLE
            // Clean any HTML tags from the content before displaying
            val cleanContent = content.replace(Regex("<[^>]*>"), "").trim()
            binding.noteContent.text = cleanContent
        }
    }

    private fun setupNoteTypeIndicator(noteType: String) {
        val (backgroundColor, textColor, typeText) = when (noteType) {
            "P" -> Triple(
                ContextCompat.getColor(this, R.color.red_note_card),
                android.graphics.Color.WHITE,
                "Private"
            )
            "A" -> Triple(
                ContextCompat.getColor(this, R.color.skyblue_note_card),
                android.graphics.Color.WHITE,
                "Accounts"
            )
            else -> Triple(
                ContextCompat.getColor(this, R.color.parrot_note_card),
                android.graphics.Color.BLACK,
                "General"
            )
        }
        
        binding.noteTypeIndicator.setBackgroundColor(backgroundColor)
        binding.noteTypeText.text = typeText
        binding.noteTypeText.setTextColor(textColor)
    }

    // Gallery Image Adapter (reused from DiaryViewerActivity)
    private inner class GalleryImageAdapter : RecyclerView.Adapter<GalleryImageAdapter.ImageViewHolder>() {
        private var imagePaths: List<String> = emptyList()

        fun updateImages(newImagePaths: List<String>) {
            imagePaths = newImagePaths
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gallery_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(imagePaths[position])
        }

        override fun getItemCount(): Int = imagePaths.size

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.galleryImage)

            fun bind(imagePath: String) {
                val uri = if (imagePath.startsWith("/")) {
                    val file = java.io.File(imagePath)
                    if (file.exists()) android.net.Uri.fromFile(file) else null
                } else if (imagePath.startsWith("content://")) {
                    android.net.Uri.parse(imagePath)
                } else null

                if (uri != null) {
                    Glide.with(imageView.context)
                        .load(uri)
                        .transform(CenterCrop(), RoundedCorners(12))
                        .placeholder(R.drawable.bg_image_rounded)
                        .error(R.drawable.bg_image_rounded)
                        .into(imageView)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update toolbar colors in case theme changed
        updateToolbarColors()
    }
    
    private fun cleanOldFormatData(content: String): String {
        // Only clean if content contains old HTML format, otherwise return as is
        if (content.contains("<span style='margin-right: 8px;'>")) {
            var cleanedContent = content
            
            // Remove old HTML format: <span style='margin-right: 8px;'>☐</span><span style='...'>text</span>
            val oldHtmlPattern = Regex("<span style='margin-right: 8px;'>([☐☑])</span><span style='[^']*'>([^<]*)</span>")
            cleanedContent = oldHtmlPattern.replace(cleanedContent) { matchResult ->
                val checkboxSymbol = matchResult.groupValues[1]
                val text = matchResult.groupValues[2]
                val isChecked = if (checkboxSymbol == "☑") "1" else "0"
                "CHECKLIST:$text:$isChecked"
            }
            
            // Clean up any remaining HTML tags
            cleanedContent = cleanedContent.replace(Regex("<[^>]*>"), "")
            
            return cleanedContent
        }
        
        // If no old format detected, return content as is
        return content
    }
    
    private fun updateCheckboxInContent(originalItem: String, isChecked: Boolean) {
        // Update the note content in the database when checkbox state changes
        lifecycleScope.launch(Dispatchers.IO) {
            val database = DiaryDatabase.getDatabase(this@NotesViewerActivity)
            val noteDao = database.noteDao()
            
            // Get current note content
            val currentNote = noteDao.getNoteById(noteId)
            currentNote?.let { note ->
                // Extract the text from the original format
                val parts = originalItem.split(":", limit = 2)
                val text = if (parts.size == 2) parts[0] else ""
                
                // Create new item format
                val newItem = "$text:${if (isChecked) "1" else "0"}"
                
                // Replace the original item with the new one in the checklist data
                val checklistMatch = Regex("CHECKLIST:(.+)").find(note.content)
                if (checklistMatch != null) {
                    val checklistData = checklistMatch.groupValues[1]
                    val items = checklistData.split("|").toMutableList()
                    
                    // Find and replace the specific item
                    for (i in items.indices) {
                        if (items[i] == originalItem) {
                            items[i] = newItem
                            break
                        }
                    }
                    
                    // Reconstruct the content
                    val newChecklistData = items.joinToString("|")
                    val newContent = note.content.replace("CHECKLIST:$checklistData", "CHECKLIST:$newChecklistData")
                    noteDao.updateNoteContent(noteId, newContent)
                }
            }
        }
    }
} 