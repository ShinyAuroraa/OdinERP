package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.PickingItemCacheEntity

@Dao
interface PickingItemDao {

    @Query("SELECT * FROM picking_items_cache WHERE task_id = :taskId ORDER BY position ASC")
    suspend fun getByTaskId(taskId: String): List<PickingItemCacheEntity>

    @Query("SELECT * FROM picking_items_cache WHERE id = :itemId LIMIT 1")
    suspend fun getById(itemId: String): PickingItemCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(items: List<PickingItemCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: PickingItemCacheEntity)

    @Query("UPDATE picking_items_cache SET local_status = :localStatus WHERE id = :itemId")
    suspend fun updateLocalStatus(itemId: String, localStatus: String)

    @Query("UPDATE picking_items_cache SET local_status = :localStatus, picked_qty = :pickedQty WHERE id = :itemId")
    suspend fun updateLocalStatusAndQty(itemId: String, localStatus: String, pickedQty: Int)

    /**
     * FEFO query: returns pending items for the same product in the given task,
     * sorted by expiry_date ASC NULLS LAST for FEFO/FIFO validation.
     */
    @Query("""
        SELECT * FROM picking_items_cache
        WHERE product_code = :productCode
          AND task_id = :taskId
          AND local_status IN ('PENDING')
        ORDER BY
          CASE WHEN expiry_date IS NULL THEN 1 ELSE 0 END ASC,
          expiry_date ASC
    """)
    suspend fun getPendingByProductSortedByExpiry(
        productCode: String,
        taskId: String
    ): List<PickingItemCacheEntity>
}
