package com.odin.wms.android.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.odin.wms.android.data.local.dao.InventoryItemDao
import com.odin.wms.android.data.local.dao.InventorySessionDao
import com.odin.wms.android.data.local.dao.InventorySyncQueueDao
import com.odin.wms.android.data.local.dao.TransferDao
import com.odin.wms.android.data.local.entity.InventorySyncQueueEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.CountItemRequestDto
import com.odin.wms.android.data.remote.dto.InventoryItemDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response

/**
 * U7–U9 — InventorySyncWorker unit tests
 */
class InventorySyncWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var apiService: WmsApiService
    private lateinit var inventorySyncQueueDao: InventorySyncQueueDao
    private lateinit var inventoryItemDao: InventoryItemDao
    private lateinit var inventorySessionDao: InventorySessionDao
    private lateinit var transferDao: TransferDao
    private lateinit var gson: Gson

    private val tenantId = "tenant-001"
    private val sessionId = "session-001"
    private val itemId = "item-001"

    private fun buildQueueEntity(
        id: Long = 1L,
        operationType: String = "COUNT_ITEM",
        retryCount: Int = 0,
        payload: String = """{"productCode":"P-001","countedQty":10,"lotNumber":null,"position":"A-01"}"""
    ) = InventorySyncQueueEntity(
        id = id,
        tenantId = tenantId,
        operationType = operationType,
        sessionId = sessionId,
        itemId = itemId,
        payload = payload,
        status = "PENDING",
        retryCount = retryCount,
        createdAt = System.currentTimeMillis()
    )

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        apiService = mockk()
        inventorySyncQueueDao = mockk(relaxed = true)
        inventoryItemDao = mockk(relaxed = true)
        inventorySessionDao = mockk(relaxed = true)
        transferDao = mockk(relaxed = true)
        gson = Gson()

        every { workerParams.inputData } returns androidx.work.Data.Builder()
            .putString("tenant_id", tenantId)
            .build()
        every { workerParams.runAttemptCount } returns 0
    }

    private fun buildWorker() = InventorySyncWorker(
        context = context,
        workerParams = workerParams,
        wmsApiService = apiService,
        inventorySyncQueueDao = inventorySyncQueueDao,
        inventoryItemDao = inventoryItemDao,
        inventorySessionDao = inventorySessionDao,
        transferDao = transferDao,
        gson = gson
    )

    // U7: COUNT_ITEM sync success → item marked SYNCED
    @Test
    fun `U7 COUNT_ITEM sync success marks item SYNCED`() = runTest {
        val queueItem = buildQueueEntity()
        val itemDto = InventoryItemDto(
            id = itemId, sessionId = sessionId, tenantId = tenantId,
            productCode = "P-001", gtin = "12345678901234",
            description = "Test", position = "A-01", systemQty = 10,
            localStatus = "COUNTED"
        )

        coEvery { inventorySyncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery { apiService.countItem(sessionId, itemId, any()) } returns Response.success(itemDto)
        coEvery { inventorySyncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { inventorySyncQueueDao.updateStatus(1L, "SYNCED") }
    }

    // U8: 404 response → item marked SYNC_CONFLICT
    @Test
    fun `U8 404 response marks item SYNC_CONFLICT`() = runTest {
        val queueItem = buildQueueEntity()

        coEvery { inventorySyncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery {
            apiService.countItem(sessionId, itemId, any())
        } returns Response.error(404, "Not Found".toResponseBody())
        coEvery { inventorySyncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker()
        worker.doWork()

        coVerify { inventorySyncQueueDao.updateStatus(1L, "SYNC_CONFLICT") }
    }

    // U9: 500 response, retry 3x → SYNC_FAILED after max retries
    @Test
    fun `U9 500 response retry 3x leads to SYNC_FAILED`() = runTest {
        // Already retried 2 times (retryCount=2 means 3rd attempt — MAX_RETRIES=3)
        val queueItem = buildQueueEntity(retryCount = 2)

        coEvery { inventorySyncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery {
            apiService.countItem(sessionId, itemId, any())
        } returns Response.error(500, "Internal Server Error".toResponseBody())
        coEvery { inventorySyncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker()
        worker.doWork()

        coVerify { inventorySyncQueueDao.updateStatus(1L, "SYNC_FAILED") }
    }

    // U10: TRANSFER 409 → treated as SYNCED (idempotency)
    @Test
    fun `U10 TRANSFER 409 response marks item SYNCED as idempotent`() = runTest {
        val transferPayload = """{"sourceLocation":"A-01","destinationLocation":"B-01","productCode":"P-001","qty":5,"lotNumber":null}"""
        val queueItem = buildQueueEntity(
            operationType = "TRANSFER",
            payload = transferPayload,
            retryCount = 0
        )

        coEvery { inventorySyncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery {
            apiService.createTransfer(any())
        } returns Response.error(409, "Conflict".toResponseBody())
        coEvery { inventorySyncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker()
        worker.doWork()

        // 409 on create should be treated as SYNCED (idempotency)
        coVerify { inventorySyncQueueDao.updateStatus(1L, "SYNCED") }
    }
}
