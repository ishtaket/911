package com.searchaid.domain.usecase

import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.repository.SearchLeadRepository
import javax.inject.Inject

class UpdateLeadStatusUseCase @Inject constructor(
    private val repository: SearchLeadRepository,
) {
    suspend operator fun invoke(leadId: Long, status: LeadStatus) =
        repository.updateStatus(leadId, status)
}
