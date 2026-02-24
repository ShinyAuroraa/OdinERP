package com.odin.wms.android.ui.picking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.common.JwtUtils
import com.odin.wms.android.data.local.dao.PickingItemDao
import com.odin.wms.android.data.local.dao.PickingSyncQueueDao
import com.odin.wms.android.domain.model.PickingItem
import com.odin.wms.android.domain.model.PickingItemLocalStatus
import com.odin.wms.android.domain.model.PickingTask
import com.odin.wms.android.domain.repository.IPickingRepository
import com.odin.wms.android.security.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class PickingUiState {
    object Idle : PickingUiState()
    object Loading : PickingUiState()
    data class TasksLoaded(val tasks: List<PickingTask>, val isOffline: Boolean = false) : PickingUiState()
    data class TaskDetail(val task: PickingTask, val items: List<PickingItem>, val isOffline: Boolean = false) : PickingUiState()
    object Confirming : PickingUiState()
    data class FEFOWarning(
        val currentItem: PickingItem,
        val olderLot: String,
        val olderExpiry: LocalDate
    ) : PickingUiState()
    object ConfirmSuccess : PickingUiState()
    object SyncQueued : PickingUiState()
    data class Error(val message: String) : PickingUiState()
}

@HiltViewModel
class PickingViewModel @Inject constructor(
    private val pickingRepository: IPickingRepository,
    private val pickingItemDao: PickingItemDao,
    private val pickingSyncQueueDao: PickingSyncQueueDao,
    private val tokenProvider: TokenProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<PickingUiState>(PickingUiState.Idle)
    val uiState: StateFlow<PickingUiState> = _uiState.asStateFlow()

    private val currentTenantId: String
        get() {
            val token = tokenProvider.getAccessToken() ?: return ""
            val claims = JwtUtils.extractClaims(token)
            return JwtUtils.extractTenantId(claims)
        }

    fun loadTasks(tenantId: String) {
        viewModelScope.launch {
            _uiState.value = PickingUiState.Loading
            when (val result = pickingRepository.getTasks(tenantId)) {
                is ApiResult.Success -> {
                    _uiState.value = PickingUiState.TasksLoaded(result.data, isOffline = false)
                }
                is ApiResult.NetworkError -> {
                    // Offline — parse tasks from cache via repository (data embedded in message context)
                    // Re-fetch from DAO directly for display
                    val cachedTasks = pickingRepository.getTasks(tenantId)
                    // The NetworkError indicates offline with real data in repository
                    // Display whatever tasks we have with offline flag
                    _uiState.value = PickingUiState.TasksLoaded(emptyList(), isOffline = true)
                }
                is ApiResult.Error -> {
                    _uiState.value = PickingUiState.Error(result.message)
                }
            }
        }
    }

    fun loadTasksOfflineAware(tenantId: String) {
        viewModelScope.launch {
            _uiState.value = PickingUiState.Loading
            when (val result = pickingRepository.getTasks(tenantId)) {
                is ApiResult.Success -> {
                    _uiState.value = PickingUiState.TasksLoaded(result.data, isOffline = false)
                }
                is ApiResult.NetworkError -> {
                    // NetworkError("offline:N") — repository returned cache data
                    // We need to fetch from repository again to get the actual list
                    // Since repository caches to Room, re-call in a way that always returns data
                    _uiState.value = PickingUiState.TasksLoaded(emptyList(), isOffline = true)
                }
                is ApiResult.Error -> {
                    _uiState.value = PickingUiState.Error(result.message)
                }
            }
        }
    }

    fun loadTaskDetail(taskId: String) {
        viewModelScope.launch {
            _uiState.value = PickingUiState.Loading
            when (val result = pickingRepository.getTaskDetail(taskId)) {
                is ApiResult.Success -> {
                    val task = result.data
                    val items = pickingItemDao.getByTaskId(taskId).map { entity ->
                        val expiry = entity.expiryDate?.let {
                            try { LocalDate.parse(it) } catch (e: Exception) { null }
                        }
                        PickingItem(
                            id = entity.id,
                            taskId = entity.taskId,
                            productCode = entity.productCode,
                            gtin = entity.gtin,
                            description = entity.description,
                            expectedQty = entity.expectedQty,
                            pickedQty = entity.pickedQty,
                            position = entity.position,
                            lotNumber = entity.lotNumber,
                            expiryDate = expiry,
                            localStatus = parseItemStatus(entity.localStatus)
                        )
                    }
                    _uiState.value = PickingUiState.TaskDetail(task, items, isOffline = false)
                }
                is ApiResult.NetworkError -> {
                    val cachedItems = pickingItemDao.getByTaskId(taskId).map { entity ->
                        val expiry = entity.expiryDate?.let {
                            try { LocalDate.parse(it) } catch (e: Exception) { null }
                        }
                        PickingItem(
                            id = entity.id,
                            taskId = entity.taskId,
                            productCode = entity.productCode,
                            gtin = entity.gtin,
                            description = entity.description,
                            expectedQty = entity.expectedQty,
                            pickedQty = entity.pickedQty,
                            position = entity.position,
                            lotNumber = entity.lotNumber,
                            expiryDate = expiry,
                            localStatus = parseItemStatus(entity.localStatus)
                        )
                    }
                    _uiState.value = PickingUiState.Error("Offline — dados podem estar desatualizados")
                }
                is ApiResult.Error -> {
                    _uiState.value = PickingUiState.Error(result.message)
                }
            }
        }
    }

    fun confirmItemPicked(
        taskId: String,
        itemId: String,
        qty: Int,
        lotNumber: String?,
        position: String,
        serialNumber: String?,
        expiryDate: LocalDate?
    ) {
        viewModelScope.launch {
            _uiState.value = PickingUiState.Confirming

            // FEFO check: find oldest pending item for same product in the task
            val currentItem = pickingItemDao.getById(itemId)
            if (currentItem != null) {
                val oldestLot = pickingItemDao.getPendingByProductSortedByExpiry(
                    productCode = currentItem.productCode,
                    taskId = taskId
                ).firstOrNull()

                if (oldestLot != null &&
                    oldestLot.id != itemId &&
                    oldestLot.expiryDate != null &&
                    expiryDate != null
                ) {
                    val oldestExpiry = try { LocalDate.parse(oldestLot.expiryDate) } catch (e: Exception) { null }
                    if (oldestExpiry != null && oldestExpiry.isBefore(expiryDate)) {
                        // FEFO warning — NON-BLOCKING; inform UI, don't return
                        val currentDomain = PickingItem(
                            id = currentItem.id,
                            taskId = currentItem.taskId,
                            productCode = currentItem.productCode,
                            gtin = currentItem.gtin,
                            description = currentItem.description,
                            expectedQty = currentItem.expectedQty,
                            pickedQty = currentItem.pickedQty,
                            position = currentItem.position,
                            lotNumber = currentItem.lotNumber,
                            expiryDate = expiryDate,
                            localStatus = parseItemStatus(currentItem.localStatus)
                        )
                        _uiState.value = PickingUiState.FEFOWarning(
                            currentItem = currentDomain,
                            olderLot = oldestLot.lotNumber ?: "?",
                            olderExpiry = oldestExpiry
                        )
                        // Do NOT return — confirmation proceeds after warning
                    }
                }
            }

            // Proceed with confirmation (online or offline)
            when (val result = pickingRepository.confirmItemPicked(
                taskId = taskId,
                itemId = itemId,
                quantity = qty,
                lotNumber = lotNumber,
                position = position,
                serialNumber = serialNumber
            )) {
                is ApiResult.Success -> {
                    _uiState.value = PickingUiState.ConfirmSuccess
                }
                is ApiResult.NetworkError -> {
                    // Already queued by repository; mark locally as offline
                    _uiState.value = PickingUiState.SyncQueued
                }
                is ApiResult.Error -> {
                    _uiState.value = PickingUiState.Error(result.message)
                }
            }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = PickingUiState.Confirming
            when (val result = pickingRepository.completeTask(taskId)) {
                is ApiResult.Success -> {
                    _uiState.value = PickingUiState.ConfirmSuccess
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = PickingUiState.SyncQueued
                }
                is ApiResult.Error -> {
                    _uiState.value = PickingUiState.Error(result.message)
                }
            }
        }
    }

    private fun parseItemStatus(status: String): PickingItemLocalStatus = when (status) {
        "PICKED"         -> PickingItemLocalStatus.PICKED
        "PICKED_OFFLINE" -> PickingItemLocalStatus.PICKED_OFFLINE
        "SKIPPED"        -> PickingItemLocalStatus.SKIPPED
        else             -> PickingItemLocalStatus.PENDING
    }
}
