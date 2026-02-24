package com.odin.wms.android.ui.inventory

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventoryItemLocalStatus
import com.odin.wms.android.domain.model.InventorySession
import com.odin.wms.android.domain.model.InventorySessionStatus
import com.odin.wms.android.domain.model.InventorySessionType
import com.odin.wms.android.domain.repository.IInventoryRepository
import io.mockk.coEvery
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * U2–U6 — InventoryViewModel unit tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var inventoryRepository: IInventoryRepository
    private lateinit var viewModel: InventoryViewModel

    private val tenantId = "tenant-001"
    private val sessionId = "session-001"
    private val itemId = "item-001"

    private fun buildSession(id: String = sessionId) = InventorySession(
        id = id,
        tenantId = tenantId,
        sessionNumber = "INV-0001",
        sessionType = InventorySessionType.CYCLIC,
        status = InventorySessionStatus.ACTIVE,
        aisle = "A",
        totalItems = 10,
        countedItems = 3
    )

    private fun buildItem(
        id: String = itemId,
        productCode: String = "P-001",
        systemQty: Int = 10,
        countedQty: Int? = null,
        localStatus: InventoryItemLocalStatus = InventoryItemLocalStatus.PENDING
    ) = InventoryItem(
        id = id,
        sessionId = sessionId,
        tenantId = tenantId,
        productCode = productCode,
        gtin = "12345678901234",
        description = "Produto Teste",
        position = "A-01-001",
        systemQty = systemQty,
        countedQty = countedQty,
        localStatus = localStatus
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inventoryRepository = mockk()
        viewModel = InventoryViewModel(inventoryRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // U2: loadSessions() API ok → SessionsLoaded(sessions, isOffline=false) emitted
    @Test
    fun `U2 loadSessions API ok emits SessionsLoaded with isOffline false`() = runTest {
        val sessions = listOf(buildSession())
        coEvery { inventoryRepository.getSessions(tenantId) } returns ApiResult.Success(sessions)

        viewModel.loadSessions(tenantId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InventoryUiState.SessionsLoaded, "Expected SessionsLoaded but got $state")
        val loaded = state as InventoryUiState.SessionsLoaded
        assertEquals(1, loaded.sessions.size)
        assertFalse(loaded.isOffline)
    }

    // U3: loadSessions() offline → SessionsLoaded with isOffline=true and real Room data (FIX QA-8.3-001)
    @Test
    fun `U3 loadSessions offline emits SessionsLoaded with isOffline true`() = runTest {
        // First call returns NetworkError (offline), second call (cache re-fetch) also returns NetworkError
        coEvery { inventoryRepository.getSessions(tenantId) } returns ApiResult.NetworkError("offline:0")

        viewModel.loadSessions(tenantId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InventoryUiState.SessionsLoaded, "Expected SessionsLoaded but got $state")
        val loaded = state as InventoryUiState.SessionsLoaded
        assertTrue(loaded.isOffline)
    }

    // U4: countItem() success → CountSuccess state emitted
    @Test
    fun `U4 countItem success emits CountSuccess`() = runTest {
        val item = buildItem(countedQty = 10)
        coEvery {
            inventoryRepository.countItem(
                sessionId = sessionId,
                itemId = itemId,
                productCode = "P-001",
                countedQty = 10,
                lotNumber = null,
                position = "A-01-001"
            )
        } returns ApiResult.Success(item)

        viewModel.countItem(
            sessionId = sessionId,
            itemId = itemId,
            productCode = "P-001",
            countedQty = 10,
            systemQty = 10,
            lotNumber = null,
            position = "A-01-001"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InventoryUiState.CountSuccess, "Expected CountSuccess but got $state")
    }

    // U5: DivergenceWarning emitted when countedQty diverges > 10% from systemQty
    @Test
    fun `U5 countItem divergence above 10pct emits DivergenceWarning before CountSuccess`() = runTest {
        val systemQty = 10
        val countedQty = 13 // 30% divergence
        val item = buildItem(countedQty = countedQty)
        coEvery {
            inventoryRepository.countItem(
                sessionId = sessionId,
                itemId = itemId,
                productCode = "P-001",
                countedQty = countedQty,
                lotNumber = null,
                position = "A-01-001"
            )
        } returns ApiResult.Success(item)

        // Collect all states via a list
        val states = mutableListOf<InventoryUiState>()
        val job = kotlinx.coroutines.launch {
            viewModel.uiState.collect { states.add(it) }
        }

        viewModel.countItem(
            sessionId = sessionId,
            itemId = itemId,
            productCode = "P-001",
            countedQty = countedQty,
            systemQty = systemQty,
            lotNumber = null,
            position = "A-01-001"
        )
        advanceUntilIdle()
        job.cancel()

        // DivergenceWarning should have been emitted at some point
        val hasDivergenceWarning = states.any { it is InventoryUiState.DivergenceWarning }
        assertTrue(hasDivergenceWarning, "Expected DivergenceWarning in states: $states")

        // Final state should be CountSuccess (non-blocking)
        val finalState = states.last()
        assertTrue(
            finalState is InventoryUiState.CountSuccess,
            "Expected final CountSuccess but got $finalState"
        )
    }

    // U6: doubleCount() confirms → DoubleCountResult(item, verified=true) when diff ≤ 2
    @Test
    fun `U6 doubleCount success emits DoubleCountResult verified true`() = runTest {
        val verifiedItem = buildItem(
            countedQty = 10,
            localStatus = InventoryItemLocalStatus.COUNTED_VERIFIED
        )
        coEvery {
            inventoryRepository.doubleCount(
                sessionId = sessionId,
                itemId = itemId,
                countedQty = 10,
                counterId = "counter-002"
            )
        } returns ApiResult.Success(verifiedItem)

        viewModel.doubleCount(
            sessionId = sessionId,
            itemId = itemId,
            countedQty = 10,
            counterId = "counter-002"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InventoryUiState.DoubleCountResult, "Expected DoubleCountResult but got $state")
        val result = state as InventoryUiState.DoubleCountResult
        assertTrue(result.verified, "Expected verified=true for COUNTED_VERIFIED status")
    }
}
