package com.odin.wms.android.data.repository

import com.google.gson.Gson
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.common.JwtUtils
import com.odin.wms.android.data.local.dao.InventoryItemDao
import com.odin.wms.android.data.local.dao.InventorySessionDao
import com.odin.wms.android.data.local.dao.InventorySyncQueueDao
import com.odin.wms.android.data.local.entity.InventoryItemCacheEntity
import com.odin.wms.android.data.local.entity.InventorySessionCacheEntity
import com.odin.wms.android.data.local.entity.InventorySyncQueueEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.CountItemRequestDto
import com.odin.wms.android.data.remote.dto.DoubleCountRequestDto
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventoryItemLocalStatus
import com.odin.wms.android.domain.model.InventorySession
import com.odin.wms.android.domain.repository.IInventoryRepository
import com.odin.wms.android.security.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val apiService: WmsApiService,
    private val inventorySessionDao: InventorySessionDao,
    private val inventoryItemDao: InventoryItemDao,
    private val inventorySyncQueueDao: InventorySyncQueueDao,
    private val tokenProvider: TokenProvider,
    private val gson: Gson
) : IInventoryRepository {

    private val currentTenantId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return JwtUtils.extractTenantId(claims)
        }

    private val currentCounterId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return (claims["sub"] as? String) ?: JwtUtils.extractUsername(claims)
        }

    override suspend fun getSessions(tenantId: String): ApiResult<List<InventorySession>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getInventorySessions(tenantId = tenantId)
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    val sessions = dtos.map { it.toDomain() }
                    val entities = dtos.map { dto ->
                        InventorySessionCacheEntity(
                            id = dto.id,
                            tenantId = dto.tenantId,
                            sessionNumber = dto.sessionNumber,
                            sessionType = dto.sessionType,
                            status = dto.status,
                            aisle = dto.aisle,
                            totalItems = dto.totalItems,
                            countedItems = dto.countedItems,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    }
                    inventorySessionDao.insertOrUpdateAll(entities)
                    ApiResult.Success(sessions)
                } else {
                    loadSessionsFromCache(tenantId)
                }
            } catch (e: IOException) {
                loadSessionsFromCache(tenantId)
            } catch (e: HttpException) {
                loadSessionsFromCache(tenantId)
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    private suspend fun loadSessionsFromCache(tenantId: String): ApiResult<List<InventorySession>> {
        val cached = inventorySessionDao.getByTenantId(tenantId)
        val sessions = cached.map { it.toDomain() }
        return ApiResult.NetworkError("offline:${sessions.size}")
    }

    override suspend fun getSessionDetail(sessionId: String): ApiResult<InventorySession> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getInventorySessionDetail(sessionId)
                if (response.isSuccessful) {
                    val dto = response.body()!!
                    val session = dto.toDomain()
                    inventorySessionDao.insertOrUpdate(
                        InventorySessionCacheEntity(
                            id = dto.id,
                            tenantId = dto.tenantId,
                            sessionNumber = dto.sessionNumber,
                            sessionType = dto.sessionType,
                            status = dto.status,
                            aisle = dto.aisle,
                            totalItems = dto.totalItems,
                            countedItems = dto.countedItems,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    )
                    ApiResult.Success(session)
                } else {
                    val cached = inventorySessionDao.getById(sessionId)
                    if (cached != null) ApiResult.NetworkError("offline:detail")
                    else ApiResult.Error("Sessão não encontrada")
                }
            } catch (e: IOException) {
                val cached = inventorySessionDao.getById(sessionId)
                if (cached != null) ApiResult.NetworkError("offline:detail")
                else ApiResult.Error("Sem conexão e dados não disponíveis offline")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun getCountingList(sessionId: String, aisle: String?): ApiResult<List<InventoryItem>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCountingList(sessionId, aisle)
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    val items = dtos.map { it.toDomain() }
                    val entities = dtos.map { dto ->
                        InventoryItemCacheEntity(
                            id = dto.id,
                            sessionId = dto.sessionId,
                            tenantId = dto.tenantId,
                            productCode = dto.productCode,
                            gtin = dto.gtin,
                            description = dto.description,
                            position = dto.position,
                            systemQty = dto.systemQty,
                            countedQty = dto.countedQty,
                            doubleCountQty = dto.doubleCountQty,
                            lotNumber = dto.lotNumber,
                            firstCounterId = dto.firstCounterId,
                            localStatus = dto.localStatus ?: "PENDING"
                        )
                    }
                    inventoryItemDao.insertOrUpdateAll(entities)
                    ApiResult.Success(items)
                } else {
                    // FIX QA-8.3-001: fallback to real Room data
                    val cached = getCountingListFromCache(sessionId)
                    ApiResult.NetworkError("offline:${cached.size}")
                }
            } catch (e: IOException) {
                // FIX QA-8.3-001: return real Room data, not emptyList()
                val cached = getCountingListFromCache(sessionId)
                ApiResult.NetworkError("offline:${cached.size}")
            } catch (e: HttpException) {
                val cached = getCountingListFromCache(sessionId)
                ApiResult.NetworkError("offline:${cached.size}")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    // FIX QA-8.3-001: exposed for ViewModel — returns REAL Room data (not emptyList())
    override suspend fun getCountingListFromCache(sessionId: String): List<InventoryItem> =
        withContext(Dispatchers.IO) {
            val cached = inventoryItemDao.getBySessionId(sessionId)
            cached.map { it.toDomain() }
        }

    override suspend fun countItem(
        sessionId: String,
        itemId: String,
        productCode: String,
        countedQty: Int,
        lotNumber: String?,
        position: String
    ): ApiResult<InventoryItem> = withContext(Dispatchers.IO) {
        val request = CountItemRequestDto(
            productCode = productCode,
            countedQty = countedQty,
            lotNumber = lotNumber,
            position = position
        )
        try {
            val response = apiService.countItem(sessionId, itemId, request)
            if (response.isSuccessful) {
                val item = response.body()!!.toDomain()
                inventoryItemDao.updateLocalStatus(itemId, "COUNTED")
                inventoryItemDao.updateCountedQty(itemId, countedQty)
                // Store firstCounterId for double-count validation
                inventoryItemDao.updateFirstCounterId(itemId, currentCounterId)
                ApiResult.Success(item)
            } else {
                ApiResult.Error("Erro HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            // Offline: enqueue and mark locally as OFFLINE_COUNTED
            val tenantId = currentTenantId
            inventorySyncQueueDao.insert(
                InventorySyncQueueEntity(
                    tenantId = tenantId,
                    operationType = "COUNT_ITEM",
                    sessionId = sessionId,
                    itemId = itemId,
                    payload = gson.toJson(request),
                    createdAt = System.currentTimeMillis()
                )
            )
            inventoryItemDao.updateLocalStatus(itemId, "OFFLINE_COUNTED")
            inventoryItemDao.updateCountedQty(itemId, countedQty)
            inventoryItemDao.updateFirstCounterId(itemId, currentCounterId)
            ApiResult.NetworkError("offline_queued")
        } catch (e: Exception) {
            ApiResult.Error("Erro inesperado: ${e.message}")
        }
    }

    override suspend fun doubleCount(
        sessionId: String,
        itemId: String,
        countedQty: Int,
        counterId: String
    ): ApiResult<InventoryItem> = withContext(Dispatchers.IO) {
        // Validate locally: counterId must differ from firstCounterId
        val cachedItem = inventoryItemDao.getById(itemId)
        if (cachedItem != null && cachedItem.firstCounterId == counterId) {
            return@withContext ApiResult.Error("Dupla contagem deve ser realizada por operador diferente")
        }

        val request = DoubleCountRequestDto(
            countedQty = countedQty,
            counterId = counterId
        )
        try {
            val response = apiService.doubleCountItem(sessionId, itemId, request)
            if (response.isSuccessful) {
                val item = response.body()!!.toDomain()
                inventoryItemDao.updateDoubleCountQty(itemId, countedQty)
                inventoryItemDao.updateLocalStatus(itemId, item.localStatus.name)
                ApiResult.Success(item)
            } else {
                ApiResult.Error("Erro HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            val tenantId = currentTenantId
            inventorySyncQueueDao.insert(
                InventorySyncQueueEntity(
                    tenantId = tenantId,
                    operationType = "DOUBLE_COUNT",
                    sessionId = sessionId,
                    itemId = itemId,
                    payload = gson.toJson(request),
                    createdAt = System.currentTimeMillis()
                )
            )
            inventoryItemDao.updateDoubleCountQty(itemId, countedQty)
            ApiResult.NetworkError("offline_queued")
        } catch (e: Exception) {
            ApiResult.Error("Erro inesperado: ${e.message}")
        }
    }

    override suspend fun submitSession(sessionId: String): ApiResult<InventorySession> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.submitInventorySession(sessionId)
                if (response.isSuccessful) {
                    val session = response.body()!!.toDomain()
                    inventorySessionDao.updateStatus(sessionId, "COMPLETED")
                    ApiResult.Success(session)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                val tenantId = currentTenantId
                inventorySyncQueueDao.insert(
                    InventorySyncQueueEntity(
                        tenantId = tenantId,
                        operationType = "SUBMIT_SESSION",
                        sessionId = sessionId,
                        payload = "{}",
                        createdAt = System.currentTimeMillis()
                    )
                )
                ApiResult.NetworkError("offline_queued")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }
}
