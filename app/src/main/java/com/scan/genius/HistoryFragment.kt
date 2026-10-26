package com.scan.genius

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.scan.genius.ads.AdManager
import com.scan.genius.data.HistoryEntity
import com.scan.genius.databinding.FragmentHistoryBinding
import com.scan.genius.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val historyViewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { item -> showDetailsDialog(item) },
            onItemLongClick = { item -> confirmDeleteSingleItem(item) },
            onDeleteClick = { item -> confirmDeleteSingleItem(item) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun observeData() {
        historyViewModel.allHistory.observe(viewLifecycleOwner) { items ->
            if (items.isNullOrEmpty()) {
                binding.tvEmptyHistory.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
                adapter.submitItems(emptyList())
            } else {
                binding.tvEmptyHistory.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                adapter.submitItems(items)
            }
        }
    }

    private fun showDetailsDialog(item: HistoryEntity) {
        val isLink = item.content.startsWith("http://", ignoreCase = true) ||
                item.content.startsWith("https://", ignoreCase = true)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.history_dialog_title)
            .setMessage(item.content)
            .setPositiveButton(R.string.scan_btn_copy) { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("History Item", item.content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.scan_toast_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.history_dialog_close, null)

        if (isLink) {
            builder.setNeutralButton(R.string.scan_btn_open_url) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.content)))
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
                }
            }
        }

        builder.show()
    }

    private fun confirmDeleteSingleItem(item: HistoryEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.history_dialog_delete)
            .setMessage("Delete this code from history?")
            .setPositiveButton(R.string.history_dialog_delete) { _, _ ->
                historyViewModel.delete(item)
                Toast.makeText(requireContext(), R.string.history_deleted_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.history_delete_all_negative, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // Sealed class representing either a Room history row or an AdMob Native Ad row
    sealed class HistoryRowItem {
        data class HistoryRecord(val data: HistoryEntity) : HistoryRowItem()
        object NativeAdSlot : HistoryRowItem()
    }

    inner class HistoryAdapter(
        private val onItemClick: (HistoryEntity) -> Unit,
        private val onItemLongClick: (HistoryEntity) -> Unit,
        private val onDeleteClick: (HistoryEntity) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val itemsWithAds = mutableListOf<HistoryRowItem>()

        fun submitItems(list: List<HistoryEntity>) {
            itemsWithAds.clear()
            list.forEachIndexed { index, entity ->
                itemsWithAds.add(HistoryRowItem.HistoryRecord(entity))
                // Insert a Native Ad every 5 items (after item 5, 10, etc.)
                if ((index + 1) % 5 == 0 && index + 1 < list.size) {
                    itemsWithAds.add(HistoryRowItem.NativeAdSlot)
                }
            }
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (itemsWithAds[position]) {
                is HistoryRowItem.HistoryRecord -> VIEW_TYPE_ITEM
                is HistoryRowItem.NativeAdSlot -> VIEW_TYPE_AD
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_ITEM) {
                val binding = ItemHistoryBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HistoryViewHolder(binding)
            } else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_native_ad, parent, false)
                NativeAdViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = itemsWithAds[position]) {
                is HistoryRowItem.HistoryRecord -> (holder as HistoryViewHolder).bind(item.data)
                is HistoryRowItem.NativeAdSlot -> (holder as NativeAdViewHolder).bind()
            }
        }

        override fun getItemCount(): Int = itemsWithAds.size

        inner class HistoryViewHolder(private val itemBinding: ItemHistoryBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: HistoryEntity) {
                itemBinding.tvContent.text = item.content
                val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                val dateString = dateFormat.format(Date(item.timestamp))
                val mode = if (item.isGenerated) "Generated" else "Scanned"
                itemBinding.tvMetadata.text = "$dateString • $mode (${item.format})"

                itemBinding.root.setOnClickListener { onItemClick(item) }
                itemBinding.root.setOnLongClickListener {
                    onItemLongClick(item)
                    true
                }
                itemBinding.btnDeleteItem.setOnClickListener { onDeleteClick(item) }
            }
        }

        inner class NativeAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nativeAdView: NativeAdView = itemView.findViewById(R.id.native_ad_view)
            private val headlineView: TextView = itemView.findViewById(R.id.ad_headline)
            private val bodyView: TextView = itemView.findViewById(R.id.ad_body)
            private val iconView: ImageView = itemView.findViewById(R.id.ad_app_icon)
            private val ctaButton: Button = itemView.findViewById(R.id.ad_call_to_action)

            fun bind() {
                AdManager.loadNativeAd(itemView.context) { nativeAd ->
                    populateNativeAd(nativeAd)
                }
            }

            private fun populateNativeAd(nativeAd: NativeAd) {
                nativeAdView.headlineView = headlineView
                nativeAdView.bodyView = bodyView
                nativeAdView.iconView = iconView
                nativeAdView.callToActionView = ctaButton

                headlineView.text = nativeAd.headline ?: "Sponsored Promotion"
                bodyView.text = nativeAd.body ?: "Tap to learn more"
                ctaButton.text = nativeAd.callToAction ?: "Install"

                val icon = nativeAd.icon
                if (icon != null) {
                    iconView.setImageDrawable(icon.drawable)
                    iconView.visibility = View.VISIBLE
                } else {
                    iconView.visibility = View.GONE
                }

                nativeAdView.setNativeAd(nativeAd)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_AD = 1
    }
}
