package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.model.LoginResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AccountService(private val database: DatabaseService) {
    private val random = SecureRandom()

    fun register(rawAccount: String, password: String): LoginResponse {
        val account = validate(rawAccount, password)
        val id = "u" + randomToken().take(18)
        val salt = ByteArray(16).also(random::nextBytes)
        val now = Instant.now().toString()
        try {
            database.connection { connection ->
                connection.prepareStatement("INSERT INTO users(id, account, password_salt, password_hash, created_at) VALUES (?, ?, ?, ?, ?)").use { statement ->
                    statement.setString(1, id); statement.setString(2, account)
                    statement.setString(3, Base64.getEncoder().encodeToString(salt))
                    statement.setString(4, Base64.getEncoder().encodeToString(hash(password, salt)))
                    statement.setString(5, now); statement.executeUpdate()
                }
                connection.prepareStatement("INSERT INTO health_profiles(user_id, name, history_diseases, updated_at) VALUES (?, ?, '[]', ?)").use { statement ->
                    statement.setString(1, id); statement.setString(2, account.substringBefore('@')); statement.setString(3, now); statement.executeUpdate()
                }
            }
        } catch (error: java.sql.SQLException) {
            if (error.message.orEmpty().contains("UNIQUE", true)) fail(HttpStatus.CONFLICT, "账号已注册，请直接登录")
            throw error
        }
        return issueToken(id)
    }

    fun login(rawAccount: String, password: String): LoginResponse {
        val account = validate(rawAccount, password)
        val user = database.connection { connection ->
            connection.prepareStatement("SELECT id, password_salt, password_hash FROM users WHERE account = ?").use { statement ->
                statement.setString(1, account); statement.executeQuery().use { result ->
                    if (!result.next()) null else StoredUser(result.getString("id"), result.getString("password_salt"), result.getString("password_hash"))
                }
            }
        } ?: fail(HttpStatus.UNAUTHORIZED, "账号或密码错误")
        if (!MessageDigest.isEqual(hash(password, Base64.getDecoder().decode(user.salt)), Base64.getDecoder().decode(user.hash))) fail(HttpStatus.UNAUTHORIZED, "账号或密码错误")
        return issueToken(user.id)
    }

    fun requireUserId(authorization: String?): String {
        val token = authorization?.removePrefix("Bearer ")?.trim().orEmpty()
        if (token.isBlank() || authorization?.startsWith("Bearer ") != true) fail(HttpStatus.UNAUTHORIZED, "请先登录")
        val now = Instant.now().toString()
        return database.connection { connection ->
            connection.prepareStatement("SELECT user_id FROM access_tokens WHERE token_hash = ? AND expires_at > ?").use { statement ->
                statement.setString(1, sha256(token)); statement.setString(2, now); statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
            }
        } ?: fail(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录")
    }

    private fun issueToken(userId: String): LoginResponse {
        val token = randomToken(); val now = Instant.now(); val expiry = now.plus(30, ChronoUnit.DAYS).toString()
        database.connection { connection ->
            connection.prepareStatement("INSERT INTO access_tokens(token_hash, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)").use { statement ->
                statement.setString(1, sha256(token)); statement.setString(2, userId); statement.setString(3, expiry); statement.setString(4, now.toString()); statement.executeUpdate()
            }
        }
        return LoginResponse(token, userId)
    }

    private fun validate(raw: String, password: String): String {
        val account = raw.trim().lowercase()
        if (!account.matches(Regex("[a-z0-9@._+-]{3,64}"))) fail(HttpStatus.BAD_REQUEST, "账号仅支持手机号、邮箱或字母数字字符")
        if (password.length !in 8..128) fail(HttpStatus.BAD_REQUEST, "密码长度必须为 8 到 128 位")
        return account
    }
    private fun randomToken() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun sha256(value: String) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
    private fun hash(password: String, salt: ByteArray) = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, 310_000, 256)).encoded
    private fun fail(status: HttpStatus, message: String): Nothing = throw ResponseStatusException(status, message)
    private data class StoredUser(val id: String, val salt: String, val hash: String)
}
