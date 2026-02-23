package com.odin.wms.android.ui.login

import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.User
import com.odin.wms.android.domain.model.WmsRole
import com.odin.wms.android.domain.repository.IAuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val mockAuthRepository = mockk<IAuthRepository>()
    private lateinit var viewModel: LoginViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val validUser = User(
        id = "user-123",
        tenantId = "tenant-abc",
        username = "operador",
        roles = listOf(WmsRole.WMS_OPERATOR)
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(mockAuthRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // U1: credenciais válidas → estado Success
    @Test
    fun `login with valid credentials transitions to Success state`() = runTest {
        coEvery { mockAuthRepository.login("operador", "senha123") } returns
                ApiResult.Success(validUser)

        viewModel.login("operador", "senha123")

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Success)
        assertEquals(validUser, (state as LoginUiState.Success).user)
    }

    // U2: erro de rede → estado NetworkError
    @Test
    fun `login with network failure transitions to Error with isNetworkError true`() = runTest {
        coEvery { mockAuthRepository.login(any(), any()) } returns
                ApiResult.NetworkError("Sem conexão — verifique o WiFi")

        viewModel.login("operador", "senha123")

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Error)
        assertTrue((state as LoginUiState.Error).isNetworkError)
        assertEquals("Sem conexão — verifique o WiFi", state.message)
    }

    @Test
    fun `login with blank username sets Error state without calling repository`() = runTest {
        viewModel.login("", "senha123")

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Error)
        assertEquals("Preencha todos os campos", (state as LoginUiState.Error).message)
    }

    @Test
    fun `login with auth error sets isAuthError true`() = runTest {
        coEvery { mockAuthRepository.login(any(), any()) } returns
                ApiResult.Error("Usuário ou senha inválidos", isAuthError = true)

        viewModel.login("errado", "errado")

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Error)
        assertTrue((state as LoginUiState.Error).isAuthError)
    }
}
