package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.InventorySyncQueueEntity

@Dao
interface InventorySyncQueueDao {

    @Query("SELECT * FROM inventory_sync_queue WHERE tenant_id = :tenantId AND status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingByTenant(tenantId: String): List<InventorySyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InventorySyncQueueEntity)

    @Query("UPDATE inventory_sync_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE inventory_sync_queue SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)

    @Query("DELETE FROM inventory_sync_queue WHERE status = 'SYNCED'")
    suspend fun deleteSynced()
}
