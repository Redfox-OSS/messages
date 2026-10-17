package dev.redfox.messages.core.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * [cachedIn] for a ViewModel, with the paging pipeline running on [Dispatchers.Default].
 *
 * `cachedIn(viewModelScope)` collects upstream on the main thread, so every `map`,
 * `insertSeparators` and mapper before it (decoding participants, formatting times, building
 * rows) would run on the UI thread while the user scrolls. Only the finished pages reach the
 * main thread this way. The scope still dies with the ViewModel.
 */
fun <T : Any> Flow<PagingData<T>>.cachedOffMain(viewModel: ViewModel): Flow<PagingData<T>> =
    cachedIn(CoroutineScope(viewModel.viewModelScope.coroutineContext + Dispatchers.Default))
