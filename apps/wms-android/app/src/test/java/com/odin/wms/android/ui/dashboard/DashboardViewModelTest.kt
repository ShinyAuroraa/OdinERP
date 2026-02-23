package com.odin.wms.android.ui.dashboard

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.OperationType
import com.odin.wms.android.domain.model.StockSummary
import com.odin.wms.android.domain.repository.IStockRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val mockStockRepository = mockk<IStockRepository>()
    private lateinit var viewModel: DashboardViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val serverSummary = StockSummary(
        tenantId = "tenant-abc",
        totalAvailable = 1500,
        pendingPickingCount = 5,
        pendingReceivingCount = 2,
        lastUpdated = System.currentTimeMillis(),
        isOffline = false
    )

    private val cachedSummary = serverSummary.copy(totalAvailable = 1400, isOffline = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // U3: API disponível → dados do servidor
    @Test
    fun `loadDashboard with API available returns server data`() = runTest {
        coEvery { mockStockRepository.getStockSummary() } returns ApiResult.Success(serverSummary)
        coEvery { mockStockRepository.getPendingTaskCount(any()) } returns 0

        viewModel = DashboardViewModel(mockStockRepository)

        val state = viewModel.uiState.value
        assertTrue(state is DashboardUiState.Success)
        val success = state as DashboardUiState.Success
        assertEquals(1500, success.stockSummary.totalAvailable)
        assertFalse(success.isOffline)
    }

    // U4: API indisponível → dados do Room com flag offline
    @Test
    fun `loadDashboard with network error falls back to cached data with offline flag`() = runTest {
        coEvery { mockStockRepository.getStockSummary() } returns
                ApiResult.NetworkError("Sem conexão")
        coEvery { mockStockRepository.getCachedStockSummary() } returns cachedSummary
        coEvery { mockStockRepository.getPendingTaskCount(any()) } returns 0

        viewModel = DashboardViewModel(mockStockRepository)

        val state = viewModel.uiState.value
        assertTrue(state is DashboardUiState.Success)
        val success = state as DashboardUiState.Success
        assertTrue(success.isOffline)
        assertEquals(1400, success.stockSummary.totalAvailable)
    }

    @Test
    fun `loadDashboard with network error and no cache returns Error state`() = runTest {
        coEvery { mockStockRepository.getStockSummary() } returns
                ApiResult.NetworkError("Sem conexão")
        coEvery { mockStockRepository.getCachedStockSummary() } returns null

        viewModel = DashboardViewModel(mockStockRepository)

        assertTrue(viewModel.uiState.value is DashboardUiState.Error)
    }
}
