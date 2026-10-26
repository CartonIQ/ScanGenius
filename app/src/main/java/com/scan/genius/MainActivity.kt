package com.scan.genius

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.ads.MobileAds
import com.scan.genius.ads.AdManager
import com.scan.genius.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val historyViewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply user-selected theme mode override (Light, Dark, or System Default)
        SettingsBottomSheetDialogFragment.applySavedTheme(applicationContext)

        // Install splash screen before super.onCreate
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Initialize Google Mobile Ads SDK & preload interstitial
        MobileAds.initialize(this) {}
        AdManager.init(applicationContext)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.title = destination.label ?: getString(R.string.app_name)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_privacy_policy -> {
                showPrivacyPolicy()
                true
            }
            R.id.action_settings -> {
                SettingsBottomSheetDialogFragment.newInstance()
                    .show(supportFragmentManager, SettingsBottomSheetDialogFragment.TAG)
                true
            }
            R.id.action_share_app -> {
                shareApp()
                true
            }
            R.id.action_rate_us -> {
                rateApp()
                true
            }
            R.id.action_delete_all -> {
                confirmDeleteAllHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareApp() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text))
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.menu_share_app))
        startActivity(shareIntent)
    }

    private fun rateApp() {
        val uri = Uri.parse("market://details?id=$packageName")
        val goToMarket = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        try {
            startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

    private fun confirmDeleteAllHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_all_confirm_title)
            .setMessage(R.string.history_delete_all_confirm_msg)
            .setPositiveButton(R.string.history_delete_all_positive) { _, _ ->
                historyViewModel.deleteAll()
                Toast.makeText(this, R.string.history_all_cleared_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.history_delete_all_negative, null)
            .show()
    }

    private fun openWebPage(url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPrivacyPolicy() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Privacy Policy")
            .setMessage("ScanGenius does not collect, store, or share any personal information. All scanned data and generated codes are stored locally on your device and are not transmitted to any server.\n\nThird-Party Advertising: This app uses Google AdMob to display advertisements. AdMob may collect and use data to provide personalized ads. You can learn more about how Google uses data here: https://policies.google.com/technologies/partner-sites\n\nCamera Permission: The app requires camera access solely for the purpose of scanning QR codes and barcodes.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

}
