package dev.redfox.messages.core.ui.mvi

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

    private data class CounterState(val count: Int = 0, val external: String = "") : UiState
    private sealed interface CounterIntent : UiIntent {
        data object Increment : CounterIntent
        data object Announce : CounterIntent
    }
    private data class CounterEffect(val message: String) : UiEffect

    private class CounterViewModel(source: MutableStateFlow<String>) :
        MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState()) {
        init {
            source.bind { copy(external = it) }
        }

        override suspend fun handle(intent: CounterIntent) = when (intent) {
            CounterIntent.Increment -> reduce { copy(count = count + 1) }
            CounterIntent.Announce -> emit(CounterEffect("count=${currentState.count}"))
        }
    }

    private val source = MutableStateFlow("a")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `intents reduce state`() = runTest {
        val vm = CounterViewModel(source)
        vm.dispatch(CounterIntent.Increment)
        vm.dispatch(CounterIntent.Increment)
        assertEquals(2, vm.state.value.count)
    }

    @Test
    fun `effects are delivered once`() = runTest {
        val vm = CounterViewModel(source)
        vm.dispatch(CounterIntent.Increment)
        vm.effects.test {
            vm.dispatch(CounterIntent.Announce)
            assertEquals(CounterEffect("count=1"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `bound flows fold into state`() = runTest {
        val vm = CounterViewModel(source)
        assertEquals("a", vm.state.value.external)
        source.value = "b"
        assertEquals("b", vm.state.value.external)
    }
}
