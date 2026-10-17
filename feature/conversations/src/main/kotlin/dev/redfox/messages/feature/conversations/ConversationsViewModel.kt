package dev.redfox.messages.feature.conversations

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import dev.redfox.messages.core.ui.mvi.cachedOffMain
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.GetDeviceSetupStatusUseCase
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.domain.usecase.ObserveSyncStatusUseCase
import dev.redfox.messages.core.domain.usecase.PagedConversationsUseCase
import dev.redfox.messages.core.domain.usecase.StartInboxSyncUseCase
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.messages.core.ui.mvi.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Inbox — full MVI for state and intents; the rows themselves are a separate paged stream
 * ([conversations]) cached in the ViewModel so rotation and back-navigation don't reload.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    pagedConversations: PagedConversationsUseCase,
    observeSyncStatus: ObserveSyncStatusUseCase,
    private val startInboxSync: StartInboxSyncUseCase,
    private val getDeviceSetupStatus: GetDeviceSetupStatusUseCase,
) : MviViewModel<ConversationsState, ConversationsIntent, ConversationsEffect>(ConversationsState()) {

    private val timeFormat = TimeFormat()
    private val filter = MutableStateFlow(InboxFilter.All)

    val conversations: Flow<PagingData<ConversationItem>> = filter
        .flatMapLatest { pagedConversations(it) }
        .map { page ->
            val now = System.currentTimeMillis()
            page.map { ConversationItem(it, timeFormat.conversationTime(it.timestamp, now)) }
        }
        .cachedOffMain(this)

    init {
        filter.bind { copy(filter = it) }
        observeSyncStatus().bind { copy(isIndexing = !it.hasCompletedOnce) }
        checkAccess()
    }

    override suspend fun handle(intent: ConversationsIntent) {
        when (intent) {
            ConversationsIntent.Refresh -> checkAccess()
            is ConversationsIntent.FilterSelected -> filter.value = intent.filter
            is ConversationsIntent.ConversationClicked -> emit(ConversationsEffect.OpenThread(intent.threadId))
            ConversationsIntent.GrantAccessClicked -> emit(ConversationsEffect.RequestSmsAccess)
        }
    }

    private fun checkAccess() {
        val granted = getDeviceSetupStatus().hasReadSmsPermission
        reduce { copy(hasSmsAccess = granted) }
        if (granted) startInboxSync()
    }
}
