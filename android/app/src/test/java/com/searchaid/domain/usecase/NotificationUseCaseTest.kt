package com.searchaid.domain.usecase

import com.searchaid.data.repository.LocalNotificationRepository
import com.searchaid.domain.repository.NotificationRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationUseCaseTest {

    private val localNotification = LocalNotificationRepository()

    @Test
    fun `SendAlert is no-op in local mode`() = runTest {
        val useCase = SendAlertUseCase(localNotification)
        useCase(1L, "Missing Person", "Alert body") // Should not throw
    }

    @Test
    fun `getToken returns null in local mode`() {
        assertNull(localNotification.getToken())
    }

    @Test
    fun `subscribe and unsubscribe do not throw`() = runTest {
        localNotification.subscribeToCase(1L)
        localNotification.unsubscribeFromCase(1L)
    }

    @Test
    fun `SendAlert delegates to repository`() = runTest {
        val mockNotification = mockk<NotificationRepository>(relaxed = true)
        val useCase = SendAlertUseCase(mockNotification)
        useCase(5L, "Title", "Body")
        coVerify { mockNotification.sendAlert(5L, "Title", "Body") }
    }
}
