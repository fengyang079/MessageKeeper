package com.messagekeeper.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.messagekeeper.data.db.entity.DeletedMessage
import com.messagekeeper.data.repository.MessageRepository
import com.messagekeeper.ui.components.Sonner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeletedHistoryViewModel(
    private val repository: MessageRepository = MessageRepository.getInstance()
) : ViewModel() {

    val deletedMessages: StateFlow<List<DeletedMessage>> = repository.getAllDeletedMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedConversationKey = MutableStateFlow<String?>(null)
    val selectedConversationKey: StateFlow<String?> = _selectedConversationKey.asStateFlow()

    fun selectConversation(key: String?) {
        _selectedConversationKey.value = key
    }

    fun restoreMessage(deleted: DeletedMessage) {
        viewModelScope.launch {
            val ok = repository.restoreDeletedMessage(deleted)
            if (ok) {
                Sonner.success("消息已恢复")
            } else {
                Sonner.error("恢复消息失败")
            }
        }
    }

    fun restoreMessages(items: List<DeletedMessage>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val count = repository.restoreDeletedMessages(items)
            if (count > 0) {
                Sonner.success("已恢复 $count 条消息")
                if (_selectedConversationKey.value != null) {
                    _selectedConversationKey.value = null
                }
            } else {
                Sonner.error("恢复失败")
            }
        }
    }

    fun permanentlyDeleteMessage(deleted: DeletedMessage) {
        viewModelScope.launch {
            repository.permanentlyDeleteMessage(deleted.id)
            Sonner.success("已彻底删除该消息")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAllDeletedMessages()
            _selectedConversationKey.value = null
            Sonner.success("已清空历史删除记录")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DeletedHistoryViewModel() as T
            }
        }
    }
}

