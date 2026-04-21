package com.searchaid.domain.usecase

import com.searchaid.domain.repository.PersonProfileRepository
import javax.inject.Inject

class DeletePersonProfileUseCase @Inject constructor(
    private val repository: PersonProfileRepository,
) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
