package com.odin.wms.android.di

import com.odin.wms.android.data.repository.ReceivingRepository
import com.odin.wms.android.domain.repository.IReceivingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReceivingModule {

    @Binds
    @Singleton
    abstract fun bindReceivingRepository(
        impl: ReceivingRepository
    ): IReceivingRepository
}
