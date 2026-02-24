package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.ReceivingItemCacheEntity

@Dao
interface ReceivingItemDao {

    @Query("SELECT * FROM receiving_items_cache WHERE order_id = :orderId")
    suspend fun getByOrderId(orderId: String): List<ReceivingItemCacheEntity>

    @Query("SELECT * FROM receiving_items_cache WHERE id = :itemId LIMIT 1")
    suspend fun getById(itemId: String): ReceivingItemCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ReceivingItemCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<ReceivingItemCacheEntity>)

    @Query("UPDATE receiving_items_cache SET local_status = :status, confirmed_qty = :confirmedQty WHERE id = :itemId")
    suspend fun updateLocalStatus(itemId: String, status: String, confirmedQty: Int)
}
