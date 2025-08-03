package com.example.diaryapp

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MountainsThemeAdapter(
    private val onThemeClick: (MountainsThemeActivity.MountainTheme) -> Unit
) : ListAdapter<MountainsThemeActivity.MountainTheme, MountainsThemeAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mountain_theme, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val dayThemeImage: ImageView = itemView.findViewById(R.id.dayThemeImage)
        private val nightThemeImage: ImageView = itemView.findViewById(R.id.nightThemeImage)
        private val themeName: TextView = itemView.findViewById(R.id.themeName)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onThemeClick(getItem(position))
                }
            }
        }

        fun bind(mountainTheme: MountainsThemeActivity.MountainTheme) {
            themeName.text = mountainTheme.name
            
            // Load both day and night images based on theme name
            when (mountainTheme.name) {
                "Icemountain" -> {
                    Glide.with(itemView.context)
                        .load(R.drawable.bg_icemountain_optimized)
                        .into(dayThemeImage)
                        
                    Glide.with(itemView.context)
                        .load(R.drawable.bg_icemountain_night_optimized)
                        .into(nightThemeImage)
                }
                "Cherry Blossom" -> {
                    Glide.with(itemView.context)
                        .load(R.drawable.bg_mountain_blossom_optimized)
                        .into(dayThemeImage)
                        
                    Glide.with(itemView.context)
                        .load(R.drawable.bg_mountain_blossom_night_optimized)
                        .into(nightThemeImage)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MountainsThemeActivity.MountainTheme>() {
        override fun areItemsTheSame(
            oldItem: MountainsThemeActivity.MountainTheme,
            newItem: MountainsThemeActivity.MountainTheme
        ): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(
            oldItem: MountainsThemeActivity.MountainTheme,
            newItem: MountainsThemeActivity.MountainTheme
        ): Boolean {
            return oldItem == newItem
        }
    }
} 