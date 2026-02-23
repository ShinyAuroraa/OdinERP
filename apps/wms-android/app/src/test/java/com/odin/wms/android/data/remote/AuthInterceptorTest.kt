package com.odin.wms.android.data.remote

import com.odin.wms.android.security.TokenProvider
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// U5: AuthInterceptor adiciona header Authorization corretamente
class AuthInterceptorTest {

    private val mockTokenProvider = mockk<TokenProvider>()
    private val interceptor = AuthInterceptor(mockTokenProvider)
    private val mockWebServer = MockWebServer()

    private val client = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .build()

    @BeforeEach
    fun setUp() {
        mockWebServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `interceptor adds Authorization header when token is available`() {
        every { mockTokenProvider.getAccessToken() } returns "test-jwt-token"
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        client.newCall(request).execute()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer test-jwt-token", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `interceptor does not add Authorization header when token is null`() {
        every { mockTokenProvider.getAccessToken() } returns null
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        client.newCall(request).execute()

        val recordedRequest = mockWebServer.takeRequest()
        assertNull(recordedRequest.getHeader("Authorization"))
    }
}
