package com.odin.wms.android.di

import javax.inject.Qualifier

/** Plain OkHttpClient — no AuthInterceptor; used for login/refresh requests */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseClient

/** OkHttpClient with AuthInterceptor — used for authenticated API calls */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedClient
