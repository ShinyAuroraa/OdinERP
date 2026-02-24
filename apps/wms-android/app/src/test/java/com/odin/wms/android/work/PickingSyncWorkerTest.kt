package com.odin.wms.android.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.odin.wms.android.data.local.dao.PickingItemDao
import com.odin.wms.android.data.local.dao.PickingSyncQueueDao
import com.odin.wms.android.data.local.dao.ShippingOrderDao
import com.odin.wms.android.data.local.dao.ShippingPackageDao
import com.odin.wms.android.data.local.entity.PickingSyncQueueEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.ConfirmPickItemRequestDto
import com.odin.wms.android.data.remote.dto.PickingItemDto
import com.odin.wms.android.data.remote.dto.PickingTaskDto
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
 * U7–U9 — PickingSyncWorker unit tests
 */
class PickingSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var apiService: WmsApiService
    private lateinit var pickingSyncQueueDao: PickingSyncQueueDao
    private lateinit var pickingItemDao: PickingItemDao
    private lateinit var shippingOrderDao: ShippingOrderDao
    private lateinit var shippingPackageDao: ShippingPackageDao
    private lateinit var gson: Gson

    private val tenantId = "tenant-001"
    private val taskId = "task-001"
    private val itemId = "item-001"

    private fun buildQueueEntity(
        id: Long = 1L,
        operationType: String = "CONFIRM_PICK",
        retryCount: Int = 0,
        payload: String = """{"quantity":5,"lotNumber":"LOT-001","position":"A-01","serialNumber":null}"""
    ) = PickingSyncQueueEntity(
        id = id,
        tenantId = tenantId,
        operationType = operationType,
        taskId = taskId,
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
        pickingSyncQueueDao = mockk(relaxed = true)
        pickingItemDao = mockk(relaxed = true)
        shippingOrderDao = mockk(relaxed = true)
        shippingPackageDao = mockk(relaxed = true)
        gson = Gson()

        every { workerParams.inputData } returns androidx.work.Data.Builder()
            .putString("tenant_id", tenantId)
            .build()
        every { workerParams.runAttemptCount } returns 0
    }

    private fun buildWorker() = PickingSyncWorker(
        context = context,
        workerParams = workerParams,
        apiService = apiService,
        pickingSyncQueueDao = pickingSyncQueueDao,
        pickingItemDao = pickingItemDao,
        shippingOrderDao = shippingOrderDao,
        shippingPackageDao = shippingPackageDao,
        gson = gson
    )

    // U7: CONFIRM_PICK sync success → item marked SYNCED
    @Test
    fun `U7 CONFIRM_PICK sync success marks item SYNCED`() = runTest {
        val queueItem = buildQueueEntity()
        val itemDto = PickingItemDto(
            id = itemId, taskId = taskId, productCode = "P-001",
            gtin = "12345678901234", description = "Test", expectedQty = 5, status = "PICKED"
        )

        coEvery { pickingSyncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery {
            apiService.confirmItemPicked(taskId, itemId, any())
        } returns Response.success(itemDto)
        coEvery { pickingSyncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pickingSyncQueueDao.updateStatus(1L, "SYNCED") }
    }

    // U8: 404 response → item marked SYNC_CONFLICT (no retry)
    @Test
    fun `U8 404 response marks item SYNC_CONFLICT`() = runTest {
        val queueItem = buildQueueEntity()

        coEvery { pickingSyncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery {
            apiService.confirmItemPicked(taskId, itemId, any())
        } returns Response.error(404, "Not Found".toResponseBody())
        coEvery { pickingSyncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker()
        worker.doWork()

        coVerify { pickingSyncQueueDao.updateStatus(1L, "SYNC_CONFLICT") }
    }

    // U9: 500 response, retry 3x → SYNC_FAILED after max retries
    @Test
    fun `U9 500 response retry 3x leads to SYNC_FAILED`() = runTest {
        val queueItem = buildQueueEntity(retryCount = 2) // already retried 2 times (0-indexed → this is 3rd attempt)

        coEvery { pickingSyncQueueDao.getPendingByTenant(tenantId) } returns listOf(queueItem)
        coEvery {
            apiService.confirmItemPicked(taskId, itemId, any())
        } returns Response.error(500, "Internal Server Error".toResponseBody())
        coEvery { pickingSyncQueueDao.deleteSynced() } returns Unit

        val worker = buildWorker()
        worker.doWork()

        coVerify { pickingSyncQueueDao.updateStatus(1L, "SYNC_FAILED") }
    }
}
