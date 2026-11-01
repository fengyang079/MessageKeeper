package com.messagekeeper.ui.screens.specialalert

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.messagekeeper.data.db.entity.SpecialAlert
import com.messagekeeper.data.repository.MessageRepository
import com.messagekeeper.notification.SpecialAlertManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpecialAlertViewModel(
    private val repository: MessageRepository = MessageRepository.getInstance()
) : ViewModel() {

    val alerts: StateFlow<List<SpecialAlert>> = repository.getAllSpecialAlerts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun addAlert(platform: String, senderName: String, soundName: String = "default") {
        viewModelScope.launch {
            repository.addSpecialAlert(platform, senderName)
            // TODO: 如果后续需要创建时指定声音，需扩展 addSpecialAlert
        }
    }

    fun toggleAlert(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleSpecialAlert(id, enabled)
        }
    }

    fun updateSound(id: Long, soundName: String) {
        viewModelScope.launch {
            repository.updateAlertSound(id, soundName)
        }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch {
            repository.deleteSpecialAlert(id)
        }
    }

    fun sendTestAlert(context: Context, alert: SpecialAlert) {
        SpecialAlertManager.sendTestAlert(
            context = context,
            senderName = alert.sender_name,
            platform = alert.platform,
            soundName = alert.sound_name
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SpecialAlertViewModel() as T
            }
        }
    }
}
