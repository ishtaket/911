package com.searchaid.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.searchaid.domain.model.AppUser
import com.searchaid.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Auth implementation. To enable:
 * 1. Add google-services.json to app/
 * 2. In RepositoryModule, change bindAuthRepository to use this class
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override val currentUser: Flow<AppUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toDomain())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInAnonymously(): AppUser {
        val result = auth.signInAnonymously().await()
        return result.user?.toDomain()
            ?: throw IllegalStateException("Anonymous sign-in returned null user")
    }

    override suspend fun signInWithEmail(email: String, password: String): AppUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user?.toDomain()
            ?: throw IllegalStateException("Email sign-in returned null user")
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override fun isAuthenticated(): Boolean = auth.currentUser != null

    private fun FirebaseUser.toDomain() = AppUser(
        uid = uid,
        displayName = displayName,
        email = email,
        isAnonymous = isAnonymous,
    )
}
