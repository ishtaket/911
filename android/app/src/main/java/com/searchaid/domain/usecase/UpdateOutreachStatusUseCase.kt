package com.searchaid.domain.usecase

import com.searchaid.domain.model.OutreachStatus
import com.searchaid.domain.repository.OutreachMessageRepository
import javax.inject.Inject

class UpdateOutreachStatusUseCase @Inject constructor(
    private val repository: OutreachMessageRepository,
) {
    suspend operator fun invoke(id: Long, status: OutreachStatus) =
        repository.updateStatus(id, status)
}
