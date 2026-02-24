package com.odin.wms.android.di

import android.content.Context
import androidx.room.Room
import com.odin.wms.android.data.local.WmsDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWmsDatabase(@ApplicationContext context: Context): WmsDatabase =
        Room.databaseBuilder(context, WmsDatabase::class.java, WmsDatabase.DATABASE_NAME)
            .addMigrations(WmsDatabase.MIGRATION_1_2, WmsDatabase.MIGRATION_2_3, WmsDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun provideStockSummaryDao(db: WmsDatabase): StockSummaryDao = db.stockSummaryDao()

    @Provides
    fun providePendingTaskDao(db: WmsDatabase): PendingTaskDao = db.pendingTaskDao()

    @Provides
    fun provideReceivingOrderDao(db: WmsDatabase): ReceivingOrderDao = db.receivingOrderDao()

    @Provides
    fun provideReceivingItemDao(db: WmsDatabase): ReceivingItemDao = db.receivingItemDao()

    @Provides
    fun provideReceivingSyncQueueDao(db: WmsDatabase): ReceivingSyncQueueDao = db.receivingSyncQueueDao()

    @Provides
    fun providePickingTaskDao(db: WmsDatabase): PickingTaskDao = db.pickingTaskDao()

    @Provides
    fun providePickingItemDao(db: WmsDatabase): PickingItemDao = db.pickingItemDao()

    @Provides
    fun providePickingSyncQueueDao(db: WmsDatabase): PickingSyncQueueDao = db.pickingSyncQueueDao()

    @Provides
    fun provideShippingOrderDao(db: WmsDatabase): ShippingOrderDao = db.shippingOrderDao()

    @Provides
    fun provideShippingPackageDao(db: WmsDatabase): ShippingPackageDao = db.shippingPackageDao()

    @Provides
    fun provideInventorySessionDao(db: WmsDatabase): InventorySessionDao = db.inventorySessionDao()

    @Provides
    fun provideInventoryItemDao(db: WmsDatabase): InventoryItemDao = db.inventoryItemDao()

    @Provides
    fun provideInventorySyncQueueDao(db: WmsDatabase): InventorySyncQueueDao = db.inventorySyncQueueDao()

    @Provides
    fun provideTransferDao(db: WmsDatabase): TransferDao = db.transferDao()
}
