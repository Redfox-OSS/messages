package dev.redfox.messages.core.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable screen state. Implementations are `@Immutable data class`es. */
interface UiState

/** Everything the user (or the system, via the Route) can ask a screen to do. */
interface UiIntent

/** One-shot events: navigation, permission/role requests, snackbars, haptics, clipboard. */
interface UiEffect

/**
 * The MVVM + MVI hybrid every screen uses.
 *
 * Outside it is MVVM: a lifecycle-scoped holder exposing [state] as a `StateFlow`.
 * Inside it is MVI: [dispatch] is the only way in, [reduce] the only way state changes,
 * and [emit] the only way one-off events leave.
 *
 * Interaction-heavy screens implement [handle] with a full `when` over a sealed intent.
 * Lightweight (MVVM-flavoured) screens mostly [bind] repository flows into state and keep
 * their intents thin.
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    fun dispatch(intent: I) {
        viewModelScope.launch { handle(intent) }
    }

    protected abstract suspend fun handle(intent: I)

    protected fun reduce(reducer: S.() -> S) {
        _state.update(reducer)
    }

    protected fun emit(effect: E) {
        _effects.trySend(effect)
    }

    /** Folds every value of [this] flow into state for the ViewModel's lifetime. */
    protected fun <T> Flow<T>.bind(reducer: S.(T) -> S) {
        viewModelScope.launch { collect { value -> reduce { reducer(value) } } }
    }
}
