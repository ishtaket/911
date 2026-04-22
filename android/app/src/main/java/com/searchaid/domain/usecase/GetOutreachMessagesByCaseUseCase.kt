package com.searchaid.domain.usecase

import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.repository.OutreachMessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetOutreachMessagesByCaseUseCase @Inject constructor(
    private val repository: OutreachMessageRepository,
) {
    operator fun invoke(caseId: Long): Flow<List<OutreachMessage>> =
        repository.observeByCase(caseId)
}
