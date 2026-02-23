package com.odin.wms.android.domain.repository

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.User

interface IAuthRepository {
    suspend fun login(username: String, password: String): ApiResult<User>
    suspend fun refreshToken(): ApiResult<Unit>
    fun logout()
    fun isLoggedIn(): Boolean
    fun getCurrentUser(): User?
}
