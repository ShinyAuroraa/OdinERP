package com.odin.wms.android.di

import com.odin.wms.android.data.repository.AuthRepository
import com.odin.wms.android.data.repository.StockRepository
import com.odin.wms.android.domain.repository.IAuthRepository
import com.odin.wms.android.domain.repository.IStockRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepository): IAuthRepository

    @Binds
    @Singleton
    abstract fun bindStockRepository(impl: StockRepository): IStockRepository
}
