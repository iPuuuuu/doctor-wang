package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException

object ReportNamespaceAuthorizer {
    fun forUser(userId: Long, reportId: Long) = "report:$userId:$reportId"
    fun requireOwned(namespace: String, userId: Long, reportId: Long) {
        if (namespace != forUser(userId, reportId)) throw ApiException("NAMESPACE_FORBIDDEN", "报告检索范围无权访问")
    }
}
