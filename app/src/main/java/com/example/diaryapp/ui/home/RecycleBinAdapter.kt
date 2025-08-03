package com.example.diaryapp.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.diaryapp.DiaryEntry
import com.example.diaryapp.R
import com.example.diaryapp.data.Note as DataNote

sealed class RecycleBinItem {
    data class Diary(val entry: DiaryEntry) : RecycleBinItem()
    data class Note(val note: DataNote) : RecycleBinItem()
}

class RecycleBinAdapter(
    private val items: List<RecycleBinItem>,
    private val onRestore: (RecycleBinItem) -> Unit,
    private val onDelete: (RecycleBinItem) -> Unit
) : RecyclerView.Adapter<RecycleBinAdapter.BinViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recycle_bin_card, parent, false)
        return BinViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: BinViewHolder, position: Int) {
        holder.bind(items[position], onRestore, onDelete)
    }

    class BinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.recycleCard)
        private val label: TextView = itemView.findViewById(R.id.recycleLabel)
        private val date: TextView = itemView.findViewById(R.id.recycleDate)
        private val restore: ImageView = itemView.findViewById(R.id.recycleRestore)
        private val delete: ImageView = itemView.findViewById(R.id.recycleDelete)

        fun bind(item: RecycleBinItem, onRestore: (RecycleBinItem) -> Unit, onDelete: (RecycleBinItem) -> Unit) {
            when (item) {
                is RecycleBinItem.Diary -> {
                    label.text = "Diary Entry"
                    date.visibility = View.VISIBLE
                    date.text = android.text.format.DateFormat.format("d MMMM yyyy", item.entry.date)
                    // Use default card color for recycle bin
                    card.setCardBackgroundColor(0xFFBDBDBD.toInt()) // neutral grey
                }
                is RecycleBinItem.Note -> {
                    label.text = "Note"
                    date.visibility = View.GONE
                    // Use the same colors as main notes UI
                    val color = when (item.note.noteType) {
                        "N" -> 0xFFA8E639.toInt() // green
                        "P" -> 0xFFFF5252.toInt() // red
                        "A" -> 0xFF4FC3F7.toInt() // sky blue
                        else -> 0xFFBDBDBD.toInt() // grey
                    }
                    card.setCardBackgroundColor(color)
                }
            }
            restore.setOnClickListener { onRestore(item) }
            delete.setOnClickListener { onDelete(item) }
        }
    }
} 