package com.example.diaryapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.navigation.fragment.findNavController
import com.example.diaryapp.R
import com.example.diaryapp.databinding.FragmentNotesHomeBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.os.Handler
import android.os.Looper
import com.example.diaryapp.ThemeUtils
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import android.widget.EditText
import android.widget.Button
import android.view.LayoutInflater as AndroidLayoutInflater
import com.example.diaryapp.ThemeManager
import android.widget.LinearLayout

class NotesHomeFragment : Fragment() {
    private var _binding: FragmentNotesHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var notesViewModel: NotesHomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        
        // Apply theme to prevent flashing
        ThemeManager.applyTheme(requireActivity())
        
        // Setup RecyclerView for notes
        notesAdapter = NotesAdapter(
            emptyList(),
            onNoteClick = { note ->
                // Check if note requires password protection
                if (note.noteType == "A" || note.noteType == "P") {
                    showPasswordDialog(note)
                } else {
                    // Open NotesViewerActivity for viewing notes
                    openNotesViewerActivity(note)
                }
            },
            onNoteDelete = { note ->
                if (note.noteType == "A" || note.noteType == "P") {
                    // Password protected notes - show password dialog
                    showPasswordProtectedDeleteDialog(note)
                } else {
                    // Regular notes - show normal confirmation dialog
                    showNormalDeleteDialog(note)
                }
            }
        )
        binding.notesRecyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.notesRecyclerView.adapter = notesAdapter

        // Setup Guide Icons Click Listeners
        setupGuideIcons()

