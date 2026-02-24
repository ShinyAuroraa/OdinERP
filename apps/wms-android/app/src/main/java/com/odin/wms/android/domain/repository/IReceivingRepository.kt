package com.odin.wms.android.domain.repository

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.DivergenceReport
import com.odin.wms.android.domain.model.ReceivingItem
import com.odin.wms.android.domain.model.ReceivingOrder

interface IReceivingRepository {

    suspend fun getOrders(tenantId: String): ApiResult<List<ReceivingOrder>>

    suspend fun getOrderDetail(orderId: String): ApiResult<ReceivingOrder>

    suspend fun confirmItem(
        orderId: String,
        itemId: String,
        quantity: Int,
        lotNumber: String?,
        expiryDate: String?,
        serialNumber: String?
    ): ApiResult<ReceivingItem>

    suspend fun reportDivergence(
        orderId: String,
        itemId: String,
        report: DivergenceReport
    ): ApiResult<ReceivingItem>

    suspend fun submitSignature(
        orderId: String,
        signatureBase64: String
    ): ApiResult<Unit>

    suspend fun completeOrder(orderId: String): ApiResult<ReceivingOrder>

    suspend fun cancelOrder(orderId: String): ApiResult<Unit>

    suspend fun getPendingSyncCount(tenantId: String): Int
}
