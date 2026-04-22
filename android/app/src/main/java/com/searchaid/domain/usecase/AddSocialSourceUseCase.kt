package com.searchaid.domain.usecase

import com.searchaid.domain.model.SocialSource
import com.searchaid.domain.repository.SocialSourceRepository
import javax.inject.Inject

class AddSocialSourceUseCase @Inject constructor(
    private val repository: SocialSourceRepository,
) {
    suspend operator fun invoke(source: SocialSource): Long = repository.add(source)
}
