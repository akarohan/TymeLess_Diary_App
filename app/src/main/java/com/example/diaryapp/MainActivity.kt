package com.example.diaryapp

import android.os.Bundle
import android.view.Menu
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.diaryapp.databinding.ActivityMainBinding
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import jp.wasabeef.richeditor.RichEditor
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.*
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.view.LayoutInflater
import android.content.Intent
import android.widget.Toast
import android.util.Log
import android.widget.LinearLayout
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.widget.Button
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.provider.MediaStore
import android.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.yalantis.ucrop.UCrop
import androidx.core.view.GravityCompat
import jp.wasabeef.blurry.Blurry
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition as GlideTransition
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.ChangeBounds
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatDelegate
import android.widget.Switch
import android.animation.ObjectAnimator
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import android.view.Gravity
import androidx.transition.TransitionManager
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import androidx.core.content.ContextCompat.getColor
import androidx.annotation.ColorInt
import android.graphics.drawable.GradientDrawable
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context

// Extension property for dp to px
// Removed: val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var selectedDate: Long = System.currentTimeMillis()
    private lateinit var db: DiaryDatabase
    private lateinit var pickProfileImageLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var takeProfileImageLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri>
    private var cameraImageUri: android.net.Uri? = null
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private lateinit var cropImageLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
    private lateinit var pickCoverImageLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var takeCoverImageLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri>
    private var coverCameraImageUri: android.net.Uri? = null
    private val COVER_CAMERA_PERMISSION_REQUEST_CODE = 1002
    private lateinit var pickThemeHeaderPicLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var cropThemeHeaderPicLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var prefs: SharedPreferences
    private lateinit var toolbarBackgroundImage: ImageView
    private lateinit var navView: NavigationView
    private lateinit var themeUpdateReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        
        // Add smooth transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbarBackgroundImage = findViewById(R.id.toolbarBackgroundImage)

        prefs = getEncryptedPrefs()

        // Set up theme update receiver
        setupThemeUpdateReceiver()

        // Set theme pic or color on the app bar (header)
        val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
        val themeHeaderPicUri = prefs.getString("theme_header_pic_uri", null)
        val isNight = ThemeManager.isNightMode(this)
        
        if (selectedThemeIndex >= 0 && themeHeaderPicUri != null && File(themeHeaderPicUri).exists()) {
            // Check if this is a custom theme (custom themes don't have day/night variants)
            // Custom theme index is typically the last one in the list
            val totalThemes = 2 + 8 + 1 // dayNightThemes + standaloneThemes + customThemes
            val customThemeIndex = totalThemes - 1 // Custom theme is the last one
            val isCustomTheme = selectedThemeIndex == customThemeIndex
            
            if (isCustomTheme) {
                // For custom themes, use the main theme file directly
                Glide.with(this)
                    .load(Uri.fromFile(File(themeHeaderPicUri)))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(toolbarBackgroundImage)
                toolbarBackgroundImage.visibility = View.VISIBLE
                binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                Log.d("THEME_MAIN", "Loaded custom theme image")
            } else {
                // For predefined themes, check if we have day/night theme files saved
                val dayFile = File(filesDir, "theme_header_pic_day.jpg")
                val nightFile = File(filesDir, "theme_header_pic_night.jpg")
                
                val sourceFile = if (isNight) nightFile else dayFile
                if (sourceFile.exists()) {
                    Glide.with(this)
                        .load(Uri.fromFile(sourceFile))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .into(toolbarBackgroundImage)
                    toolbarBackgroundImage.visibility = View.VISIBLE
                    binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    Log.d("THEME_MAIN", "Loaded ${if (isNight) "night" else "day"} theme image")
                } else {
                    // Fallback to the saved theme file
                    Glide.with(this)
                        .load(Uri.fromFile(File(themeHeaderPicUri)))
                        .centerCrop()
                        .into(toolbarBackgroundImage)
                    toolbarBackgroundImage.visibility = View.VISIBLE
                    binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    Log.d("THEME_MAIN", "Loaded fallback theme image")
                }
            }
        } else {
            toolbarBackgroundImage.visibility = View.GONE
            val backgroundColor = if (isNight) ContextCompat.getColor(this, R.color.black) else ContextCompat.getColor(this, R.color.white)
            binding.appBarMain.toolbar.setBackgroundColor(backgroundColor)
            Log.d("THEME_MAIN", "No theme selected, using ${if (isNight) "black" else "white"} background")
        }

        // Fresh Navigation Drawer Setup
        setupFreshNavigationDrawer()

        db = DiaryDatabase.getDatabase(this)

        updateDateText()
        updateFreshNavigationDrawerHeader()

        // Set greeting message below the logo based on time and user name
        val greetingTextView = binding.appBarMain.toolbar.findViewById<TextView>(R.id.greeting)
        val userName = prefs.getString("name", "User") ?: "User"
        greetingTextView?.text = getGreetingMessage(userName)

        // Set up toolbar profile image to open navigation drawer when tapped
        val toolbarProfileImageView = binding.appBarMain.toolbar.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.profileImageView)
        toolbarProfileImageView?.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
            // Update colors when drawer opens
            val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            val isNightMode = themePrefs.getBoolean("is_night_mode", false)
            Log.d("NAV_DRAWER", "Profile tapped, updating colors for night mode: $isNightMode")
            updateNavDrawerColors(isNightMode)
        }

        // Set version number in footer
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val versionTextView = findViewById<TextView>(R.id.navAppVersion)
        versionTextView?.text = "Version $versionName"

        setupImageLaunchers()

        // Night mode switch logic
        setupNightModeSwitch()
        
        // Initialize navigation drawer colors based on current theme
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isNightMode = themePrefs.getBoolean("is_night_mode", false)
        
        // Use post to ensure views are ready
        binding.root.post {
            updateNavDrawerColors(isNightMode)
            updateNavDrawerHeaderBackground(isNightMode)
        }
    }

    private fun setupFreshNavigationDrawer() {
        val drawerLayout: DrawerLayout = binding.drawerLayout
        
        // Get the custom nav view and make it intercept all touches
        val customNavView = findViewById<LinearLayout>(R.id.custom_nav_view)
        customNavView?.setOnTouchListener { _, _ ->
            // Consume all touch events - this prevents any background interaction
            true
        }
        
        // Set up drawer state change listener to update colors when drawer opens
        drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                // Update colors when drawer opens
                val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
                val isNightMode = themePrefs.getBoolean("is_night_mode", false)
                updateNavDrawerColors(isNightMode)
            }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })
        
        // Set up custom click listeners for each menu item
        findViewById<LinearLayout>(R.id.nav_home)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_home)
        }
        
        findViewById<LinearLayout>(R.id.nav_settings)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        findViewById<LinearLayout>(R.id.nav_customize_login)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, LoginPageCustomizeActivity::class.java))
        }
        

        
        findViewById<LinearLayout>(R.id.nav_change_theme_pic)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, ThemePictureSelectionActivity::class.java))
        }
        
        findViewById<LinearLayout>(R.id.nav_backup_restore)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, BackupRestoreActivity::class.java))
        }
        
        findViewById<LinearLayout>(R.id.nav_recycle_bin)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, com.example.diaryapp.ui.home.RecycleBinActivity::class.java))
        }
        
        findViewById<LinearLayout>(R.id.nav_logout)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            // Clear only session-related preferences, preserve login credentials and images
            prefs.edit().apply()
            // Redirect to AuthActivity
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        
        // Make version text non-interactive to prevent background interaction
        findViewById<TextView>(R.id.navAppVersion)?.setOnClickListener {
            // Do nothing - prevents interaction with background
        }
        
        findViewById<TextView>(R.id.navBuiltBy)?.setOnClickListener {
            // Do nothing - prevents interaction with background
        }
    }

    private fun updateFreshNavigationDrawerHeader() {
        // Find views directly in the custom layout
        val themeBackgroundImage = findViewById<ImageView>(R.id.themeBackgroundImage)
        val profileImageView = findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.navProfileImageView)
        val firstNameTextView = findViewById<TextView>(R.id.navUserFirstName)
        val lastNameTextView = findViewById<TextView>(R.id.navUserLastName)
        val usernameTextView = findViewById<TextView>(R.id.navUserUsername)
        
        // Update username and name in the navigation drawer header
        val fullName = prefs.getString("name", "Rohan Mahapatra") ?: "Rohan Mahapatra"
        val userUsername = prefs.getString("username", "rohan") ?: "rohan"
        
        // Split the name into first and last name
        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts[0]
        val lastName = if (nameParts.size > 1) nameParts[1] else ""
        
        firstNameTextView?.text = firstName
        lastNameTextView?.text = lastName
        usernameTextView?.text = "@$userUsername"
        
        // Hide last name TextView if it's empty to reduce spacing
        if (lastName.isEmpty()) {
            lastNameTextView?.visibility = View.GONE
        } else {
            lastNameTextView?.visibility = View.VISIBLE
        }
        
        // Update profile image
        val profilePicUri = prefs.getString("profile_pic_uri", null)
        Log.d("NAV_DRAWER", "Profile pic URI: $profilePicUri")
        
        if (profilePicUri != null && profileImageView != null) {
            val uri = if (profilePicUri.startsWith("/")) {
                android.net.Uri.fromFile(java.io.File(profilePicUri))
            } else {
                android.net.Uri.parse(profilePicUri)
            }
            Glide.with(this)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_user_placeholder)
                .error(R.drawable.ic_user_placeholder)
                .into(profileImageView)
            Log.d("NAV_DRAWER", "Loading profile image from: $uri")
        } else if (profileImageView != null) {
            profileImageView.setImageResource(R.drawable.ic_user_placeholder)
            Log.d("NAV_DRAWER", "No profile pic URI, using placeholder")
        } else {
            Log.e("NAV_DRAWER", "Profile image view not found!")
        }
        
        // Update theme background image
        val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
        val themeHeaderPicUri = prefs.getString("theme_header_pic_uri", null)
        
        if (selectedThemeIndex >= 0 && themeHeaderPicUri != null && themeBackgroundImage != null && File(themeHeaderPicUri).exists()) {
            val uri = android.net.Uri.fromFile(File(themeHeaderPicUri))
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(themeBackgroundImage)
        } else if (themeBackgroundImage != null) {
            // No theme selected, use solid color background based on day/night mode
            themeBackgroundImage.setImageDrawable(null)
            val isNight = ThemeManager.isNightMode(this)
            val backgroundColor = if (isNight) ContextCompat.getColor(this, R.color.black) else ContextCompat.getColor(this, R.color.white)
            themeBackgroundImage.setBackgroundColor(backgroundColor)
        }
    }



    private fun setupImageLaunchers() {
        cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val resultUri = UCrop.getOutput(data!!)
                if (resultUri != null) {
                    saveProfileImageFromUri(resultUri)
                }
            }
        }
        pickProfileImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                launchCropper(it)
            }
        }
        takeProfileImageLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            val file = File(filesDir, "profile_image.jpg")
            if (success && file.exists() && file.length() > 0) {
                launchCropper(android.net.Uri.fromFile(file))
            } else {
                Toast.makeText(this, "Failed to save photo. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
        pickCoverImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                saveCoverImageFromUri(it)
            }
        }
        takeCoverImageLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            val file = File(filesDir, "cover_image.jpg")
            if (success && file.exists() && file.length() > 0) {
                saveCoverImageFromUri(android.net.Uri.fromFile(file))
            } else {
                Toast.makeText(this, "Failed to save cover photo. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }

        pickThemeHeaderPicLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val destUri = Uri.fromFile(File(filesDir, "theme_header_pic_cropped.jpg"))
                val uCrop = UCrop.of(it, destUri)
                    .withAspectRatio(16f, 9f)
                    .withMaxResultSize(1200, 675)
                cropThemeHeaderPicLauncher.launch(uCrop.getIntent(this))
            }
        }
        cropThemeHeaderPicLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val resultUri = UCrop.getOutput(data!!)
                if (resultUri != null) {
                    saveThemeHeaderPicFromUri(resultUri)
                }
            }
        }
    }

    private fun setupNightModeSwitch() {
        // Find the new custom switch
        val dayNightSwitch = findViewById<com.example.diaryapp.ui.DayNightSwitchView>(R.id.day_night_switch)
        // Always read the current state from preferences
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isNightMode = themePrefs.getBoolean("is_night_mode", false)
        dayNightSwitch.initializeState(isNightMode)
        
        // Initialize app logo based on current theme
        updateAppLogo(isNightMode)
        dayNightSwitch.setListener { isNight ->
            // Get current state without saving yet
            val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            val currentIsNight = themePrefs.getBoolean("is_night_mode", false)
            
            // Apply theme changes if there's an actual change
            if (currentIsNight != isNight) {
                    android.util.Log.d("MainActivity", "Theme change detected: currentIsNight=$currentIsNight, isNight=$isNight")
                    // Apply theme transition (switch will animate on its own)
                    ThemeManager.applySmoothThemeTransition(this@MainActivity, currentIsNight, isNight)
                    android.util.Log.d("MainActivity", "ThemeManager.applySmoothThemeTransition called")
                    
                    // Update app logo based on theme
                    updateAppLogo(isNight)
                
                // Switch theme picture if needed
                val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
                if (selectedThemeIndex >= 0) {
                    android.util.Log.d("MainActivity", "Switching theme picture for index: $selectedThemeIndex")
                    
                    // Check if we have day/night theme files saved
                    val dayFile = File(filesDir, "theme_header_pic_day.jpg")
                    val nightFile = File(filesDir, "theme_header_pic_night.jpg")
                    val outputFile = File(filesDir, "theme_header_pic.jpg")
                    
                    val sourceFile = if (isNight) nightFile else dayFile
                    if (sourceFile.exists()) {
                        try {
                            sourceFile.copyTo(outputFile, overwrite = true)
                            prefs.edit().putString("theme_header_pic_uri", outputFile.absolutePath).apply()
                            
                            Glide.with(this)
                                .load(Uri.fromFile(outputFile))
                                .centerCrop()
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .into(toolbarBackgroundImage)
                            
                            toolbarBackgroundImage.visibility = View.VISIBLE
                            binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            Log.d("THEME_PIC", "Switched theme to ${if (isNight) "night" else "day"} mode")
                        } catch (e: Exception) {
                            Log.e("THEME_PIC", "Failed to switch theme", e)
                        }
                    } else {
                        // Fallback to the old method for predefined themes
                        switchThemePictureForCurrentMode(isNight)
                    }
                }
                
                // Update navigation drawer colors immediately
                updateNavDrawerColors(isNight)
                
                // Also update navigation drawer header background if needed
                updateNavDrawerHeaderBackground(isNight)
                
                // Update main toolbar background if no theme is selected
                if (selectedThemeIndex == -1) {
                    toolbarBackgroundImage.visibility = View.GONE
                    val backgroundColor = if (isNight) ContextCompat.getColor(this, R.color.black) else ContextCompat.getColor(this, R.color.white)
                    binding.appBarMain.toolbar.setBackgroundColor(backgroundColor)
                    Log.d("THEME_PIC", "Updated main toolbar to ${if (isNight) "black" else "white"} background")
                }
            }
        }
    }

    private fun updateNavDrawerColors(isNight: Boolean) {
        Log.d("NAV_DRAWER", "updateNavDrawerColors called with isNight: $isNight")
        
        val backgroundColor = if (isNight) ContextCompat.getColor(this, R.color.black) else ContextCompat.getColor(this, R.color.white)
        val textColor = if (isNight) ContextCompat.getColor(this, R.color.white) else ContextCompat.getColor(this, R.color.black)
        
        // Update main container background
        val mainContainer = findViewById<LinearLayout>(R.id.custom_nav_view)
        if (mainContainer != null) {
            mainContainer.setBackgroundColor(backgroundColor)
            Log.d("NAV_DRAWER", "Main container background updated to: ${if (isNight) "BLACK" else "WHITE"}")
        } else {
            Log.e("NAV_DRAWER", "Main container not found!")
        }
        
        // Update menu container background
        val menuContainer = findViewById<LinearLayout>(R.id.nav_menu_container)
        if (menuContainer != null) {
            menuContainer.setBackgroundColor(backgroundColor)
            Log.d("NAV_DRAWER", "Menu container background updated")
        } else {
            Log.e("NAV_DRAWER", "Menu container not found!")
        }
        
        // Update footer background
        val footerContainer = findViewById<LinearLayout>(R.id.nav_footer_container)
        if (footerContainer != null) {
            footerContainer.setBackgroundColor(backgroundColor)
            Log.d("NAV_DRAWER", "Footer container background updated")
        } else {
            Log.e("NAV_DRAWER", "Footer container not found!")
        }
        
        // Update all icons
        val iconIds = listOf(
            R.id.nav_home_icon, R.id.nav_settings_icon, R.id.nav_customize_login_icon,
            R.id.nav_change_theme_pic_icon, R.id.nav_backup_restore_icon,
            R.id.nav_recycle_bin_icon, R.id.nav_logout_icon
        )
        
        iconIds.forEach { iconId ->
            val iconView = findViewById<ImageView>(iconId)
            if (iconView != null) {
                iconView.setColorFilter(textColor)
                Log.d("NAV_DRAWER", "Updated icon: ${resources.getResourceEntryName(iconId)}")
            } else {
                Log.e("NAV_DRAWER", "Icon not found: ${resources.getResourceEntryName(iconId)}")
            }
        }
        
        // Update all text
        val textIds = listOf(
            R.id.nav_home_text, R.id.nav_settings_text, R.id.nav_customize_login_text,
            R.id.nav_change_theme_pic_text, R.id.nav_backup_restore_text,
            R.id.nav_recycle_bin_text, R.id.nav_logout_text, R.id.navAppVersion, R.id.navBuiltBy
        )
        
        textIds.forEach { textId ->
            val textView = findViewById<TextView>(textId)
            if (textView != null) {
                textView.setTextColor(textColor)
                Log.d("NAV_DRAWER", "Updated text: ${resources.getResourceEntryName(textId)}")
            } else {
                Log.e("NAV_DRAWER", "Text not found: ${resources.getResourceEntryName(textId)}")
            }
        }
        
        // Update navigation drawer header text colors
        val navUserFirstName = findViewById<TextView>(R.id.navUserFirstName)
        val navUserLastName = findViewById<TextView>(R.id.navUserLastName)
        val navUserUsername = findViewById<TextView>(R.id.navUserUsername)
        
        if (navUserFirstName != null) {
            navUserFirstName.setTextColor(textColor)
            Log.d("NAV_DRAWER", "Updated navUserFirstName text color")
        } else {
            Log.e("NAV_DRAWER", "navUserFirstName not found!")
        }
        
        if (navUserLastName != null) {
            navUserLastName.setTextColor(textColor)
            Log.d("NAV_DRAWER", "Updated navUserLastName text color")
        } else {
            Log.e("NAV_DRAWER", "navUserLastName not found!")
        }
        
        if (navUserUsername != null) {
            navUserUsername.setTextColor(textColor)
            Log.d("NAV_DRAWER", "Updated navUserUsername text color")
        } else {
            Log.e("NAV_DRAWER", "navUserUsername not found!")
        }
        
        // Update profile picture border
        val profileImageView = findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.navProfileImageView)
        if (profileImageView != null) {
            profileImageView.borderColor = textColor
            Log.d("NAV_DRAWER", "Profile image border updated")
        } else {
            Log.e("NAV_DRAWER", "Profile image not found!")
        }
        
        // Force redraw of the navigation drawer
        mainContainer?.invalidate()
        
        Log.d("NAV_DRAWER", "updateNavDrawerColors completed")
    }

    private fun updateDateText() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        val text = android.text.format.DateFormat.format("yyyy-MM-dd", cal)
        // dateTextView.text = text
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onPause() {
        super.onPause()
        // TODO: Implement save logic
    }

    override fun onResume() {
        super.onResume()
        
        // Check if theme needs to be applied
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isNightMode = themePrefs.getBoolean("is_night_mode", false)
        val currentThemeMode = AppCompatDelegate.getDefaultNightMode()
        val shouldBeNight = if (isNightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        
        // Apply theme if needed
        if (currentThemeMode != shouldBeNight) {
            val fromNight = currentThemeMode == AppCompatDelegate.MODE_NIGHT_YES
            ThemeManager.applySmoothThemeTransition(this, fromNight, isNightMode)
        }
        
        // Check for theme picture updates
        val themeHeaderPicUri = prefs.getString("theme_header_pic_uri", null)
        val themeJustApplied = prefs.getBoolean("theme_just_applied", false)
        
        if (themeJustApplied) {
            // Clear the flag
            prefs.edit().putBoolean("theme_just_applied", false).apply()
            Log.d("THEME_RESUME", "Theme was just applied, refreshing display")
        }
        
        // Load theme image or use color
        val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
        if (selectedThemeIndex >= 0 && themeHeaderPicUri != null && File(themeHeaderPicUri).exists()) {
            // Check if we have day/night theme files saved
            val dayFile = File(filesDir, "theme_header_pic_day.jpg")
            val nightFile = File(filesDir, "theme_header_pic_night.jpg")
            
            val sourceFile = if (isNightMode) nightFile else dayFile
            if (sourceFile.exists()) {
                Log.d("THEME_RESUME", "Loading ${if (isNightMode) "night" else "day"} theme image: ${sourceFile.absolutePath}")
                Glide.with(this)
                    .load(Uri.fromFile(sourceFile))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(toolbarBackgroundImage)
                toolbarBackgroundImage.visibility = View.VISIBLE
                binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                Log.d("THEME_RESUME", "Day/night theme image loaded successfully")
            } else {
                // Fallback to the saved theme file
                Log.d("THEME_RESUME", "Loading fallback theme image: $themeHeaderPicUri")
                Glide.with(this)
                    .load(Uri.fromFile(File(themeHeaderPicUri)))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(toolbarBackgroundImage)
                toolbarBackgroundImage.visibility = View.VISIBLE
                binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                Log.d("THEME_RESUME", "Fallback theme image loaded successfully")
            }
        } else {
            Log.d("THEME_RESUME", "No theme file found, using color")
            toolbarBackgroundImage.visibility = View.GONE
            val backgroundColor = if (isNightMode) ContextCompat.getColor(this, R.color.black) else ContextCompat.getColor(this, R.color.white)
            binding.appBarMain.toolbar.setBackgroundColor(backgroundColor)
        }
        
        // Update navigation drawer header
        updateFreshNavigationDrawerHeader()
        
        // Update navigation drawer colors based on current theme
        updateNavDrawerColors(isNightMode)
        
        // Update toolbar profile image on resume
        val toolbarProfileImageView = binding.appBarMain.toolbar.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.profileImageView)
        val profilePicUri = prefs.getString("profile_pic_uri", null)
        if (profilePicUri != null && toolbarProfileImageView != null) {
            val uri = if (profilePicUri.startsWith("/")) {
                android.net.Uri.fromFile(java.io.File(profilePicUri))
            } else {
                android.net.Uri.parse(profilePicUri)
            }
            Glide.with(this)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_user_placeholder)
                .error(R.drawable.ic_user_placeholder)
                .into(toolbarProfileImageView)
        } else if (toolbarProfileImageView != null) {
            toolbarProfileImageView.setImageResource(R.drawable.ic_user_placeholder)
        }
        // Update greeting message on resume
        val greetingTextView = binding.appBarMain.toolbar.findViewById<TextView>(R.id.greeting)
        val userName = prefs.getString("name", "User") ?: "User"
        greetingTextView?.text = getGreetingMessage(userName)
    }

    private fun launchCameraForProfileImage() {
        val file = File(filesDir, "profile_image.jpg")
        cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        takeProfileImageLauncher.launch(cameraImageUri)
    }

    private fun launchCameraForCoverImage() {
        val file = File(filesDir, "cover_image.jpg")
        coverCameraImageUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        takeCoverImageLauncher.launch(coverCameraImageUri)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCameraForProfileImage()
        } else if (requestCode == COVER_CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCameraForCoverImage()
        }
    }

    private fun launchCropper(sourceUri: Uri) {
        val destUri = android.net.Uri.fromFile(File(filesDir, "profile_image_cropped.jpg"))
        val uCrop = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)
        cropImageLauncher.launch(uCrop.getIntent(this))
    }

    private fun saveProfileImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "profile_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            Log.d("PROFILE_IMAGE", "Saved profile image to: ${file.absolutePath}, exists: ${file.exists()}")
            // Save file path in preferences
            prefs.edit().putString("profile_pic_uri", file.absolutePath).apply()
            Log.d("PROFILE_IMAGE", "Saved profile_pic_uri in prefs: ${file.absolutePath}")
            updateFreshNavigationDrawerHeader()

            // Update toolbar/home header profile image immediately
            val toolbarProfileImageView = binding.appBarMain.toolbar.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.profileImageView)
            val profilePicUri = prefs.getString("profile_pic_uri", null)
            Log.d("PROFILE_IMAGE", "Toolbar profilePicUri: $profilePicUri")
            if (profilePicUri != null && toolbarProfileImageView != null) {
                val uri = if (profilePicUri.startsWith("/")) {
                    android.net.Uri.fromFile(java.io.File(profilePicUri))
                } else {
                    android.net.Uri.parse(profilePicUri)
                }
                Glide.with(this)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(toolbarProfileImageView)
            } else if (toolbarProfileImageView != null) {
                toolbarProfileImageView.setImageResource(R.drawable.ic_user_placeholder)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveCoverImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "cover_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            // Save file path in preferences
            prefs.edit().putString("cover_pic_uri", file.absolutePath).apply()
            updateFreshNavigationDrawerHeader()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeProfileImage() {
        val file = File(filesDir, "profile_image.jpg")
        if (file.exists()) file.delete()
        prefs.edit().remove("profile_pic_uri").apply()
        updateFreshNavigationDrawerHeader()
        // Update toolbar/home header profile image immediately
        val toolbarProfileImageView = binding.appBarMain.toolbar.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.profileImageView)
        toolbarProfileImageView?.setImageResource(R.drawable.ic_user_placeholder)
    }

    private fun removeCoverImage() {
        val file = File(filesDir, "cover_image.jpg")
        if (file.exists()) file.delete()
        prefs.edit().remove("cover_pic_uri").apply()
        updateFreshNavigationDrawerHeader()
    }

    private fun saveThemeHeaderPicFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val timestamp = System.currentTimeMillis()
            val file = File(filesDir, "theme_header_pic_$timestamp.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            prefs.edit().putString("theme_header_pic_uri", file.absolutePath).apply()
            
            // Immediately apply the theme header image to the main toolbar
            if (::toolbarBackgroundImage.isInitialized) {
                Glide.with(this)
                    .load(Uri.fromFile(file))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(toolbarBackgroundImage)
                toolbarBackgroundImage.visibility = View.VISIBLE
                binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                Log.d("THEME_PIC", "Applied theme header image immediately: ${file.absolutePath}")
            }
            
            // Update navigation drawer header
            updateFreshNavigationDrawerHeader()
            
            Toast.makeText(this, "Theme header image applied!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to apply theme image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getEncryptedPrefs() = EncryptedSharedPreferences.create(
        "diary_auth_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun switchThemePictureForCurrentMode(isNight: Boolean) {
        val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
        
        if (selectedThemeIndex == -1) { // No theme selected
            // Hide background image and set toolbar to proper day/night color
            toolbarBackgroundImage.visibility = View.GONE
            val backgroundColor = if (isNight) ContextCompat.getColor(this, R.color.black) else ContextCompat.getColor(this, R.color.white)
            binding.appBarMain.toolbar.setBackgroundColor(backgroundColor)
            Log.d("THEME_PIC", "No theme selected, using ${if (isNight) "black" else "white"} background")
        } else if (selectedThemeIndex == 9) { // Custom theme
            val dayFile = File(filesDir, "theme_header_pic_day.jpg")
            val nightFile = File(filesDir, "theme_header_pic_night.jpg")
            val outputFile = File(filesDir, "theme_header_pic.jpg")
            
            val sourceFile = if (isNight) nightFile else dayFile
            if (sourceFile.exists()) {
                try {
                    sourceFile.copyTo(outputFile, overwrite = true)
                    prefs.edit().putString("theme_header_pic_uri", outputFile.absolutePath).apply()
                    
                    Glide.with(this)
                        .load(Uri.fromFile(outputFile))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .into(toolbarBackgroundImage)
                    
                    toolbarBackgroundImage.visibility = View.VISIBLE
                    binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    Log.d("THEME_PIC", "Switched custom theme to ${if (isNight) "night" else "day"} mode")
                } catch (e: Exception) {
                    Log.e("THEME_PIC", "Failed to switch custom theme", e)
                }
            }
        } else if (selectedThemeIndex >= 0 && selectedThemeIndex < 9) { // Predefined themes
            val themePictures = listOf(
                Pair(R.drawable.theme_abstract_day, R.drawable.theme_abstract_night),
                Pair(R.drawable.theme_city_day, R.drawable.theme_city_night),
                Pair(R.drawable.theme_forest_day, R.drawable.theme_forest_night),
                Pair(R.drawable.theme_minimal_day, R.drawable.theme_minimal_night),
                Pair(R.drawable.theme_mountains_day, R.drawable.theme_mountains_night),
                Pair(R.drawable.theme_nature_day, R.drawable.theme_nature_night),
                Pair(R.drawable.theme_ocean_day, R.drawable.theme_ocean_night),
                Pair(R.drawable.theme_sunset_day, R.drawable.theme_sunset_night),
                Pair(R.drawable.bg_icemountain_optimized, R.drawable.bg_icemountain_night_optimized)
            )
            
            val themePair = themePictures[selectedThemeIndex]
            val resourceId = if (isNight) themePair.second else themePair.first
            
            try {
                val outputFile = File(filesDir, "theme_header_pic.jpg")
                
                // Use proper bitmap saving instead of raw resource copying
                val bitmap = android.graphics.BitmapFactory.decodeResource(resources, resourceId)
                FileOutputStream(outputFile).use { outputStream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                    outputStream.flush()
                }
                
                prefs.edit().putString("theme_header_pic_uri", outputFile.absolutePath).apply()
                
                Glide.with(this)
                    .load(Uri.fromFile(outputFile))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(toolbarBackgroundImage)
                
                toolbarBackgroundImage.visibility = View.VISIBLE
                binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                Log.d("THEME_PIC", "Switched predefined theme to ${if (isNight) "night" else "day"} mode")
            } catch (e: Exception) {
                Log.e("THEME_PIC", "Failed to switch predefined theme", e)
            }
        }
        
        // Update navigation drawer header
        updateFreshNavigationDrawerHeader()
    }
    
    private fun updateNavDrawerHeaderBackground(isNight: Boolean) {
        // Update the navigation drawer header background to match the current theme
        val navHeaderBackground = findViewById<ImageView>(R.id.themeBackgroundImage)
        if (navHeaderBackground != null) {
            val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
            
            if (selectedThemeIndex >= 0) {
                // Check if we have day/night theme files saved
                val dayFile = File(filesDir, "theme_header_pic_day.jpg")
                val nightFile = File(filesDir, "theme_header_pic_night.jpg")
                
                val sourceFile = if (isNight) nightFile else dayFile
                if (sourceFile.exists()) {
                    Glide.with(this)
                        .load(Uri.fromFile(sourceFile))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .into(navHeaderBackground)
                    Log.d("NAV_DRAWER", "Updated header background to ${if (isNight) "night" else "day"} mode")
                }
            } else {
                // No theme selected, use solid color background based on day/night mode
                navHeaderBackground.setImageDrawable(null)
                val backgroundColor = if (isNight) ContextCompat.getColor(this, R.color.black) else ContextCompat.getColor(this, R.color.white)
                navHeaderBackground.setBackgroundColor(backgroundColor)
                Log.d("NAV_DRAWER", "No theme selected, using ${if (isNight) "black" else "white"} background")
            }
        }
    }

    private fun updateAppLogo(isNight: Boolean) {
        val appLogo = findViewById<ImageView>(R.id.appLogo)
        if (appLogo != null) {
            val logoDrawable = if (isNight) {
                ContextCompat.getDrawable(this, R.drawable.timeless_textlogo_night)
            } else {
                ContextCompat.getDrawable(this, R.drawable.tymeless_textlogo)
            }
            appLogo.setImageDrawable(logoDrawable)
            Log.d("APP_LOGO", "Updated app logo to ${if (isNight) "night" else "day"} mode")
        } else {
            Log.e("APP_LOGO", "App logo ImageView not found")
        }
    }

    private fun getGreetingMessage(name: String): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11 -> "Good Morning, $name"
            hour in 12..16 -> "Good Afternoon, $name"
            else -> "Good Evening, $name"
        }
    }

    private fun testNavDrawerColors() {
        // Test method to manually toggle colors
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val currentIsNight = themePrefs.getBoolean("is_night_mode", false)
        val newIsNight = !currentIsNight
        
        Log.d("NAV_DRAWER_TEST", "Testing color change from $currentIsNight to $newIsNight")
        updateNavDrawerColors(newIsNight)
        
        // Save the new state
        themePrefs.edit().putBoolean("is_night_mode", newIsNight).apply()
    }

    private fun setupThemeUpdateReceiver() {
        val filter = IntentFilter(ThemeManager.THEME_UPDATE_ACTION)
        themeUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ThemeManager.THEME_UPDATE_ACTION) {
                    Log.d("THEME_UPDATE", "Received theme update broadcast. Applying new theme.")
                    ThemeManager.applyTheme(this@MainActivity)
                    val isNight = ThemeManager.isNightMode(this@MainActivity)
                    updateNavDrawerColors(isNight)
                    updateAppLogo(isNight)
                    updateFreshNavigationDrawerHeader()
                    // Re-apply theme header image if it exists
                    val selectedThemeIndex = prefs.getInt("selected_theme_index", -1)
                    val themeHeaderPicUri = prefs.getString("theme_header_pic_uri", null)
                    Log.d("THEME_UPDATE", "Theme URI: $themeHeaderPicUri, Selected Index: $selectedThemeIndex")
                    
                    if (selectedThemeIndex >= 0 && themeHeaderPicUri != null && File(themeHeaderPicUri).exists()) {
                        // Check if we have day/night theme files saved
                        val dayFile = File(filesDir, "theme_header_pic_day.jpg")
                        val nightFile = File(filesDir, "theme_header_pic_night.jpg")
                        
                        val sourceFile = if (isNight) nightFile else dayFile
                        if (sourceFile.exists()) {
                            Log.d("THEME_UPDATE", "Loading ${if (isNight) "night" else "day"} theme image: ${sourceFile.absolutePath}")
                            Glide.with(this@MainActivity)
                                .load(Uri.fromFile(sourceFile))
                                .centerCrop()
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .into(toolbarBackgroundImage)
                            toolbarBackgroundImage.visibility = View.VISIBLE
                            binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            Log.d("THEME_UPDATE", "Day/night theme image loaded successfully")
                        } else {
                            // Fallback to the saved theme file
                            Log.d("THEME_UPDATE", "Loading fallback theme image: $themeHeaderPicUri")
                            Glide.with(this@MainActivity)
                                .load(Uri.fromFile(File(themeHeaderPicUri)))
                                .centerCrop()
                                .into(toolbarBackgroundImage)
                            toolbarBackgroundImage.visibility = View.VISIBLE
                            binding.appBarMain.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            Log.d("THEME_UPDATE", "Fallback theme image loaded successfully")
                        }
                    } else {
                        Log.d("THEME_UPDATE", "No theme file found, using color")
                        toolbarBackgroundImage.visibility = View.GONE
                        val backgroundColor = if (isNight) ContextCompat.getColor(this@MainActivity, R.color.black) else ContextCompat.getColor(this@MainActivity, R.color.white)
                        binding.appBarMain.toolbar.setBackgroundColor(backgroundColor)
                    }
                }
            }
        }
        registerReceiver(themeUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(themeUpdateReceiver)
    }
}