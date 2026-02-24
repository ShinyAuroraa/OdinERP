package com.odin.wms.android.ui.dashboard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.OperationType
import com.odin.wms.android.domain.model.StockSummary
import com.odin.wms.android.domain.repository.IAuthRepository
import com.odin.wms.android.domain.repository.IStockRepository
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

// I2 (Instrumented): DashboardScreen — cards renderizam com dados mockados
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockStockRepository = mockk<IStockRepository>()
    private val mockAuthRepository = mockk<IAuthRepository>(relaxed = true)

    private val testSummary = StockSummary(
        tenantId = "tenant-abc",
        totalAvailable = 2500,
        pendingPickingCount = 7,
        pendingReceivingCount = 3,
        lastUpdated = System.currentTimeMillis(),
        isOffline = false
    )

    @Test
    fun dashboardScreen_rendersCardsWithMockedData() {
        coEvery { mockStockRepository.getStockSummary() } returns ApiResult.Success(testSummary)
        coEvery { mockStockRepository.getPendingTaskCount(any()) } returns 0

        composeTestRule.setContent {
            val viewModel = DashboardViewModel(mockStockRepository, mockAuthRepository)
            DashboardScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("dashboard_content").assertIsDisplayed()
        composeTestRule.onNodeWithText("2500").assertIsDisplayed()
        composeTestRule.onNodeWithText("Picking").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recebimento").assertIsDisplayed()
    }

    @Test
    fun dashboardScreen_showsOfflineBannerWhenDataIsCached() {
        coEvery { mockStockRepository.getStockSummary() } returns
                ApiResult.NetworkError("Sem conexão")
        coEvery { mockStockRepository.getCachedStockSummary() } returns
                testSummary.copy(isOffline = true)

        composeTestRule.setContent {
            val viewModel = DashboardViewModel(mockStockRepository, mockAuthRepository)
            DashboardScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Dados offline", substring = true).assertIsDisplayed()
    }
}
