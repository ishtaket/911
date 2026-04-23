package com.searchaid.domain.usecase

import com.searchaid.domain.model.AppUser
import com.searchaid.domain.repository.AuthRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend fun anonymously(): AppUser = authRepository.signInAnonymously()

    suspend fun withEmail(email: String, password: String): AppUser =
        authRepository.signInWithEmail(email, password)
}
