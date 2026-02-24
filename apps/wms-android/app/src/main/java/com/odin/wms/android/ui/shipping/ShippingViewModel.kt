package com.odin.wms.android.ui.shipping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.ShippingOrder
import com.odin.wms.android.domain.model.ShippingPackage
import com.odin.wms.android.domain.repository.IShippingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ShippingUiState {
    object Idle : ShippingUiState()
    object Loading : ShippingUiState()
    data class OrdersLoaded(val orders: List<ShippingOrder>, val isOffline: Boolean = false) : ShippingUiState()
    data class OrderDetail(val order: ShippingOrder, val isOffline: Boolean = false) : ShippingUiState()
    data class PackageLoaded(val pkg: ShippingPackage) : ShippingUiState()
    object ShippingComplete : ShippingUiState()
    object SyncQueued : ShippingUiState()
    data class Error(val message: String) : ShippingUiState()
}

@HiltViewModel
class ShippingViewModel @Inject constructor(
    private val shippingRepository: IShippingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShippingUiState>(ShippingUiState.Idle)
    val uiState: StateFlow<ShippingUiState> = _uiState.asStateFlow()

    fun loadOrders(tenantId: String) {
        viewModelScope.launch {
            _uiState.value = ShippingUiState.Loading
            when (val result = shippingRepository.getOrders(tenantId)) {
                is ApiResult.Success -> {
                    _uiState.value = ShippingUiState.OrdersLoaded(result.data, isOffline = false)
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = ShippingUiState.OrdersLoaded(emptyList(), isOffline = true)
                }
                is ApiResult.Error -> {
                    _uiState.value = ShippingUiState.Error(result.message)
                }
            }
        }
    }

    fun loadOrderDetail(orderId: String) {
        viewModelScope.launch {
            _uiState.value = ShippingUiState.Loading
            when (val result = shippingRepository.getOrderDetail(orderId)) {
                is ApiResult.Success -> {
                    _uiState.value = ShippingUiState.OrderDetail(result.data, isOffline = false)
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = ShippingUiState.Error("Offline — dados podem estar desatualizados")
                }
                is ApiResult.Error -> {
                    _uiState.value = ShippingUiState.Error(result.message)
                }
            }
        }
    }

    fun loadPackage(orderId: String, packageId: String, trackingCode: String, vehiclePlate: String?) {
        viewModelScope.launch {
            _uiState.value = ShippingUiState.Loading
            when (val result = shippingRepository.loadPackage(orderId, packageId, trackingCode, vehiclePlate)) {
                is ApiResult.Success -> {
                    _uiState.value = ShippingUiState.PackageLoaded(result.data)
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = ShippingUiState.SyncQueued
                }
                is ApiResult.Error -> {
                    _uiState.value = ShippingUiState.Error(result.message)
                }
            }
        }
    }

    fun completeShipping(orderId: String) {
        viewModelScope.launch {
            _uiState.value = ShippingUiState.Loading
            when (val result = shippingRepository.completeShipping(orderId)) {
                is ApiResult.Success -> {
                    _uiState.value = ShippingUiState.ShippingComplete
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = ShippingUiState.SyncQueued
                }
                is ApiResult.Error -> {
                    _uiState.value = ShippingUiState.Error(result.message)
                }
            }
        }
    }
}
