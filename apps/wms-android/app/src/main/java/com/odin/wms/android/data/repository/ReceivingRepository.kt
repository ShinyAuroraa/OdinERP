package com.odin.wms.android.data.repository

import android.content.Context
import com.google.gson.Gson
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.common.JwtUtils
import com.odin.wms.android.data.local.dao.ReceivingItemDao
import com.odin.wms.android.data.local.dao.ReceivingOrderDao
import com.odin.wms.android.data.local.dao.ReceivingSyncQueueDao
import com.odin.wms.android.data.local.entity.ReceivingItemCacheEntity
import com.odin.wms.android.data.local.entity.ReceivingOrderCacheEntity
import com.odin.wms.android.data.local.entity.ReceivingSyncQueueEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.ConfirmItemRequestDto
import com.odin.wms.android.data.remote.dto.CompleteReceivingDto
import com.odin.wms.android.data.remote.dto.DivergenceRequestDto
import com.odin.wms.android.domain.model.DivergenceReport
import com.odin.wms.android.domain.model.ReceivingItem
import com.odin.wms.android.domain.model.ReceivingItemStatus
import com.odin.wms.android.domain.model.ReceivingOrder
import com.odin.wms.android.domain.model.ReceivingOrderStatus
import com.odin.wms.android.domain.repository.IReceivingRepository
import com.odin.wms.android.security.TokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceivingRepository @Inject constructor(
    private val apiService: WmsApiService,
    private val orderDao: ReceivingOrderDao,
    private val itemDao: ReceivingItemDao,
    private val syncQueueDao: ReceivingSyncQueueDao,
    private val tokenProvider: TokenProvider,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : IReceivingRepository {

    private val currentTenantId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return JwtUtils.extractTenantId(claims)
        }

    override suspend fun getOrders(tenantId: String): ApiResult<List<ReceivingOrder>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getReceivingOrders(tenantId = tenantId)
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    val orders = dtos.map { it.toDomain() }
                    // Cache to Room
                    val entities = dtos.map { dto ->
                        ReceivingOrderCacheEntity(
                            id = dto.id,
                            tenantId = tenantId,
                            orderNumber = dto.orderNumber,
                            supplier = dto.supplier,
                            expectedDate = dto.expectedDate,
                            status = dto.status,
                            totalItems = dto.totalItems,
                            confirmedItems = dto.confirmedItems,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    }
                    orderDao.insertOrUpdateAll(entities)
                    ApiResult.Success(orders)
                } else {
                    fallbackOrdersFromCache(tenantId)
                }
            } catch (e: IOException) {
                fallbackOrdersFromCache(tenantId)
            } catch (e: HttpException) {
                fallbackOrdersFromCache(tenantId)
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    private suspend fun fallbackOrdersFromCache(tenantId: String): ApiResult<List<ReceivingOrder>> {
        val cached = orderDao.getByTenantId(tenantId)
        val orders = cached.map { entity ->
            ReceivingOrder(
                id = entity.id,
                orderNumber = entity.orderNumber,
                supplier = entity.supplier,
                expectedDate = entity.expectedDate,
                status = parseOrderStatus(entity.status),
                totalItems = entity.totalItems,
                confirmedItems = entity.confirmedItems
            )
        }
        // Wrap as Success but caller checks via isOffline in ViewModel
        return ApiResult.NetworkError("offline:${orders.size}")
    }

    override suspend fun getOrderDetail(orderId: String): ApiResult<ReceivingOrder> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getReceivingOrderDetail(orderId)
                if (response.isSuccessful) {
                    val dto = response.body()!!
                    val order = dto.toDomain()
                    // Cache items
                    val tenantId = currentTenantId
                    val itemEntities = dto.items.map { item ->
                        ReceivingItemCacheEntity(
                            id = item.id,
                            orderId = orderId,
                            tenantId = tenantId,
                            productCode = item.productCode,
                            gtin = item.gtin,
                            description = item.description,
                            expectedQty = item.expectedQty,
                            confirmedQty = item.confirmedQty,
                            localStatus = item.status
                        )
                    }
                    itemDao.insertOrUpdateAll(itemEntities)
                    ApiResult.Success(order)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                // Fallback: build from cached order + items
                val cachedOrder = orderDao.getById(orderId)
                val cachedItems = itemDao.getByOrderId(orderId)
                if (cachedOrder != null) {
                    val items = cachedItems.map { item ->
                        ReceivingItem(
                            id = item.id,
                            orderId = item.orderId,
                            productCode = item.productCode,
                            gtin = item.gtin,
                            description = item.description,
                            expectedQty = item.expectedQty,
                            confirmedQty = item.confirmedQty,
                            localStatus = parseItemStatus(item.localStatus)
                        )
                    }
                    ApiResult.Success(
                        ReceivingOrder(
                            id = cachedOrder.id,
                            orderNumber = cachedOrder.orderNumber,
                            supplier = cachedOrder.supplier,
                            expectedDate = cachedOrder.expectedDate,
                            status = parseOrderStatus(cachedOrder.status),
                            totalItems = cachedOrder.totalItems,
                            confirmedItems = cachedOrder.confirmedItems,
                            items = items
                        )
                    )
                } else {
                    ApiResult.NetworkError("Sem conexão e dados não disponíveis offline")
                }
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun confirmItem(
        orderId: String,
        itemId: String,
        quantity: Int,
        lotNumber: String?,
        expiryDate: String?,
        serialNumber: String?
    ): ApiResult<ReceivingItem> = withContext(Dispatchers.IO) {
        val request = ConfirmItemRequestDto(
            quantity = quantity,
            lotNumber = lotNumber,
            expiryDate = expiryDate,
            serialNumber = serialNumber
        )
        try {
            val response = apiService.confirmReceivingItem(orderId, itemId, request)
            if (response.isSuccessful) {
                val item = response.body()!!.toDomain()
                itemDao.updateLocalStatus(itemId, "CONFIRMED", quantity)
                ApiResult.Success(item)
            } else if (response.code() == 409) {
                // Conflict — queue anyway
                enqueueConfirm(orderId, itemId, request)
                itemDao.updateLocalStatus(itemId, "CONFIRMED_OFFLINE", quantity)
                ApiResult.Error("Conflito — operação enfileirada para sync")
            } else {
                ApiResult.Error("Erro HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            enqueueConfirm(orderId, itemId, request)
            itemDao.updateLocalStatus(itemId, "CONFIRMED_OFFLINE", quantity)
            ApiResult.NetworkError("offline_queued")
        } catch (e: Exception) {
            ApiResult.Error("Erro inesperado: ${e.message}")
        }
    }

    private suspend fun enqueueConfirm(
        orderId: String,
        itemId: String,
        request: ConfirmItemRequestDto
    ) {
        val tenantId = currentTenantId
        syncQueueDao.insert(
            ReceivingSyncQueueEntity(
                tenantId = tenantId,
                operationType = "CONFIRM",
                orderId = orderId,
                itemId = itemId,
                payload = gson.toJson(request),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun reportDivergence(
        orderId: String,
        itemId: String,
        report: DivergenceReport
    ): ApiResult<ReceivingItem> = withContext(Dispatchers.IO) {
        val tenantId = currentTenantId

        // Save photos to filesDir
        val photoBase64List = ArrayList(report.photoBase64List)
        val savedPhotoPaths = mutableListOf<String>()
        val photoDir = File(context.filesDir, "receiving/$tenantId").apply { mkdirs() }
        photoBase64List.forEachIndexed { index, base64 ->
            val file = File(photoDir, "div_${itemId}_$index.jpg")
            file.writeText(base64)
            savedPhotoPaths.add(file.absolutePath)
        }

        val requestDto = DivergenceRequestDto(
            type = report.type.name,
            actualQty = report.actualQty,
            notes = report.notes,
            photos = photoBase64List
        )

        try {
            val response = apiService.reportDivergence(orderId, itemId, requestDto)
            if (response.isSuccessful) {
                val item = response.body()!!.toDomain()
                itemDao.updateLocalStatus(itemId, "DIVERGENT", report.actualQty)
                // Clean up local photos after successful sync
                savedPhotoPaths.forEach { path -> File(path).delete() }
                ApiResult.Success(item)
            } else {
                ApiResult.Error("Erro HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            // Queue divergence with photo paths in payload
            val payloadMap = mapOf(
                "type" to report.type.name,
                "actualQty" to report.actualQty,
                "notes" to report.notes,
                "photoPaths" to savedPhotoPaths
            )
            syncQueueDao.insert(
                ReceivingSyncQueueEntity(
                    tenantId = tenantId,
                    operationType = "DIVERGENCE",
                    orderId = orderId,
                    itemId = itemId,
                    payload = gson.toJson(payloadMap),
                    createdAt = System.currentTimeMillis()
                )
            )
            itemDao.updateLocalStatus(itemId, "DIVERGENT", report.actualQty)
            ApiResult.NetworkError("offline_queued")
        } catch (e: Exception) {
            ApiResult.Error("Erro inesperado: ${e.message}")
        }
    }

    override suspend fun submitSignature(
        orderId: String,
        signatureBase64: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val tenantId = currentTenantId
        val completedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val request = CompleteReceivingDto(
            signatureBase64 = signatureBase64,
            completedAt = completedAt
        )
        try {
            val response = apiService.submitSignature(orderId, request)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("Erro HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            syncQueueDao.insert(
                ReceivingSyncQueueEntity(
                    tenantId = tenantId,
                    operationType = "SIGNATURE",
                    orderId = orderId,
                    payload = gson.toJson(request),
                    signatureBase64 = signatureBase64,
                    createdAt = System.currentTimeMillis()
                )
            )
            ApiResult.NetworkError("offline_queued")
        } catch (e: Exception) {
            ApiResult.Error("Erro inesperado: ${e.message}")
        }
    }

    override suspend fun completeOrder(orderId: String): ApiResult<ReceivingOrder> =
        withContext(Dispatchers.IO) {
            val tenantId = currentTenantId
            try {
                val response = apiService.completeReceivingOrder(orderId)
                if (response.isSuccessful) {
                    val order = response.body()!!.toDomain()
                    orderDao.updateStatus(orderId, "COMPLETED")
                    ApiResult.Success(order)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                syncQueueDao.insert(
                    ReceivingSyncQueueEntity(
                        tenantId = tenantId,
                        operationType = "COMPLETE",
                        orderId = orderId,
                        payload = "{}",
                        createdAt = System.currentTimeMillis()
                    )
                )
                orderDao.updateStatus(orderId, "COMPLETED")
                ApiResult.NetworkError("offline_queued")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun cancelOrder(orderId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.cancelReceivingOrder(orderId)
                if (response.isSuccessful) {
                    orderDao.updateStatus(orderId, "CANCELLED")
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

    override suspend fun getPendingSyncCount(tenantId: String): Int =
        withContext(Dispatchers.IO) {
            syncQueueDao.getPendingCount(tenantId)
        }

    // --- Helpers ---

    private fun parseOrderStatus(status: String): ReceivingOrderStatus = when (status) {
        "IN_PROGRESS" -> ReceivingOrderStatus.IN_PROGRESS
        "COMPLETED"   -> ReceivingOrderStatus.COMPLETED
        "CANCELLED"   -> ReceivingOrderStatus.CANCELLED
        else          -> ReceivingOrderStatus.PENDING
    }

    private fun parseItemStatus(status: String): ReceivingItemStatus = when (status) {
        "CONFIRMED"         -> ReceivingItemStatus.CONFIRMED
        "CONFIRMED_OFFLINE" -> ReceivingItemStatus.CONFIRMED_OFFLINE
        "DIVERGENT"         -> ReceivingItemStatus.DIVERGENT
        else                -> ReceivingItemStatus.PENDING
    }
}
