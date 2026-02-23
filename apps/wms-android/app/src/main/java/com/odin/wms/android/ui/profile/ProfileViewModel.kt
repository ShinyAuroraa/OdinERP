package com.odin.wms.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odin.wms.android.data.local.dao.PendingTaskDao
import com.odin.wms.android.domain.model.User
import com.odin.wms.android.domain.repository.IAuthRepository
import com.odin.wms.android.security.TokenProvider
import com.odin.wms.android.common.JwtUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: IAuthRepository,
    private val pendingTaskDao: PendingTaskDao,
    private val tokenProvider: TokenProvider
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _syncFailedCount = MutableStateFlow(0)
    val syncFailedCount: StateFlow<Int> = _syncFailedCount.asStateFlow()

    init {
        _currentUser.value = authRepository.getCurrentUser()
        loadSyncStatus()
    }

    private fun loadSyncStatus() {
        viewModelScope.launch {
            val tenantId = currentTenantId()
            if (tenantId.isNotEmpty()) {
                _syncFailedCount.value = pendingTaskDao.getFailedByTenant(tenantId).size
            }
        }
    }

    fun logout() = authRepository.logout()

    private fun currentTenantId(): String {
        val token = tokenProvider.getAccessToken() ?: return ""
        return JwtUtils.extractTenantId(JwtUtils.extractClaims(token))
    }
}
