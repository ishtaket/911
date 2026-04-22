package com.searchaid.domain.usecase

import com.searchaid.domain.model.SearchZone
import com.searchaid.domain.repository.SearchZoneRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSearchZonesByCaseUseCase @Inject constructor(
    private val repository: SearchZoneRepository,
) {
    operator fun invoke(caseId: Long): Flow<List<SearchZone>> =
        repository.observeByCase(caseId)
}
