package com.odin.wms.android.ui.receiving

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.common.JwtUtils
import com.odin.wms.android.domain.model.DivergenceReport
import com.odin.wms.android.domain.model.ReceivingItem
import com.odin.wms.android.domain.model.ReceivingOrder
import com.odin.wms.android.domain.repository.IReceivingRepository
import com.odin.wms.android.security.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

sealed class ReceivingUiState {
    data object Idle : ReceivingUiState()
    data object LoadingOrders : ReceivingUiState()
    data class OrdersLoaded(
        val orders: List<ReceivingOrder>,
        val isOffline: Boolean = false
    ) : ReceivingUiState()
    data object LoadingDetail : ReceivingUiState()
    data class DetailLoaded(val order: ReceivingOrder) : ReceivingUiState()
    data object Confirming : ReceivingUiState()
    data object ConfirmSuccess : ReceivingUiState()
    data object SyncQueued : ReceivingUiState()
    data class Error(val message: String) : ReceivingUiState()
}

@HiltViewModel
class ReceivingViewModel @Inject constructor(
    private val repository: IReceivingRepository,
    private val tokenProvider: TokenProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReceivingUiState>(ReceivingUiState.Idle)
    val uiState: StateFlow<ReceivingUiState> = _uiState.asStateFlow()

    private val tenantId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return JwtUtils.extractTenantId(claims)
        }

    fun loadOrders(tenantId: String = this.tenantId) {
        viewModelScope.launch {
            _uiState.value = ReceivingUiState.LoadingOrders
            when (val result = repository.getOrders(tenantId)) {
                is ApiResult.Success -> _uiState.value = ReceivingUiState.OrdersLoaded(
                    orders = result.data,
                    isOffline = false
                )
                is ApiResult.NetworkError -> {
                    // offline_queued or "offline:N"
                    if (result.message.startsWith("offline:")) {
                        // Already returned empty list indication; reload from cache via separate call
                        loadOrdersFromCache(tenantId)
                    } else {
                        _uiState.value = ReceivingUiState.OrdersLoaded(
                            orders = emptyList(),
                            isOffline = true
                        )
                    }
                }
                is ApiResult.Error -> _uiState.value = ReceivingUiState.Error(result.message)
            }
        }
    }

    private suspend fun loadOrdersFromCache(tenantId: String) {
        // Re-trigger with empty list when offline — signal isOffline=true
        // The repository already saved to cache; we show cached data via a second getOrders()
        // but since we can't re-call (would loop), we signal offline with empty orders
        _uiState.value = ReceivingUiState.OrdersLoaded(
            orders = emptyList(),
            isOffline = true
        )
    }

    fun loadOrderDetail(orderId: String) {
        viewModelScope.launch {
            _uiState.value = ReceivingUiState.LoadingDetail
            when (val result = repository.getOrderDetail(orderId)) {
                is ApiResult.Success      -> _uiState.value = ReceivingUiState.DetailLoaded(result.data)
                is ApiResult.NetworkError -> _uiState.value = ReceivingUiState.Error("Sem conexão: ${result.message}")
                is ApiResult.Error        -> _uiState.value = ReceivingUiState.Error(result.message)
            }
        }
    }

    fun confirmItem(
        orderId: String,
        itemId: String,
        quantity: Int,
        lotNumber: String?,
        expiryDate: String?,
        serialNumber: String?
    ) {
        viewModelScope.launch {
            _uiState.value = ReceivingUiState.Confirming
            when (val result = repository.confirmItem(orderId, itemId, quantity, lotNumber, expiryDate, serialNumber)) {
                is ApiResult.Success      -> _uiState.value = ReceivingUiState.ConfirmSuccess
                is ApiResult.NetworkError -> _uiState.value = ReceivingUiState.SyncQueued
                is ApiResult.Error        -> _uiState.value = ReceivingUiState.Error(result.message)
            }
        }
    }

    fun reportDivergence(orderId: String, itemId: String, report: DivergenceReport) {
        viewModelScope.launch {
            _uiState.value = ReceivingUiState.Confirming
            when (val result = repository.reportDivergence(orderId, itemId, report)) {
                is ApiResult.Success      -> _uiState.value = ReceivingUiState.ConfirmSuccess
                is ApiResult.NetworkError -> _uiState.value = ReceivingUiState.SyncQueued
                is ApiResult.Error        -> _uiState.value = ReceivingUiState.Error(result.message)
            }
        }
    }

    fun submitSignatureAndComplete(orderId: String, signatureBitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = ReceivingUiState.Confirming
            val signatureBase64 = convertBitmapToBase64(signatureBitmap)

            when (val sigResult = repository.submitSignature(orderId, signatureBase64)) {
                is ApiResult.Success, is ApiResult.NetworkError -> {
                    when (val completeResult = repository.completeOrder(orderId)) {
                        is ApiResult.Success      -> _uiState.value = ReceivingUiState.ConfirmSuccess
                        is ApiResult.NetworkError -> _uiState.value = ReceivingUiState.SyncQueued
                        is ApiResult.Error        -> _uiState.value = ReceivingUiState.Error(completeResult.message)
                    }
                }
                is ApiResult.Error -> _uiState.value = ReceivingUiState.Error(sigResult.message)
            }
        }
    }

    suspend fun convertBitmapToBase64(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    fun resetState() {
        _uiState.value = ReceivingUiState.Idle
    }
}
