package com.searchaid.domain.usecase

import com.searchaid.domain.model.SocialSource
import com.searchaid.domain.repository.SocialSourceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSocialSourcesByPersonUseCase @Inject constructor(
    private val repository: SocialSourceRepository,
) {
    operator fun invoke(personId: Long): Flow<List<SocialSource>> =
        repository.observeByPerson(personId)
}
