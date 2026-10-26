package com.scan.genius

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.scan.genius.databinding.BottomSheetSettingsBinding

class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val currentMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.rbThemeLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }

        binding.rgThemeOptions.setOnCheckedChangeListener { _, checkedId ->
            val modeToApply = when (checkedId) {
                R.id.rb_theme_light -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.rb_theme_dark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }

            prefs.edit().putInt(KEY_THEME_MODE, modeToApply).apply()
            AppCompatDelegate.setDefaultNightMode(modeToApply)
        }

        binding.btnCloseSettings.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "SettingsBottomSheet"
        const val PREFS_SETTINGS = "scan_genius_settings"
        const val KEY_THEME_MODE = "key_theme_mode"

        fun newInstance(): SettingsBottomSheetDialogFragment = SettingsBottomSheetDialogFragment()

        fun applySavedTheme(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            val mode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
