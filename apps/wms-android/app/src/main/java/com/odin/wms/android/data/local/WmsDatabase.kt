package com.odin.wms.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.odin.wms.android.data.local.dao.PendingTaskDao
import com.odin.wms.android.data.local.dao.StockSummaryDao
import com.odin.wms.android.data.local.entity.PendingTaskCacheEntity
import com.odin.wms.android.data.local.entity.StockSummaryCacheEntity

@Database(
    entities = [
        StockSummaryCacheEntity::class,
        PendingTaskCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WmsDatabase : RoomDatabase() {
    abstract fun stockSummaryDao(): StockSummaryDao
    abstract fun pendingTaskDao(): PendingTaskDao

    companion object {
        const val DATABASE_NAME = "wms_cache.db"
    }
}
