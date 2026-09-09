package com.pocketai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketai.PocketAIApp
import com.pocketai.brain.ActiveRoute
import com.pocketai.brain.BrainStreamEvent
import com.pocketai.brain.ChatMessage
import com.pocketai.brain.MessageRole
import com.pocketai.brain.ModelSourceType
import com.pocketai.data.preferences.RoutingMode
import com.pocketai.data.repositories.Conversation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val currentConversation: Conversation? = null,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val activeRoute: ActiveRoute? = null,
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val errorMessage: String? = null,
    val isDrawerOpen: Boolean = false,
    val isModelSwitcherOpen: Boolean = false
)

class ChatViewModel : ViewModel() {

    private val app = PocketAIApp.instance
    private val conversationRepo = app.conversationRepository
    private val brainCore = app.brainCore
    private val modelRouter = app.modelRouter
    private val preferencesManager = app.preferencesManager
    private val localModelRepo = app.localModelRepository
    private val cloudModelRepo = app.cloudModelRepository
    private val obsidianManager = app.obsidianManager

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    private val _isStreaming = MutableStateFlow(false)
    private val _streamingContent = MutableStateFlow("")
    private val _streamingModelName = MutableStateFlow<String?>(null)
    private val _streamingSource = MutableStateFlow(ModelSourceType.LOCAL)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isDrawerOpen = MutableStateFlow(false)
    private val _isModelSwitcherOpen = MutableStateFlow(false)

    private var activeStreamingJob: Job? = null

    val activeRoute: StateFlow<ActiveRoute?> = modelRouter.activeRouteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allConversations: StateFlow<List<Conversation>> = conversationRepo.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loadedLocalModel = localModelRepo.loadedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val enabledCloudProviders = cloudModelRepo.enabledProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appPreferences = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val conversationAndMessages = combine(
        _currentConversation,
        allConversations,
        _messages
    ) { conv, convList, msgs -> Triple(conv, convList, msgs) }

    val uiState: StateFlow<ChatUiState> = combine(
        conversationAndMessages,
        activeRoute,
        _isStreaming,
        _streamingContent,
        _errorMessage
    ) { (conv, convList, msgs), route, streaming, streamText, err ->
        ChatUiState(
            currentConversation = conv,
            conversations = convList,
            messages = msgs,
            activeRoute = route,
            isStreaming = streaming,
            streamingContent = streamText,
            errorMessage = err,
            isDrawerOpen = _isDrawerOpen.value,
            isModelSwitcherOpen = _isModelSwitcherOpen.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    init {
        viewModelScope.launch {
            allConversations.collect { list ->
                if (_currentConversation.value == null && list.isNotEmpty()) {
                    selectConversation(list.first())
                } else if (_currentConversation.value == null && list.isEmpty()) {
                    startNewConversation()
                }
            }
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val route = activeRoute.value
            val newConv = conversationRepo.createNewConversation(
                title = "New Conversation",
                modelName = route?.displayName
            )
            _currentConversation.value = newConv
            loadMessages(newConv.id)
            _isDrawerOpen.value = false
        }
    }

    fun selectConversation(conversation: Conversation) {
        _currentConversation.value = conversation
        loadMessages(conversation.id)
        _isDrawerOpen.value = false
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationRepo.deleteConversation(id)
            if (_currentConversation.value?.id == id) {
                _currentConversation.value = null
            }
        }
    }

    private fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            conversationRepo.getMessages(conversationId).collect { msgList ->
                _messages.value = msgList
            }
        }
    }

    fun sendMessage(content: String) {
        val prompt = content.trim()
        if (prompt.isEmpty() || _isStreaming.value) return

        viewModelScope.launch {
            val conv = _currentConversation.value ?: run {
                val newConv = conversationRepo.createNewConversation(title = "New Conversation")
                _currentConversation.value = newConv
                newConv
            }

            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = prompt,
                timestamp = System.currentTimeMillis()
            )

            // Persist user message
            conversationRepo.saveMessage(conv.id, userMessage)
            _errorMessage.value = null

            // Stream AI response
            streamAssistantResponse(conv.id, prompt)
        }
    }

    private fun streamAssistantResponse(conversationId: String, userPrompt: String) {
        activeStreamingJob?.cancel()
        _isStreaming.value = true
        _streamingContent.value = ""

        activeStreamingJob = viewModelScope.launch {
            val history = _messages.value
            var resolvedSource = ModelSourceType.LOCAL
            var resolvedModelName = activeRoute.value?.displayName ?: "AI Model"

            brainCore.processPromptStream(userPrompt, history).collect { event ->
                when (event) {
                    is BrainStreamEvent.Metadata -> {
                        resolvedSource = event.source
                        resolvedModelName = event.modelName
                        _streamingSource.value = event.source
                        _streamingModelName.value = event.modelName
                    }
                    is BrainStreamEvent.Chunk -> {
                        _streamingContent.value += event.text
                    }
                    is BrainStreamEvent.Complete -> {
                        val assistantMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            content = event.fullResponse,
                            timestamp = System.currentTimeMillis(),
                            modelSource = resolvedSource,
                            modelName = resolvedModelName,
                            latencyMs = event.latencyMs,
                            tokensPerSecond = if (event.tokensPerSecond > 0) event.tokensPerSecond else null
                        )
                        conversationRepo.saveMessage(conversationId, assistantMessage)
                        _isStreaming.value = false
                        _streamingContent.value = ""
                    }
                    is BrainStreamEvent.Error -> {
                        _errorMessage.value = event.message
                        _isStreaming.value = false
                        _streamingContent.value = ""
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        brainCore.stopGeneration()
        activeStreamingJob?.cancel()
        val partial = _streamingContent.value
        val conv = _currentConversation.value
        if (partial.isNotBlank() && conv != null) {
            viewModelScope.launch {
                val assistantMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = "$partial [Stopped]",
                    timestamp = System.currentTimeMillis(),
                    modelSource = _streamingSource.value,
                    modelName = _streamingModelName.value
                )
                conversationRepo.saveMessage(conv.id, assistantMessage)
                _isStreaming.value = false
                _streamingContent.value = ""
            }
        } else {
            _isStreaming.value = false
            _streamingContent.value = ""
        }
    }

    fun regenerateLatest() {
        val msgs = _messages.value
        val lastUserMsg = msgs.lastOrNull { it.role == MessageRole.USER }
        val conv = _currentConversation.value
        if (lastUserMsg != null && conv != null && !_isStreaming.value) {
            streamAssistantResponse(conv.id, lastUserMsg.content)
        }
    }

    fun toggleDrawer(open: Boolean) {
        _isDrawerOpen.value = open
    }

    fun toggleModelSwitcher(open: Boolean) {
        _isModelSwitcherOpen.value = open
    }

    fun setRoutingMode(mode: RoutingMode) {
        viewModelScope.launch {
            preferencesManager.setRoutingMode(mode)
        }
    }

    fun exportToObsidian(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val conv = _currentConversation.value ?: return
        val msgs = _messages.value
        viewModelScope.launch {
            val formatted = msgs.joinToString("\n\n") {
                val speaker = if (it.role == MessageRole.USER) "### User" else "### PocketAI (${it.modelName ?: it.modelSource.name})"
                "$speaker\n${it.content}"
            }
            val res = obsidianManager.exportConversationToVault(conv.title, formatted)
            if (res.isSuccess) {
                onSuccess(res.getOrNull() ?: "Note created")
            } else {
                onError(res.exceptionOrNull()?.message ?: "Export failed")
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
