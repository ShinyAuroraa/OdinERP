package com.odin.wms.android.data.repository

import com.odin.wms.android.BuildConfig
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.common.JwtUtils
import com.odin.wms.android.domain.model.User
import com.odin.wms.android.domain.repository.IAuthRepository
import com.odin.wms.android.security.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import com.odin.wms.android.di.BaseClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val tokenProvider: TokenProvider,
    @BaseClient private val httpClient: OkHttpClient
) : IAuthRepository {

    private val tokenUrl: String
        get() = "${BuildConfig.KEYCLOAK_URL}/realms/${BuildConfig.KEYCLOAK_REALM}" +
                "/protocol/openid-connect/token"

    // Note: ROPC grant used for native login form (AC3).
    // A PKCE browser-redirect flow can be added later via AppAuth-Android.
    override suspend fun login(username: String, password: String): ApiResult<User> =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "password")
                    .add("client_id", BuildConfig.KEYCLOAK_CLIENT_ID)
                    .add("username", username)
                    .add("password", password)
                    .add("scope", "openid profile")
                    .build()

                val request = Request.Builder().url(tokenUrl).post(body).build()
                val response = httpClient.newCall(request).execute()

                when {
                    response.isSuccessful -> {
                        val bodyStr = response.body?.string()
                            ?: return@withContext ApiResult.Error("Resposta vazia do servidor")
                        val json = JSONObject(bodyStr)
                        val accessToken = json.getString("access_token")
                        val refreshToken = json.getString("refresh_token")
                        val expiresIn = json.getLong("expires_in")
                        val expiresAt = System.currentTimeMillis() / 1000 + expiresIn

                        tokenProvider.saveTokens(accessToken, refreshToken, expiresAt)

                        val claims = JwtUtils.extractClaims(accessToken)
                        val user = User(
                            id = claims["sub"] as? String ?: "",
                            tenantId = JwtUtils.extractTenantId(claims),
                            username = JwtUtils.extractUsername(claims),
                            roles = JwtUtils.extractRoles(claims)
                        )
                        ApiResult.Success(user)
                    }
                    response.code == 401 ->
                        ApiResult.Error("Usuário ou senha inválidos", isAuthError = true)
                    else ->
                        ApiResult.Error("Erro do servidor: ${response.code}")
                }
            } catch (e: IOException) {
                ApiResult.NetworkError("Sem conexão — verifique o WiFi")
            } catch (e: Exception) {
                ApiResult.Error("Erro inesperado: ${e.message}")
            }
        }

    override suspend fun refreshToken(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val refreshToken = tokenProvider.getRefreshToken()
            ?: return@withContext ApiResult.Error("Token de renovação ausente", isAuthError = true)

        try {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", BuildConfig.KEYCLOAK_CLIENT_ID)
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder().url(tokenUrl).post(body).build()
            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                    ?: return@withContext ApiResult.Error("Resposta vazia")
                val json = JSONObject(bodyStr)
                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.getString("refresh_token")
                val expiresIn = json.getLong("expires_in")
                tokenProvider.saveTokens(
                    newAccessToken,
                    newRefreshToken,
                    System.currentTimeMillis() / 1000 + expiresIn
                )
                ApiResult.Success(Unit)
            } else {
                tokenProvider.clearTokens()
                ApiResult.Error("Sessão expirada — faça login novamente", isAuthError = true)
            }
        } catch (e: IOException) {
            ApiResult.NetworkError("Sem conexão")
        } catch (e: Exception) {
            ApiResult.Error("Erro ao renovar token: ${e.message}")
        }
    }

    override fun logout() = tokenProvider.clearTokens()

    override fun isLoggedIn(): Boolean = tokenProvider.hasValidToken()

    override fun getCurrentUser(): User? {
        val token = tokenProvider.getAccessToken() ?: return null
        if (tokenProvider.isTokenExpired()) return null
        return try {
            val claims = JwtUtils.extractClaims(token)
            User(
                id = claims["sub"] as? String ?: "",
                tenantId = JwtUtils.extractTenantId(claims),
                username = JwtUtils.extractUsername(claims),
                roles = JwtUtils.extractRoles(claims)
            )
        } catch (e: Exception) {
            null
        }
    }
}
