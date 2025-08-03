package com.example.diaryapp.ui.home

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.diaryapp.R
import com.example.diaryapp.data.Note

class NotesAdapter(
    private var notes: List<Note>,
    private val onNoteClick: (Note) -> Unit,
    private val onNoteDelete: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {
    override fun getItemViewType(position: Int): Int {
        return when (notes[position].noteType) {
            "P" -> 1
            "A" -> 2
            else -> 0
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val layout = when (viewType) {
            1 -> R.layout.item_pnote_card
            2 -> R.layout.item_anote_card
            else -> R.layout.item_note_card
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return NoteViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        Log.d("NotesAdapter", "Binding position $position, title=${notes[position].title}")
        holder.bind(notes[position])
    }

    override fun getItemCount(): Int = notes.size

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    private fun parseContentForDisplay(content: String): String {
        try {
            // Clean up any old format data first
            val cleanedContent = cleanOldFormatData(content)
            
            // Check if content contains checklist items
            if (cleanedContent.contains("CHECKLIST:")) {
                // Extract regular text content (before checklist)
                val textContent = cleanedContent.replace(Regex("CHECKLIST:.+"), "").trim()
                
                // Extract checklist items
                val checklistMatch = Regex("CHECKLIST:(.+)").find(cleanedContent)
                if (checklistMatch != null) {
                    val checklistData = checklistMatch.groupValues[1]
                    if (checklistData.isNotEmpty()) {
                        val items = checklistData.split("|")
                        val bulletPoints = items.mapNotNull { item ->
                            try {
                                if (item.isNotEmpty()) {
                                    val parts = item.split(":", limit = 2)
                                    if (parts.size == 2) {
                                        val text = parts[0].replace("\\:", ":").replace("\\|", "|")
                                        val isChecked = parts[1] == "1"
                                        val checkboxSymbol = if (isChecked) "☑" else "☐"
                                        "$checkboxSymbol ${text.trim()}"
                                    } else null
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        // Combine text content with bullet points
                        return if (textContent.isNotEmpty()) {
                            "$textContent\n\n${bulletPoints.joinToString("\n")}"
                        } else {
                            bulletPoints.joinToString("\n")
                        }
                    }
                }
            }
            
            // If no checklist found, return original content
            return cleanedContent
        } catch (e: Exception) {
            android.util.Log.e("NotesAdapter", "Error parsing content for display", e)
            return content
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

    inner class NoteViewHolder(itemView: View, private val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = when (viewType) {
            1 -> itemView.findViewById(R.id.pnoteTitle)
            2 -> itemView.findViewById(R.id.anoteTitle)
            else -> itemView.findViewById(R.id.noteTitle)
        }
        private val content: TextView = when (viewType) {
            1 -> itemView.findViewById(R.id.pnoteContent)
            2 -> itemView.findViewById(R.id.anoteContent)
            else -> itemView.findViewById(R.id.noteContent)
        }
        private val btnDelete: ImageView? = when (viewType) {
            1 -> itemView.findViewById(R.id.btnDeletePNote)
            2 -> itemView.findViewById(R.id.btnDeleteANote)
            else -> itemView.findViewById(R.id.btnDeleteNote)
        }
        // For A note: single image and container
        private val anoteImage: ImageView? = if (viewType == 2) itemView.findViewById(R.id.anoteImage) else null
        private val anoteImageContainer: View? = if (viewType == 2) itemView.findViewById(R.id.anoteImageContainer) else null

        fun bind(note: Note) {
            title.text = note.title
            // Hide content for password protected cards (A and P)
            if (note.noteType == "A" || note.noteType == "P") {
                content.text = "••••••••••••••••••••"
                content.setTextColor(android.graphics.Color.GRAY)
            } else {
                // Parse content and convert checklist items to bullet points
                val displayContent = parseContentForDisplay(note.content)
                content.text = displayContent
                content.setTextColor(android.graphics.Color.parseColor("#222222"))
            }
            // Show thumbnail or image for A notes
            if (note.noteType == "A" && anoteImage != null && anoteImageContainer != null) {
                android.util.Log.d("NotesAdapter", "=== PROCESSING A-NOTE ===")
                android.util.Log.d("NotesAdapter", "Note ID: ${note.id}")
                android.util.Log.d("NotesAdapter", "Note title: ${note.title}")
                android.util.Log.d("NotesAdapter", "Image paths: ${note.imagePaths}")
                android.util.Log.d("NotesAdapter", "Thumbnail path: ${note.thumbnailPath}")
                android.util.Log.d("NotesAdapter", "Thumbnail path is null: ${note.thumbnailPath == null}")
                
                // Priority: thumbnail first, then first image from imagePaths
                val pathToShow = note.thumbnailPath ?: note.imagePaths.firstOrNull()
                android.util.Log.d("NotesAdapter", "Final path to show: $pathToShow")
                android.util.Log.d("NotesAdapter", "Path source: ${if (note.thumbnailPath != null) "THUMBNAIL" else "REGULAR_IMAGE"}")
                
                if (pathToShow != null) {
                    anoteImageContainer.visibility = View.VISIBLE
                    android.util.Log.d("NotesAdapter", "Loading image from path: $pathToShow")
                    
                    // Handle both relative and absolute paths
                    val file = if (pathToShow.startsWith("/")) {
                        // Absolute path
                        java.io.File(pathToShow)
                    } else {
                        // Relative path (filename only) - construct full path
                        java.io.File(anoteImage.context.filesDir, pathToShow)
                    }
                    
                    android.util.Log.d("NotesAdapter", "Full file path: ${file.absolutePath}")
                    android.util.Log.d("NotesAdapter", "File exists: ${file.exists()}")
                    
                    val uri = when {
                        file.exists() -> android.net.Uri.fromFile(file)
                        pathToShow.startsWith("content://") -> android.net.Uri.parse(pathToShow)
                        else -> null
                    }
                    android.util.Log.d("NotesAdapter", "Final URI: $uri")
                    if (uri != null) {
                        com.bumptech.glide.Glide.with(anoteImage.context)
                            .load(uri)
                            .placeholder(com.example.diaryapp.R.drawable.bg_image_rounded)
                            .error(com.example.diaryapp.R.drawable.bg_image_rounded)
                            .centerCrop()
                            .into(anoteImage)
                        android.util.Log.d("NotesAdapter", "Image loaded successfully")
                    } else {
                        anoteImage.setImageResource(com.example.diaryapp.R.drawable.bg_image_rounded)
                        android.util.Log.d("NotesAdapter", "Using placeholder image")
                    }
                } else {
                    anoteImageContainer.visibility = View.GONE
                    android.util.Log.d("NotesAdapter", "No thumbnail or image paths, hiding container")
                }
            }
            itemView.setOnClickListener { onNoteClick(note) }
            btnDelete?.setOnClickListener { onNoteDelete(note) }
        }
    }
} 