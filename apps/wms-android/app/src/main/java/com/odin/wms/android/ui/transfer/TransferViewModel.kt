package com.odin.wms.android.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.Transfer
import com.odin.wms.android.domain.repository.ITransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TransferUiState {
    object Idle : TransferUiState()
    object Loading : TransferUiState()
    data class TransfersLoaded(val transfers: List<Transfer>, val isOffline: Boolean = false) : TransferUiState()
    object Creating : TransferUiState()
    data class TransferCreated(val transfer: Transfer) : TransferUiState()
    data class TransferConfirmed(val transfer: Transfer) : TransferUiState()
    object SyncQueued : TransferUiState()
    data class Error(val message: String) : TransferUiState()
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transferRepository: ITransferRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    fun loadTransfers(tenantId: String) {
        viewModelScope.launch {
            _uiState.value = TransferUiState.Loading
            when (val result = transferRepository.getTransfers(tenantId)) {
                is ApiResult.Success -> {
                    _uiState.value = TransferUiState.TransfersLoaded(result.data, isOffline = false)
                }
                is ApiResult.NetworkError -> {
                    // NetworkError — repository returned cache data
                    _uiState.value = TransferUiState.TransfersLoaded(emptyList(), isOffline = true)
                }
                is ApiResult.Error -> {
                    _uiState.value = TransferUiState.Error(result.message)
                }
            }
        }
    }

    fun createTransfer(
        sourceLocation: String,
        destinationLocation: String,
        productCode: String,
        qty: Int,
        lotNumber: String?
    ) {
        viewModelScope.launch {
            _uiState.value = TransferUiState.Creating
            when (val createResult = transferRepository.createTransfer(
                sourceLocation = sourceLocation,
                destinationLocation = destinationLocation,
                productCode = productCode,
                qty = qty,
                lotNumber = lotNumber
            )) {
                is ApiResult.Success -> {
                    val transfer = createResult.data
                    // Immediately confirm the created transfer
                    when (val confirmResult = transferRepository.confirmTransfer(transfer.id)) {
                        is ApiResult.Success -> {
                            _uiState.value = TransferUiState.TransferConfirmed(confirmResult.data)
                        }
                        is ApiResult.NetworkError -> {
                            _uiState.value = TransferUiState.SyncQueued
                        }
                        is ApiResult.Error -> {
                            // Transfer was created but confirm failed — still show as created
                            _uiState.value = TransferUiState.TransferCreated(transfer)
                        }
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = TransferUiState.SyncQueued
                }
                is ApiResult.Error -> {
                    _uiState.value = TransferUiState.Error(createResult.message)
                }
            }
        }
    }

    fun confirmTransfer(transferId: String) {
        viewModelScope.launch {
            _uiState.value = TransferUiState.Creating
            when (val result = transferRepository.confirmTransfer(transferId)) {
                is ApiResult.Success -> {
                    _uiState.value = TransferUiState.TransferConfirmed(result.data)
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = TransferUiState.SyncQueued
                }
                is ApiResult.Error -> {
                    _uiState.value = TransferUiState.Error(result.message)
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = TransferUiState.Idle
    }
}
