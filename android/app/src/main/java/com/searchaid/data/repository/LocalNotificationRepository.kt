package com.searchaid.data.repository

import com.searchaid.domain.repository.NotificationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local notification stub. No push capability — alerts are local only.
 * Replace with FcmNotificationRepository when Firebase is configured.
 */
@Singleton
class LocalNotificationRepository @Inject constructor() : NotificationRepository {

    private val subscribedCases = mutableSetOf<Long>()

    override suspend fun subscribeToCase(caseId: Long) {
        subscribedCases.add(caseId)
    }

    override suspend fun unsubscribeFromCase(caseId: Long) {
        subscribedCases.remove(caseId)
    }

    override suspend fun sendAlert(caseId: Long, title: String, body: String) {
        // No-op in local mode — no push delivery
        // Real implementation would call FCM HTTP API or use Firebase Cloud Functions
    }

    override fun getToken(): String? = null // No FCM token in offline mode
}
