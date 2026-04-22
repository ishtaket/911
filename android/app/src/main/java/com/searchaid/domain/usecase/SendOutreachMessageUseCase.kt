package com.searchaid.domain.usecase

import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.repository.OutreachMessageRepository
import javax.inject.Inject

class SendOutreachMessageUseCase @Inject constructor(
    private val repository: OutreachMessageRepository,
) {
    suspend operator fun invoke(message: OutreachMessage): Long = repository.add(message)
}
