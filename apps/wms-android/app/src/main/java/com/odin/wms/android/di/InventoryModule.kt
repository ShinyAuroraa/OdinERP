package com.odin.wms.android.di

import com.odin.wms.android.data.repository.InventoryRepository
import com.odin.wms.android.data.repository.TransferRepository
import com.odin.wms.android.domain.repository.IInventoryRepository
import com.odin.wms.android.domain.repository.ITransferRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InventoryModule {

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(
        inventoryRepository: InventoryRepository
    ): IInventoryRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(
        transferRepository: TransferRepository
    ): ITransferRepository
}
