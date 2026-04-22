package com.searchaid.domain.usecase

import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.repository.SearchLeadRepository
import javax.inject.Inject

class AddLeadUseCase @Inject constructor(
    private val repository: SearchLeadRepository,
) {
    suspend operator fun invoke(lead: SearchLead): Long = repository.add(lead)
}
