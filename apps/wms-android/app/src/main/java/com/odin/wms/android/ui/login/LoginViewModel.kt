package com.odin.wms.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.User
import com.odin.wms.android.domain.repository.IAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val user: User) : LoginUiState
    data class Error(
        val message: String,
        val isNetworkError: Boolean = false,
        val isAuthError: Boolean = false
    ) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: IAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Preencha todos os campos")
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            _uiState.value = when (val result = authRepository.login(username, password)) {
                is ApiResult.Success     -> LoginUiState.Success(result.data)
                is ApiResult.Error       -> LoginUiState.Error(result.message, isAuthError = result.isAuthError)
                is ApiResult.NetworkError -> LoginUiState.Error(result.message, isNetworkError = true)
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
