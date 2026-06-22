package com.yadisksync.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yadisksync.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val oauthToken: String = "",
    val oldestDateMillis: Long = 0L,
    val storagePath: String = "",
    val syncIntervalMinutes: Int = 15,
    val deleteOldPhotos: Boolean = false,
    val deleteAfterDays: Int = 7
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.oauthToken,
                settingsRepository.oldestDateMillis,
                settingsRepository.storagePath,
                settingsRepository.syncIntervalMinutes,
                settingsRepository.deleteOldPhotos,
                settingsRepository.deleteAfterDays
            ) { values ->
                SettingsUiState(
                    oauthToken = values[0] as String,
                    oldestDateMillis = values[1] as Long,
                    storagePath = values[2] as String,
                    syncIntervalMinutes = values[3] as Int,
                    deleteOldPhotos = values[4] as Boolean,
                    deleteAfterDays = values[5] as Int
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    fun setOauthToken(token: String) {
        viewModelScope.launch { settingsRepository.setOauthToken(token) }
    }

    fun setOldestDate(millis: Long) {
        viewModelScope.launch { settingsRepository.setOldestDateMillis(millis) }
    }

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSyncIntervalMinutes(minutes) }
    }

    fun setDeleteOldPhotos(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDeleteOldPhotos(enabled) }
    }

    fun setDeleteAfterDays(days: Int) {
        viewModelScope.launch { settingsRepository.setDeleteAfterDays(days) }
    }
}