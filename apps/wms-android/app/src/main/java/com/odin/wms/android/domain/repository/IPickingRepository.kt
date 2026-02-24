package com.odin.wms.android.domain.repository

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.PickingItem
import com.odin.wms.android.domain.model.PickingTask

interface IPickingRepository {
    suspend fun getTasks(tenantId: String): ApiResult<List<PickingTask>>
    suspend fun getTaskDetail(taskId: String): ApiResult<PickingTask>
    suspend fun confirmItemPicked(
        taskId: String,
        itemId: String,
        quantity: Int,
        lotNumber: String?,
        position: String,
        serialNumber: String?
    ): ApiResult<PickingItem>
    suspend fun completeTask(taskId: String): ApiResult<PickingTask>
    suspend fun cancelTask(taskId: String): ApiResult<Unit>
}
