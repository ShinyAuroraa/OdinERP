package com.odin.wms.android.ui.operations

import androidx.lifecycle.ViewModel
import com.odin.wms.android.domain.model.WmsRole
import com.odin.wms.android.domain.repository.IAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OperationsViewModel @Inject constructor(
    authRepository: IAuthRepository
) : ViewModel() {

    private val _userRole = MutableStateFlow(WmsRole.WMS_OPERATOR)
    val userRole: StateFlow<WmsRole> = _userRole.asStateFlow()

    init {
        val user = authRepository.getCurrentUser()
        _userRole.value = user?.primaryRole ?: WmsRole.WMS_OPERATOR
    }
}
