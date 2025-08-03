package com.example.diaryapp.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.diaryapp.DiaryEntry
import com.example.diaryapp.R
import java.text.SimpleDateFormat
import java.util.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class DiaryEntryAdapter(
    var entries: List<DiaryEntry>,
    private val onEntryClick: (DiaryEntry) -> Unit,
    private val onDeleteClick: (DiaryEntry) -> Unit
) : RecyclerView.Adapter<DiaryEntryAdapter.EntryViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_diary_entry, parent, false)
        return EntryViewHolder(view, onEntryClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount(): Int = entries.size

    fun updateEntries(newEntries: List<DiaryEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    class EntryViewHolder(itemView: View, val onEntryClick: (DiaryEntry) -> Unit, val onDeleteClick: (DiaryEntry) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.entryTitle)
        private val dateText: TextView = itemView.findViewById(R.id.entryDate)
        private val previewText: TextView = itemView.findViewById(R.id.entryPreview)
        private val moodEmoji: TextView = itemView.findViewById(R.id.moodEmoji)
        private val imageLeft: ImageView = itemView.findViewById(R.id.imageLeft)
        private val imageTopRight: ImageView = itemView.findViewById(R.id.imageTopRight)
        private val imageBottomRight: ImageView = itemView.findViewById(R.id.imageBottomRight)
        private val imageBottomRight2: ImageView = itemView.findViewById(R.id.imageBottomRight2)
        private val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
        private val imagesRow: View = itemView.findViewById(R.id.imagesRow)
        private val audioIndicator: ImageView = itemView.findViewById(R.id.audioIndicator)
        private var currentEntry: DiaryEntry? = null

        init {
            itemView.setOnClickListener {
                currentEntry?.let { onEntryClick(it) }
            }
            deleteButton.setOnClickListener {
                currentEntry?.let { onDeleteClick(it) }
            }
        }

        fun bind(entry: DiaryEntry) {
            currentEntry = entry
            titleText.text = entry.title ?: "(No Title)"
            val sdf = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
            dateText.text = sdf.format(Date(entry.date))
            
            // Set mood emoji based on entry mood - handle both old and new systems
            val mood = entry.mood
            val moodEmojiText = when {
                mood <= 1 -> if (mood == 0) "😢" else "😊" // New 2-value system
                mood <= 5 -> "😢" // Old system: 0-5 = sad
                else -> "😊" // Old system: 6-10 = happy
            }
            moodEmoji.text = moodEmojiText
            moodEmoji.visibility = View.VISIBLE
            
            // Show a styled preview using Html.fromHtml, let TextView handle ellipsis
            val spanned = android.text.Html.fromHtml(entry.htmlContent, android.text.Html.FROM_HTML_MODE_LEGACY)
            previewText.text = spanned

            // Images logic
            val imageViews = listOf(imageLeft, imageTopRight, imageBottomRight, imageBottomRight2)
            if (entry.imagePaths.isNotEmpty()) {
                imagesRow.visibility = View.VISIBLE
                
                // Handle single image - make it take full width
                if (entry.imagePaths.size == 1) {
                    // Show only the left image and make it take full width
                    imageLeft.visibility = View.VISIBLE
                    imageTopRight.visibility = View.GONE
                    imageBottomRight.visibility = View.GONE
                    imageBottomRight2.visibility = View.GONE
                    
                    // Set the left image to take full width
                    val cardView = imageLeft.parent as? androidx.cardview.widget.CardView
                    cardView?.let { card ->
                        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.width = 0
                        card.layoutParams = layoutParams
                    }
                    
                    // Hide the right column container
                    val rightColumn = itemView.findViewById<LinearLayout>(R.id.rightColumn)
                    rightColumn?.visibility = View.GONE
                    
                    val path = entry.imagePaths[0]
                    val uri = if (path.startsWith("/")) {
                        val file = java.io.File(path)
                        if (file.exists()) android.net.Uri.fromFile(file) else null
                    } else if (path.startsWith("content://")) {
                        android.net.Uri.parse(path)
                    } else null
                    if (uri != null) {
                                                    Glide.with(imageLeft.context)
                                .load(uri)
                                .transform(com.bumptech.glide.load.resource.bitmap.FitCenter())
                                .placeholder(R.drawable.bg_image_rounded)
                                .error(R.drawable.bg_image_rounded)
                                .into(imageLeft)
                    }
                } else if (entry.imagePaths.size == 2) {
                    // Two images - show them side by side, each taking half width
                    imageLeft.visibility = View.VISIBLE
                    imageTopRight.visibility = View.VISIBLE
                    imageBottomRight.visibility = View.GONE
                    imageBottomRight2.visibility = View.GONE
                    
                    // Show the right column but modify it for 2 images
                    val rightColumn = itemView.findViewById<LinearLayout>(R.id.rightColumn)
                    rightColumn?.visibility = View.VISIBLE
                    
                    // Set both images to take equal width
                    val leftCardView = imageLeft.parent as? androidx.cardview.widget.CardView
                    leftCardView?.let { card ->
                        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.width = 0
                        card.layoutParams = layoutParams
                    }
                    
                    // Modify the right column to show only one image (top right) at full height
                    val topRightCardView = imageTopRight.parent as? androidx.cardview.widget.CardView
                    topRightCardView?.let { card ->
                        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
                        card.layoutParams = layoutParams
                    }
                    
                    // Load the two images
                    for (i in 0..1) {
                        val path = entry.imagePaths[i]
                        val uri = if (path.startsWith("/")) {
                            val file = java.io.File(path)
                            if (file.exists()) android.net.Uri.fromFile(file) else null
                        } else if (path.startsWith("content://")) {
                            android.net.Uri.parse(path)
                        } else null
                        if (uri != null) {
                            val imageView = if (i == 0) imageLeft else imageTopRight
                            Glide.with(imageView.context)
                                .load(uri)
                                .transform(com.bumptech.glide.load.resource.bitmap.FitCenter())
                                .placeholder(R.drawable.bg_image_rounded)
                                .error(R.drawable.bg_image_rounded)
                                .into(imageView)
                        }
                    }
                } else if (entry.imagePaths.size == 3) {
                    // Three images - left takes more space, right column split into top and bottom
                    imageLeft.visibility = View.VISIBLE
                    imageTopRight.visibility = View.VISIBLE
                    imageBottomRight.visibility = View.VISIBLE
                    imageBottomRight2.visibility = View.GONE
                    
                    // Show the right column
                    val rightColumn = itemView.findViewById<LinearLayout>(R.id.rightColumn)
                    rightColumn?.visibility = View.VISIBLE
                    
                    // Set left image to take more space (2/3 of the width)
                    val leftCardView = imageLeft.parent as? androidx.cardview.widget.CardView
                    leftCardView?.let { card ->
                        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 2f
                        layoutParams.width = 0
                        card.layoutParams = layoutParams
                    }
                    
                    // Reset right column images to half height each
                    val topRightCardView = imageTopRight.parent as? androidx.cardview.widget.CardView
                    topRightCardView?.let { card ->
                        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.height = 0
                        card.layoutParams = layoutParams
                    }
                    
                    // For 3 images, the bottom right image should take the entire bottom space
                    val bottomRightContainer = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.bottomRightContainer)
                    bottomRightContainer?.let { container ->
                        val layoutParams = container.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.width = 0
                        container.layoutParams = layoutParams
                    }
                    
                    // Hide the second bottom right image container for 3 images
                    val bottomRightContainer2 = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.bottomRightContainer2)
                    bottomRightContainer2?.visibility = View.GONE
                    
                    // Load the three images
                    for (i in 0..2) {
                        val path = entry.imagePaths[i]
                        val uri = if (path.startsWith("/")) {
                            val file = java.io.File(path)
                            if (file.exists()) android.net.Uri.fromFile(file) else null
                        } else if (path.startsWith("content://")) {
                            android.net.Uri.parse(path)
                        } else null
                        if (uri != null) {
                            val imageView = when (i) {
                                0 -> imageLeft
                                1 -> imageTopRight
                                2 -> imageBottomRight
                                else -> imageLeft
                            }
                            imageView.visibility = View.VISIBLE
                            Glide.with(imageView.context)
                                .load(uri)
                                .transform(CenterCrop())
                                .placeholder(R.drawable.bg_image_rounded)
                                .error(R.drawable.bg_image_rounded)
                                .into(imageView)
                        }
                    }
                } else if (entry.imagePaths.size == 4) {
                    // Four images - left takes more space, right column has 2 stacked images
                    imageLeft.visibility = View.VISIBLE
                    imageTopRight.visibility = View.VISIBLE
                    imageBottomRight.visibility = View.VISIBLE
                    imageBottomRight2.visibility = View.VISIBLE
                    
                    // Show the right column
                    val rightColumn = itemView.findViewById<LinearLayout>(R.id.rightColumn)
                    rightColumn?.visibility = View.VISIBLE
                    
                    // Set left image to take more space (2/3 of the width)
                    val leftCardView = imageLeft.parent as? androidx.cardview.widget.CardView
                    leftCardView?.let { card ->
                        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 2f
                        layoutParams.width = 0
                        card.layoutParams = layoutParams
                    }
                    
                    // Reset right column images to half height each
                    val topRightCardView = imageTopRight.parent as? androidx.cardview.widget.CardView
                    topRightCardView?.let { card ->
                        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.height = 0
                        card.layoutParams = layoutParams
                    }
                    
                    // For 4 images, show both bottom containers side by side
                    val bottomRightContainer = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.bottomRightContainer)
                    bottomRightContainer?.let { container ->
                        val layoutParams = container.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.width = 0
                        container.layoutParams = layoutParams
                    }
                    
                    val bottomRightContainer2 = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.bottomRightContainer2)
                    bottomRightContainer2?.let { container ->
                        container.visibility = View.VISIBLE
                        val layoutParams = container.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.width = 0
                        container.layoutParams = layoutParams
                    }
                    
                    // Load the four images
                    for (i in 0..3) {
                        val path = entry.imagePaths[i]
                        val uri = if (path.startsWith("/")) {
                            val file = java.io.File(path)
                            if (file.exists()) android.net.Uri.fromFile(file) else null
                        } else if (path.startsWith("content://")) {
                            android.net.Uri.parse(path)
                        } else null
                        if (uri != null) {
                            val imageView = when (i) {
                                0 -> imageLeft
                                1 -> imageTopRight
                                2 -> imageBottomRight
                                3 -> imageBottomRight2
                                else -> imageLeft
                            }
                            imageView.visibility = View.VISIBLE
                            Glide.with(imageView.context)
                                .load(uri)
                                .transform(com.bumptech.glide.load.resource.bitmap.FitCenter())
                                .placeholder(R.drawable.bg_image_rounded)
                                .error(R.drawable.bg_image_rounded)
                                .into(imageView)
                        }
                    }
                } else {
                    // Five or more images - use the original layout with first 3 images
                    // Show the right column
                    val rightColumn = itemView.findViewById<LinearLayout>(R.id.rightColumn)
                    rightColumn?.visibility = View.VISIBLE
                    
                    // Reset the left image to half width
                    val cardView = imageLeft.parent as? androidx.cardview.widget.CardView
                    cardView?.let { card ->
                        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
                        layoutParams.weight = 1f
                        layoutParams.width = 0
                        card.layoutParams = layoutParams
                    }
                    
                for (i in imageViews.indices) {
                    if (entry.imagePaths.size > i) {
                        val path = entry.imagePaths[i]
                        val uri = if (path.startsWith("/")) {
                            val file = java.io.File(path)
                            if (file.exists()) android.net.Uri.fromFile(file) else null
                        } else if (path.startsWith("content://")) {
                            android.net.Uri.parse(path)
                        } else null
                        if (uri != null) {
                            imageViews[i].visibility = View.VISIBLE
                            Glide.with(imageViews[i].context)
                                .load(uri)
                                    .transform(com.bumptech.glide.load.resource.bitmap.FitCenter())
                                .placeholder(R.drawable.bg_image_rounded)
                                .error(R.drawable.bg_image_rounded)
                                .into(imageViews[i])
                        } else {
                            imageViews[i].visibility = View.GONE
                        }
                    } else {
                        imageViews[i].visibility = View.GONE
                        }
                    }
                }
            } else {
                imagesRow.visibility = View.GONE
                imageViews.forEach { it.visibility = View.GONE }
            }

            // Audio indicator logic: show microphone icon if there is at least one audio item
            val firstAudio = entry.audioList.firstOrNull()
            if (firstAudio != null) {
                audioIndicator.visibility = View.VISIBLE
            } else {
                audioIndicator.visibility = View.GONE
            }
        }
    }
} 