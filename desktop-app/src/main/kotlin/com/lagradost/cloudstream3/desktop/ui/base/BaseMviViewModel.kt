package com.lagradost.cloudstream3.desktop.ui.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.arkivanov.essenty.instancekeeper.InstanceKeeper

/**
 * Marker interface for all UI States.
 * Must represent an immutable snapshot of the screen at any given millisecond.
 */
interface UiState

/**
 * Marker interface for all UI Events / Intents.
 * Represents user actions dispatched from Compose UI to the ViewModel.
 */
interface UiEvent

/**
 * Marker interface for one-shot UI side effects (e.g., navigation, toast alerts, dialog triggers).
 */
interface UiEffect

/**
 * Foundation for Unidirectional Data Flow (UDF) across all Desktop screens.
 * Encapsulates structured coroutine scope, atomic state reduction, one-shot side effects,
 * and clean lifecycle disposal.
 */
abstract class BaseMviViewModel<State : UiState, Event : UiEvent, Effect : UiEffect>(
    initialState: State,
) : InstanceKeeper.Instance {
    /**
     * Managed coroutine scope for this ViewModel.
     * Automatically cancelled when [dispose] is called when leaving the screen.
     */
    protected val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<Effect>(extraBufferCapacity = 64)
    val effectFlow: SharedFlow<Effect> = _effectFlow.asSharedFlow()

    /**
     * Central entrypoint for all actions/events emitted by the UI.
     */
    fun onEvent(event: Event) {
        handleEvent(event)
    }

    /**
     * Subclasses must implement this to process events and trigger state reducers or side effects.
     */
    protected abstract fun handleEvent(event: Event)

    /**
     * Thread-safe, atomic reducer to update [uiState].
     */
    protected fun updateState(reducer: State.() -> State) {
        _uiState.update(reducer)
    }

    /**
     * Emits a one-shot side effect to the UI (e.g., showing a snackbar or navigating).
     */
    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch {
            _effectFlow.emit(effect)
        }
    }

    /**
     * Cleans up all running coroutines when the Compose screen is disposed.
     * Subclasses can override this to cancel custom listeners or jobs, but must call `super.dispose()`.
     */
    open fun dispose() {
        viewModelScope.cancel()
    }

    /**
     * Called by Decompose's InstanceKeeper when the Component holding this ViewModel is destroyed.
     */
    override fun onDestroy() {
        dispose()
    }
}
