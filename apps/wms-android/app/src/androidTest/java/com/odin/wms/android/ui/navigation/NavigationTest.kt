package com.odin.wms.android.ui.navigation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.StockSummary
import com.odin.wms.android.domain.model.User
import com.odin.wms.android.domain.model.WmsRole
import com.odin.wms.android.domain.repository.IAuthRepository
import com.odin.wms.android.domain.repository.IStockRepository
import com.odin.wms.android.ui.login.LoginScreen
import com.odin.wms.android.ui.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

// I3 (Instrumented): navegação Login → Dashboard → Profile → Logout
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockAuthRepository = mockk<IAuthRepository>(relaxed = true)
    private val mockStockRepository = mockk<IStockRepository>(relaxed = true)

    private val testUser = User(
        id = "user-1", tenantId = "tenant-abc",
        username = "operador", roles = listOf(WmsRole.WMS_OPERATOR)
    )

    @Test
    fun navigation_loginSuccessNavigatesToDashboard() {
        coEvery { mockAuthRepository.login(any(), any()) } returns ApiResult.Success(testUser)
        coEvery { mockStockRepository.getStockSummary() } returns
                ApiResult.Success(StockSummary("tenant-abc", 100, 0, 0, System.currentTimeMillis()))
        coEvery { mockStockRepository.getCachedStockSummary() } returns null
        every { mockAuthRepository.getCurrentUser() } returns testUser

        var navigatedToDashboard = false

        composeTestRule.setContent {
            val loginViewModel = LoginViewModel(mockAuthRepository)
            LoginScreen(
                onLoginSuccess = { navigatedToDashboard = true },
                viewModel = loginViewModel
            )
        }

        composeTestRule.onNodeWithTag("username_field").performTextInput("operador")
        composeTestRule.onNodeWithTag("password_field").performTextInput("senha123")
        composeTestRule.onNodeWithTag("login_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) { navigatedToDashboard }
        assert(navigatedToDashboard) { "Expected navigation to Dashboard after login" }
    }
}
