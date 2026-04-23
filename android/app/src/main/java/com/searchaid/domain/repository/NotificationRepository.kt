package com.searchaid.domain.repository

/**
 * Abstraction for push notifications (FCM). Offline mode uses
 * local notifications only; when Firebase is configured, swap
 * to FcmNotificationRepository via DI.
 */
interface NotificationRepository {
    suspend fun subscribeToCase(caseId: Long)
    suspend fun unsubscribeFromCase(caseId: Long)
    suspend fun sendAlert(caseId: Long, title: String, body: String)
    fun getToken(): String?
}
