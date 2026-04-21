package com.searchaid.domain.usecase

import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.repository.PersonProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllProfilesUseCase @Inject constructor(
    private val repository: PersonProfileRepository,
) {
    operator fun invoke(): Flow<List<PersonProfile>> = repository.observeAll()
}
