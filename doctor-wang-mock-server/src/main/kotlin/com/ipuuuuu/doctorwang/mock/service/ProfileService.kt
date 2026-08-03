package com.ipuuuuu.doctorwang.mock.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.model.UserProfileResponse
import com.ipuuuuu.doctorwang.mock.model.UpdateProfileRequest
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class ProfileService(private val database: DatabaseService, private val mapper: ObjectMapper) {
    fun profile(userId: String): UserProfileResponse = database.connection { connection ->
        connection.prepareStatement("SELECT u.id, h.name, h.age, h.height, h.weight, h.history_diseases FROM users u JOIN health_profiles h ON h.user_id = u.id WHERE u.id = ?").use { statement ->
            statement.setString(1, userId); statement.executeQuery().use { row ->
                if (!row.next()) throw IllegalStateException("用户健康档案不存在")
                UserProfileResponse(row.getString("id"), row.getString("name"), row.getIntOrNull("age"), row.getIntOrNull("height"), row.getFloatOrNull("weight"), mapper.readValue(row.getString("history_diseases"), mapper.typeFactory.constructCollectionType(List::class.java, String::class.java)), emptyList())
            }
        }
    }
    fun update(userId: String, request: UpdateProfileRequest): UserProfileResponse {
        require(request.name.trim().length in 1..50) { "姓名长度应为 1 到 50 位" }
        require(request.age in 0..150 && request.height in 0..300 && request.weight in 0f..500f) { "健康档案数据不合法" }
        database.connection { connection -> connection.prepareStatement("UPDATE health_profiles SET name=?, age=?, height=?, weight=?, history_diseases=?, updated_at=? WHERE user_id=?").use { s ->
            s.setString(1, request.name.trim()); s.setInt(2, request.age); s.setInt(3, request.height); s.setFloat(4, request.weight); s.setString(5, mapper.writeValueAsString(request.historyDiseases.distinct())); s.setString(6, Instant.now().toString()); s.setString(7, userId); s.executeUpdate()
        }}
        return profile(userId)
    }
    private fun java.sql.ResultSet.getIntOrNull(column: String) = getInt(column).let { if (wasNull()) 0 else it }
    private fun java.sql.ResultSet.getFloatOrNull(column: String) = getFloat(column).let { if (wasNull()) 0f else it }
}
