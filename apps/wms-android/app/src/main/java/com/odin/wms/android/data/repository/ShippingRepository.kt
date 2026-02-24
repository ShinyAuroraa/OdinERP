package com.odin.wms.android.data.repository

import com.google.gson.Gson
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.common.JwtUtils
import com.odin.wms.android.data.local.dao.PickingSyncQueueDao
import com.odin.wms.android.data.local.dao.ShippingOrderDao
import com.odin.wms.android.data.local.dao.ShippingPackageDao
import com.odin.wms.android.data.local.entity.PickingSyncQueueEntity
import com.odin.wms.android.data.local.entity.ShippingOrderCacheEntity
import com.odin.wms.android.data.local.entity.ShippingPackageCacheEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.LoadPackageRequestDto
import com.odin.wms.android.domain.model.ShippingOrder
import com.odin.wms.android.domain.model.ShippingOrderStatus
import com.odin.wms.android.domain.model.ShippingPackage
import com.odin.wms.android.domain.model.ShippingPackageStatus
import com.odin.wms.android.domain.repository.IShippingRepository
import com.odin.wms.android.security.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShippingRepository @Inject constructor(
    private val apiService: WmsApiService,
    private val shippingOrderDao: ShippingOrderDao,
    private val shippingPackageDao: ShippingPackageDao,
    private val pickingSyncQueueDao: PickingSyncQueueDao,
    private val tokenProvider: TokenProvider,
    private val gson: Gson
) : IShippingRepository {

    private val currentTenantId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return JwtUtils.extractTenantId(claims)
        }

    // FIX QA-8.2-003: loadOrdersFromCache returns REAL Room data, never emptyList()
    private suspend fun loadOrdersFromCache(tenantId: String): ApiResult<List<ShippingOrder>> {
        val cached = shippingOrderDao.getByTenantId(tenantId)
        val orders = cached.map { it.toDomain() }
        return ApiResult.NetworkError("offline:${orders.size}")
    }

    override suspend fun getOrders(tenantId: String): ApiResult<List<ShippingOrder>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getShippingOrders(tenantId = tenantId)
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    val orders = dtos.map { it.toDomain() }
                    val entities = dtos.map { dto ->
                        ShippingOrderCacheEntity(
                            id = dto.id,
                            tenantId = tenantId,
                            orderNumber = dto.orderNumber,
                            carrier = dto.carrier,
                            vehiclePlate = dto.vehiclePlate,
                            status = dto.status,
                            totalPackages = dto.totalPackages,
                            loadedPackages = dto.loadedPackages,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    }
                    shippingOrderDao.insertOrUpdateAll(entities)
                    ApiResult.Success(orders)
                } else {
                    loadOrdersFromCache(tenantId)
                }
            } catch (e: IOException) {
                // FIX QA-8.2-003: return real Room data on network failure
                loadOrdersFromCache(tenantId)
            } catch (e: HttpException) {
                loadOrdersFromCache(tenantId)
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun getOrderDetail(orderId: String): ApiResult<ShippingOrder> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getShippingOrderDetail(orderId)
                if (response.isSuccessful) {
                    val dto = response.body()!!
                    val order = dto.toDomain()
                    val tenantId = currentTenantId
                    // Cache order
                    shippingOrderDao.insertOrUpdate(
                        ShippingOrderCacheEntity(
                            id = dto.id,
                            tenantId = tenantId,
                            orderNumber = dto.orderNumber,
                            carrier = dto.carrier,
                            vehiclePlate = dto.vehiclePlate,
                            status = dto.status,
                            totalPackages = dto.totalPackages,
                            loadedPackages = dto.loadedPackages,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    )
                    // Cache packages
                    val packageEntities = dto.packages.map { pkg ->
                        ShippingPackageCacheEntity(
                            id = pkg.id,
                            orderId = orderId,
                            tenantId = tenantId,
                            trackingCode = pkg.trackingCode,
                            weight = pkg.weight,
                            status = pkg.status
                        )
                    }
                    shippingPackageDao.insertOrUpdateAll(packageEntities)
                    ApiResult.Success(order)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                val cachedOrder = shippingOrderDao.getById(orderId)
                val cachedPackages = shippingPackageDao.getByOrderId(orderId)
                if (cachedOrder != null) {
                    ApiResult.NetworkError("offline:detail")
                } else {
                    ApiResult.NetworkError("Sem conexão e dados não disponíveis offline")
                }
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun loadPackage(
        orderId: String,
        packageId: String,
        trackingCode: String,
        vehiclePlate: String?
    ): ApiResult<ShippingPackage> = withContext(Dispatchers.IO) {
        val request = LoadPackageRequestDto(trackingCode = trackingCode, vehiclePlate = vehiclePlate)
        try {
            val response = apiService.loadPackage(orderId, packageId, request)
            if (response.isSuccessful) {
                val pkg = response.body()!!.toDomain()
                shippingPackageDao.updateStatus(packageId, "LOADED")
                // Update loaded packages count
                val allPackages = shippingPackageDao.getByOrderId(orderId)
                val loadedCount = allPackages.count { it.status == "LOADED" }
                shippingOrderDao.updateLoadedPackages(orderId, loadedCount)
                ApiResult.Success(pkg)
            } else {
                ApiResult.Error("Erro HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            // Offline: enqueue operation
            val tenantId = currentTenantId
            pickingSyncQueueDao.insert(
                PickingSyncQueueEntity(
                    tenantId = tenantId,
                    operationType = "LOAD_PACKAGE",
                    taskId = orderId,
                    itemId = packageId,
                    payload = gson.toJson(request),
                    createdAt = System.currentTimeMillis()
                )
            )
            shippingPackageDao.updateStatus(packageId, "LOADED")
            ApiResult.NetworkError("offline_queued")
        } catch (e: Exception) {
            ApiResult.Error("Erro inesperado: ${e.message}")
        }
    }

    override suspend fun completeShipping(orderId: String): ApiResult<ShippingOrder> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.completeShippingOrder(orderId)
                if (response.isSuccessful) {
                    val order = response.body()!!.toDomain()
                    shippingOrderDao.updateStatus(orderId, "COMPLETED")
                    ApiResult.Success(order)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                val tenantId = currentTenantId
                pickingSyncQueueDao.insert(
                    PickingSyncQueueEntity(
                        tenantId = tenantId,
                        operationType = "COMPLETE_SHIPPING",
                        taskId = orderId,
                        payload = "{}",
                        createdAt = System.currentTimeMillis()
                    )
                )
                shippingOrderDao.updateStatus(orderId, "COMPLETED")
                ApiResult.NetworkError("offline_queued")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    // --- Entity extension functions ---

    private fun ShippingOrderCacheEntity.toDomain(): ShippingOrder {
        val orderStatus = when (status) {
            "IN_PROGRESS" -> ShippingOrderStatus.IN_PROGRESS
            "COMPLETED"   -> ShippingOrderStatus.COMPLETED
            else          -> ShippingOrderStatus.SHIPPING_PENDING
        }
        return ShippingOrder(
            id = id,
            orderNumber = orderNumber,
            carrier = carrier,
            vehiclePlate = vehiclePlate,
            status = orderStatus,
            packages = emptyList(),
            totalPackages = totalPackages,
            loadedPackages = loadedPackages
        )
    }

    private fun ShippingPackageCacheEntity.toDomain(): ShippingPackage {
        val pkgStatus = when (status) {
            "LOADED" -> ShippingPackageStatus.LOADED
            else     -> ShippingPackageStatus.PENDING
        }
        return ShippingPackage(
            id = id,
            orderId = orderId,
            trackingCode = trackingCode,
            weight = weight,
            status = pkgStatus
        )
    }
}
