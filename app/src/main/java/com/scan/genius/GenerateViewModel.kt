package com.scan.genius

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.scan.genius.data.AppDatabase
import com.scan.genius.data.HistoryEntity
import com.scan.genius.data.HistoryRepository
import com.scan.genius.util.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenerateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HistoryRepository

    private val _generatedBitmap = MutableLiveData<Bitmap?>()
    val generatedBitmap: LiveData<Bitmap?> = _generatedBitmap

    private var selectedColor: Int = Color.BLACK
    private var compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG

    init {
        val dao = AppDatabase.getDatabase(application).historyDao()
        repository = HistoryRepository(dao)
    }

    fun setColor(color: Int) {
        selectedColor = color
    }

    fun setCompressFormat(format: Bitmap.CompressFormat) {
        compressFormat = format
    }

    fun generateQrCode(text: String, size: Int = 600) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                QrGenerator.createQrBitmap(text, size, selectedColor, Color.WHITE)
            }
            _generatedBitmap.value = bitmap

            if (bitmap != null) {
                // Automatically save generated QR code to history
                withContext(Dispatchers.IO) {
                    repository.insert(
                        HistoryEntity(
                            content = text,
                            format = "QR_CODE",
                            timestamp = System.currentTimeMillis(),
                            isGenerated = true
                        )
                    )
                }
            }
        }
    }

    fun clearGenerated() {
        _generatedBitmap.value = null
    }
}
