package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.ShippingPackageCacheEntity

@Dao
interface ShippingPackageDao {

    @Query("SELECT * FROM shipping_packages_cache WHERE order_id = :orderId ORDER BY tracking_code ASC")
    suspend fun getByOrderId(orderId: String): List<ShippingPackageCacheEntity>

    @Query("SELECT * FROM shipping_packages_cache WHERE id = :packageId LIMIT 1")
    suspend fun getById(packageId: String): ShippingPackageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(packages: List<ShippingPackageCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pkg: ShippingPackageCacheEntity)

    @Query("UPDATE shipping_packages_cache SET status = :status WHERE id = :packageId")
    suspend fun updateStatus(packageId: String, status: String)
}
