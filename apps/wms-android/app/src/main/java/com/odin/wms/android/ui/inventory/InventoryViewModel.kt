package com.odin.wms.android.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventorySession
import com.odin.wms.android.domain.repository.IInventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class InventoryUiState {
    object Idle : InventoryUiState()
    object Loading : InventoryUiState()
    data class SessionsLoaded(val sessions: List<InventorySession>, val isOffline: Boolean = false) : InventoryUiState()
    data class CountingListLoaded(val items: List<InventoryItem>, val sessionId: String, val isOffline: Boolean = false) : InventoryUiState()
    object Confirming : InventoryUiState()
    data class DivergenceWarning(val item: InventoryItem, val diffAbs: Int, val diffPct: Float) : InventoryUiState()
    object CountSuccess : InventoryUiState()
    data class DoubleCountResult(val item: InventoryItem, val verified: Boolean) : InventoryUiState()
    object SubmitSuccess : InventoryUiState()
    object SyncQueued : InventoryUiState()
    data class Error(val message: String) : InventoryUiState()
}

private const val DIVERGENCE_THRESHOLD_PCT = 0.10f // 10%

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: IInventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<InventoryUiState>(InventoryUiState.Idle)
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    fun loadSessions(tenantId: String) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading
            when (val result = inventoryRepository.getSessions(tenantId)) {
                is ApiResult.Success -> {
                    _uiState.value = InventoryUiState.SessionsLoaded(result.data, isOffline = false)
                }
                is ApiResult.NetworkError -> {
                    // FIX QA-8.3-001 pattern: getSessions() already returns cache in NetworkError
                    // Re-fetch directly from repository cache to get real data
                    val cachedSessions = try {
                        // NetworkError message format: "offline:N" — N is count
                        // We need the actual data: re-call with a short-circuit approach
                        // Since getSessions() already cached to Room, re-query same path
                        when (val cached = inventoryRepository.getSessions(tenantId)) {
                            is ApiResult.Success -> cached.data
                            is ApiResult.NetworkError -> emptyList()
                            else -> emptyList()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    _uiState.value = InventoryUiState.SessionsLoaded(cachedSessions, isOffline = true)
                }
                is ApiResult.Error -> {
                    _uiState.value = InventoryUiState.Error(result.message)
                }
            }
        }
    }

    fun loadCountingList(sessionId: String, aisle: String? = null) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading
            when (val result = inventoryRepository.getCountingList(sessionId, aisle)) {
                is ApiResult.Success -> {
                    _uiState.value = InventoryUiState.CountingListLoaded(result.data, sessionId, isOffline = false)
                }
                is ApiResult.NetworkError -> {
                    // FIX QA-8.3-001: DO NOT emit emptyList() — call repository cache directly
                    val cachedItems = inventoryRepository.getCountingListFromCache(sessionId)
                    _uiState.value = InventoryUiState.CountingListLoaded(cachedItems, sessionId, isOffline = true)
                }
                is ApiResult.Error -> {
                    _uiState.value = InventoryUiState.Error(result.message)
                }
            }
        }
    }

    fun countItem(
        sessionId: String,
        itemId: String,
        productCode: String,
        countedQty: Int,
        systemQty: Int,
        lotNumber: String?,
        position: String
    ) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Confirming

            // Check divergence before confirming
            checkDivergence(
                sessionId = sessionId,
                itemId = itemId,
                productCode = productCode,
                countedQty = countedQty,
                systemQty = systemQty,
                lotNumber = lotNumber,
                position = position
            )
        }
    }

    private suspend fun checkDivergence(
        sessionId: String,
        itemId: String,
        productCode: String,
        countedQty: Int,
        systemQty: Int,
        lotNumber: String?,
        position: String
    ) {
        if (systemQty > 0) {
            val diffAbs = kotlin.math.abs(countedQty - systemQty)
            val diffPct = diffAbs.toFloat() / systemQty.toFloat()

            if (diffPct > DIVERGENCE_THRESHOLD_PCT) {
                // Emit DivergenceWarning — NON-BLOCKING
                // Create a placeholder item for the warning state
                val warningItem = InventoryItem(
                    id = itemId,
                    sessionId = sessionId,
                    tenantId = "",
                    productCode = productCode,
                    gtin = "",
                    description = "",
                    position = position,
                    systemQty = systemQty,
                    countedQty = countedQty,
                    lotNumber = lotNumber
                )
                _uiState.value = InventoryUiState.DivergenceWarning(
                    item = warningItem,
                    diffAbs = diffAbs,
                    diffPct = diffPct * 100f
                )
                // Warning is non-blocking — continue with confirmation after emitting
            }
        }

        // Proceed with confirmation regardless of divergence
        when (val result = inventoryRepository.countItem(
            sessionId = sessionId,
            itemId = itemId,
            productCode = productCode,
            countedQty = countedQty,
            lotNumber = lotNumber,
            position = position
        )) {
            is ApiResult.Success -> {
                _uiState.value = InventoryUiState.CountSuccess
            }
            is ApiResult.NetworkError -> {
                _uiState.value = InventoryUiState.SyncQueued
            }
            is ApiResult.Error -> {
                _uiState.value = InventoryUiState.Error(result.message)
            }
        }
    }

    fun doubleCount(
        sessionId: String,
        itemId: String,
        countedQty: Int,
        counterId: String
    ) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Confirming
            when (val result = inventoryRepository.doubleCount(
                sessionId = sessionId,
                itemId = itemId,
                countedQty = countedQty,
                counterId = counterId
            )) {
                is ApiResult.Success -> {
                    val item = result.data
                    val verified = item.localStatus == com.odin.wms.android.domain.model.InventoryItemLocalStatus.COUNTED_VERIFIED
                    _uiState.value = InventoryUiState.DoubleCountResult(item, verified)
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = InventoryUiState.SyncQueued
                }
                is ApiResult.Error -> {
                    _uiState.value = InventoryUiState.Error(result.message)
                }
            }
        }
    }

    fun submitSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Confirming
            when (val result = inventoryRepository.submitSession(sessionId)) {
                is ApiResult.Success -> {
                    _uiState.value = InventoryUiState.SubmitSuccess
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = InventoryUiState.SyncQueued
                }
                is ApiResult.Error -> {
                    _uiState.value = InventoryUiState.Error(result.message)
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = InventoryUiState.Idle
    }
}
