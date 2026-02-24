package com.odin.wms.android.data.remote

import com.odin.wms.android.data.remote.dto.CompleteReceivingDto
import com.odin.wms.android.data.remote.dto.ConfirmItemRequestDto
import com.odin.wms.android.data.remote.dto.ConfirmPickItemRequestDto
import com.odin.wms.android.data.remote.dto.CountItemRequestDto
import com.odin.wms.android.data.remote.dto.CreateTransferRequestDto
import com.odin.wms.android.data.remote.dto.DivergenceRequestDto
import com.odin.wms.android.data.remote.dto.DoubleCountRequestDto
import com.odin.wms.android.data.remote.dto.InventoryItemDto
import com.odin.wms.android.data.remote.dto.InventorySessionDto
import com.odin.wms.android.data.remote.dto.LoadPackageRequestDto
import com.odin.wms.android.data.remote.dto.PendingTasksDto
import com.odin.wms.android.data.remote.dto.PickingItemDto
import com.odin.wms.android.data.remote.dto.PickingTaskDto
import com.odin.wms.android.data.remote.dto.ReceivingItemDto
import com.odin.wms.android.data.remote.dto.ReceivingOrderDto
import com.odin.wms.android.data.remote.dto.ShippingOrderDto
import com.odin.wms.android.data.remote.dto.ShippingPackageDto
import com.odin.wms.android.data.remote.dto.StockSummaryDto
import com.odin.wms.android.data.remote.dto.TransferDto
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

    // --- Picking endpoints (Story 5.1) ---

    @GET("api/v1/picking/orders")
    suspend fun getPickingOrders(
        @Query("status") status: String = "PICKING_PENDING",
        @Query("tenantId") tenantId: String
    ): Response<List<PickingTaskDto>>

    @GET("api/v1/picking/orders/{id}")
    suspend fun getPickingOrderDetail(
        @Path("id") taskId: String
    ): Response<PickingTaskDto>

    @PUT("api/v1/picking/orders/{id}/items/{itemId}/pick")
    suspend fun confirmItemPicked(
        @Path("id") taskId: String,
        @Path("itemId") itemId: String,
        @Body request: ConfirmPickItemRequestDto
    ): Response<PickingItemDto>

    @PUT("api/v1/picking/orders/{id}/complete")
    suspend fun completePickingOrder(
        @Path("id") taskId: String
    ): Response<PickingTaskDto>

    @PUT("api/v1/picking/orders/{id}/cancel")
    suspend fun cancelPickingOrder(
        @Path("id") taskId: String
    ): Response<Unit>

    // --- Shipping endpoints (Story 5.3) ---

    @GET("api/v1/shipping/orders")
    suspend fun getShippingOrders(
        @Query("status") status: String = "SHIPPING_PENDING",
        @Query("tenantId") tenantId: String
    ): Response<List<ShippingOrderDto>>

    @GET("api/v1/shipping/orders/{id}")
    suspend fun getShippingOrderDetail(
        @Path("id") orderId: String
    ): Response<ShippingOrderDto>

    @PUT("api/v1/shipping/orders/{id}/packages/{packageId}/load")
    suspend fun loadPackage(
        @Path("id") orderId: String,
        @Path("packageId") packageId: String,
        @Body request: LoadPackageRequestDto
    ): Response<ShippingPackageDto>

    @POST("api/v1/shipping/orders/{id}/complete")
    suspend fun completeShippingOrder(
        @Path("id") orderId: String
    ): Response<ShippingOrderDto>

    // --- Inventory endpoints (Story 4.3) ---

    @GET("api/v1/inventory/sessions")
    suspend fun getInventorySessions(
        @Query("status") status: String = "ACTIVE",
        @Query("tenantId") tenantId: String
    ): Response<List<InventorySessionDto>>

    @GET("api/v1/inventory/sessions/{id}")
    suspend fun getInventorySessionDetail(
        @Path("id") sessionId: String
    ): Response<InventorySessionDto>

    @GET("api/v1/inventory/sessions/{id}/counting-list")
    suspend fun getCountingList(
        @Path("id") sessionId: String,
        @Query("aisle") aisle: String? = null
    ): Response<List<InventoryItemDto>>

    @PUT("api/v1/inventory/sessions/{id}/items/{itemId}/count")
    suspend fun countItem(
        @Path("id") sessionId: String,
        @Path("itemId") itemId: String,
        @Body request: CountItemRequestDto
    ): Response<InventoryItemDto>

    @POST("api/v1/inventory/sessions/{id}/double-count/{itemId}")
    suspend fun doubleCountItem(
        @Path("id") sessionId: String,
        @Path("itemId") itemId: String,
        @Body request: DoubleCountRequestDto
    ): Response<InventoryItemDto>

    @POST("api/v1/inventory/sessions/{id}/submit")
    suspend fun submitInventorySession(
        @Path("id") sessionId: String
    ): Response<InventorySessionDto>

    // --- Transfer endpoints (Story 4.5) ---

    @GET("api/v1/transfers")
    suspend fun getTransfers(
        @Query("status") status: String = "PENDING",
        @Query("tenantId") tenantId: String
    ): Response<List<TransferDto>>

    @POST("api/v1/transfers")
    suspend fun createTransfer(
        @Body request: CreateTransferRequestDto
    ): Response<TransferDto>

    @PUT("api/v1/transfers/{id}/confirm")
    suspend fun confirmTransfer(
        @Path("id") transferId: String
    ): Response<TransferDto>

    @PUT("api/v1/transfers/{id}/cancel")
    suspend fun cancelTransfer(
        @Path("id") transferId: String
    ): Response<Unit>
}
