package com.odin.wms.android.data.repository

import com.google.gson.Gson
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.data.local.dao.PickingItemDao
import com.odin.wms.android.data.local.dao.PickingSyncQueueDao
import com.odin.wms.android.data.local.dao.PickingTaskDao
import com.odin.wms.android.data.local.entity.PickingSyncQueueEntity
import com.odin.wms.android.data.local.entity.PickingTaskCacheEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.ConfirmPickItemRequestDto
import com.odin.wms.android.data.remote.dto.PickingItemDto
import com.odin.wms.android.data.remote.dto.PickingTaskDto
import com.odin.wms.android.security.TokenProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException

/**
 * U10–U12 — PickingRepository unit tests
 */
class PickingRepositoryTest {

    private lateinit var apiService: WmsApiService
    private lateinit var pickingTaskDao: PickingTaskDao
    private lateinit var pickingItemDao: PickingItemDao
    private lateinit var pickingSyncQueueDao: PickingSyncQueueDao
    private lateinit var tokenProvider: TokenProvider
    private lateinit var repository: PickingRepository

    private val tenantId = "tenant-001"
    private val taskId = "task-001"
    private val itemId = "item-001"

    private fun buildTaskDto(id: String = taskId) = PickingTaskDto(
        id = id,
        taskNumber = "PT-0001",
        pickingOrderId = "po-001",
        status = "PICKING_PENDING",
        corridor = "A",
        priority = 1,
        totalItems = 3,
        pickedItems = 0
    )

    private fun buildCacheEntity(id: String = taskId) = PickingTaskCacheEntity(
        id = id,
        tenantId = tenantId,
        taskNumber = "PT-0001",
        pickingOrderId = "po-001",
        status = "PICKING_PENDING",
        corridor = "A",
        priority = 1,
        totalItems = 3,
        pickedItems = 0,
        lastSyncAt = System.currentTimeMillis()
    )

    @BeforeEach
    fun setUp() {
        apiService = mockk()
        pickingTaskDao = mockk(relaxed = true)
        pickingItemDao = mockk(relaxed = true)
        pickingSyncQueueDao = mockk(relaxed = true)
        tokenProvider = mockk()
        every { tokenProvider.getAccessToken() } returns null

        repository = PickingRepository(
            apiService = apiService,
            pickingTaskDao = pickingTaskDao,
            pickingItemDao = pickingItemDao,
            pickingSyncQueueDao = pickingSyncQueueDao,
            tokenProvider = tokenProvider,
            gson = Gson()
        )
    }

    // U10: confirmItemPicked success → DAO called with updated status
    @Test
    fun `U10 confirmItemPicked success returns Success and calls dao updateLocalStatusAndQty`() = runTest {
        val itemDto = PickingItemDto(
            id = itemId, taskId = taskId, productCode = "P-001",
            gtin = "12345678901234", description = "Test", expectedQty = 5,
            pickedQty = 5, status = "PICKED"
        )
        coEvery {
            apiService.confirmItemPicked(
                taskId = taskId,
                itemId = itemId,
                request = ConfirmPickItemRequestDto(quantity = 5, position = "A-01")
            )
        } returns Response.success(itemDto)
        coEvery { pickingItemDao.getByTaskId(taskId) } returns emptyList()

        val result = repository.confirmItemPicked(
            taskId = taskId,
            itemId = itemId,
            quantity = 5,
            lotNumber = null,
            position = "A-01",
            serialNumber = null
        )

        assertTrue(result is ApiResult.Success, "Expected Success but got $result")
        coVerify { pickingItemDao.updateLocalStatusAndQty(itemId, "PICKED", 5) }
    }

    // U11: confirmItemPicked network error → item enqueued in picking_sync_queue
    @Test
    fun `U11 confirmItemPicked network error enqueues operation in sync queue`() = runTest {
        coEvery {
            apiService.confirmItemPicked(
                taskId = taskId,
                itemId = itemId,
                request = any()
            )
        } throws IOException("No network")

        val queueSlot = slot<PickingSyncQueueEntity>()
        coEvery { pickingSyncQueueDao.insert(capture(queueSlot)) } returns Unit

        val result = repository.confirmItemPicked(
            taskId = taskId,
            itemId = itemId,
            quantity = 5,
            lotNumber = null,
            position = "A-01",
            serialNumber = null
        )

        assertTrue(result is ApiResult.NetworkError, "Expected NetworkError but got $result")
        coVerify { pickingSyncQueueDao.insert(any()) }
        assertEquals("CONFIRM_PICK", queueSlot.captured.operationType)
        assertEquals(taskId, queueSlot.captured.taskId)
        assertEquals(itemId, queueSlot.captured.itemId)
    }

    // U12: loadTasksFromCache (getTasks offline) returns real Room data, NOT emptyList (fix QA-8.2-003)
    @Test
    fun `U12 getTasks network error returns real Room cache data not emptyList`() = runTest {
        val cachedEntity = buildCacheEntity()
        coEvery { apiService.getPickingOrders(tenantId = tenantId) } throws IOException("No network")
        coEvery { pickingTaskDao.getByTenantId(tenantId) } returns listOf(cachedEntity)

        val result = repository.getTasks(tenantId)

        // FIX QA-8.2-003: should return NetworkError (which carries offline data context)
        // but critically, should NOT be ApiResult.Error (blank) or an empty Success
        assertTrue(
            result is ApiResult.NetworkError,
            "Expected NetworkError (offline with cache) but got $result"
        )
        // Verify DAO was actually called — not bypassed with emptyList()
        coVerify { pickingTaskDao.getByTenantId(tenantId) }
    }
}
