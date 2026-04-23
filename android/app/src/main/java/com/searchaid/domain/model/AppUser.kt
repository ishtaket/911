package com.searchaid.domain.model

data class AppUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val isAnonymous: Boolean,
)

enum class AuthStatus {
    AUTHENTICATED,
    UNAUTHENTICATED,
    LOADING,
}
