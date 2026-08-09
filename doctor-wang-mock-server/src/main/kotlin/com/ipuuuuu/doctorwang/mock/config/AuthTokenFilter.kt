package com.ipuuuuu.doctorwang.mock.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
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
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return path == "/api/login" || path == "/healthz" || path == "/"
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

        val session = userSessionRepository.findByToken(token)
        if (session == null || session.expiresAt.isBefore(OffsetDateTime.now())) {
            writeUnauthorized(response)
            return
        }

        try {
            AuthContext.set(AuthenticatedUser(sessionToken = token, user = session.user))
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

    companion object {
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
