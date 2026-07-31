package com.ipuuuuu.doctorwang.data.repository

import android.net.Uri
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.UserProfile

interface DoctorWangRepository {
    suspend fun isLoggedIn(): Boolean
    suspend fun login(account: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun getProfile(): UserProfile
    suspend fun getHealthProfile(): UserProfile
    suspend fun getHistoryMessages(): List<Message>
    suspend fun sendMessage(content: String, currentMessages: List<Message>): Result<Message>
    suspend fun uploadReport(uri: Uri?): Result<Long>
    suspend fun getReport(reportId: Long): MedicalReport
}
