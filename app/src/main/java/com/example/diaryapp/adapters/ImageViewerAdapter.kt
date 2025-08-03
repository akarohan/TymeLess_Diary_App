package com.example.diaryapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.example.diaryapp.R

class ImageViewerAdapter(private val imagePaths: ArrayList<String>) : 
    RecyclerView.Adapter<ImageViewerAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_viewer, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imagePaths[position])
    }

    override fun getItemCount(): Int = imagePaths.size

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.fullScreenImage)

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
                    .transform(FitCenter())
                    .into(imageView)
            }
        }
    }
} 