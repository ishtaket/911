package com.searchaid.domain.usecase

import com.searchaid.domain.model.AppUser
import com.searchaid.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<AppUser?> = authRepository.currentUser
}
