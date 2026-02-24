package com.odin.wms.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.odin.wms.android.data.local.dao.PendingTaskDao
import com.odin.wms.android.data.local.dao.ReceivingItemDao
import com.odin.wms.android.data.local.dao.ReceivingOrderDao
import com.odin.wms.android.data.local.dao.ReceivingSyncQueueDao
import com.odin.wms.android.data.local.dao.StockSummaryDao
import com.odin.wms.android.data.local.entity.PendingTaskCacheEntity
import com.odin.wms.android.data.local.entity.ReceivingItemCacheEntity
import com.odin.wms.android.data.local.entity.ReceivingOrderCacheEntity
import com.odin.wms.android.data.local.entity.ReceivingSyncQueueEntity
import com.odin.wms.android.data.local.entity.StockSummaryCacheEntity

@Database(
    entities = [
        StockSummaryCacheEntity::class,
        PendingTaskCacheEntity::class,
        ReceivingOrderCacheEntity::class,
        ReceivingItemCacheEntity::class,
        ReceivingSyncQueueEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WmsDatabase : RoomDatabase() {
    abstract fun stockSummaryDao(): StockSummaryDao
    abstract fun pendingTaskDao(): PendingTaskDao
    abstract fun receivingOrderDao(): ReceivingOrderDao
    abstract fun receivingItemDao(): ReceivingItemDao
    abstract fun receivingSyncQueueDao(): ReceivingSyncQueueDao

    companion object {
        const val DATABASE_NAME = "wms_cache.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Table: receiving_orders_cache
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS receiving_orders_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        tenant_id TEXT NOT NULL,
                        order_number TEXT NOT NULL,
                        supplier TEXT NOT NULL,
                        expected_date TEXT NOT NULL,
                        status TEXT NOT NULL,
                        total_items INTEGER NOT NULL,
                        confirmed_items INTEGER NOT NULL,
                        last_sync_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_receiving_orders_tenant_number
                    ON receiving_orders_cache(tenant_id, order_number)
                    """.trimIndent()
                )

                // Table: receiving_items_cache
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS receiving_items_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        order_id TEXT NOT NULL,
                        tenant_id TEXT NOT NULL,
                        product_code TEXT NOT NULL,
                        gtin TEXT NOT NULL,
                        description TEXT NOT NULL,
                        expected_qty INTEGER NOT NULL,
                        confirmed_qty INTEGER NOT NULL DEFAULT 0,
                        local_status TEXT NOT NULL DEFAULT 'PENDING'
                    )
                    """.trimIndent()
                )

                // Table: receiving_sync_queue
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS receiving_sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tenant_id TEXT NOT NULL,
                        operation_type TEXT NOT NULL,
                        order_id TEXT NOT NULL,
                        item_id TEXT,
                        payload TEXT NOT NULL,
                        signature_base64 TEXT,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        retry_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
