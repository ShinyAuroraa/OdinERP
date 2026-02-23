package com.odin.wms.android.ui.login

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.odin.wms.android.common.ApiResult
import com.odin.wms.android.domain.model.User
import com.odin.wms.android.domain.model.WmsRole
import com.odin.wms.android.domain.repository.IAuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

// I1 (Instrumented): LoginScreen — campos visíveis e botão habilitado
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockAuthRepository = mockk<IAuthRepository>(relaxed = true)

    @Test
    fun loginScreen_displaysUsernamePasswordAndButton() {
        composeTestRule.setContent {
            val viewModel = LoginViewModel(mockAuthRepository)
            LoginScreen(onLoginSuccess = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("username_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_button").assertIsEnabled()
    }

    @Test
    fun loginScreen_showsErrorMessageOnAuthFailure() {
        coEvery { mockAuthRepository.login(any(), any()) } returns
                ApiResult.Error("Usuário ou senha inválidos", isAuthError = true)

        composeTestRule.setContent {
            val viewModel = LoginViewModel(mockAuthRepository)
            LoginScreen(onLoginSuccess = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("username_field").performTextInput("errado")
        composeTestRule.onNodeWithTag("password_field").performTextInput("errada")
        composeTestRule.onNodeWithTag("login_button").performClick()

        composeTestRule.onNodeWithTag("error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Usuário ou senha inválidos").assertIsDisplayed()
    }
}
