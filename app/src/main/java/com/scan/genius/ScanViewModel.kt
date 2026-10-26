package com.scan.genius

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.scan.genius.data.AppDatabase
import com.scan.genius.data.HistoryEntity
import com.scan.genius.data.HistoryRepository
import kotlinx.coroutines.launch

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HistoryRepository

    private val _scannedContent = MutableLiveData<String?>()
    val scannedContent: LiveData<String?> = _scannedContent

    private val _currentFormat = MutableLiveData<String>("QR_CODE")
    val currentFormat: LiveData<String> = _currentFormat

    init {
        val dao = AppDatabase.getDatabase(application).historyDao()
        repository = HistoryRepository(dao)
    }

    fun onScanResult(content: String, format: String) {
        _scannedContent.value = content
        _currentFormat.value = format
    }

    fun saveToHistory(content: String, format: String) {
        viewModelScope.launch {
            val entity = HistoryEntity(
                content = content,
                format = format,
                timestamp = System.currentTimeMillis(),
                isGenerated = false
            )
            repository.insert(entity)
        }
    }

    fun clearScannedContent() {
        _scannedContent.value = null
    }
}
