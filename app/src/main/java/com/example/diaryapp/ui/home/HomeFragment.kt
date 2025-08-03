package com.example.diaryapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.diaryapp.DiaryEntry
import com.example.diaryapp.EditEntryActivity
import com.example.diaryapp.DiaryViewerActivity
import com.example.diaryapp.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.Snackbar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import de.hdodenhof.circleimageview.CircleImageView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.diaryapp.R
import androidx.navigation.fragment.findNavController
import com.example.diaryapp.ThemeUtils
import android.util.Log
import com.example.diaryapp.ThemeManager
import android.content.SharedPreferences

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DiaryEntryAdapter
    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel =
            ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        
        // Apply theme to prevent flashing
        ThemeManager.applyTheme(requireActivity())
        
        // Set theme color only on the app bar (header)
        // val themeColor = ThemeUtils.getCurrentThemeColor(requireActivity())
        // binding.toolbar.setBackgroundColor(themeColor)
        
        val swipeRefreshLayout = binding.swipeRefreshLayout
        recyclerView = binding.diaryRecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = DiaryEntryAdapter(emptyList(), { entry ->
            val intent = Intent(requireContext(), DiaryViewerActivity::class.java)
            intent.putExtra("entry_date", entry.date)
            startActivity(intent)
        }, { entry ->
            // Show confirmation dialog before deleting
            val context = requireContext()
            val dialogView = android.view.LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_message, null)
            val messageText = dialogView.findViewById<android.widget.TextView>(R.id.dialogMessage)
            messageText.text = "Do you want to delete this Page ?"
            
            // Set dynamic text color based on theme
            val isNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val textColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            messageText.setTextColor(textColor)
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
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
                dialog.dismiss()
                // Soft delete: set deletedAt and update entry
                Log.d("DeleteDiaryEntry", "Deleting entry with id=${entry.id}, date=${entry.date}")
                lifecycleScope.launch {
                    val db = com.example.diaryapp.DiaryDatabase.getDatabase(context)
                    val deletedEntry = entry.copy(deletedAt = System.currentTimeMillis())
                    Log.d("DeleteDiaryEntry", "Soft deleted entry: $deletedEntry")
                    db.diaryEntryDao().insertOrUpdate(deletedEntry)
                    homeViewModel.loadEntries()
                    // Show Snackbar
                    val rootView = requireActivity().findViewById<View>(android.R.id.content)
                    com.google.android.material.snackbar.Snackbar.make(rootView, "Moved to Recycle Bin", 3000).show()
                }
            }
            
            negativeButton?.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        })
        recyclerView.adapter = adapter

        homeViewModel.entries.observe(viewLifecycleOwner) {
            adapter.updateEntries(it)
        }
        homeViewModel.loadEntries()

        swipeRefreshLayout.setOnRefreshListener {
            homeViewModel.loadEntries()
            swipeRefreshLayout.isRefreshing = false
        }

        val fab = binding.addEntryFab
        fab.setOnClickListener {
            val intent = Intent(requireContext(), EditEntryActivity::class.java)
            // No extras: new blank entry
            startActivity(intent)
        }

        // --- Bottom Notch Navigation Setup ---
        val notchInclude = binding.root.findViewById<View>(R.id.bottomNotchNavInclude)
        val diaryTab = notchInclude.findViewById<View>(R.id.diaryTab)
        val notesTab = notchInclude.findViewById<View>(R.id.notesTab)
        val diaryIcon = notchInclude.findViewById<android.widget.ImageView>(R.id.diaryIcon)
        val notesIcon = notchInclude.findViewById<android.widget.ImageView>(R.id.notesIcon)
        val diaryLabel = notchInclude.findViewById<android.widget.TextView>(R.id.diaryLabel)
        val notesLabel = notchInclude.findViewById<android.widget.TextView>(R.id.notesLabel)

        // Set Diary as selected by default
        diaryTab.isSelected = true
        notesTab.isSelected = false
        diaryIcon.setColorFilter(android.graphics.Color.WHITE)
        diaryLabel.setTextColor(android.graphics.Color.WHITE)
        notesIcon.setColorFilter(android.graphics.Color.BLACK)
        notesLabel.setTextColor(android.graphics.Color.BLACK)

        diaryTab.setOnClickListener {
            // Already on Diary, just animate the UI
            animateToggleTransition(diaryTab, notesTab, diaryIcon, notesIcon, diaryLabel, notesLabel, true)
        }
        notesTab.setOnClickListener {
            // Animate the transition smoothly
            animateToggleTransition(diaryTab, notesTab, diaryIcon, notesIcon, diaryLabel, notesLabel, false)
            // Navigate after a short delay to allow animation to start
            notesTab.postDelayed({
                findNavController().navigate(R.id.nav_notes_home)
            }, 100)
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        // Refresh theme when returning to fragment
        ThemeManager.applyTheme(requireActivity())
        homeViewModel.loadEntries()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

    private fun getEncryptedPrefs(): SharedPreferences? {
        return try {
            EncryptedSharedPreferences.create(
                "diary_auth_prefs",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                requireContext(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to create encrypted preferences: ${e.message}")
            
            // Show user-friendly error message with solution
            showEncryptionErrorDialog()
            
            null
        }
    }
    
    private fun showEncryptionErrorDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("App Data Error")
            .setMessage("The app's encrypted data has become corrupted. This can happen after app updates or device changes.\n\nTo fix this:\n1. Go to Settings > Apps > TymeLess\n2. Tap 'Storage & cache'\n3. Tap 'Clear storage'\n4. Restart the app")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
    }
}