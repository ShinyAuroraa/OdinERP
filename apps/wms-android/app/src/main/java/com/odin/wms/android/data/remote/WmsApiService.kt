package com.odin.wms.android.data.remote

import com.odin.wms.android.data.remote.dto.CompleteReceivingDto
import com.odin.wms.android.data.remote.dto.ConfirmItemRequestDto
import com.odin.wms.android.data.remote.dto.DivergenceRequestDto
import com.odin.wms.android.data.remote.dto.PendingTasksDto
import com.odin.wms.android.data.remote.dto.ReceivingItemDto
import com.odin.wms.android.data.remote.dto.ReceivingOrderDto
import com.odin.wms.android.data.remote.dto.StockSummaryDto
import com.odin.wms.android.data.remote.dto.WarehouseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
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

    // --- Receiving endpoints ---

    @GET("api/v1/receiving/orders")
    suspend fun getReceivingOrders(
        @Query("status") status: String = "PENDING",
        @Query("tenantId") tenantId: String
    ): Response<List<ReceivingOrderDto>>

    @GET("api/v1/receiving/orders/{id}")
    suspend fun getReceivingOrderDetail(
        @Path("id") orderId: String
    ): Response<ReceivingOrderDto>

    @PUT("api/v1/receiving/orders/{id}/items/{itemId}/confirm")
    suspend fun confirmReceivingItem(
        @Path("id") orderId: String,
        @Path("itemId") itemId: String,
        @Body request: ConfirmItemRequestDto
    ): Response<ReceivingItemDto>

    @PUT("api/v1/receiving/orders/{id}/items/{itemId}/divergence")
    suspend fun reportDivergence(
        @Path("id") orderId: String,
        @Path("itemId") itemId: String,
        @Body request: DivergenceRequestDto
    ): Response<ReceivingItemDto>

    @POST("api/v1/receiving/orders/{id}/signature")
    suspend fun submitSignature(
        @Path("id") orderId: String,
        @Body request: CompleteReceivingDto
    ): Response<Unit>

    @POST("api/v1/receiving/orders/{id}/complete")
    suspend fun completeReceivingOrder(
        @Path("id") orderId: String
    ): Response<ReceivingOrderDto>

    @PUT("api/v1/receiving/orders/{id}/cancel")
    suspend fun cancelReceivingOrder(
        @Path("id") orderId: String
    ): Response<Unit>
}
