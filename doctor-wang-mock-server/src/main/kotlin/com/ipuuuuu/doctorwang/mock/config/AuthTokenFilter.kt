package com.ipuuuuu.doctorwang.mock.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import com.ipuuuuu.doctorwang.mock.service.JwtTokenService
import com.ipuuuuu.doctorwang.mock.model.ApiError
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.OffsetDateTime

@Component
class AuthTokenFilter(
    private val userSessionRepository: UserSessionRepository,
    private val jwtTokenService: JwtTokenService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = applicationPath(request)
        return (request.method == "POST" && (path == "/api/login" || path == "/api/register")) ||
            (request.method == "GET" && (path == "/healthz" || path == "/"))
    }

    private fun applicationPath(request: HttpServletRequest): String {
        // In a real servlet container `servletPath` is the dispatcher path, but
        // MockMvc (and requests behind a context path) may expose the route via
        // `requestURI` instead. Normalize to the application-relative URI so
        // public routes are evaluated consistently without broadening the list.
        val contextPath = request.contextPath.orEmpty()
        val path = request.requestURI
            ?.removePrefix(contextPath)
            ?.ifEmpty { "/" }
            ?: request.servletPath
        return path
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.getHeader(HEADER_AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()

        if (token.isNullOrBlank()) {
            writeUnauthorized(response)
            return
        }

        val jwt = try {
            jwtTokenService.verify(token)
        } catch (error: ApiException) {
            if (error.code == "AUTH_CONFIGURATION_INVALID") {
                writeServiceUnavailable(response)
                return
            }
            null
        } catch (_: Exception) {
            null
        }
        val session = jwt?.let { userSessionRepository.findByJti(it.sessionId) }
        if (jwt == null || session == null || session.user.id != jwt.userDatabaseId || session.user.userId != jwt.subject ||
            session.tokenHash != jwtTokenService.tokenHash(token) || session.expiresAt.isBefore(OffsetDateTime.now())
        ) {
            writeUnauthorized(response)
            return
        }

        // A repeated logout must be idempotent. A revoked bearer is never
        // allowed to reach any other route, but may invoke this no-content,
        // user-scoped endpoint after all JWT/session binding checks pass.
        if (session.revokedAt != null && !(request.method == "POST" && applicationPath(request) == "/api/logout")) {
            writeUnauthorized(response)
            return
        }

        try {
            AuthContext.set(AuthenticatedUser(sessionToken = token, sessionId = jwt.sessionId, user = session.user))
            filterChain.doFilter(request, response)
        } finally {
            AuthContext.clear()
        }
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.characterEncoding = Charsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.use {
            it.write(objectMapper.writeValueAsString(ApiError("AUTHENTICATION_REQUIRED", "未登录或登录已失效", TraceIdContext.get())))
        }
    }

    private fun writeServiceUnavailable(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
        response.characterEncoding = Charsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.use {
            it.write(objectMapper.writeValueAsString(ApiError("AUTH_CONFIGURATION_INVALID", "认证服务配置不可用", TraceIdContext.get())))
        }
    }

    companion object {
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
