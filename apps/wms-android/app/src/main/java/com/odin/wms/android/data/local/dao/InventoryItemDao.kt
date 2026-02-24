package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.InventoryItemCacheEntity

@Dao
interface InventoryItemDao {

    @Query("SELECT * FROM inventory_items_cache WHERE session_id = :sessionId ORDER BY position ASC")
    suspend fun getBySessionId(sessionId: String): List<InventoryItemCacheEntity>

    @Query("SELECT * FROM inventory_items_cache WHERE session_id = :sessionId AND position LIKE :aisle || '%' ORDER BY position ASC")
    suspend fun getBySessionAndAisle(sessionId: String, aisle: String): List<InventoryItemCacheEntity>

    @Query("SELECT * FROM inventory_items_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): InventoryItemCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: InventoryItemCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<InventoryItemCacheEntity>)

    @Query("UPDATE inventory_items_cache SET local_status = :status WHERE id = :id")
    suspend fun updateLocalStatus(id: String, status: String)

    @Query("UPDATE inventory_items_cache SET counted_qty = :countedQty WHERE id = :id")
    suspend fun updateCountedQty(id: String, countedQty: Int)

    @Query("UPDATE inventory_items_cache SET double_count_qty = :doubleCountQty WHERE id = :id")
    suspend fun updateDoubleCountQty(id: String, doubleCountQty: Int)

    @Query("UPDATE inventory_items_cache SET first_counter_id = :firstCounterId WHERE id = :id")
    suspend fun updateFirstCounterId(id: String, firstCounterId: String)
}
