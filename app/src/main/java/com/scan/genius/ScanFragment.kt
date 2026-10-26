package com.scan.genius

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.ads.AdView
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.scan.genius.ads.AdManager
import com.scan.genius.databinding.FragmentScanBinding

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val scanViewModel: ScanViewModel by viewModels()
    private var bannerAdView: AdView? = null
    private var isFlashlightOn = false
    private var cameraIdWithFlash: String? = null

    // Barcode scanner launcher using ZXing Embedded
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            handleScanSuccess(result.contents, result.formatName ?: "QR_CODE")
        }
    }

    // Gallery image picker launcher
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            decodeBarcodeFromUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initFlashlightCameraId()
        setupListeners()
        setupAutoOpenSwitch()
        observeViewModel()
        loadBannerAd()
    }

    private fun setupListeners() {
        binding.btnOpenCamera.setOnClickListener {
            launchScanner()
        }

        binding.btnScanGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnToggleFlashlight.setOnClickListener {
            toggleFlashlight()
        }

        binding.btnCopyResult.setOnClickListener {
            val text = binding.tvScannedText.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Scanned Code", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.scan_toast_copied, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenUrl.setOnClickListener {
            val text = binding.tvScannedText.text.toString()
            openUrlInBrowser(text)
        }

        binding.btnSaveHistory.setOnClickListener {
            val text = binding.tvScannedText.text.toString()
            val format = scanViewModel.currentFormat.value ?: "QR_CODE"
            if (text.isNotEmpty()) {
                scanViewModel.saveToHistory(text, format)
                Toast.makeText(requireContext(), R.string.scan_toast_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAutoOpenSwitch() {
        val sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.switchAutoOpen.isChecked = sharedPrefs.getBoolean(KEY_AUTO_OPEN_LINKS, false)

        binding.switchAutoOpen.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean(KEY_AUTO_OPEN_LINKS, isChecked).apply()
        }
    }

    private fun observeViewModel() {
        scanViewModel.scannedContent.observe(viewLifecycleOwner) { content ->
            if (content.isNullOrEmpty()) {
                binding.cardResult.visibility = View.GONE
            } else {
                binding.cardResult.visibility = View.VISIBLE
                binding.tvScannedText.text = content

                val isLink = content.startsWith("http://", ignoreCase = true) ||
                        content.startsWith("https://", ignoreCase = true)
                binding.btnOpenUrl.visibility = if (isLink) View.VISIBLE else View.GONE
            }
        }
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            setPrompt(getString(R.string.scan_subtitle))
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true)
            setCaptureActivity(CaptureActivity::class.java)
        }
        barcodeLauncher.launch(options)
    }

    private fun handleScanSuccess(rawResult: String, format: String) {
        triggerHapticFeedback()
        scanViewModel.onScanResult(rawResult, format)

        // Show interstitial ad every 3rd scan
        AdManager.onScanCompleted(requireActivity())

        val sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoOpen = sharedPrefs.getBoolean(KEY_AUTO_OPEN_LINKS, false)
        val isLink = rawResult.startsWith("http://", ignoreCase = true) ||
                rawResult.startsWith("https://", ignoreCase = true) ||
                rawResult.contains(".com") || rawResult.contains(".dev") || rawResult.contains(".net") || rawResult.contains(".org")

        if (autoOpen && isLink) {
            openUrlInBrowser(rawResult)
        }
    }

    private fun openUrlInBrowser(url: String) {
        try {
            val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeBarcodeFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                Toast.makeText(requireContext(), R.string.scan_toast_no_code_found, Toast.LENGTH_SHORT).show()
                return
            }

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)

            handleScanSuccess(result.text, result.barcodeFormat.name)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.scan_toast_no_code_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(100)
                }
            }
        } catch (_: Exception) {}
    }

    private fun initFlashlightCameraId() {
        try {
            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraIdWithFlash = id
                    break
                }
            }
        } catch (_: Exception) {}
    }

    private fun toggleFlashlight() {
        val camId = cameraIdWithFlash
        if (camId == null) {
            Toast.makeText(requireContext(), "Flashlight not available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            isFlashlightOn = !isFlashlightOn
            cameraManager.setTorchMode(camId, isFlashlightOn)
            binding.btnToggleFlashlight.text = if (isFlashlightOn) "Flashlight (ON)" else getString(R.string.scan_flashlight)
        } catch (e: CameraAccessException) {
            Toast.makeText(requireContext(), "Error toggling flashlight", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBannerAd() {
        bannerAdView = AdManager.createBannerAd(requireContext())
        binding.adViewContainer.removeAllViews()
        binding.adViewContainer.addView(bannerAdView)
    }

    override fun onDestroyView() {
        if (isFlashlightOn && cameraIdWithFlash != null) {
            try {
                val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cameraManager.setTorchMode(cameraIdWithFlash!!, false)
            } catch (_: Exception) {}
        }
        bannerAdView?.destroy()
        bannerAdView = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val PREFS_NAME = "scan_genius_prefs"
        const val KEY_AUTO_OPEN_LINKS = "key_auto_open_links"
    }
}
