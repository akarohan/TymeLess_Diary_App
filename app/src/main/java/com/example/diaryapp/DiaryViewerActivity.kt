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
import com.example.diaryapp.HealthConnectService
import com.example.diaryapp.utils.LocationUtils

import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.*
import java.time.LocalDate
import java.time.ZoneId
import android.widget.Toast
import kotlin.math.roundToInt




class DiaryViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDiaryViewerBinding
    private lateinit var imageAdapter: GalleryImageAdapter
    private var currentEntry: DiaryEntry? = null
    private lateinit var healthConnectService: HealthConnectService
    
    // Health data views
    private lateinit var healthDataSection: View
    private lateinit var stepsData: TextView
    private lateinit var sleepData: TextView
    private lateinit var screenTimeData: TextView
    
    // Location views


    private lateinit var currentLocationContainer: View
    private lateinit var currentLocationText: TextView
    private lateinit var locationUtils: LocationUtils
    


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
        
        // Initialize health data views
        healthDataSection = binding.healthDataSection
        stepsData = binding.stepsData
        sleepData = binding.sleepData
        screenTimeData = binding.screenTimeData
        
        // Initialize location views


        currentLocationContainer = binding.currentLocationContainer
        currentLocationText = binding.currentLocationText
        locationUtils = LocationUtils(this@DiaryViewerActivity)
        
        // Initialize Health Connect service after views are set up
        healthConnectService = HealthConnectService.getInstance(this)
        
        android.util.Log.d("DiaryViewerActivity", "Health data views initialized: healthDataSection=${healthDataSection != null}, stepsData=${stepsData != null}, sleepData=${sleepData != null}, screenTimeData=${screenTimeData != null}")
        
        // Set test text immediately to see if views are working
        stepsData.text = "TEST DATA"
        sleepData.text = "TEST DATA"
        screenTimeData.text = "TEST DATA"
        healthDataSection.visibility = View.VISIBLE
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
            
            // Convert entry date to LocalDate for step count
            val entryLocalDate = LocalDate.ofInstant(
                java.time.Instant.ofEpochMilli(entryDate), 
                ZoneId.systemDefault()
            )
            
            // Get step count for this date
            val stepCount = getStepCountForDate(entryLocalDate)
            
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
    
    private suspend fun getStepCountForDate(date: LocalDate): Int {
        return try {
            // Only get from Health Connect service - no local database fallback
            if (healthConnectService.initialize()) {
                val stepCount = healthConnectService.getStepCountForDate(date)
                android.util.Log.d("DiaryViewer", "Health Connect service returned $stepCount steps for $date")
                return stepCount
            } else {
                android.util.Log.d("DiaryViewer", "Health Connect service not available")
            }
            
            -1 // Return -1 to indicate no data available
        } catch (e: Exception) {
            android.util.Log.e("DiaryViewer", "Error getting step count for date: $date", e)
            -1
        }
    }
    
    private fun requestHealthConnectPermissions() {
        // No permissions needed for step counter sensor
        android.util.Log.d("DiaryViewer", "No permissions needed for step counter")
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

        // Setup saved location display
        setupSavedLocationDisplay(entry)
        
        // Setup location display
        setupLocationDisplay(entry)
        
        // Load health data for this entry
        loadHealthData(entry)
    }

    private fun setupLocationDisplay(entry: DiaryEntry) {
        // This function is now handled by setupSavedLocationDisplay
        // Keeping it empty to avoid any automatic location opening
    }

    private fun setupSavedLocationDisplay(entry: DiaryEntry) {
        // Check if entry has saved location data
        if (entry.latitude != null && entry.longitude != null) {
            val cityName = if (entry.address != null) {
                // Always prefer the full address for city extraction
                extractCityFromAddress(entry.address)
            } else if (entry.locationName != null && entry.locationName != "Current Location") {
                // Only use locationName if it's not "Current Location"
                extractCityFromAddress(entry.locationName)
            } else {
                "Location"
            }
            
            android.util.Log.d("DiaryViewer", "Location data - locationName: ${entry.locationName}, address: ${entry.address}, extracted city: $cityName")
            
            currentLocationText.text = cityName
            currentLocationContainer.visibility = View.VISIBLE
            
            // Set click listener to open saved location in Google Maps
            currentLocationContainer.setOnClickListener {
                locationUtils.openLocationInMaps(
                    entry.latitude!!,
                    entry.longitude!!,
                    entry.locationName ?: "Location"
                )
            }
        } else {
            currentLocationContainer.visibility = View.GONE
        }
    }
    

    
    private fun extractCityFromAddress(address: String?): String {
        if (address.isNullOrEmpty()) return "Location"
        
        android.util.Log.d("DiaryViewer", "Extracting city from address: $address")
        
        // Split address by commas and look for city
        val parts = address.split(",").map { it.trim() }
        android.util.Log.d("DiaryViewer", "Address parts: $parts")
        
        // First, look for landmark names (usually the first part)
        val firstPart = parts.firstOrNull()
        if (firstPart != null && firstPart.length > 3 && firstPart.length <= 25) {
            // Check if it looks like a landmark name (not a number, not too long)
            if (!firstPart.matches(Regex("\\d+.*")) && 
                !firstPart.equals("India", ignoreCase = true) &&
                !firstPart.matches(Regex(".*\\d+.*")) &&
                !firstPart.equals("Odisha", ignoreCase = true) &&
                !firstPart.equals("Khandagiri", ignoreCase = true) &&
                !firstPart.equals("Kolathia", ignoreCase = true)) {
                
                android.util.Log.d("DiaryViewer", "Using landmark name: $firstPart")
                return firstPart
            }
        }
        
        // For Indian addresses: typically format is "Street, Area, City, State PIN, Country"
        // We want to find the city which is usually the 3rd or 4th part from the end
        // Skip the last parts (Country, State+PIN) and look for the city
        
        // Filter out parts that are likely not cities
        val cityCandidates = parts.filter { part ->
            part.isNotEmpty() && 
            part.length > 2 && 
            part.length <= 20 &&
            !part.matches(Regex("\\d{5,6}")) && // Skip postal codes
            !part.matches(Regex("\\d+\\s*[A-Z]{2}")) && // Skip state codes
            !part.equals("India", ignoreCase = true) && // Skip country
            !part.matches(Regex(".*\\d+.*")) && // Skip parts with numbers (like Plus Codes)
            !part.matches(Regex("Phase [IVX]+", RegexOption.IGNORE_CASE)) && // Skip Phase I, II, etc.
            !part.matches(Regex("Lane \\d+", RegexOption.IGNORE_CASE)) && // Skip Lane numbers
            !part.equals("Odisha", ignoreCase = true) && // Skip state names
            !part.equals("Khandagiri", ignoreCase = true) && // Skip sub-areas
            !part.equals("Kolathia", ignoreCase = true) // Skip areas
        }
        
        android.util.Log.d("DiaryViewer", "City candidates: $cityCandidates")
        
        // Return the first meaningful city candidate, or fallback
        val result = cityCandidates.firstOrNull() ?: "Location"
        android.util.Log.d("DiaryViewer", "Final city result: $result")
        return result
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
    
    override fun onPause() {
        super.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
    }
    
    private fun loadHealthData(entry: DiaryEntry) {
        // Convert entry date to LocalDate
        val entryDate = java.time.Instant.ofEpochMilli(entry.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        
        android.util.Log.d("DiaryViewerActivity", "Loading health data for date: $entryDate")
        
        // Use coroutine to load health data asynchronously
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Always show health data section for testing
                healthDataSection.visibility = View.VISIBLE
                android.util.Log.d("DiaryViewerActivity", "Health data section visibility set to VISIBLE")
                
                // Get health data for this date (now async)
                val healthData = healthConnectService.getHealthDataForDate(entryDate)
                android.util.Log.d("DiaryViewerActivity", "Retrieved health data: $healthData")
                android.util.Log.d("DiaryViewerActivity", "Step count: ${healthData?.stepCount}, Sleep time: ${healthData?.sleepTime}, Screen time: ${healthData?.screenTime}")
                
                if (healthData != null && (healthData.stepCount > 0 || healthData.sleepTime != null || healthData.screenTime != null)) {
                    // Format steps data
                    val stepsText = if (healthData.stepCount > 0) {
                        "${healthData.stepCount}"
                    } else {
                        "No Data"
                    }
                    stepsData.text = stepsText
                    

                    
                    // Format sleep data
                    val sleepText = if (healthData.sleepTime != null && healthData.sleepTime > 0) {
                        val hours = healthData.sleepTime / 60
                        val minutes = healthData.sleepTime % 60
                        "${hours}h${minutes}m"
                    } else {
                        "No Data"
                    }
                    sleepData.text = sleepText
                    

                    
                    // Format screen time data
                    val screenTimeText = if (healthData.screenTime != null && healthData.screenTime > 0) {
                        val hours = healthData.screenTime / 60
                        val minutes = healthData.screenTime % 60
                        "${hours}h${minutes}m"
                    } else {
                        "No Data"
                    }
                    screenTimeData.text = screenTimeText
                    

                } else {
                    // Show "No Data" when no health data is available
                    stepsData.text = "No Data"
                    sleepData.text = "No Data"
                    screenTimeData.text = "No Data"
                    android.util.Log.d("DiaryViewerActivity", "No health data available, showing 'No Data'")
                }
            } catch (e: Exception) {
                // Show "No Data" on error
                stepsData.text = "No Data"
                sleepData.text = "No Data"
                screenTimeData.text = "No Data"
            }
        }
    }
} 