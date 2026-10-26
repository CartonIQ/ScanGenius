package com.scan.genius

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.ads.AdView
import com.scan.genius.ads.AdManager
import com.scan.genius.databinding.FragmentGenerateBinding
import com.scan.genius.util.ImageSaver

class GenerateFragment : Fragment() {

    private var _binding: FragmentGenerateBinding? = null
    private val binding get() = _binding!!

    private val generateViewModel: GenerateViewModel by viewModels()
    private var bannerAdView: AdView? = null
    private var currentQrBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenerateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupContentTypeSelection()
        setupColorSelection()
        setupFormatSelection()
        setupActionButtons()
        observeViewModel()
        loadBannerAd()
    }

    private fun setupContentTypeSelection() {
        binding.rgContentType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_text -> {
                    binding.tilSingleInput.visibility = View.VISIBLE
                    binding.layoutWifiInputs.visibility = View.GONE
                    binding.layoutContactInputs.visibility = View.GONE
                    binding.etSingleInput.hint = getString(R.string.hint_text)
                }
                R.id.rb_url -> {
                    binding.tilSingleInput.visibility = View.VISIBLE
                    binding.layoutWifiInputs.visibility = View.GONE
                    binding.layoutContactInputs.visibility = View.GONE
                    binding.etSingleInput.hint = getString(R.string.hint_url)
                }
                R.id.rb_wifi -> {
                    binding.tilSingleInput.visibility = View.GONE
                    binding.layoutWifiInputs.visibility = View.VISIBLE
                    binding.layoutContactInputs.visibility = View.GONE
                }
                R.id.rb_contact -> {
                    binding.tilSingleInput.visibility = View.GONE
                    binding.layoutWifiInputs.visibility = View.GONE
                    binding.layoutContactInputs.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupColorSelection() {
        binding.chipGroupColors.setOnCheckedStateChangeListener { _, checkedIds ->
            val color = when (checkedIds.firstOrNull()) {
                R.id.chip_color_blue -> ContextCompat.getColor(requireContext(), R.color.qr_blue)
                R.id.chip_color_red -> ContextCompat.getColor(requireContext(), R.color.qr_red)
                R.id.chip_color_green -> ContextCompat.getColor(requireContext(), R.color.qr_green)
                else -> Color.BLACK
            }
            generateViewModel.setColor(color)
        }
    }

    private fun setupFormatSelection() {
        binding.rgFormat.setOnCheckedChangeListener { _, checkedId ->
            val format = if (checkedId == R.id.rb_format_jpg) {
                Bitmap.CompressFormat.JPEG
            } else {
                Bitmap.CompressFormat.PNG
            }
            generateViewModel.setCompressFormat(format)
        }
    }

    private fun setupActionButtons() {
        binding.btnGenerateQr.setOnClickListener {
            val content = assembleQrContent()
            if (content.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.generate_error_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateViewModel.generateQrCode(content)
        }

        binding.btnSaveDownload.setOnClickListener {
            val bitmap = currentQrBitmap
            if (bitmap == null) {
                Toast.makeText(requireContext(), R.string.generate_error_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val isJpg = binding.rbFormatJpg.isChecked
            val extension = if (isJpg) "jpg" else "png"
            val format = if (isJpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG

            val success = ImageSaver.saveBitmapToDownloads(
                requireContext(),
                bitmap,
                "ScanGenius_${System.currentTimeMillis()}.$extension",
                format
            )

            if (success) {
                Toast.makeText(requireContext(), R.string.generate_toast_saved, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), R.string.generate_toast_save_failed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShareQr.setOnClickListener {
            val bitmap = currentQrBitmap
            if (bitmap != null) {
                val isJpg = binding.rbFormatJpg.isChecked
                val extension = if (isJpg) "jpg" else "png"
                ImageSaver.shareBitmap(
                    requireContext(),
                    bitmap,
                    "ScanGenius_Shared.$extension",
                    getString(R.string.share_chooser_title)
                )
            }
        }
    }

    private fun assembleQrContent(): String? {
        return when (binding.rgContentType.checkedRadioButtonId) {
            R.id.rb_text, R.id.rb_url -> {
                binding.etSingleInput.text?.toString()?.trim()
            }
            R.id.rb_wifi -> {
                val ssid = binding.etWifiSsid.text?.toString()?.trim()
                val password = binding.etWifiPassword.text?.toString()?.trim() ?: ""
                if (ssid.isNullOrEmpty()) null else "WIFI:T:WPA;S:$ssid;P:$password;;"
            }
            R.id.rb_contact -> {
                val name = binding.etContactName.text?.toString()?.trim()
                val phone = binding.etContactPhone.text?.toString()?.trim() ?: ""
                val email = binding.etContactEmail.text?.toString()?.trim() ?: ""
                if (name.isNullOrEmpty()) {
                    null
                } else {
                    "BEGIN:VCARD\nVERSION:3.0\nFN:$name\nTEL:$phone\nEMAIL:$email\nEND:VCARD"
                }
            }
            else -> null
        }
    }

    private fun observeViewModel() {
        generateViewModel.generatedBitmap.observe(viewLifecycleOwner) { bitmap ->
            currentQrBitmap = bitmap
            if (bitmap != null) {
                binding.cardPreview.visibility = View.VISIBLE
                binding.ivQrCode.setImageBitmap(bitmap)
                binding.scrollView.post {
                    binding.scrollView.smoothScrollTo(0, binding.cardPreview.top)
                }
            } else {
                binding.cardPreview.visibility = View.GONE
            }
        }
    }

    private fun loadBannerAd() {
        bannerAdView = AdManager.createBannerAd(requireContext())
        binding.adViewContainer.removeAllViews()
        binding.adViewContainer.addView(bannerAdView)
    }

    override fun onDestroyView() {
        bannerAdView?.destroy()
        bannerAdView = null
        _binding = null
        super.onDestroyView()
    }
}
