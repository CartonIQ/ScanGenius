package com.scan.genius

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.scan.genius.data.AppDatabase
import com.scan.genius.data.HistoryEntity
import com.scan.genius.data.HistoryRepository
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HistoryRepository
    val allHistory: LiveData<List<HistoryEntity>>

    init {
        val dao = AppDatabase.getDatabase(application).historyDao()
        repository = HistoryRepository(dao)
        allHistory = repository.allHistory
    }

    fun delete(item: HistoryEntity) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }
}
