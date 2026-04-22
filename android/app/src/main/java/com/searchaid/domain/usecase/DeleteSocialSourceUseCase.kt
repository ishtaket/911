package com.searchaid.domain.usecase

import com.searchaid.domain.repository.SocialSourceRepository
import javax.inject.Inject

class DeleteSocialSourceUseCase @Inject constructor(
    private val repository: SocialSourceRepository,
) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
