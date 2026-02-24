package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.ShippingOrderCacheEntity

@Dao
interface ShippingOrderDao {

    @Query("SELECT * FROM shipping_orders_cache WHERE tenant_id = :tenantId ORDER BY order_number ASC")
    suspend fun getByTenantId(tenantId: String): List<ShippingOrderCacheEntity>

    @Query("SELECT * FROM shipping_orders_cache WHERE id = :orderId LIMIT 1")
    suspend fun getById(orderId: String): ShippingOrderCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(orders: List<ShippingOrderCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(order: ShippingOrderCacheEntity)

    @Query("UPDATE shipping_orders_cache SET status = :status WHERE id = :orderId")
    suspend fun updateStatus(orderId: String, status: String)

    @Query("UPDATE shipping_orders_cache SET loaded_packages = :loadedPackages WHERE id = :orderId")
    suspend fun updateLoadedPackages(orderId: String, loadedPackages: Int)
}
