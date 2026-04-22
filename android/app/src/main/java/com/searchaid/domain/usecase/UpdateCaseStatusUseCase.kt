package com.searchaid.domain.usecase

import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.repository.MissingCaseRepository
import javax.inject.Inject

class UpdateCaseStatusUseCase @Inject constructor(
    private val repository: MissingCaseRepository,
) {
    suspend operator fun invoke(caseId: Long, status: CaseStatus) =
        repository.updateStatus(caseId, status)
}
