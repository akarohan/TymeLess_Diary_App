package com.example.diaryapp

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.diaryapp.R
import com.bumptech.glide.Glide
import android.view.ViewGroup.LayoutParams

class ImageBlockAdapter(
    private val imageUris: List<Uri>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ImageBlockAdapter.ImageBlockViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageBlockViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_block, parent, false)
        return ImageBlockViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageBlockViewHolder, position: Int) {
        val uri = imageUris[position]
        holder.bind(uri)
        holder.imageDelete.setOnClickListener {
            android.util.Log.d("ImageBlockAdapter", "Delete button clicked for position: $position")
            onRemove(position)
        }
    }

    override fun getItemCount(): Int = imageUris.size

    class ImageBlockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val imageDelete: ImageButton = itemView.findViewById(R.id.imageDelete)
        fun bind(uri: Uri) {
            val finalUri = if (uri.scheme == null || uri.scheme == "file") {
                // If the Uri is a file path or has no scheme, use fromFile
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) {
                    android.util.Log.d("ImageBlockAdapter", "Loading image from file: ${file.absolutePath}")
                    Uri.fromFile(file)
                } else {
                    android.util.Log.e("ImageBlockAdapter", "File does not exist: ${file.absolutePath}")
                    uri // Return original URI even if file doesn't exist
                }
            } else {
                uri
            }
            
            Glide.with(imageView.context)
                .load(finalUri)
                .placeholder(R.drawable.bg_image_rounded)
                .error(R.drawable.bg_image_rounded)
                .into(imageView)
        }
    }
} 