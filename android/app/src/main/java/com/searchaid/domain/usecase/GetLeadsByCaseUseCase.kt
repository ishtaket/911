package com.searchaid.domain.usecase

import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.repository.SearchLeadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLeadsByCaseUseCase @Inject constructor(
    private val repository: SearchLeadRepository,
) {
    operator fun invoke(caseId: Long): Flow<List<SearchLead>> =
        repository.observeByCase(caseId)
}
