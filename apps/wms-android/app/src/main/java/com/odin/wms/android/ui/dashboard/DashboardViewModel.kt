package com.odin.wms.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.StockSummary
import com.odin.wms.android.domain.model.User
import com.odin.wms.android.domain.repository.IAuthRepository
import com.odin.wms.android.domain.repository.IStockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val stockSummary: StockSummary,
        val isOffline: Boolean = false
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stockRepository: IStockRepository,
    private val authRepository: IAuthRepository
) : ViewModel() {

    val currentUser: User? get() = authRepository.getCurrentUser()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            when (val result = stockRepository.getStockSummary()) {
                is ApiResult.Success -> _uiState.value = DashboardUiState.Success(
                    stockSummary = result.data,
                    isOffline = false
                )
                is ApiResult.NetworkError -> {
                    val cached = stockRepository.getCachedStockSummary()
                    _uiState.value = if (cached != null) {
                        DashboardUiState.Success(stockSummary = cached, isOffline = true)
                    } else {
                        DashboardUiState.Error(result.message)
                    }
                }
                is ApiResult.Error -> _uiState.value = DashboardUiState.Error(result.message)
            }
        }
    }

    fun refresh() = loadDashboard()
}
