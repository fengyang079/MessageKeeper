package com.messagekeeper.ui.screens.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.data.db.entity.Message
import com.messagekeeper.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResult(
    val message: Message,
    val conversationName: String,
)

class ConversationsViewModel(
    private val repository: MessageRepository = MessageRepository.getInstance()
) : ViewModel() {

    init {
        // 自动触发合并受污染的旧历史对话
        viewModelScope.launch {
            repository.mergeDuplicateConversations()
        }
    }

    val conversations: StateFlow<List<Conversation>> = repository.getAllConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            viewModelScope.launch {
                _isSearching.value = true
                val messages = repository.searchMessages(query)
                val results = messages.map { msg ->
                    val conv = repository.getConversationById(msg.conversation_id)
                    SearchResult(
                        message = msg,
                        conversationName = conv?.display_name ?: "未知对话"
                    )
                }
                _searchResults.value = results
                _isSearching.value = false
            }
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    fun togglePin(id: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinConversation(id, isPinned)
        }
    }

    fun toggleSystem(id: Long, isSystem: Boolean) {
        viewModelScope.launch {
            repository.toggleSystemConversation(id, isSystem)
        }
    }

    /** 设置会话类型（0=自动识别 1=群聊 2=私信） */
    fun setChatType(id: Long, chatType: Int) {
        viewModelScope.launch {
            repository.setChatType(id, chatType)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConversationsViewModel() as T
            }
        }
    }
}
