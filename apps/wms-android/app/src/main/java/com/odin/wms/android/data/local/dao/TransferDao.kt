package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.TransferCacheEntity

@Dao
interface TransferDao {

    @Query("SELECT * FROM transfers_cache WHERE tenant_id = :tenantId ORDER BY created_at DESC")
    suspend fun getByTenantId(tenantId: String): List<TransferCacheEntity>

    @Query("SELECT * FROM transfers_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransferCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: TransferCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<TransferCacheEntity>)

    @Query("UPDATE transfers_cache SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE transfers_cache SET local_status = :localStatus WHERE id = :id")
    suspend fun updateLocalStatus(id: String, localStatus: String)
}
