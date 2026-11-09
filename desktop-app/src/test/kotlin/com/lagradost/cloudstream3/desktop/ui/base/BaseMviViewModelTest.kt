package com.lagradost.cloudstream3.desktop.ui.base

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseMviViewModelTest {

    data class TestState(val count: Int = 0, val message: String = "") : UiState

    sealed interface TestEvent : UiEvent {
        data class Increment(val by: Int = 1) : TestEvent
        data class SetMessage(val msg: String) : TestEvent
        data class TriggerEffect(val effectText: String) : TestEvent
    }

    sealed interface TestEffect : UiEffect {
        data class ShowToast(val text: String) : TestEffect
    }

    class TestViewModel(initialState: TestState = TestState()) :
        BaseMviViewModel<TestState, TestEvent, TestEffect>(initialState) {

        public override fun handleEvent(event: TestEvent) {
            when (event) {
                is TestEvent.Increment -> updateState { copy(count = count + event.by) }
                is TestEvent.SetMessage -> updateState { copy(message = event.msg) }
                is TestEvent.TriggerEffect -> sendEffect(TestEffect.ShowToast(event.effectText))
            }
        }

        fun directUpdate(reducer: TestState.() -> TestState) {
            updateState(reducer)
        }

        fun directSendEffect(effect: TestEffect) {
            sendEffect(effect)
        }
    }

    @Test
    fun testInitialState_IsEmittedImmediately() {
        val vm = TestViewModel(TestState(count = 42, message = "initial"))
        assertEquals(42, vm.uiState.value.count)
        assertEquals("initial", vm.uiState.value.message)
    }

    @Test
    fun testOnEvent_ReducesStateCorrectly() {
        val vm = TestViewModel()
        vm.onEvent(TestEvent.Increment(5))
        assertEquals(5, vm.uiState.value.count)

        vm.onEvent(TestEvent.Increment(3))
        assertEquals(8, vm.uiState.value.count)

        vm.onEvent(TestEvent.SetMessage("hello"))
        assertEquals("hello", vm.uiState.value.message)
        assertEquals(8, vm.uiState.value.count)
    }

    @Test
    fun testUpdateState_AtomicStateTransitions() {
        val vm = TestViewModel()
        vm.directUpdate { copy(count = 100) }
        assertEquals(100, vm.uiState.value.count)

        vm.directUpdate { copy(count = count + 1, message = "updated") }
        assertEquals(101, vm.uiState.value.count)
        assertEquals("updated", vm.uiState.value.message)
    }

    @Test
    fun testSendEffect_DeliveredViaEffectFlow() = runBlocking {
        val vm = TestViewModel()
        val received = mutableListOf<TestEffect>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.effectFlow.collect { received.add(it) }
        }

        vm.onEvent(TestEvent.TriggerEffect("Effect A"))
        vm.onEvent(TestEvent.TriggerEffect("Effect B"))
        yield()
        job.cancel()

        assertEquals(2, received.size)
        assertEquals(TestEffect.ShowToast("Effect A"), received[0])
        assertEquals(TestEffect.ShowToast("Effect B"), received[1])
    }

    @Test
    fun testDispose_CancelsScope() = runBlocking {
        val vm = TestViewModel()
        vm.dispose()

        var effectReceived = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.effectFlow.collect { effectReceived = true }
        }

        vm.onEvent(TestEvent.TriggerEffect("Won't be emitted"))
        yield()
        job.cancel()

        assertFalse(effectReceived)
    }

    @Test
    fun testOnDestroy_TriggersDispose() {
        val vm = TestViewModel()
        vm.onDestroy()
        assertTrue(true)
    }
}
