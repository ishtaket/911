package com.searchaid.domain.usecase

import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.repository.PersonProfileRepository
import javax.inject.Inject

class UpdatePersonProfileUseCase @Inject constructor(
    private val repository: PersonProfileRepository,
) {
    suspend operator fun invoke(profile: PersonProfile) = repository.update(profile)
}
