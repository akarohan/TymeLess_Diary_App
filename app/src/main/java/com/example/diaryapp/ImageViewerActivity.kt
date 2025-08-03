package com.example.diaryapp

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.diaryapp.adapters.ImageViewerAdapter

class ImageViewerActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private var imagePaths: ArrayList<String> = ArrayList()
    private var initialPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        // Get data from intent
        imagePaths = intent.getStringArrayListExtra("image_paths") ?: ArrayList()
        initialPosition = intent.getIntExtra("initial_position", 0)

        // Initialize views
        viewPager = findViewById(R.id.viewPager)

        // Setup ViewPager
        val adapter = ImageViewerAdapter(imagePaths)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(initialPosition, false)
    }
} 