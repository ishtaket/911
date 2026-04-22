package com.searchaid.domain.usecase

import com.searchaid.domain.model.AuditLogEntry
import com.searchaid.domain.repository.AuditLogRepository
import javax.inject.Inject

class LogActionUseCase @Inject constructor(
    private val repository: AuditLogRepository,
) {
    suspend operator fun invoke(
        action: String,
        caseId: Long? = null,
        details: String? = null,
        operatorId: String? = null,
    ) {
        repository.log(
            AuditLogEntry(
                caseId = caseId,
                action = action,
                details = details,
                operatorId = operatorId,
            )
        )
    }
}
