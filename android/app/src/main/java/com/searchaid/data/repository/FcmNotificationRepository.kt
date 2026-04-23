package com.searchaid.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import com.searchaid.domain.repository.NotificationRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM notification implementation. To enable:
 * 1. Add google-services.json to app/
 * 2. In RepositoryModule, change bindNotificationRepository to use this class
 */
@Singleton
class FcmNotificationRepository @Inject constructor(
    private val messaging: FirebaseMessaging,
) : NotificationRepository {

    override suspend fun subscribeToCase(caseId: Long) {
        messaging.subscribeToTopic("case_$caseId").await()
    }

    override suspend fun unsubscribeFromCase(caseId: Long) {
        messaging.unsubscribeFromTopic("case_$caseId").await()
    }

    override suspend fun sendAlert(caseId: Long, title: String, body: String) {
        // FCM send requires server-side Cloud Functions
        // Client subscribes to topics; server sends to topics
        // This is a placeholder — real sends happen server-side
    }

    override fun getToken(): String? {
        return try {
            // Token retrieval is async, but for synchronous check return cached
            null
        } catch (_: Exception) {
            null
        }
    }
}
