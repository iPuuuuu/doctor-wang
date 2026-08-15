package com.ipuuuuu.doctorwang.data.repository

import android.net.Uri
import com.ipuuuuu.doctorwang.data.api.ReportListItem
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.UserProfile
import okhttp3.ResponseBody

data class ReportPage(
    val items: List<ReportListItem>,
    val totalPages: Int,
)

interface DoctorWangRepository {
    suspend fun isLoggedIn(): Boolean
    suspend fun login(account: String, password: String): Result<Unit>
    suspend fun register(account: String, password: String, name: String, age: Int, height: Int, weight: Float): Result<Unit>
    suspend fun logout()
    suspend fun clearSession()
    suspend fun getProfile(): UserProfile
    suspend fun updateProfile(name: String?, age: Int?, height: Int?, weight: Float?): Result<UserProfile>
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit>
    suspend fun getHealthProfile(): UserProfile
    suspend fun getHistoryMessages(): List<Message>
    suspend fun sendMessage(content: String, currentMessages: List<Message>): Result<Message>
    suspend fun sendAnxietyMessage(content: String, currentMessages: List<Message>): Result<Message>
    suspend fun uploadReport(uri: Uri?): Result<Long>
    suspend fun getReport(reportId: Long): MedicalReport
    suspend fun getReportStatus(reportId: Long): MedicalReport
    suspend fun listReports(page: Int = 0, size: Int = 20): Result<ReportPage>
    suspend fun downloadReportFile(reportId: Long): Result<ResponseBody>
    suspend fun retryReport(reportId: Long): Result<MedicalReport>
    suspend fun deleteReport(reportId: Long): Result<Unit>
}
