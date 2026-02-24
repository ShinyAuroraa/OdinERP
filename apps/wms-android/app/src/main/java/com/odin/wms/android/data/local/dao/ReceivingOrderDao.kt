package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.odin.wms.android.data.local.entity.ReceivingOrderCacheEntity

@Dao
interface ReceivingOrderDao {

    @Query("SELECT * FROM receiving_orders_cache WHERE tenant_id = :tenantId ORDER BY expected_date ASC")
    suspend fun getByTenantId(tenantId: String): List<ReceivingOrderCacheEntity>

    @Query("SELECT * FROM receiving_orders_cache WHERE id = :orderId LIMIT 1")
    suspend fun getById(orderId: String): ReceivingOrderCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ReceivingOrderCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<ReceivingOrderCacheEntity>)

    @Update
    suspend fun update(entity: ReceivingOrderCacheEntity)

    @Query("UPDATE receiving_orders_cache SET status = :status WHERE id = :orderId")
    suspend fun updateStatus(orderId: String, status: String)
}
