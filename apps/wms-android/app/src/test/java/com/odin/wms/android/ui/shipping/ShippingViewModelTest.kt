package com.odin.wms.android.ui.shipping

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.ShippingPackage
import com.odin.wms.android.domain.model.ShippingPackageStatus
import com.odin.wms.android.domain.repository.IShippingRepository
import io.mockk.any
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * U13 — ShippingViewModel unit tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShippingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var shippingRepository: IShippingRepository
    private lateinit var viewModel: ShippingViewModel

    private val orderId = "order-001"
    private val packageId = "pkg-001"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        shippingRepository = mockk()
        viewModel = ShippingViewModel(shippingRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // U13: loadPackage() success → state PackageLoaded emitted
    @Test
    fun `U13 loadPackage success emits PackageLoaded state`() = runTest {
        val pkg = ShippingPackage(
            id = packageId,
            orderId = orderId,
            trackingCode = "TRK-001",
            weight = 2.5,
            status = ShippingPackageStatus.LOADED
        )
        coEvery {
            shippingRepository.loadPackage(orderId, packageId, any(), any())
        } returns ApiResult.Success(pkg)

        viewModel.loadPackage(
            orderId = orderId,
            packageId = packageId,
            trackingCode = "TRK-001",
            vehiclePlate = "ABC-1234"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ShippingUiState.PackageLoaded, "Expected PackageLoaded but got $state")
        val loaded = state as ShippingUiState.PackageLoaded
        assertTrue(loaded.pkg.status == ShippingPackageStatus.LOADED)
        assertTrue(loaded.pkg.trackingCode == "TRK-001")
    }
}
