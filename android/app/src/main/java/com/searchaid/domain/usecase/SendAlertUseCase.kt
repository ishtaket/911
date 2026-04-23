package com.searchaid.domain.usecase

import com.searchaid.domain.repository.NotificationRepository
import javax.inject.Inject

class SendAlertUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {
    suspend operator fun invoke(caseId: Long, title: String, body: String) =
        notificationRepository.sendAlert(caseId, title, body)
}
