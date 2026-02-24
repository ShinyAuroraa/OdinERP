package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.odin.wms.android.data.local.entity.ReceivingSyncQueueEntity

@Dao
interface ReceivingSyncQueueDao {

    @Query("SELECT * FROM receiving_sync_queue WHERE tenant_id = :tenantId AND status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingByTenant(tenantId: String): List<ReceivingSyncQueueEntity>

    @Query("SELECT COUNT(*) FROM receiving_sync_queue WHERE tenant_id = :tenantId AND status = 'PENDING'")
    suspend fun getPendingCount(tenantId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ReceivingSyncQueueEntity): Long

    @Query("UPDATE receiving_sync_queue SET status = :status, retry_count = :retryCount WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, retryCount: Int)

    @Query("DELETE FROM receiving_sync_queue WHERE status = 'SYNCED'")
    suspend fun deleteSynced()

    @Transaction
    suspend fun insertAndReturnId(entity: ReceivingSyncQueueEntity): Long = insert(entity)
}
