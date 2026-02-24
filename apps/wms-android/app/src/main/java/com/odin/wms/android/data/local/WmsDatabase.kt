package com.odin.wms.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.odin.wms.android.data.local.dao.InventoryItemDao
import com.odin.wms.android.data.local.dao.InventorySessionDao
import com.odin.wms.android.data.local.dao.InventorySyncQueueDao
import com.odin.wms.android.data.local.dao.PendingTaskDao
import com.odin.wms.android.data.local.dao.PickingItemDao
import com.odin.wms.android.data.local.dao.PickingSyncQueueDao
import com.odin.wms.android.data.local.dao.PickingTaskDao
import com.odin.wms.android.data.local.dao.ReceivingItemDao
import com.odin.wms.android.data.local.dao.ReceivingOrderDao
import com.odin.wms.android.data.local.dao.ReceivingSyncQueueDao
import com.odin.wms.android.data.local.dao.ShippingOrderDao
import com.odin.wms.android.data.local.dao.ShippingPackageDao
import com.odin.wms.android.data.local.dao.StockSummaryDao
import com.odin.wms.android.data.local.dao.TransferDao
import com.odin.wms.android.data.local.entity.InventoryItemCacheEntity
import com.odin.wms.android.data.local.entity.InventorySessionCacheEntity
import com.odin.wms.android.data.local.entity.InventorySyncQueueEntity
import com.odin.wms.android.data.local.entity.PendingTaskCacheEntity
import com.odin.wms.android.data.local.entity.PickingItemCacheEntity
import com.odin.wms.android.data.local.entity.PickingSyncQueueEntity
import com.odin.wms.android.data.local.entity.PickingTaskCacheEntity
import com.odin.wms.android.data.local.entity.ReceivingItemCacheEntity
import com.odin.wms.android.data.local.entity.ReceivingOrderCacheEntity
import com.odin.wms.android.data.local.entity.ReceivingSyncQueueEntity
import com.odin.wms.android.data.local.entity.ShippingOrderCacheEntity
import com.odin.wms.android.data.local.entity.ShippingPackageCacheEntity
import com.odin.wms.android.data.local.entity.StockSummaryCacheEntity
import com.odin.wms.android.data.local.entity.TransferCacheEntity

