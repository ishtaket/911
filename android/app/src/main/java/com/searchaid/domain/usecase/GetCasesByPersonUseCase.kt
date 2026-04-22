package com.searchaid.domain.usecase

import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.repository.MissingCaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCasesByPersonUseCase @Inject constructor(
    private val repository: MissingCaseRepository,
) {
    operator fun invoke(personId: Long): Flow<List<MissingCase>> =
        repository.observeByPerson(personId)
}
