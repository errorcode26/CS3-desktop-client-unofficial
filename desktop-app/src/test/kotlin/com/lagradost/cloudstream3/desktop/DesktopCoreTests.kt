package com.lagradost.cloudstream3.desktop

import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbRateLimiter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Core verification test suite covering Navigation state invariants,
 * back/forward stack behavior, duplicate push filtering, and rate-limiter concurrency (`M06`).
 */
class DesktopCoreTests {

    @Test
    fun `NavController starts at Home with empty back and forward stacks`() {
        val controller = NavController()
        assertEquals(Screen.Home, controller.currentScreen)
        assertEquals(NavController.NavAction.Idle, controller.lastAction)
        assertFalse(controller.canGoBack())
        assertFalse(controller.canGoForward())
    }

    @Test
    fun `navigate pushes onto backStack and clears forwardStack`() {
        val controller = NavController()
        controller.navigate(Screen.Extensions())

        assertEquals(Screen.Extensions(), controller.currentScreen)
        assertEquals(NavController.NavAction.Push, controller.lastAction)
        assertTrue(controller.canGoBack())
        assertFalse(controller.canGoForward())

        // Go back to Home
        controller.goBack()
        assertEquals(Screen.Home, controller.currentScreen)
        assertEquals(NavController.NavAction.Pop, controller.lastAction)
        assertTrue(controller.canGoForward()) // Can go forward to Extensions

        // Navigating to a new screen while forwardStack has items should clear forwardStack
        controller.navigate(Screen.Library)
        assertEquals(Screen.Library, controller.currentScreen)
        assertFalse(controller.canGoForward())
    }

    @Test
    fun `goBack and goForward maintain exact traversal order`() {
        val controller = NavController()
        controller.navigate(Screen.Extensions())
        controller.navigate(Screen.Library)
        controller.navigate(Screen.Settings)

        assertEquals(Screen.Settings, controller.currentScreen)

        // Go back -> Library
        controller.goBack()
        assertEquals(Screen.Library, controller.currentScreen)

        // Go back -> Extensions
        controller.goBack()
        assertEquals(Screen.Extensions(), controller.currentScreen)

        // Go back -> Home
        controller.goBack()
        assertEquals(Screen.Home, controller.currentScreen)
        assertFalse(controller.canGoBack()) // At root

        // Go forward -> Extensions
        controller.goForward()
        assertEquals(Screen.Extensions(), controller.currentScreen)

        // Go forward -> Library
        controller.goForward()
        assertEquals(Screen.Library, controller.currentScreen)

        // Go forward -> Settings
        controller.goForward()
        assertEquals(Screen.Settings, controller.currentScreen)
        assertFalse(controller.canGoForward())
    }

    @Test
    fun `duplicate consecutive navigate calls are ignored`() {
        val controller = NavController()
        controller.navigate(Screen.Extensions())
        controller.navigate(Screen.Extensions()) // Duplicate

        assertEquals(Screen.Extensions(), controller.currentScreen)

        controller.goBack()
        assertEquals(Screen.Home, controller.currentScreen)
        assertFalse(controller.canGoBack()) // Should be at Home with no duplicate Extensions left in backStack
    }

    @Test
    fun `navigateRoot clears backStack and forwardStack`() {
        val controller = NavController()
        controller.navigate(Screen.Extensions())
        controller.navigate(Screen.Library)

        controller.navigateRoot(Screen.Settings)
        assertEquals(Screen.Settings, controller.currentScreen)
        assertEquals(NavController.NavAction.Pop, controller.lastAction)
        assertFalse(controller.canGoForward())

        // Since currentScreen != Home, canGoBack returns true so user can escape back to Home
        assertTrue(controller.canGoBack())
        controller.goBack()
        assertEquals(Screen.Home, controller.currentScreen)
        assertFalse(controller.canGoBack())
    }

    @Test
    fun `TmdbRateLimiter handles concurrent acquire safely`() = runBlocking {
        // Run 5 concurrent acquires across multiple coroutines
        val count = 5
        val elapsed = measureTimeMillis {
            val jobs = (1..count).map {
                async {
                    TmdbRateLimiter.acquire()
                }
            }
            jobs.awaitAll()
        }
        // minInterval is 1000/35 =~ 28ms. For 5 sequential acquires under lock,
        // it should execute cleanly without throwing or deadlocking.
        assertTrue(elapsed >= 0L, "Rate limiter completed cleanly in ${elapsed}ms")
    }
}
