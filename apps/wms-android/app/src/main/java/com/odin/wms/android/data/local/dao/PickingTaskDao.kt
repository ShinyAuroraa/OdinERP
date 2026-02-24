package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.PickingTaskCacheEntity

@Dao
interface PickingTaskDao {

    @Query("SELECT * FROM picking_tasks_cache WHERE tenant_id = :tenantId ORDER BY priority DESC, task_number ASC")
    suspend fun getByTenantId(tenantId: String): List<PickingTaskCacheEntity>

    @Query("SELECT * FROM picking_tasks_cache WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: String): PickingTaskCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(tasks: List<PickingTaskCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(task: PickingTaskCacheEntity)

    @Query("UPDATE picking_tasks_cache SET status = :status WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: String)

    @Query("UPDATE picking_tasks_cache SET picked_items = :pickedItems WHERE id = :taskId")
    suspend fun updatePickedItems(taskId: String, pickedItems: Int)
}
