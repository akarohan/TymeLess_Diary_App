package com.example.diaryapp

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import android.util.Log

class ChecklistAdapter(
    private val checklistItems: MutableList<ChecklistItem>,
    private val onItemChanged: () -> Unit
) : RecyclerView.Adapter<ChecklistAdapter.ChecklistViewHolder>() {

    private var onStartDragListener: OnStartDragListener? = null

    interface OnStartDragListener {
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }

    fun setOnStartDragListener(listener: OnStartDragListener) {
        onStartDragListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChecklistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_checklist, parent, false)
        return ChecklistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChecklistViewHolder, position: Int) {
        val item = checklistItems[position]
        holder.bind(item, position)
    }

    override fun getItemCount(): Int = checklistItems.size

    fun addItem() {
        val newItem = ChecklistItem(text = "", isChecked = false)
        checklistItems.add(newItem)
        notifyItemInserted(checklistItems.size - 1)
        
        // FIXED: Trigger save immediately when item is added
        onItemChanged()
        Log.d("CHECKLIST_DEBUG", "New checklist item added - triggering immediate save")
    }

    fun removeItem(position: Int) {
        if (position in 0 until checklistItems.size) {
            checklistItems.removeAt(position)
            notifyItemRemoved(position)
            onItemChanged()
        }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                checklistItems[i] = checklistItems.set(i + 1, checklistItems[i])
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                checklistItems[i] = checklistItems.set(i - 1, checklistItems[i])
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onItemChanged()
    }

    fun getChecklistItems(): List<ChecklistItem> = checklistItems.toList()

    inner class ChecklistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        private val checklistText: EditText = itemView.findViewById(R.id.checklistText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(item: ChecklistItem, position: Int) {
            // Set checkbox state
            checkBox.isChecked = item.isChecked
            
            // Set text (avoid triggering text watcher during binding)
            checklistText.removeTextChangedListener(textWatcher)
            checklistText.setText(item.text)
            checklistText.addTextChangedListener(textWatcher)
            
            // Update text style based on checkbox state
            updateTextStyle()

            // Set up checkbox listener
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.isChecked = isChecked
                updateTextStyle()
                onItemChanged()
            }

            // Set up drag handle
            dragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onStartDragListener?.onStartDrag(this)
                }
                false
            }

            // Set up delete button
            deleteButton.setOnClickListener {
                removeItem(adapterPosition)
            }

            // Set up text watcher
            checklistText.tag = position
            
            // FIXED: Add focus listener to save when user focuses on checklist item
            checklistText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    Log.d("CHECKLIST_DEBUG", "Checklist item $position focused - triggering save")
                    onItemChanged()
                }
            }
            
            // Handle Enter key press to add new checklist item
                    checklistText.setOnKeyListener { _, keyCode, event ->
                        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN) {
                            // Add new checklist item below current one
                            val currentPosition = adapterPosition
                            if (currentPosition != RecyclerView.NO_POSITION) {
                                val newItem = ChecklistItem(text = "", isChecked = false)
                                checklistItems.add(currentPosition + 1, newItem)
                                notifyItemInserted(currentPosition + 1)
                                onItemChanged()

                                // Focus on the new item's text field with a delay to ensure it's created
                                (itemView.parent as? RecyclerView)?.postDelayed({
                                    val newViewHolder = (itemView.parent as? RecyclerView)?.findViewHolderForAdapterPosition(currentPosition + 1)
                                    val newEditText = (newViewHolder as? ChecklistViewHolder)?.checklistText
                                    newEditText?.requestFocus()
                                    
                                    // Show keyboard after focus
                                    newEditText?.postDelayed({
                                        val imm = itemView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.showSoftInput(newEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                    }, 100)
                                }, 50)
                            }
                            true
                        } else {
                            false
                        }
                    }
        }

        private val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // FIXED: Save immediately when user starts typing
                val position = checklistText.tag as? Int
                if (position != null && position in 0 until checklistItems.size) {
                    checklistItems[position].text = s?.toString() ?: ""
                    onItemChanged()
                    Log.d("CHECKLIST_DEBUG", "Text changing for item $position: '${s?.toString()}'")
                }
            }
            override fun afterTextChanged(s: Editable?) {
                val position = checklistText.tag as? Int
                if (position != null && position in 0 until checklistItems.size) {
                    checklistItems[position].text = s?.toString() ?: ""
                    // FIXED: Call onItemChanged immediately when text changes
                    onItemChanged()
                    Log.d("CHECKLIST_DEBUG", "Text changed for item $position: '${s?.toString()}'")
                }
            }
        }
        
        private fun updateTextStyle() {
            val isChecked = checkBox.isChecked
            
            // Get dynamic text color based on theme
            val isNightMode = itemView.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val normalTextColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            val checkedTextColor = android.graphics.Color.GRAY // Keep gray for checked items in both themes
            
            if (isChecked) {
                // Apply strikethrough
                checklistText.paintFlags = checklistText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                checklistText.setTextColor(checkedTextColor)
            } else {
                // Remove strikethrough
                checklistText.paintFlags = checklistText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                checklistText.setTextColor(normalTextColor)
            }
        }
    }
}

class ChecklistItemTouchHelper(
    private val adapter: ChecklistAdapter
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used for swipe
    }
} 