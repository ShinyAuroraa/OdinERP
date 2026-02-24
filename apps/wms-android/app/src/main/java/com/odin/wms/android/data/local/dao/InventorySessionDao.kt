package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.InventorySessionCacheEntity

@Dao
interface InventorySessionDao {

    @Query("SELECT * FROM inventory_sessions_cache WHERE tenant_id = :tenantId ORDER BY session_number ASC")
    suspend fun getByTenantId(tenantId: String): List<InventorySessionCacheEntity>

    @Query("SELECT * FROM inventory_sessions_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): InventorySessionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: InventorySessionCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<InventorySessionCacheEntity>)

    @Query("UPDATE inventory_sessions_cache SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE inventory_sessions_cache SET counted_items = :countedItems WHERE id = :id")
    suspend fun updateCountedItems(id: String, countedItems: Int)
}
