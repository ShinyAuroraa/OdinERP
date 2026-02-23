package com.odin.wms.android.data.remote

import com.odin.wms.android.security.TokenProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)

        // 401 after attaching token: caller (repository) handles re-login.
        // TODO(C4): implement okhttp3.Authenticator for proactive token refresh on 401 —
        //  Authenticator lets OkHttp automatically retry the request after refreshing credentials.
        return response
    }
}
