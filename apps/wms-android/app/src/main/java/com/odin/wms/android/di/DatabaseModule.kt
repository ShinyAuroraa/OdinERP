package com.odin.wms.android.di

import android.content.Context
import androidx.room.Room
import com.odin.wms.android.data.local.WmsDatabase
import com.odin.wms.android.data.local.dao.PendingTaskDao
import com.odin.wms.android.data.local.dao.ReceivingItemDao
import com.odin.wms.android.data.local.dao.ReceivingOrderDao
import com.odin.wms.android.data.local.dao.ReceivingSyncQueueDao
import com.odin.wms.android.data.local.dao.StockSummaryDao
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
            .addMigrations(WmsDatabase.MIGRATION_1_2)
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
}
