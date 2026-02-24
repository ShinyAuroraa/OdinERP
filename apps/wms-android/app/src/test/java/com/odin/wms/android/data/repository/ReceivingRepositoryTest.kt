package com.odin.wms.android.data.repository

import android.content.Context
import com.google.gson.Gson
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.data.local.dao.ReceivingItemDao
import com.odin.wms.android.data.local.dao.ReceivingOrderDao
import com.odin.wms.android.data.local.dao.ReceivingSyncQueueDao
import com.odin.wms.android.data.local.entity.ReceivingItemCacheEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.ConfirmItemRequestDto
import com.odin.wms.android.data.remote.dto.ReceivingItemDto
import com.odin.wms.android.domain.model.ReceivingItemStatus
import com.odin.wms.android.security.TokenProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.File

/**
 * U12 — ReceivingRepository unit tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceivingRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var apiService: WmsApiService
    private lateinit var orderDao: ReceivingOrderDao
    private lateinit var itemDao: ReceivingItemDao
    private lateinit var syncQueueDao: ReceivingSyncQueueDao
    private lateinit var tokenProvider: TokenProvider
    private lateinit var context: Context
    private lateinit var repository: ReceivingRepository

    private val successItemDto = ReceivingItemDto(
        id = "item-1",
        orderId = "order-1",
        productCode = "P001",
        gtin = "07891234567890",
        description = "Produto X",
        expectedQty = 10,
        confirmedQty = 5,
        status = "CONFIRMED"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apiService = mockk()
        orderDao = mockk(relaxed = true)
        itemDao = mockk(relaxed = true)
        syncQueueDao = mockk(relaxed = true)
        tokenProvider = mockk()
        context = mockk(relaxed = true)

        every { tokenProvider.getAccessToken() } returns null
        every { context.filesDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")

        repository = ReceivingRepository(
            apiService = apiService,
            orderDao = orderDao,
            itemDao = itemDao,
            syncQueueDao = syncQueueDao,
            tokenProvider = tokenProvider,
            gson = Gson(),
            context = context
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // U12: confirmItem() — success → ReceivingItemDao.updateLocalStatus(CONFIRMED) called
    @Test
    fun `U12 confirmItem on API success calls itemDao updateLocalStatus with CONFIRMED`() = runTest {
        coEvery {
            apiService.confirmReceivingItem("order-1", "item-1", any())
        } returns Response.success(successItemDto)

        val result = repository.confirmItem(
            orderId = "order-1",
            itemId = "item-1",
            quantity = 5,
            lotNumber = "LOT-A",
            expiryDate = null,
            serialNumber = null
        )

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { itemDao.updateLocalStatus("item-1", "CONFIRMED", 5) }
    }

    @Test
    fun `confirmItem on network error queues operation and marks CONFIRMED_OFFLINE`() = runTest {
        coEvery {
            apiService.confirmReceivingItem(any(), any(), any())
        } throws java.io.IOException("No network")
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.confirmItem(
            orderId = "order-1",
            itemId = "item-1",
            quantity = 3,
            lotNumber = null,
            expiryDate = null,
            serialNumber = null
        )

        assertTrue(result is ApiResult.NetworkError)
        coVerify(exactly = 1) { syncQueueDao.insert(any()) }
        coVerify(exactly = 1) { itemDao.updateLocalStatus("item-1", "CONFIRMED_OFFLINE", 3) }
    }
}
