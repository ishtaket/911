package com.searchaid.domain.repository

import com.searchaid.domain.model.AppUser
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for authentication. Offline mode uses a local stub;
 * when Firebase is configured, swap to FirebaseAuthRepository via DI.
 */
interface AuthRepository {
    val currentUser: Flow<AppUser?>
    suspend fun signInAnonymously(): AppUser
    suspend fun signInWithEmail(email: String, password: String): AppUser
    suspend fun signOut()
    fun isAuthenticated(): Boolean
}
