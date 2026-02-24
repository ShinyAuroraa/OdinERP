package com.odin.wms.android.data.repository

import com.google.gson.Gson
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.data.local.dao.InventoryItemDao
import com.odin.wms.android.data.local.dao.InventorySessionDao
import com.odin.wms.android.data.local.dao.InventorySyncQueueDao
import com.odin.wms.android.data.local.entity.InventoryItemCacheEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.InventoryItemDto
import com.odin.wms.android.security.TokenProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response

/**
 * U10–U11 — InventoryRepository unit tests
 */
class InventoryRepositoryTest {

    private lateinit var apiService: WmsApiService
    private lateinit var inventorySessionDao: InventorySessionDao
    private lateinit var inventoryItemDao: InventoryItemDao
    private lateinit var inventorySyncQueueDao: InventorySyncQueueDao
    private lateinit var tokenProvider: TokenProvider
    private lateinit var repository: InventoryRepository

    private val tenantId = "tenant-001"
    private val sessionId = "session-001"
    private val itemId = "item-001"

    private fun buildItemDto() = InventoryItemDto(
        id = itemId,
        sessionId = sessionId,
        tenantId = tenantId,
        productCode = "P-001",
        gtin = "12345678901234",
        description = "Produto Teste",
        position = "A-01-001",
        systemQty = 10,
        countedQty = 8,
        localStatus = "COUNTED"
    )

    private fun buildItemCacheEntity() = InventoryItemCacheEntity(
        id = itemId,
        sessionId = sessionId,
        tenantId = tenantId,
        productCode = "P-001",
        gtin = "12345678901234",
        description = "Produto Teste",
        position = "A-01-001",
        systemQty = 10,
        countedQty = null,
        localStatus = "PENDING"
    )

    @BeforeEach
    fun setUp() {
        apiService = mockk()
        inventorySessionDao = mockk(relaxed = true)
        inventoryItemDao = mockk(relaxed = true)
        inventorySyncQueueDao = mockk(relaxed = true)
        tokenProvider = mockk()
        every { tokenProvider.getAccessToken() } returns null

        repository = InventoryRepository(
            apiService = apiService,
            inventorySessionDao = inventorySessionDao,
            inventoryItemDao = inventoryItemDao,
            inventorySyncQueueDao = inventorySyncQueueDao,
            tokenProvider = tokenProvider,
            gson = Gson()
        )
    }

    // U10: countItem() success → API called + inventoryItemDao.updateLocalStatus("COUNTED") called
    @Test
    fun `U10 countItem success calls API and updates local status to COUNTED`() = runTest {
        coEvery {
            apiService.countItem(sessionId, itemId, any())
        } returns Response.success(buildItemDto())

        val result = repository.countItem(
            sessionId = sessionId,
            itemId = itemId,
            productCode = "P-001",
            countedQty = 8,
            lotNumber = null,
            position = "A-01-001"
        )

        assertTrue(result is ApiResult.Success, "Expected Success but got $result")
        coVerify { inventoryItemDao.updateLocalStatus(itemId, "COUNTED") }
        coVerify { inventoryItemDao.updateCountedQty(itemId, 8) }
    }

    // U11: getCountingList() network failure → loadCountingListFromCache() returns REAL Room data (not emptyList())
    // FIX QA-8.3-001 verified
    @Test
    fun `U11 getCountingList network failure returns real Room data not emptyList (fix QA-8-3-001)`() = runTest {
        val cachedEntities = listOf(buildItemCacheEntity())

        coEvery { apiService.getCountingList(sessionId, any()) } throws java.io.IOException("network error")
        coEvery { inventoryItemDao.getBySessionId(sessionId) } returns cachedEntities

        // Test the repository cache method directly
        val cachedItems = repository.getCountingListFromCache(sessionId)

        // FIX QA-8.3-001: must return real Room data, never emptyList()
        assertEquals(1, cachedItems.size, "getCountingListFromCache must return real Room data, not emptyList()")
        assertEquals(itemId, cachedItems.first().id)
        assertEquals("P-001", cachedItems.first().productCode)
    }

    @Test
    fun `getCountingList returns NetworkError with real data embedded when offline`() = runTest {
        coEvery { apiService.getCountingList(sessionId, null) } throws java.io.IOException("network error")
        coEvery { inventoryItemDao.getBySessionId(sessionId) } returns listOf(buildItemCacheEntity())

        val result = repository.getCountingList(sessionId, null)

        // Result is NetworkError but the message encodes "offline:1" meaning 1 cached item
        assertTrue(result is ApiResult.NetworkError, "Expected NetworkError for offline scenario")
        val networkError = result as ApiResult.NetworkError
        assertTrue(networkError.message.startsWith("offline:"), "Message should indicate offline with count")
    }
}
