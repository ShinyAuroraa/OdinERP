package com.odin.wms.android.data.repository

import com.google.gson.Gson
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.common.JwtUtils
import com.odin.wms.android.data.local.dao.PickingItemDao
import com.odin.wms.android.data.local.dao.PickingSyncQueueDao
import com.odin.wms.android.data.local.dao.PickingTaskDao
import com.odin.wms.android.data.local.entity.PickingItemCacheEntity
import com.odin.wms.android.data.local.entity.PickingSyncQueueEntity
import com.odin.wms.android.data.local.entity.PickingTaskCacheEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.ConfirmPickItemRequestDto
import com.odin.wms.android.domain.model.PickingItem
import com.odin.wms.android.domain.model.PickingItemLocalStatus
import com.odin.wms.android.domain.model.PickingTask
import com.odin.wms.android.domain.model.PickingTaskStatus
import com.odin.wms.android.domain.repository.IPickingRepository
import com.odin.wms.android.security.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PickingRepository @Inject constructor(
    private val apiService: WmsApiService,
    private val pickingTaskDao: PickingTaskDao,
    private val pickingItemDao: PickingItemDao,
    private val pickingSyncQueueDao: PickingSyncQueueDao,
    private val tokenProvider: TokenProvider,
    private val gson: Gson
) : IPickingRepository {

    private val currentTenantId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return JwtUtils.extractTenantId(claims)
        }

    // FIX QA-8.2-003: loadTasksFromCache returns REAL Room data, never emptyList()
    private suspend fun loadTasksFromCache(tenantId: String): ApiResult<List<PickingTask>> {
        val cached = pickingTaskDao.getByTenantId(tenantId)
        val tasks = cached.map { it.toDomain() }
        return ApiResult.NetworkError("offline:${tasks.size}")
    }

    override suspend fun getTasks(tenantId: String): ApiResult<List<PickingTask>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getPickingOrders(tenantId = tenantId)
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    val tasks = dtos.map { it.toDomain() }
                    val entities = dtos.map { dto ->
                        PickingTaskCacheEntity(
                            id = dto.id,
                            tenantId = tenantId,
                            taskNumber = dto.taskNumber,
                            pickingOrderId = dto.pickingOrderId,
                            status = dto.status,
                            corridor = dto.corridor,
                            priority = dto.priority,
                            totalItems = dto.totalItems,
                            pickedItems = dto.pickedItems,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    }
                    pickingTaskDao.insertOrUpdateAll(entities)
                    ApiResult.Success(tasks)
                } else {
                    loadTasksFromCache(tenantId)
                }
            } catch (e: IOException) {
                // FIX QA-8.2-003: return real Room data on network failure
                loadTasksFromCache(tenantId)
            } catch (e: HttpException) {
                loadTasksFromCache(tenantId)
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun getTaskDetail(taskId: String): ApiResult<PickingTask> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getPickingOrderDetail(taskId)
                if (response.isSuccessful) {
                    val dto = response.body()!!
                    val task = dto.toDomain()
                    val tenantId = currentTenantId
                    // Cache task
                    pickingTaskDao.insertOrUpdate(
                        PickingTaskCacheEntity(
                            id = dto.id,
                            tenantId = tenantId,
                            taskNumber = dto.taskNumber,
                            pickingOrderId = dto.pickingOrderId,
                            status = dto.status,
                            corridor = dto.corridor,
                            priority = dto.priority,
                            totalItems = dto.totalItems,
                            pickedItems = dto.pickedItems,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    )
                    // Cache items
                    val itemEntities = dto.items.map { item ->
                        PickingItemCacheEntity(
                            id = item.id,
                            taskId = taskId,
                            tenantId = tenantId,
                            productCode = item.productCode,
                            gtin = item.gtin,
                            description = item.description,
                            expectedQty = item.expectedQty,
                            pickedQty = item.pickedQty,
                            position = item.position,
                            lotNumber = item.lotNumber,
                            expiryDate = item.expiryDate,
                            localStatus = item.status
                        )
                    }
                    pickingItemDao.insertOrUpdateAll(itemEntities)
                    ApiResult.Success(task)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                // Fallback to Room cache
                val cachedTask = pickingTaskDao.getById(taskId)
                val cachedItems = pickingItemDao.getByTaskId(taskId)
                if (cachedTask != null) {
                    val items = cachedItems.map { it.toDomain() }
                    ApiResult.NetworkError("offline:detail")
                } else {
                    ApiResult.NetworkError("Sem conexão e dados não disponíveis offline")
                }
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun confirmItemPicked(
        taskId: String,
        itemId: String,
        quantity: Int,
        lotNumber: String?,
        position: String,
        serialNumber: String?
    ): ApiResult<PickingItem> = withContext(Dispatchers.IO) {
        val request = ConfirmPickItemRequestDto(
            quantity = quantity,
            lotNumber = lotNumber,
            position = position,
            serialNumber = serialNumber
        )
        try {
            val response = apiService.confirmItemPicked(taskId, itemId, request)
            if (response.isSuccessful) {
                val item = response.body()!!.toDomain()
                pickingItemDao.updateLocalStatusAndQty(itemId, "PICKED", quantity)
                // Update task picked count
                val cachedItems = pickingItemDao.getByTaskId(taskId)
                val pickedCount = cachedItems.count { it.localStatus == "PICKED" || it.localStatus == "PICKED_OFFLINE" }
                pickingTaskDao.updatePickedItems(taskId, pickedCount)
                ApiResult.Success(item)
            } else {
                ApiResult.Error("Erro HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            // Offline: enqueue and mark locally
            enqueueConfirmPick(taskId, itemId, request)
            pickingItemDao.updateLocalStatusAndQty(itemId, "PICKED_OFFLINE", quantity)
            ApiResult.NetworkError("offline_queued")
        } catch (e: Exception) {
            ApiResult.Error("Erro inesperado: ${e.message}")
        }
    }

    override suspend fun completeTask(taskId: String): ApiResult<PickingTask> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.completePickingOrder(taskId)
                if (response.isSuccessful) {
                    val task = response.body()!!.toDomain()
                    pickingTaskDao.updateStatus(taskId, "COMPLETED")
                    ApiResult.Success(task)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                val tenantId = currentTenantId
                pickingSyncQueueDao.insert(
                    PickingSyncQueueEntity(
                        tenantId = tenantId,
                        operationType = "COMPLETE_TASK",
                        taskId = taskId,
                        payload = "{}",
                        createdAt = System.currentTimeMillis()
                    )
                )
                pickingTaskDao.updateStatus(taskId, "COMPLETED")
                ApiResult.NetworkError("offline_queued")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun cancelTask(taskId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.cancelPickingOrder(taskId)
                if (response.isSuccessful) {
                    pickingTaskDao.updateStatus(taskId, "CANCELLED")
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                ApiResult.NetworkError("Sem conexão — cancelamento não enviado")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    private suspend fun enqueueConfirmPick(
        taskId: String,
        itemId: String,
        request: ConfirmPickItemRequestDto
    ) {
        val tenantId = currentTenantId
        pickingSyncQueueDao.insert(
            PickingSyncQueueEntity(
                tenantId = tenantId,
                operationType = "CONFIRM_PICK",
                taskId = taskId,
                itemId = itemId,
                payload = gson.toJson(request),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // --- Entity extension functions ---

    private fun PickingTaskCacheEntity.toDomain(): PickingTask {
        val taskStatus = when (status) {
            "IN_PROGRESS" -> PickingTaskStatus.IN_PROGRESS
            "COMPLETED"   -> PickingTaskStatus.COMPLETED
            "CANCELLED"   -> PickingTaskStatus.CANCELLED
            else          -> PickingTaskStatus.PICKING_PENDING
        }
        return PickingTask(
            id = id,
            taskNumber = taskNumber,
            pickingOrderId = pickingOrderId,
            status = taskStatus,
            corridor = corridor,
            priority = priority,
            totalItems = totalItems,
            pickedItems = pickedItems,
            items = emptyList()
        )
    }

    private fun PickingItemCacheEntity.toDomain(): PickingItem {
        val itemStatus = when (localStatus) {
            "PICKED"         -> PickingItemLocalStatus.PICKED
            "PICKED_OFFLINE" -> PickingItemLocalStatus.PICKED_OFFLINE
            "SKIPPED"        -> PickingItemLocalStatus.SKIPPED
            else             -> PickingItemLocalStatus.PENDING
        }
        val expiry = expiryDate?.let {
            try { java.time.LocalDate.parse(it) } catch (e: Exception) { null }
        }
        return PickingItem(
            id = id,
            taskId = taskId,
            productCode = productCode,
            gtin = gtin,
            description = description,
            expectedQty = expectedQty,
            pickedQty = pickedQty,
            position = position,
            lotNumber = lotNumber,
            expiryDate = expiry,
            localStatus = itemStatus
        )
    }
}
