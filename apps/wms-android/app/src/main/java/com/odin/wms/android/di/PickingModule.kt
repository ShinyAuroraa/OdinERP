package com.odin.wms.android.di

import com.odin.wms.android.data.repository.PickingRepository
import com.odin.wms.android.data.repository.ShippingRepository
import com.odin.wms.android.domain.repository.IPickingRepository
import com.odin.wms.android.domain.repository.IShippingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PickingModule {

    @Binds
    @Singleton
    abstract fun bindPickingRepository(impl: PickingRepository): IPickingRepository

    @Binds
    @Singleton
    abstract fun bindShippingRepository(impl: ShippingRepository): IShippingRepository
}