@Database(
    entities = [
        StockSummaryCacheEntity::class,
        PendingTaskCacheEntity::class,
        ReceivingOrderCacheEntity::class,
        ReceivingItemCacheEntity::class,
        ReceivingSyncQueueEntity::class,
        PickingTaskCacheEntity::class,
        PickingItemCacheEntity::class,
        PickingSyncQueueEntity::class,
        ShippingOrderCacheEntity::class,
        ShippingPackageCacheEntity::class,
        InventorySessionCacheEntity::class,
        InventoryItemCacheEntity::class,
        InventorySyncQueueEntity::class,
        TransferCacheEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class WmsDatabase : RoomDatabase() {
    abstract fun stockSummaryDao(): StockSummaryDao
    abstract fun pendingTaskDao(): PendingTaskDao
    abstract fun receivingOrderDao(): ReceivingOrderDao
    abstract fun receivingItemDao(): ReceivingItemDao
    abstract fun receivingSyncQueueDao(): ReceivingSyncQueueDao
    abstract fun pickingTaskDao(): PickingTaskDao
    abstract fun pickingItemDao(): PickingItemDao
    abstract fun pickingSyncQueueDao(): PickingSyncQueueDao
    abstract fun shippingOrderDao(): ShippingOrderDao
    abstract fun shippingPackageDao(): ShippingPackageDao
    abstract fun inventorySessionDao(): InventorySessionDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun inventorySyncQueueDao(): InventorySyncQueueDao
    abstract fun transferDao(): TransferDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Table: picking_tasks_cache
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS picking_tasks_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        tenant_id TEXT NOT NULL,
                        task_number TEXT NOT NULL,
                        picking_order_id TEXT NOT NULL,
                        status TEXT NOT NULL,
                        corridor TEXT,
                        priority INTEGER NOT NULL DEFAULT 0,
                        total_items INTEGER NOT NULL,
                        picked_items INTEGER NOT NULL DEFAULT 0,
                        last_sync_at INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_picking_tasks_tenant_number
                    ON picking_tasks_cache(tenant_id, task_number)
                """.trimIndent())

                // Table: picking_items_cache
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS picking_items_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        tenant_id TEXT NOT NULL,
                        product_code TEXT NOT NULL,
                        gtin TEXT NOT NULL,
                        description TEXT NOT NULL,
                        expected_qty INTEGER NOT NULL,
                        picked_qty INTEGER NOT NULL DEFAULT 0,
                        position TEXT NOT NULL,
                        lot_number TEXT,
                        expiry_date TEXT,
                        local_status TEXT NOT NULL DEFAULT 'PENDING'
                    )
                """.trimIndent())
                // Index for FEFO query performance
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_picking_items_expiry
                    ON picking_items_cache(product_code, expiry_date)
                """.trimIndent())

                // Table: picking_sync_queue
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS picking_sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tenant_id TEXT NOT NULL,
                        operation_type TEXT NOT NULL,
                        task_id TEXT NOT NULL,
                        item_id TEXT,
                        payload TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        retry_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // Table: shipping_orders_cache
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS shipping_orders_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        tenant_id TEXT NOT NULL,
                        order_number TEXT NOT NULL,
                        carrier TEXT NOT NULL,
                        vehicle_plate TEXT,
                        status TEXT NOT NULL,
                        total_packages INTEGER NOT NULL,
                        loaded_packages INTEGER NOT NULL DEFAULT 0,
                        last_sync_at INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_shipping_orders_tenant_number
                    ON shipping_orders_cache(tenant_id, order_number)
                """.trimIndent())

                // Table: shipping_packages_cache
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS shipping_packages_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        order_id TEXT NOT NULL,
                        tenant_id TEXT NOT NULL,
                        tracking_code TEXT NOT NULL,
                        weight REAL,
                        status TEXT NOT NULL DEFAULT 'PENDING'
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS inventory_sessions_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        tenant_id TEXT NOT NULL,
                        session_number TEXT NOT NULL,
                        session_type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        aisle TEXT,
                        total_items INTEGER NOT NULL DEFAULT 0,
                        counted_items INTEGER NOT NULL DEFAULT 0,
                        last_sync_at INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_inv_sessions_tenant_number
                    ON inventory_sessions_cache(tenant_id, session_number)
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS inventory_items_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        tenant_id TEXT NOT NULL,
                        product_code TEXT NOT NULL,
                        gtin TEXT NOT NULL,
                        description TEXT NOT NULL,
                        position TEXT NOT NULL,
                        system_qty INTEGER NOT NULL,
                        counted_qty INTEGER,
                        double_count_qty INTEGER,
                        lot_number TEXT,
                        first_counter_id TEXT,
                        local_status TEXT NOT NULL DEFAULT 'PENDING'
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_inventory_items_session_pos
                    ON inventory_items_cache(session_id, position)
                """.trimIndent())
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_inventory_items_session_product
                    ON inventory_items_cache(session_id, product_code)
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS inventory_sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tenant_id TEXT NOT NULL,
                        operation_type TEXT NOT NULL,
                        session_id TEXT NOT NULL,
                        item_id TEXT,
                        payload TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        retry_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS transfers_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        tenant_id TEXT NOT NULL,
                        source_location TEXT NOT NULL,
                        destination_location TEXT NOT NULL,
                        product_code TEXT NOT NULL,
                        qty INTEGER NOT NULL,
                        lot_number TEXT,
                        status TEXT NOT NULL,
                        local_status TEXT NOT NULL DEFAULT 'PENDING',
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
