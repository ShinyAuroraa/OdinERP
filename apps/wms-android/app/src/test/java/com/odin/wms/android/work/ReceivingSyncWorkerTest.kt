package com.odin.wms.android.work

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestWorkerBuilder
import com.google.gson.Gson
import com.odin.wms.android.data.local.dao.ReceivingItemDao
import com.odin.wms.android.data.local.dao.ReceivingOrderDao
import com.odin.wms.android.data.local.dao.ReceivingSyncQueueDao
import com.odin.wms.android.data.local.entity.ReceivingSyncQueueEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.ConfirmItemRequestDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.util.concurrent.Executors

/**
 * U9, U10, U11 — ReceivingSyncWorker unit tests
 *
 * Note: These tests use TestWorkerBuilder from work-testing artifact.
 * They run on JVM without Android instrumentation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceivingSyncWorkerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val gson = Gson()

    private lateinit var context: Context
    private lateinit var apiService: WmsApiService
    private lateinit var syncQueueDao: ReceivingSyncQueueDao
    private lateinit var orderDao: ReceivingOrderDao
    private lateinit var itemDao: ReceivingItemDao

    private val tenantId = "tenant-001"

    private fun buildQueueItem(
        id: Long = 1L,
        operationType: String = "CONFIRM",
        retryCount: Int = 0,
        itemId: String? = "item-1"
    ): ReceivingSyncQueueEntity {
        val payload = gson.toJson(
            ConfirmItemRequestDto(quantity = 5, lotNumber = "LOT-A", expiryDate = null, serialNumber = null)
        )
        return ReceivingSyncQueueEntity(
            id = id,
            tenantId = tenantId,
            operationType = operationType,
            orderId = "order-1",
            itemId = itemId,
            payload = payload,
            retryCount = retryCount,
            createdAt = System.currentTimeMillis()
        )
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        apiService = mockk()
        syncQueueDao = mockk(relaxed = true)
        orderDao = mockk(relaxed = true)
        itemDao = mockk(relaxed = true)
    }

    private fun buildWorker(tenantId: String): ReceivingSyncWorker {
        val inputData = Data.Builder()
            .putString(ReceivingSyncWorker.KEY_TENANT_ID, tenantId)
            .build()

        // Manual construction since TestWorkerBuilder requires real Context for some APIs
        val params = mockk<WorkerParameters>(relaxed = true)
        coEvery { params.inputData } returns inputData
        coEvery { params.runAttemptCount } returns 0

        return ReceivingSyncWorker(
            context = context,
            params = params,
            apiService = apiService,
            syncQueueDao = syncQueueDao,
            orderDao = orderDao,
            itemDao = itemDao,
            gson = gson
        )
    }

    // U9: Queue with 1 CONFIRM item, REST succeeds → status SYNCED
    @Test
    fun `U9 doWork with CONFIRM queue item on success marks SYNCED`() = runTest {
        val queueItem = buildQueueItem()
        coEvery { syncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery { apiService.confirmReceivingItem(any(), any(), any()) } returns Response.success(
            com.odin.wms.android.data.remote.dto.ReceivingItemDto(
                id = "item-1", orderId = "order-1", productCode = "P001",
                gtin = "07891234567890", description = "Test", expectedQty = 10, confirmedQty = 5
            )
        )
        coEvery { syncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker(tenantId)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { syncQueueDao.updateStatus(queueItem.id, "SYNCED", queueItem.retryCount) }
    }

    // U10: 404 response (conflict) → item SYNC_CONFLICT, no retry
    @Test
    fun `U10 doWork with 404 response marks SYNC_CONFLICT`() = runTest {
        val queueItem = buildQueueItem()
        coEvery { syncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery { apiService.confirmReceivingItem(any(), any(), any()) } returns Response.error(
            404, ResponseBody.create(null, "Not Found")
        )
        coEvery { syncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker(tenantId)
        worker.doWork()

        coVerify { syncQueueDao.updateStatus(queueItem.id, "SYNC_CONFLICT", queueItem.retryCount) }
    }

    // U11: 500 response → retryCount incremented; after 3 attempts → SYNC_FAILED
    @Test
    fun `U11 doWork with 500 after max retries marks SYNC_FAILED`() = runTest {
        // Item already at retryCount = 2 (next increment = 3 = MAX_RETRIES)
        val queueItem = buildQueueItem(retryCount = 2)
        coEvery { syncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery { apiService.confirmReceivingItem(any(), any(), any()) } returns Response.error(
            500, ResponseBody.create(null, "Internal Server Error")
        )
        coEvery { syncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker(tenantId)
        worker.doWork()

        // newRetryCount = 3 >= MAX_RETRIES(3) → SYNC_FAILED
        coVerify { syncQueueDao.updateStatus(queueItem.id, "SYNC_FAILED", 3) }
    }
}
