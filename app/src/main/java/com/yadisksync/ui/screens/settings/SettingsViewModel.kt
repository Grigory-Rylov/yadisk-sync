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
    val syncIntervalMinutes: Int = 15
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
                settingsRepository.syncIntervalMinutes
            ) { token, oldestDate, path, interval ->
                SettingsUiState(token, oldestDate, path, interval)
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
}