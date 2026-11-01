package com.messagekeeper.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.messagekeeper.data.db.entity.Message
import com.messagekeeper.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.paging.cachedIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val conversationId: Long,
    private val repository: MessageRepository = MessageRepository.getInstance()
) : ViewModel() {

    val messages: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<com.messagekeeper.data.db.entity.Message>> = repository.getMessagesPagingData(conversationId).cachedIn(viewModelScope)

    private val _conversationName = MutableStateFlow("")
    val conversationName: StateFlow<String> = _conversationName.asStateFlow()

    init {
        viewModelScope.launch {
            val conversation = repository.getConversationById(conversationId)
            _conversationName.value = conversation?.display_name ?: ""
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            repository.deleteMessage(message)
        }
    }

    fun onConversationEntered() {
        viewModelScope.launch {
            repository.resetUnreadCount(conversationId)
        }
    }

    class Factory(private val conversationId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(conversationId) as T
        }
    }
}

