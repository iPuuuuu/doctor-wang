package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.persistence.repository.AppUserRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 账号注销（隐私合规）：删除用户报告原文件（磁盘）、尽力清理向量索引，
 * 再删除用户行（数据库外键级联清理会话/聊天/健康档案/报告记录/审计）。
 * 不可恢复——App 端需二次确认。
 */
@Service
class AccountService(
    private val appUserRepository: AppUserRepository,
    private val reports: MedicalReportRepository,
    private val files: MedicalReportFileRepository,
    private val storage: FileStorageService,
    private val aiClient: AiReportClient,
) {
    private val log = LoggerFactory.getLogger(AccountService::class.java)

    @Transactional
    fun deleteAccount() {
        val authenticated = AuthContext.get() ?: throw SecurityException("未登录或登录已失效")
        val user = appUserRepository.findById(authenticated.user.id)
            .orElseThrow { SecurityException("用户不存在") }

        // 1. 删除报告原文件（磁盘）+ 尽力清理向量索引（AI 服务不可用不阻塞注销）
        reports.findAllByUserOrderByCreatedAtDescIdDesc(user).forEach { report ->
            runCatching { files.findByReport(report)?.let { storage.delete(it.storageKey) } }
                .onFailure { log.warn("delete report file failed: {}", it.message) }
            runCatching { aiClient.deleteIndex("report:${user.id}:${report.id}") }
                .onFailure { log.warn("delete vector index failed: {}", it.message) }
        }

        // 2. 删除用户行：级联清理 user_session / chat_message / health_profile / medical_report / audit 等
        appUserRepository.delete(user)
    }
}
