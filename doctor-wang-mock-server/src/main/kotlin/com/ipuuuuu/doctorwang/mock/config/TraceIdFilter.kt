package com.ipuuuuu.doctorwang.mock.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val traceId = request.getHeader("X-Trace-Id")?.takeIf { it.length in 1..128 } ?: UUID.randomUUID().toString()
        response.setHeader("X-Trace-Id", traceId)
        TraceIdContext.set(traceId)
        try { chain.doFilter(request, response) } finally { TraceIdContext.clear() }
    }
}

object TraceIdContext {
    private val current = ThreadLocal<String?>()
    fun set(value: String) = current.set(value)
    fun get(): String = current.get() ?: UUID.randomUUID().toString()
    fun clear() = current.remove()
}
