package com.odin.wms.android.domain.repository

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.Transfer

interface ITransferRepository {
    suspend fun getTransfers(tenantId: String): ApiResult<List<Transfer>>
    suspend fun createTransfer(
        sourceLocation: String,
        destinationLocation: String,
        productCode: String,
        qty: Int,
        lotNumber: String?
    ): ApiResult<Transfer>
    suspend fun confirmTransfer(transferId: String): ApiResult<Transfer>
    suspend fun cancelTransfer(transferId: String): ApiResult<Unit>
}
