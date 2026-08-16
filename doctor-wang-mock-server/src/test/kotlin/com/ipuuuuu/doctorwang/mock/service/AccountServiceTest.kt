package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.AuthenticatedUser
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportFileEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.AppUserRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.Optional

/**
 * 账号注销（AccountService）：删除报告文件 + 尽力清理向量索引 + 级联删除用户。
 */
class AccountServiceTest {

    private val appRepo = mock(AppUserRepository::class.java)
    private val reportRepo = mock(MedicalReportRepository::class.java)
    private val fileRepo = mock(MedicalReportFileRepository::class.java)
    private val storage = mock(FileStorageService::class.java)
    private val aiClient = mock(AiReportClient::class.java)

    private val user = AppUserEntity(id = 7, userId = "u7", account = "del@test.local", passwordHash = "h", name = "删", age = 30, height = 170, weight = 65f)

    @BeforeEach
    fun setUp() { AuthContext.set(AuthenticatedUser("tok", "sid", user)) }
    @AfterEach
    fun tearDown() { AuthContext.clear() }

    @Test
    fun `deletes report files and vector indexes then removes user`() {
        val report = MedicalReportEntity(id = 9, user = user, title = "体检", reportDate = "", reportType = "",
            summary = "s", status = "COMPLETED")
        val file = MedicalReportFileEntity(report = report, storageKey = "k/9", originalFileName = "r.pdf",
            mimeType = "application/pdf", fileSize = 1L, sha256 = "s")
        `when`(appRepo.findById(7L)).thenReturn(Optional.of(user))
        `when`(reportRepo.findAllByUserOrderByCreatedAtDescIdDesc(user)).thenReturn(listOf(report))
        `when`(fileRepo.findByReport(report)).thenReturn(file)

        AccountService(appRepo, reportRepo, fileRepo, storage, aiClient).deleteAccount()

        verify(storage).delete("k/9")
        verify(aiClient).deleteIndex("report:7:9")
        verify(appRepo).delete(user)
    }

    @Test
    fun `delete proceeds when ai index cleanup fails`() {
        val report = MedicalReportEntity(id = 9, user = user, title = "体检", reportDate = "", reportType = "",
            summary = "s", status = "COMPLETED")
        `when`(appRepo.findById(7L)).thenReturn(Optional.of(user))
        `when`(reportRepo.findAllByUserOrderByCreatedAtDescIdDesc(user)).thenReturn(listOf(report))
        `when`(fileRepo.findByReport(report)).thenReturn(null)
        `when`(aiClient.deleteIndex("report:7:9")).thenThrow(RuntimeException("ai down"))

        AccountService(appRepo, reportRepo, fileRepo, storage, aiClient).deleteAccount()

        verify(appRepo).delete(user)
    }
}
