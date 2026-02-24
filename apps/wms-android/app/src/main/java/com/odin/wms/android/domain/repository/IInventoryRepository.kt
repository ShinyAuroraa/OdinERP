package com.odin.wms.android.domain.repository

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventorySession

interface IInventoryRepository {
    suspend fun getSessions(tenantId: String): ApiResult<List<InventorySession>>
    suspend fun getSessionDetail(sessionId: String): ApiResult<InventorySession>
    suspend fun getCountingList(sessionId: String, aisle: String? = null): ApiResult<List<InventoryItem>>
    suspend fun getCountingListFromCache(sessionId: String): List<InventoryItem>
    suspend fun countItem(
        sessionId: String,
        itemId: String,
        productCode: String,
        countedQty: Int,
        lotNumber: String?,
        position: String
    ): ApiResult<InventoryItem>
    suspend fun doubleCount(
        sessionId: String,
        itemId: String,
        countedQty: Int,
        counterId: String
    ): ApiResult<InventoryItem>
    suspend fun submitSession(sessionId: String): ApiResult<InventorySession>
}
