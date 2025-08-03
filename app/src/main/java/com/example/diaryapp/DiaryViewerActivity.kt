package com.example.diaryapp

import android.content.Intent
import android.os.Bundle
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
import com.example.diaryapp.databinding.ActivityDiaryViewerBinding
import com.example.diaryapp.ThemeManager
import com.example.diaryapp.DiaryEntry
import com.example.diaryapp.DiaryDatabase
import com.example.diaryapp.EditEntryActivity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DiaryViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDiaryViewerBinding
    private lateinit var imageAdapter: GalleryImageAdapter
    private var currentEntry: DiaryEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDiaryViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set status bar appearance based on theme
        setupStatusBar()

        // Get entry data from intent
        val entryDate = intent.getLongExtra("entry_date", 0)
        if (entryDate == 0L) {
            finish()
            return
        }

        // Load entry from database
        loadEntry(entryDate)

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
            currentEntry?.let { entry ->
                val intent = Intent(this, EditEntryActivity::class.java)
                intent.putExtra("entry_date", entry.date)
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
            layoutManager = LinearLayoutManager(this@DiaryViewerActivity, LinearLayoutManager.HORIZONTAL, false)
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
        val totalItems = imageAdapter.itemCount
        
        // Find the most centered item
        val centerX = binding.imageGallery.width / 2
        var activePosition = 0
        var minDistance = Int.MAX_VALUE
        
        for (i in 0 until totalItems) {
            val child = layoutManager.findViewByPosition(i)
            if (child != null) {
                val childCenter = child.left + child.width / 2
                val distance = Math.abs(centerX - childCenter)
                if (distance < minDistance) {
                    minDistance = distance
                    activePosition = i
                }
            }
        }
        
        // Ensure activePosition is within valid range
        val safeActivePosition = if (activePosition >= 0 && activePosition < binding.imageIndicator.childCount) {
            activePosition
        } else {
            0
        }
        
        // Update dot indicators
        for (i in 0 until binding.imageIndicator.childCount) {
            val dot = binding.imageIndicator.getChildAt(i)
            if (i == safeActivePosition) {
                dot.background = getDrawable(R.drawable.circle_indicator_active)
            } else {
                dot.background = getDrawable(R.drawable.circle_indicator_inactive)
            }
        }
    }

    private fun loadEntry(entryDate: Long) {
        // Load entry from database using coroutines
        CoroutineScope(Dispatchers.IO).launch {
            val db = DiaryDatabase.getDatabase(this@DiaryViewerActivity)
            val entry = db.diaryEntryDao().getEntryByDate(entryDate)
            
            withContext(Dispatchers.Main) {
                if (entry != null) {
                    currentEntry = entry
                    displayEntry(entry)
                } else {
                    finish()
                }
            }
        }
    }

    private fun displayEntry(entry: DiaryEntry) {
        // Set date
        val dateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yyyy, EEEE", Locale.getDefault())
        
        binding.entryDate.text = dateFormat.format(Date(entry.date)).uppercase()
        binding.entryYear.text = yearFormat.format(Date(entry.date))

        // Set title
        binding.entryTitle.text = entry.title ?: "(No Title)"

        // Set content
        val spanned = android.text.Html.fromHtml(entry.htmlContent, android.text.Html.FROM_HTML_MODE_LEGACY)
        binding.entryContent.text = spanned

        // Load images
        if (entry.imagePaths.isNotEmpty()) {
            binding.imageGallery.visibility = View.VISIBLE
            binding.imageIndicator.visibility = View.VISIBLE
            imageAdapter.updateImages(entry.imagePaths)
            setupImageIndicators(entry.imagePaths.size)
        } else {
            binding.imageGallery.visibility = View.GONE
            binding.imageIndicator.visibility = View.GONE
        }

        // Setup mood switch
        setupMoodSwitch(entry.mood)
    }

    private fun setupMoodSwitch(mood: Int) {
        val moodToggleSwitch = binding.moodToggleSwitch
        val moodEmoji = binding.moodEmoji

        // Set initial state based on mood
        moodToggleSwitch.isChecked = mood == 1 // 1 = happy, 0 = sad

        // Set switch colors to match day/night theme
        val isDarkTheme = ThemeManager.isNightMode(this)
        if (isDarkTheme) {
            moodToggleSwitch.trackTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.darker_gray))
            moodToggleSwitch.thumbTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.white))
        } else {
            moodToggleSwitch.trackTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.greyback))
            moodToggleSwitch.thumbTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.white))
        }

        // Update emoji based on mood
        updateMoodDisplay(mood)

        moodToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            val newMood = if (isChecked) 1 else 0 // 1 = happy, 0 = sad
            updateMoodDisplay(newMood)
            updateMoodInDatabase(newMood)
        }
    }

    private fun updateMoodDisplay(mood: Int) {
        val moodEmoji = binding.moodEmoji
        moodEmoji.text = if (mood == 1) "😊" else "😢"
    }

    private fun updateMoodInDatabase(mood: Int) {
        currentEntry?.let { entry ->
            CoroutineScope(Dispatchers.IO).launch {
                val db = DiaryDatabase.getDatabase(this@DiaryViewerActivity)
                val updatedEntry = entry.copy(mood = mood)
                                     db.diaryEntryDao().insertOrUpdate(updatedEntry)
            }
        }
    }

    private fun setupImageIndicators(imageCount: Int) {
        binding.imageIndicator.removeAllViews()
        
        for (i in 0 until imageCount) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(12, 12).apply {
                    marginEnd = 8
                }
                // Initially highlight the first dot
                background = getDrawable(if (i == 0) R.drawable.circle_indicator_active else R.drawable.circle_indicator_inactive)
            }
            binding.imageIndicator.addView(dot)
        }
    }

    // Gallery Image Adapter
    inner class GalleryImageAdapter : RecyclerView.Adapter<GalleryImageAdapter.ImageViewHolder>() {
        private var imagePaths: List<String> = emptyList()

        fun updateImages(paths: List<String>) {
            imagePaths = paths
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_image, parent, false)
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
                
                // Add click listener to open image viewer
                imageView.setOnClickListener {
                    openImageViewer(adapterPosition)
                }
            }
        }
        
        private fun openImageViewer(clickedImageIndex: Int) {
            currentEntry?.let { entry ->
                if (entry.imagePaths.isNotEmpty()) {
                    val intent = Intent(this@DiaryViewerActivity, ImageViewerActivity::class.java)
                    intent.putStringArrayListExtra("image_paths", ArrayList(entry.imagePaths))
                    intent.putExtra("initial_position", clickedImageIndex)
                    startActivity(intent)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update toolbar colors in case theme changed
        updateToolbarColors()
    }
} 