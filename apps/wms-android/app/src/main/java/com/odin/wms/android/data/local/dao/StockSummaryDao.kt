package com.odin.wms.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.wms.android.data.local.entity.StockSummaryCacheEntity

@Dao
interface StockSummaryDao {

    @Query("SELECT * FROM stock_summary_cache WHERE tenantId = :tenantId LIMIT 1")
    suspend fun getByTenant(tenantId: String): StockSummaryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StockSummaryCacheEntity)

    @Query("DELETE FROM stock_summary_cache WHERE tenantId = :tenantId")
    suspend fun deleteByTenant(tenantId: String)
}
