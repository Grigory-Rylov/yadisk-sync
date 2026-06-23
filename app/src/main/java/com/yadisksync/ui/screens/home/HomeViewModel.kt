package com.yadisksync.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.yadisksync.data.local.SyncedFileEntity
import com.yadisksync.domain.repository.SettingsRepository
import com.yadisksync.domain.repository.SyncRepository
import com.yadisksync.domain.usecase.SyncPhotosUseCase
import com.yadisksync.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val recentFiles: List<SyncedFileEntity> = emptyList(),
    val errorMessage: String? = null,
    val syncResult: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val syncPhotosUseCase: SyncPhotosUseCase,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var syncJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.lastSyncTime,
                syncRepository.getRecentFiles(20)
            ) { lastSync, files ->
                HomeUiState(lastSyncTime = lastSync, recentFiles = files)
            }.collect { state ->
                _uiState.value = state
            }
        }

        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
                .collect { infos ->
                    val runningByWorker = infos.any { it.state == WorkInfo.State.RUNNING }
                    val runningByManual = syncJob?.isActive == true
                    _uiState.update { it.copy(isSyncing = runningByWorker || runningByManual) }
                }
        }
    }

    fun syncNow() {
        if (syncJob?.isActive == true) return
        _uiState.update { it.copy(errorMessage = null, syncResult = 0) }
        syncJob = viewModelScope.launch {
            val result = syncPhotosUseCase()
            if (result.isFailure) {
                val msg = result.exceptionOrNull()
                if (msg is CancellationException) {
                    _uiState.update { it.copy(syncResult = 0) }
                } else {
                    _uiState.update { it.copy(errorMessage = msg?.message ?: "Sync failed") }
                }
            } else {
                _uiState.update { it.copy(syncResult = result.getOrDefault(0)) }
            }
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
    }

    fun schedulePeriodicSync(intervalMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(),
            java.util.concurrent.TimeUnit.MINUTES,
            5, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}