package com.odin.wms.android.ui.transfer

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.Transfer
import com.odin.wms.android.domain.model.TransferStatus
import com.odin.wms.android.domain.repository.ITransferRepository
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
 * U12 — TransferViewModel unit test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var transferRepository: ITransferRepository
    private lateinit var viewModel: TransferViewModel

    private val tenantId = "tenant-001"
    private val transferId = "transfer-001"

    private fun buildTransfer(
        id: String = transferId,
        status: TransferStatus = TransferStatus.CONFIRMED
    ) = Transfer(
        id = id,
        tenantId = tenantId,
        sourceLocation = "A-01-001",
        destinationLocation = "B-02-003",
        productCode = "P-001",
        qty = 5,
        lotNumber = null,
        status = status,
        localStatus = "PENDING",
        createdAt = System.currentTimeMillis()
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        transferRepository = mockk()
        viewModel = TransferViewModel(transferRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // U12: createTransfer() success → TransferConfirmed state emitted
    @Test
    fun `U12 createTransfer success emits TransferConfirmed`() = runTest {
        val createdTransfer = buildTransfer(status = TransferStatus.PENDING)
        val confirmedTransfer = buildTransfer(status = TransferStatus.CONFIRMED)

        coEvery {
            transferRepository.createTransfer(
                sourceLocation = "A-01-001",
                destinationLocation = "B-02-003",
                productCode = "P-001",
                qty = 5,
                lotNumber = null
            )
        } returns ApiResult.Success(createdTransfer)

        coEvery {
            transferRepository.confirmTransfer(transferId)
        } returns ApiResult.Success(confirmedTransfer)

        viewModel.createTransfer(
            sourceLocation = "A-01-001",
            destinationLocation = "B-02-003",
            productCode = "P-001",
            qty = 5,
            lotNumber = null
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(
            state is TransferUiState.TransferConfirmed,
            "Expected TransferConfirmed but got $state"
        )
        val confirmed = state as TransferUiState.TransferConfirmed
        assertTrue(
            confirmed.transfer.status == TransferStatus.CONFIRMED,
            "Expected transfer status CONFIRMED but got ${confirmed.transfer.status}"
        )
    }

    @Test
    fun `createTransfer network failure emits SyncQueued`() = runTest {
        coEvery {
            transferRepository.createTransfer(any(), any(), any(), any(), any())
        } returns ApiResult.NetworkError("network error")

        viewModel.createTransfer(
            sourceLocation = "A-01-001",
            destinationLocation = "B-02-003",
            productCode = "P-001",
            qty = 5,
            lotNumber = null
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(
            state is TransferUiState.SyncQueued,
            "Expected SyncQueued for offline createTransfer but got $state"
        )
    }
}
