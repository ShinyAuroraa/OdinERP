package com.odin.wms.android.ui.receiving

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.DivergenceReport
import com.odin.wms.android.domain.model.DivergenceType
import com.odin.wms.android.domain.model.ReceivingItem
import com.odin.wms.android.domain.model.ReceivingItemStatus
import com.odin.wms.android.domain.model.ReceivingOrder
import com.odin.wms.android.domain.model.ReceivingOrderStatus
import com.odin.wms.android.domain.repository.IReceivingRepository
import com.odin.wms.android.security.TokenProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * U4–U8 — ReceivingViewModel unit tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceivingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: IReceivingRepository
    private lateinit var tokenProvider: TokenProvider
    private lateinit var viewModel: ReceivingViewModel

    private val tenantId = "tenant-001"

    private fun buildOrder(id: String = "order-1") = ReceivingOrder(
        id = id,
        orderNumber = "RC-0001",
        supplier = "Fornecedor X",
        expectedDate = "2026-03-01",
        status = ReceivingOrderStatus.PENDING,
        totalItems = 3,
        confirmedItems = 0
    )

    private fun buildItem(id: String = "item-1", status: ReceivingItemStatus = ReceivingItemStatus.PENDING) =
        ReceivingItem(
            id = id,
            orderId = "order-1",
            productCode = "P-001",
            gtin = "07891234567890",
            description = "Produto Teste",
            expectedQty = 10,
            confirmedQty = 0,
            localStatus = status
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        tokenProvider = mockk()
        every { tokenProvider.getAccessToken() } returns null
        viewModel = ReceivingViewModel(repository, tokenProvider)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // U4: loadOrders() — API available → state OrdersLoaded with list
    @Test
    fun `U4 loadOrders when API succeeds emits OrdersLoaded with orders`() = runTest {
        val orders = listOf(buildOrder("o1"), buildOrder("o2"))
        coEvery { repository.getOrders(any()) } returns ApiResult.Success(orders)

        viewModel.loadOrders(tenantId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReceivingUiState.OrdersLoaded)
        assertEquals(2, (state as ReceivingUiState.OrdersLoaded).orders.size)
        assertEquals(false, state.isOffline)
    }

    // U5: loadOrders() — API fails → state OrdersLoaded with isOffline=true
    @Test
    fun `U5 loadOrders when API fails emits OrdersLoaded with isOffline true`() = runTest {
        coEvery { repository.getOrders(any()) } returns ApiResult.NetworkError("offline:0")

        viewModel.loadOrders(tenantId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReceivingUiState.OrdersLoaded)
        assertTrue((state as ReceivingUiState.OrdersLoaded).isOffline)
    }

    // U6: confirmItem() — offline → state SyncQueued
    @Test
    fun `U6 confirmItem when offline emits SyncQueued`() = runTest {
        coEvery {
            repository.confirmItem(any(), any(), any(), any(), any(), any())
        } returns ApiResult.NetworkError("offline_queued")

        viewModel.confirmItem("order-1", "item-1", 5, "LOT-A", null, null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ReceivingUiState.SyncQueued)
    }

    // U7: reportDivergence() — type DAMAGE with photo → success state
    @Test
    fun `U7 reportDivergence with DAMAGE type and photo emits ConfirmSuccess`() = runTest {
        val item = buildItem("item-1", ReceivingItemStatus.PENDING)
        val report = DivergenceReport(
            itemId = "item-1",
            type = DivergenceType.DAMAGE,
            actualQty = 2,
            notes = "Embalagem danificada",
            photoBase64List = listOf("base64photo1")
        )
        coEvery { repository.reportDivergence(any(), any(), any()) } returns ApiResult.Success(item)

        viewModel.reportDivergence("order-1", "item-1", report)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ReceivingUiState.ConfirmSuccess)
    }

    // U8: submitSignatureAndComplete() — success → calls completeOrder after submitSignature
    @Test
    fun `U8 submitSignatureAndComplete calls completeOrder after submitSignature success`() = runTest {
        val order = buildOrder()
        coEvery { repository.submitSignature(any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.completeOrder(any()) } returns ApiResult.Success(order)

        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        viewModel.submitSignatureAndComplete("order-1", bitmap)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.submitSignature("order-1", any()) }
        coVerify(exactly = 1) { repository.completeOrder("order-1") }
        assertTrue(viewModel.uiState.value is ReceivingUiState.ConfirmSuccess)
    }
}
