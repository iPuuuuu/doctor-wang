package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.model.HealthRecordResponse
import com.ipuuuuu.doctorwang.mock.model.LoginResponse
import com.ipuuuuu.doctorwang.mock.model.UserProfileResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.UserSessionEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.AppUserRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileDiseaseRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileRecordRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class BackendService(
    private val passwordHasher: PasswordHasher,
    private val jwtTokenService: JwtTokenService,
    private val appUserRepository: AppUserRepository,
    private val userSessionRepository: UserSessionRepository,
    private val healthProfileDiseaseRepository: HealthProfileDiseaseRepository,
    private val healthProfileRecordRepository: HealthProfileRecordRepository,
) {
    @Transactional
    fun login(account: String, password: String): LoginResponse {
        val user = appUserRepository.findByAccount(account.trim())
            ?: throw ApiException("AUTHENTICATION_FAILED", "账号或密码错误")
        if (!passwordHasher.matches(password, user.passwordHash)) {
            throw ApiException("AUTHENTICATION_FAILED", "账号或密码错误")
        }

        val (token, jwt) = jwtTokenService.issue(user.userId, user.id)
        userSessionRepository.save(
            UserSessionEntity(
                jti = jwt.sessionId,
                tokenHash = jwtTokenService.tokenHash(token),
                user = user,
                expiresAt = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(jwt.expiresAtEpochSeconds), java.time.ZoneOffset.UTC),
            ),
        )
        return LoginResponse(token = token, userId = user.userId)
    }

    @Transactional
    fun register(account: String, password: String, name: String, age: Int, height: Int, weight: Float): LoginResponse {
        val trimmed = account.trim()
        require(trimmed.isNotBlank()) { "账号不能为空" }
        require(password.length >= 6) { "密码长度不能少于6位" }
        require(name.isNotBlank()) { "姓名不能为空" }
        require(age in 1..150) { "年龄格式不正确" }
        require(height in 30..250) { "身高格式不正确" }
        require(weight in 1f..500f) { "体重格式不正确" }
        if (appUserRepository.findByAccount(trimmed) != null) {
            throw ApiException("ACCOUNT_EXISTS", "该账号已被注册")
        }
        val userId = "u${OffsetDateTime.now().toInstant().toEpochMilli().toString().takeLast(8)}${(1000..9999).random()}"
        val user = appUserRepository.save(
            AppUserEntity(
                userId = userId,
                account = trimmed,
                passwordHash = passwordHasher.hash(password),
                name = name.trim(),
                age = age,
                height = height,
                weight = weight,
            ),
        )
        val (token, jwt) = jwtTokenService.issue(user.userId, user.id)
        userSessionRepository.save(
            UserSessionEntity(
                jti = jwt.sessionId,
                tokenHash = jwtTokenService.tokenHash(token),
                user = user,
                expiresAt = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(jwt.expiresAtEpochSeconds), java.time.ZoneOffset.UTC),
            ),
        )
        return LoginResponse(token = token, userId = user.userId)
    }

    @Transactional
    fun updateProfile(name: String?, age: Int?, height: Int?, weight: Float?): UserProfileResponse {
        val user = requireCurrentUser()
        val managed = appUserRepository.findById(user.id).orElseThrow { SecurityException("用户不存在") }
        name?.takeIf { it.isNotBlank() }?.let { managed.name = it.trim() }
        age?.let { require(it in 1..150) { "年龄格式不正确" }; managed.age = it }
        height?.let { require(it in 30..250) { "身高格式不正确" }; managed.height = it }
        weight?.let { require(it in 1f..500f) { "体重格式不正确" }; managed.weight = it }
        managed.updatedAt = OffsetDateTime.now()
        return managed.toUserProfileResponse()
    }

    @Transactional
    fun changePassword(oldPassword: String, newPassword: String) {
        val authenticated = AuthContext.get() ?: throw SecurityException("未登录或登录已失效")
        val user = authenticated.user
        require(newPassword.length >= 6) { "密码长度不能少于6位" }
        if (!passwordHasher.matches(oldPassword, user.passwordHash)) {
            throw ApiException("PASSWORD_MISMATCH", "原密码错误")
        }
        val managed = appUserRepository.findById(user.id).orElseThrow { SecurityException("用户不存在") }
        managed.passwordHash = passwordHasher.hash(newPassword)
        managed.updatedAt = OffsetDateTime.now()
        // Revoke all other sessions for this user except the current one (ADR-0003)
        userSessionRepository.revokeAllExcept(
            userId = user.id,
            currentJti = authenticated.sessionId,
            revokedAt = OffsetDateTime.now(),
        )
    }

    @Transactional
    fun logout() {
        val authenticated = AuthContext.get() ?: throw SecurityException("未登录或登录已失效")
        // The filter binds this JTI to the bearer token and authenticated user.
        // A zero-row update is intentionally successful: it represents a retry
        // after this same session has already been revoked.
        userSessionRepository.revokeByJtiAndUserId(
            jti = authenticated.sessionId,
            userId = authenticated.user.id,
            revokedAt = OffsetDateTime.now(),
        )
    }

    @Transactional(readOnly = true)
    fun getProfile(): UserProfileResponse = requireCurrentUser().toUserProfileResponse()

    @Transactional(readOnly = true)
    fun getHealthProfile(): UserProfileResponse = requireCurrentUser().toUserProfileResponse()

    private fun requireCurrentUser(): AppUserEntity {
        return AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效")
    }

    private fun AppUserEntity.toUserProfileResponse(): UserProfileResponse {
        val diseases = healthProfileDiseaseRepository.findAllByUserOrderByIdAsc(this).map { it.diseaseName }
        val records = healthProfileRecordRepository.findAllByUserOrderByIdAsc(this).map {
            HealthRecordResponse(date = it.recordDate, type = it.recordType)
        }
        return UserProfileResponse(
            userId = userId,
            name = name,
            age = age,
            height = height,
            weight = weight,
            historyDiseases = diseases,
            records = records,
        )
    }
}
