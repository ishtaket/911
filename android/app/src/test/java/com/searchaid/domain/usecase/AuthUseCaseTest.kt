package com.searchaid.domain.usecase

import com.searchaid.data.repository.OfflineAuthRepository
import com.searchaid.domain.model.AppUser
import com.searchaid.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUseCaseTest {

    private val offlineAuth = OfflineAuthRepository()

    @Test
    fun `GetCurrentUser returns local operator in offline mode`() = runTest {
        val useCase = GetCurrentUserUseCase(offlineAuth)
        val user = useCase().first()
        assertNotNull(user)
        assertEquals("local-operator", user!!.uid)
        assertEquals("Local Operator", user.displayName)
        assertTrue(user.isAnonymous)
    }

    @Test
    fun `SignIn anonymously returns local user in offline mode`() = runTest {
        val useCase = SignInUseCase(offlineAuth)
        val user = useCase.anonymously()
        assertEquals("local-operator", user.uid)
    }

    @Test
    fun `SignIn with email throws in offline mode`() = runTest {
        val useCase = SignInUseCase(offlineAuth)
        try {
            useCase.withEmail("test@test.com", "pass")
            throw AssertionError("Should have thrown")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message!!.contains("Firebase"))
        }
    }

    @Test
    fun `SignOut keeps user authenticated in offline mode`() = runTest {
        val useCase = SignOutUseCase(offlineAuth)
        useCase()
        assertTrue(offlineAuth.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns true in offline mode`() {
        assertTrue(offlineAuth.isAuthenticated())
    }

    @Test
    fun `GetCurrentUser delegates to repository`() = runTest {
        val mockAuth = mockk<AuthRepository>()
        val testUser = AppUser("uid-123", "Test", "test@test.com", false)
        every { mockAuth.currentUser } returns flowOf(testUser)

        val useCase = GetCurrentUserUseCase(mockAuth)
        val user = useCase().first()
        assertEquals("uid-123", user!!.uid)
        assertEquals("Test", user.displayName)
    }

    @Test
    fun `SignIn delegates to repository`() = runTest {
        val mockAuth = mockk<AuthRepository>()
        val testUser = AppUser("uid-123", "Test", "test@test.com", false)
        coEvery { mockAuth.signInWithEmail("test@test.com", "pass") } returns testUser

        val useCase = SignInUseCase(mockAuth)
        val user = useCase.withEmail("test@test.com", "pass")
        assertEquals("uid-123", user.uid)
        coVerify { mockAuth.signInWithEmail("test@test.com", "pass") }
    }
}
