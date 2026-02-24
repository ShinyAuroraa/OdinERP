package com.odin.wms.android.domain.repository

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.ShippingOrder
import com.odin.wms.android.domain.model.ShippingPackage

interface IShippingRepository {
    suspend fun getOrders(tenantId: String): ApiResult<List<ShippingOrder>>
    suspend fun getOrderDetail(orderId: String): ApiResult<ShippingOrder>
    suspend fun loadPackage(
        orderId: String,
        packageId: String,
        trackingCode: String,
        vehiclePlate: String?
    ): ApiResult<ShippingPackage>
    suspend fun completeShipping(orderId: String): ApiResult<ShippingOrder>
}
