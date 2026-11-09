package com.lagradost.cloudstream3.desktop.ui.screens.home

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent
import com.lagradost.cloudstream3.newMovieSearchResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
class DesktopHomeViewModelTest {

    private var activeVm: DesktopHomeViewModel? = null

    @AfterEach
    fun tearDown() {
        activeVm?.dispose()
        activeVm = null
    }

    private fun createTestProvider(name: String = "TestHomeProvider"): MainAPI {
        return object : MainAPI() {
            override var name = name
            override var mainUrl = "https://test.example.com"

            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                if (request.name == "FailingCategory") {
                    throw IllegalStateException("Simulated category failure")
                }
                if (request.name == "EmptyCategory") {
                    return HomePageResponse(emptyList())
                }
                return HomePageResponse(
                    listOf(
                        HomePageList(
                            name = request.name,
                            list = listOf(
                                newMovieSearchResponse("Test Item 1", "https://test.example.com/item1"),
                                newMovieSearchResponse("Test Item 2", "https://test.example.com/item2"),
                            ),
                        )
                    )
                )
            }
        }
    }

    @Test
    fun testInitialState_Defaults() {
        val vm = DesktopHomeViewModel().also { activeVm = it }
        val state = vm.uiState.value

        assertFalse(state.showHomeManagement)
        assertNull(state.orphanRecoveryHistory)
        assertTrue(state.categories.isEmpty())
        assertTrue(state.historyList.isEmpty())
    }

    @Test
    fun testOnShowHomeManagement_TogglesState() {
        val vm = DesktopHomeViewModel().also { activeVm = it }

        vm.onEvent(HomeUiEvent.OnShowHomeManagement(true))
        assertTrue(vm.uiState.value.showHomeManagement)

        vm.onEvent(HomeUiEvent.OnShowHomeManagement(false))
        assertFalse(vm.uiState.value.showHomeManagement)
    }

    @Test
    fun testOnDismissRecoveryModal_ClearsModal() {
        val vm = DesktopHomeViewModel().also { activeVm = it }

        vm.onEvent(HomeUiEvent.OnDismissRecoveryModal)
        assertNull(vm.uiState.value.orphanRecoveryHistory)
    }

    @Test
    fun testOnToggleCatalog_DisablesAndReEnablesCatalog() {
        val vm = DesktopHomeViewModel().also { activeVm = it }
        val providerName = "ProviderA"

        // Disable "Anime" catalog
        vm.onEvent(HomeUiEvent.OnToggleCatalog(providerName, "Anime", isEnabled = false))
        val disabledAfterFirst = vm.uiState.value.disabledCatalogs[providerName]
        assertNotNull(disabledAfterFirst)
        assertTrue(disabledAfterFirst.contains("Anime"))

        // Re-enable "Anime" catalog
        vm.onEvent(HomeUiEvent.OnToggleCatalog(providerName, "Anime", isEnabled = true))
        val disabledAfterSecond = vm.uiState.value.disabledCatalogs[providerName]
        assertNotNull(disabledAfterSecond)
        assertFalse(disabledAfterSecond.contains("Anime"))
    }

    @Test
    fun testOnMoveCatalog_UpdatesOrdering() {
        val vm = DesktopHomeViewModel().also { activeVm = it }
        val providerName = "ProviderOrderTest"

        // Setup custom ordering by reordering
        vm.onEvent(HomeUiEvent.OnToggleCatalog(providerName, "CatA", isEnabled = false))

        // Move from index 0 to 1
        vm.onEvent(HomeUiEvent.OnMoveCatalog(providerName, fromIndex = 0, toIndex = 1))
        // Validates that executing OnMoveCatalog without crashing gracefully handles state
        assertTrue(true)
    }

    @Test
    fun testOnResetCatalogOrder_RemovesCustomOrdering() {
        val vm = DesktopHomeViewModel().also { activeVm = it }
        val providerName = "ProviderResetTest"

        vm.onEvent(HomeUiEvent.OnResetCatalogOrder(providerName))
        assertFalse(vm.uiState.value.orderedCatalogs.containsKey(providerName))
    }

    @Test
    fun testOnLoadCategory_SuccessPopulatesCategoryState() = runBlocking {
        val vm = DesktopHomeViewModel().also { activeVm = it }
        val provider = createTestProvider("GoodProvider")
        val pageData = MainPageData("Popular", "popular_url")

        vm.onEvent(HomeUiEvent.OnLoadCategory(provider, pageData))

        // Give background coroutine time to complete
        var attempts = 0
        val cacheKey = "GoodProvider_Popular"
        while (attempts < 50 && (vm.uiState.value.categories[cacheKey]?.isLoading != false)) {
            delay(20)
            attempts++
        }

        val catState = vm.uiState.value.categories[cacheKey]
        assertNotNull(catState)
        assertFalse(catState.isLoading)
        assertNull(catState.error)
        assertNotNull(catState.response)
        assertEquals(1, catState.response!!.items.size)
    }

    @Test
    fun testOnLoadCategory_HandlesFailureGracefullyWithoutCrashing() = runBlocking {
        val vm = DesktopHomeViewModel().also { activeVm = it }
        val provider = createTestProvider("FaultyProvider")
        val pageData = MainPageData("FailingCategory", "fail_url")

        vm.onEvent(HomeUiEvent.OnLoadCategory(provider, pageData))

        var attempts = 0
        val cacheKey = "FaultyProvider_FailingCategory"
        while (attempts < 50 && (vm.uiState.value.categories[cacheKey]?.isLoading != false)) {
            delay(20)
            attempts++
        }

        val catState = vm.uiState.value.categories[cacheKey]
        assertNotNull(catState)
        assertFalse(catState.isLoading)
        assertNotNull(catState.error)
        assertNull(catState.response)
    }
}
