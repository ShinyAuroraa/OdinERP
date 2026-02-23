package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.odin.wms.android.data.local.entity.PendingTaskCacheEntity

@Dao
interface PendingTaskDao {

    @Query("SELECT * FROM pending_task_cache WHERE tenantId = :tenantId AND status = 'PENDING'")
    suspend fun getPendingByTenant(tenantId: String): List<PendingTaskCacheEntity>

    @Query("SELECT * FROM pending_task_cache WHERE tenantId = :tenantId AND status = 'SYNC_FAILED'")
    suspend fun getFailedByTenant(tenantId: String): List<PendingTaskCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingTaskCacheEntity)

    @Update
    suspend fun update(entity: PendingTaskCacheEntity)

    @Query("DELETE FROM pending_task_cache WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM pending_task_cache WHERE tenantId = :tenantId AND status = 'PENDING'")
    suspend fun countPendingByTenant(tenantId: String): Int
}
