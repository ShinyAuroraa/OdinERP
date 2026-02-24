package com.odin.wms.android.data.repository

import com.google.gson.Gson
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.common.JwtUtils
import com.odin.wms.android.data.local.dao.InventorySyncQueueDao
import com.odin.wms.android.data.local.dao.TransferDao
import com.odin.wms.android.data.local.entity.InventorySyncQueueEntity
import com.odin.wms.android.data.local.entity.TransferCacheEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.CreateTransferRequestDto
import com.odin.wms.android.domain.model.Transfer
import com.odin.wms.android.domain.repository.ITransferRepository
import com.odin.wms.android.security.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val apiService: WmsApiService,
    private val transferDao: TransferDao,
    private val inventorySyncQueueDao: InventorySyncQueueDao,
    private val tokenProvider: TokenProvider,
    private val gson: Gson
) : ITransferRepository {

    private val currentTenantId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return JwtUtils.extractTenantId(claims)
        }

    override suspend fun getTransfers(tenantId: String): ApiResult<List<Transfer>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getTransfers(tenantId = tenantId)
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    val transfers = dtos.map { it.toDomain() }
                    val entities = dtos.map { dto ->
                        TransferCacheEntity(
                            id = dto.id,
                            tenantId = tenantId,
                            sourceLocation = dto.sourceLocation,
                            destinationLocation = dto.destinationLocation,
                            productCode = dto.productCode,
                            qty = dto.qty,
                            lotNumber = dto.lotNumber,
                            status = dto.status,
                            localStatus = "PENDING",
                            createdAt = dto.createdAt
                        )
                    }
                    transferDao.insertOrUpdateAll(entities)
                    ApiResult.Success(transfers)
                } else {
                    loadTransfersFromCache(tenantId)
                }
            } catch (e: IOException) {
                loadTransfersFromCache(tenantId)
            } catch (e: HttpException) {
                loadTransfersFromCache(tenantId)
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    private suspend fun loadTransfersFromCache(tenantId: String): ApiResult<List<Transfer>> {
        val cached = transferDao.getByTenantId(tenantId)
        val transfers = cached.map { it.toDomain() }
        return ApiResult.NetworkError("offline:${transfers.size}")
    }

    override suspend fun createTransfer(
        sourceLocation: String,
        destinationLocation: String,
        productCode: String,
        qty: Int,
        lotNumber: String?
    ): ApiResult<Transfer> = withContext(Dispatchers.IO) {
        val request = CreateTransferRequestDto(
            sourceLocation = sourceLocation,
            destinationLocation = destinationLocation,
            productCode = productCode,
            qty = qty,
            lotNumber = lotNumber
        )
        try {
            val response = apiService.createTransfer(request)
            if (response.isSuccessful) {
                val transfer = response.body()!!.toDomain()
                val entity = TransferCacheEntity(
                    id = transfer.id,
                    tenantId = currentTenantId,
                    sourceLocation = transfer.sourceLocation,
                    destinationLocation = transfer.destinationLocation,
                    productCode = transfer.productCode,
                    qty = transfer.qty,
                    lotNumber = transfer.lotNumber,
                    status = transfer.status.name,
                    localStatus = "PENDING",
                    createdAt = transfer.createdAt
                )
                transferDao.insertOrUpdate(entity)
                ApiResult.Success(transfer)
            } else {
                ApiResult.Error("Erro HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            val tenantId = currentTenantId
            val localId = UUID.randomUUID().toString()
            val entity = TransferCacheEntity(
                id = localId,
                tenantId = tenantId,
                sourceLocation = sourceLocation,
                destinationLocation = destinationLocation,
                productCode = productCode,
                qty = qty,
                lotNumber = lotNumber,
                status = "PENDING",
                localStatus = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            transferDao.insertOrUpdate(entity)
            inventorySyncQueueDao.insert(
                InventorySyncQueueEntity(
                    tenantId = tenantId,
                    operationType = "TRANSFER",
                    sessionId = localId, // Use localId as session_id field for TRANSFER ops
                    payload = gson.toJson(request),
                    createdAt = System.currentTimeMillis()
                )
            )
            ApiResult.NetworkError("offline_queued")
        } catch (e: Exception) {
            ApiResult.Error("Erro inesperado: ${e.message}")
        }
    }

    override suspend fun confirmTransfer(transferId: String): ApiResult<Transfer> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.confirmTransfer(transferId)
                if (response.isSuccessful) {
                    val transfer = response.body()!!.toDomain()
                    transferDao.updateStatus(transferId, "CONFIRMED")
                    ApiResult.Success(transfer)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                transferDao.updateLocalStatus(transferId, "PENDING")
                ApiResult.NetworkError("offline_queued")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun cancelTransfer(transferId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.cancelTransfer(transferId)
                if (response.isSuccessful) {
                    transferDao.updateStatus(transferId, "CANCELLED")
                    transferDao.updateLocalStatus(transferId, "CANCELLED")
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
}
