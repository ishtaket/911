package com.searchaid.data.repository

import com.searchaid.domain.model.AppUser
import com.searchaid.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline auth stub. Auto-authenticates as a local operator.
 * Replace with FirebaseAuthRepository when Firebase is configured.
 */
@Singleton
class OfflineAuthRepository @Inject constructor() : AuthRepository {

    private val localUser = AppUser(
        uid = "local-operator",
        displayName = "Local Operator",
        email = null,
        isAnonymous = true,
    )

    private val _currentUser = MutableStateFlow<AppUser?>(localUser)
    override val currentUser: Flow<AppUser?> = _currentUser

    override suspend fun signInAnonymously(): AppUser {
        _currentUser.value = localUser
        return localUser
    }

    override suspend fun signInWithEmail(email: String, password: String): AppUser {
        // Offline mode: email sign-in not supported
        throw UnsupportedOperationException("Email sign-in requires Firebase. Configure google-services.json to enable.")
    }

    override suspend fun signOut() {
        // In offline mode, sign-out re-authenticates as local operator
        _currentUser.value = localUser
    }

    override fun isAuthenticated(): Boolean = _currentUser.value != null
}
