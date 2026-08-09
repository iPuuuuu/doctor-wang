package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReportNamespaceAuthorizerTest {
    @Test fun `builds isolated report namespace`() = assertEquals("report:7:42", ReportNamespaceAuthorizer.forUser(7, 42))
    @Test fun `rejects cross user namespace`() = assertThrows(ApiException::class.java) { ReportNamespaceAuthorizer.requireOwned("report:8:42", 7, 42) }
}
