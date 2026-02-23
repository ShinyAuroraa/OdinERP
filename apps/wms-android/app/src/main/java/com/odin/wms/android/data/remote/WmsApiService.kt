package com.odin.wms.android.data.remote

import com.odin.wms.android.data.remote.dto.PendingTasksDto
import com.odin.wms.android.data.remote.dto.StockSummaryDto
import com.odin.wms.android.data.remote.dto.WarehouseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WmsApiService {

    @GET("api/v1/stock/summary")
    suspend fun getStockSummary(
        @Query("warehouseId") warehouseId: String? = null
    ): Response<StockSummaryDto>

    @GET("api/v1/warehouses")
    suspend fun getWarehouses(): Response<List<WarehouseDto>>

    @GET("api/v1/tasks/pending/count")
    suspend fun getPendingTaskCounts(): Response<PendingTasksDto>
}
