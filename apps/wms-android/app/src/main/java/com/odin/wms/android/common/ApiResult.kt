package com.odin.wms.android.common

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(
        val message: String,
        val isAuthError: Boolean = false
    ) : ApiResult<Nothing>()
    data class NetworkError(val message: String) : ApiResult<Nothing>()
}
