package com.odin.wms.android.data.repository

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.data.local.dao.StockSummaryDao
import com.odin.wms.android.data.local.entity.StockSummaryCacheEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.domain.model.OperationType
import com.odin.wms.android.domain.model.StockSummary
import com.odin.wms.android.domain.repository.IStockRepository
import com.odin.wms.android.security.TokenProvider
import com.odin.wms.android.common.JwtUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepository @Inject constructor(
    private val apiService: WmsApiService,
    private val stockSummaryDao: StockSummaryDao,
    private val tokenProvider: TokenProvider
) : IStockRepository {

    private val currentTenantId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return JwtUtils.extractTenantId(claims)
        }

    override suspend fun getStockSummary(): ApiResult<StockSummary> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getStockSummary()
                if (response.isSuccessful) {
                    val dto = response.body()!!
                    val summary = StockSummary(
                        tenantId = dto.tenantId,
                        totalAvailable = dto.totalAvailable,
                        pendingPickingCount = dto.pendingPicking,
                        pendingReceivingCount = dto.pendingReceiving,
                        lastUpdated = dto.updatedAt,
                        isOffline = false
                    )
                    // Cache result in Room
                    stockSummaryDao.upsert(
                        StockSummaryCacheEntity(
                            tenantId = dto.tenantId,
                            totalAvailable = dto.totalAvailable,
                            pendingPickingCount = dto.pendingPicking,
                            pendingReceivingCount = dto.pendingReceiving,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    )
                    ApiResult.Success(summary)
                } else {
                    ApiResult.Error("Erro HTTP ${response.code()}")
                }
            } catch (e: IOException) {
                ApiResult.NetworkError("Sem conexão — verifique o WiFi")
            } catch (e: HttpException) {
                ApiResult.Error("Erro do servidor: ${e.code()}")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun getCachedStockSummary(): StockSummary? =
        withContext(Dispatchers.IO) {
            val tenantId = currentTenantId.ifEmpty { return@withContext null }
            stockSummaryDao.getByTenant(tenantId)?.let { entity ->
                StockSummary(
                    tenantId = entity.tenantId,
                    totalAvailable = entity.totalAvailable,
                    pendingPickingCount = entity.pendingPickingCount,
                    pendingReceivingCount = entity.pendingReceivingCount,
                    lastUpdated = entity.lastSyncAt,
                    isOffline = true
                )
            }
        }

    override suspend fun getPendingTaskCount(type: OperationType): Int =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getPendingTaskCounts()
                if (response.isSuccessful) {
                    val dto = response.body()!!
                    when (type) {
                        OperationType.PICKING   -> dto.pickingCount
                        OperationType.RECEIVING -> dto.receivingCount
                        OperationType.INVENTORY -> dto.inventoryCount
                        OperationType.TRANSFER  -> dto.transferCount
                    }
                } else 0
            } catch (e: Exception) {
                0
            }
        }
}
