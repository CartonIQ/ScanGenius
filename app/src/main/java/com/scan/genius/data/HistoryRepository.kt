package com.scan.genius.data

import androidx.lifecycle.LiveData

class HistoryRepository(private val historyDao: HistoryDao) {

    val allHistory: LiveData<List<HistoryEntity>> = historyDao.getAllHistory()

    suspend fun insert(item: HistoryEntity) {
        historyDao.insert(item)
    }

    suspend fun delete(item: HistoryEntity) {
        historyDao.delete(item)
    }

    suspend fun deleteAll() {
        historyDao.deleteAll()
    }
}
