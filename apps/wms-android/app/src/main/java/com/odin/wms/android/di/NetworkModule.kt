package com.odin.wms.android.di

import com.odin.wms.android.BuildConfig
import com.odin.wms.android.data.remote.AuthInterceptor
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.di.AuthenticatedClient
import com.odin.wms.android.di.BaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Plain OkHttpClient (no AuthInterceptor) — used by AuthRepository for login/refresh */
    @Provides
    @Singleton
    @BaseClient
    fun provideBaseOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
        } else {
            // Certificate pinning only in release (OBS-2 from PO validation)
            builder.certificatePinner(
                CertificatePinner.Builder()
                    // TODO(C3/security): placeholder pin — all HTTPS connections WILL FAIL in release until replaced.
                    // Extract real pin: openssl s_client -connect $API_HOST:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    .add(BuildConfig.API_HOST, "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                    .build()
            )
        }

        return builder.build()
    }

    /** OkHttpClient with AuthInterceptor — used for authenticated API calls */
    @Provides
    @Singleton
    @AuthenticatedClient
    fun provideAuthenticatedOkHttpClient(
        @BaseClient base: OkHttpClient,
        authInterceptor: AuthInterceptor
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(authInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @AuthenticatedClient okHttpClient: OkHttpClient
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideWmsApiService(retrofit: Retrofit): WmsApiService =
        retrofit.create(WmsApiService::class.java)
}
