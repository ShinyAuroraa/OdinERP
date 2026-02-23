package com.odin.wms.android.domain.repository

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.OperationType
import com.odin.wms.android.domain.model.StockSummary

interface IStockRepository {
    suspend fun getStockSummary(): ApiResult<StockSummary>
    suspend fun getCachedStockSummary(): StockSummary?
    suspend fun getPendingTaskCount(type: OperationType): Int
}