        // Setup ViewModel
        notesViewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)).get(NotesHomeViewModel::class.java)
        notesViewModel.notes.observe(viewLifecycleOwner) { notes ->
            Log.d("NotesHomeFragment", "Notes updated: count=${notes.size}, titles=${notes.map { it.title }}")
            notesAdapter.updateNotes(notes)
        }

        // --- Bottom Notch Navigation Setup ---
        val notchInclude = binding.root.findViewById<View>(R.id.bottomNotchNavInclude)
        val diaryTab = notchInclude.findViewById<View>(R.id.diaryTab)
        val notesTab = notchInclude.findViewById<View>(R.id.notesTab)
        val diaryIcon = notchInclude.findViewById<android.widget.ImageView>(R.id.diaryIcon)
        val notesIcon = notchInclude.findViewById<android.widget.ImageView>(R.id.notesIcon)
        val diaryLabel = notchInclude.findViewById<android.widget.TextView>(R.id.diaryLabel)
        val notesLabel = notchInclude.findViewById<android.widget.TextView>(R.id.notesLabel)

        // Set Notes as selected by default
        diaryTab.isSelected = false
        notesTab.isSelected = true
        diaryIcon.setColorFilter(android.graphics.Color.BLACK)
        diaryLabel.setTextColor(android.graphics.Color.BLACK)
        notesIcon.setColorFilter(android.graphics.Color.WHITE)
        notesLabel.setTextColor(android.graphics.Color.WHITE)

        diaryTab.setOnClickListener {
            // Animate the transition smoothly
            animateToggleTransition(diaryTab, notesTab, diaryIcon, notesIcon, diaryLabel, notesLabel, true)
            // Navigate after a short delay to allow animation to start
            diaryTab.postDelayed({
                findNavController().navigate(R.id.nav_home)
            }, 100)
        }
        notesTab.setOnClickListener {
            // Already on Notes, just animate the UI
            animateToggleTransition(diaryTab, notesTab, diaryIcon, notesIcon, diaryLabel, notesLabel, false)
        }
        return root
    }
    
    private fun animateToggleTransition(
        diaryTab: View, 
        notesTab: View, 
        diaryIcon: android.widget.ImageView, 
        notesIcon: android.widget.ImageView, 
        diaryLabel: android.widget.TextView, 
        notesLabel: android.widget.TextView, 
        selectDiary: Boolean
    ) {
        val duration = 300L
        
        if (selectDiary) {
            // Animate to Diary selected
            diaryTab.isSelected = true
            notesTab.isSelected = false
            
            // Animate diary elements to white
            diaryIcon.animate().setDuration(duration).alpha(1f).start()
            diaryLabel.animate().setDuration(duration).alpha(1f).start()
            
            // Animate notes elements to black
            notesIcon.animate().setDuration(duration).alpha(0.7f).start()
            notesLabel.animate().setDuration(duration).alpha(0.7f).start()
            
            // Set colors with animation
            diaryIcon.setColorFilter(android.graphics.Color.WHITE)
            diaryLabel.setTextColor(android.graphics.Color.WHITE)
            notesIcon.setColorFilter(android.graphics.Color.BLACK)
            notesLabel.setTextColor(android.graphics.Color.BLACK)
        } else {
            // Animate to Notes selected
            diaryTab.isSelected = false
            notesTab.isSelected = true
            
            // Animate diary elements to black
            diaryIcon.animate().setDuration(duration).alpha(0.7f).start()
            diaryLabel.animate().setDuration(duration).alpha(0.7f).start()
            
            // Animate notes elements to white
            notesIcon.animate().setDuration(duration).alpha(1f).start()
            notesLabel.animate().setDuration(duration).alpha(1f).start()
            
            // Set colors with animation
            diaryIcon.setColorFilter(android.graphics.Color.BLACK)
            diaryLabel.setTextColor(android.graphics.Color.BLACK)
            notesIcon.setColorFilter(android.graphics.Color.WHITE)
            notesLabel.setTextColor(android.graphics.Color.WHITE)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh theme when returning to fragment
        ThemeManager.applyTheme(requireActivity())
    }

    private fun showPasswordDialog(note: com.example.diaryapp.data.Note) {
        val dialogView = AndroidLayoutInflater.from(requireContext()).inflate(R.layout.dialog_password_protection, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)
        val submitButton = dialogView.findViewById<Button>(R.id.submitButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        submitButton.setOnClickListener {
            val password = passwordInput.text.toString()
            if (validatePassword(password)) {
                dialog.dismiss()
                openNotesViewerActivity(note)
            } else {
                Toast.makeText(requireContext(), "Incorrect password", Toast.LENGTH_SHORT).show()
                passwordInput.text.clear()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(android.graphics.Color.WHITE)
    }

    private fun validatePassword(password: String): Boolean {
        val prefs = getEncryptedPrefs()
        val savedPasswordHash = prefs.getString("password_hash", null)
        return savedPasswordHash != null && hash(password) == savedPasswordHash
    }

    private fun showPasswordProtectedDeleteDialog(note: com.example.diaryapp.data.Note) {
        val dialogView = AndroidLayoutInflater.from(requireContext()).inflate(R.layout.dialog_password_protection, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)
        val submitButton = dialogView.findViewById<Button>(R.id.submitButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        submitButton.setOnClickListener {
            val password = passwordInput.text.toString()
            if (validatePassword(password)) {
                dialog.dismiss()
                // Password correct - proceed with deletion
                Log.d("DeleteNote", "Password correct, deleting note with id=${note.id}")
                val deletedNote = note.copy(deletedAt = System.currentTimeMillis())
                Log.d("DeleteNote", "Soft deleted note: $deletedNote")
                notesViewModel.update(deletedNote)
                // Show Snackbar: Moved to Recycle Bin
                val rootView = requireActivity().findViewById<View>(android.R.id.content)
                com.google.android.material.snackbar.Snackbar.make(rootView, "Moved to Recycle Bin", 3000).show()
            } else {
                Toast.makeText(requireContext(), "Incorrect password", Toast.LENGTH_SHORT).show()
                passwordInput.text.clear()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(android.graphics.Color.WHITE)
    }

    private fun showNormalDeleteDialog(note: com.example.diaryapp.data.Note) {
        // Show confirmation dialog for regular notes
        val dialogView = AndroidLayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_message, null)
        val messageText = dialogView.findViewById<android.widget.TextView>(R.id.dialogMessage)
        
        // Set dynamic message based on note type
        val message = when (note.noteType) {
            "P" -> "Do you want to delete this password protected note?"
            "A" -> "Do you want to delete this audio note?"
            else -> "Do you want to delete this note?"
        }
        messageText.text = message
        
        // Set dynamic text color based on theme
        val isNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        messageText.setTextColor(textColor)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Create custom buttons with proper styling
        val positiveButton = dialogView.findViewById<android.widget.Button>(R.id.positiveButton)
        val negativeButton = dialogView.findViewById<android.widget.Button>(R.id.negativeButton)
        
        // Set button text colors based on theme
        positiveButton?.setTextColor(textColor)
        negativeButton?.setTextColor(textColor)
        
        positiveButton?.setOnClickListener {
            Log.d("DeleteNote", "Deleting note with id=${note.id}")
            // Soft delete: set deletedAt and update note
            val deletedNote = note.copy(deletedAt = System.currentTimeMillis())
            Log.d("DeleteNote", "Soft deleted note: $deletedNote")
            notesViewModel.update(deletedNote)
            // Show Snackbar: Moved to Recycle Bin
            val rootView = requireActivity().findViewById<View>(android.R.id.content)
            com.google.android.material.snackbar.Snackbar.make(rootView, "Moved to Recycle Bin", 3000).show()
            dialog.dismiss()
        }
        
        negativeButton?.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun openEditNoteActivity(note: com.example.diaryapp.data.Note) {
        val intent = Intent(requireContext(), com.example.diaryapp.ui.home.EditNoteActivity::class.java)
        intent.putExtra("note_id", note.id)
        intent.putExtra("note_title", note.title)
        intent.putExtra("note_content", note.content)
        intent.putExtra("note_type", note.noteType)
        startActivity(intent)
    }

    private fun openNotesViewerActivity(note: com.example.diaryapp.data.Note) {
        val intent = Intent(requireContext(), com.example.diaryapp.NotesViewerActivity::class.java)
        intent.putExtra("note_id", note.id)
        startActivity(intent)
    }

    private fun getEncryptedPrefs() = EncryptedSharedPreferences.create(
        "diary_auth_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        requireContext(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun setupGuideIcons() {
        // Find the guide card and its icon containers
        val guideCard = binding.guideCard
        val guideCardLayout = guideCard.getChildAt(0) as LinearLayout
        val iconsContainer = guideCardLayout.getChildAt(0) as LinearLayout
        
        // Get the three icon containers (General, Accounts, Private)
        val generalContainer = iconsContainer.getChildAt(0) as LinearLayout
        val accountsContainer = iconsContainer.getChildAt(1) as LinearLayout
        val privateContainer = iconsContainer.getChildAt(2) as LinearLayout
        
        // Set click listeners for each guide icon
        generalContainer.setOnClickListener {
            val intent = Intent(requireContext(), com.example.diaryapp.ui.home.EditNoteActivity::class.java)
            startActivity(intent)
        }
        
        accountsContainer.setOnClickListener {
            val intent = Intent(requireContext(), com.example.diaryapp.ui.home.EditNoteActivity::class.java)
            intent.putExtra("note_type", "A")
            startActivity(intent)
        }
        
        privateContainer.setOnClickListener {
            val intent = Intent(requireContext(), com.example.diaryapp.ui.home.EditNoteActivity::class.java)
            intent.putExtra("note_type", "P")
            startActivity(intent)
        }
        
        // Add visual feedback for clicks
        generalContainer.isClickable = true
        generalContainer.isFocusable = true
        generalContainer.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")),
            null,
            null
        )
        
        accountsContainer.isClickable = true
        accountsContainer.isFocusable = true
        accountsContainer.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")),
            null,
            null
        )
        
        privateContainer.isClickable = true
        privateContainer.isFocusable = true
        privateContainer.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336")),
            null,
            null
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 