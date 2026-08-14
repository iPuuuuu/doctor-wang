package com.ipuuuuu.doctorwang.mock.config

import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity

data class AuthenticatedUser(
    val sessionToken: String,
    val sessionId: String,
    val user: AppUserEntity,
)

object AuthContext {
    private val currentUser = ThreadLocal<AuthenticatedUser?>()

    fun set(authenticatedUser: AuthenticatedUser) {
        currentUser.set(authenticatedUser)
    }

    fun get(): AuthenticatedUser? = currentUser.get()

    fun clear() {
        currentUser.remove()
    }
}
