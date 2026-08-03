package com.ipuuuuu.doctorwang.mock.service

import jakarta.annotation.PostConstruct
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class DatabaseService(
    @Value("\${DOCTOR_WANG_DATA_DIR:/opt/doctor-wang/data}") private val dataDir: String,
) {
    private lateinit var jdbcUrl: String

    @PostConstruct
    fun initialize() {
        Files.createDirectories(Path.of(dataDir))
        jdbcUrl = "jdbc:sqlite:${Path.of(dataDir, "doctor-wang.db").toAbsolutePath()}"
        connection { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, account TEXT NOT NULL UNIQUE, password_salt TEXT NOT NULL, password_hash TEXT NOT NULL, created_at TEXT NOT NULL)")
                statement.execute("CREATE TABLE IF NOT EXISTS access_tokens (token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), expires_at TEXT NOT NULL, created_at TEXT NOT NULL)")
                statement.execute("CREATE TABLE IF NOT EXISTS health_profiles (user_id TEXT PRIMARY KEY REFERENCES users(id), name TEXT NOT NULL, age INTEGER, height INTEGER, weight REAL, history_diseases TEXT NOT NULL DEFAULT '[]', updated_at TEXT NOT NULL)")
                statement.execute("CREATE TABLE IF NOT EXISTS medical_reports (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL REFERENCES users(id), original_name TEXT NOT NULL, storage_path TEXT NOT NULL, media_type TEXT NOT NULL, size_bytes INTEGER NOT NULL, status TEXT NOT NULL, extracted_text TEXT NOT NULL DEFAULT '', analysis_summary TEXT NOT NULL DEFAULT '', findings_json TEXT NOT NULL DEFAULT '[]', suggestions_json TEXT NOT NULL DEFAULT '[]', failed_reason TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL, updated_at TEXT NOT NULL DEFAULT '')")
                statement.execute("CREATE TABLE IF NOT EXISTS chat_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL REFERENCES users(id), role TEXT NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL)")
                addColumnIfMissing(statement, "medical_reports", "extracted_text", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(statement, "medical_reports", "analysis_summary", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(statement, "medical_reports", "findings_json", "TEXT NOT NULL DEFAULT '[]'")
                addColumnIfMissing(statement, "medical_reports", "suggestions_json", "TEXT NOT NULL DEFAULT '[]'")
                addColumnIfMissing(statement, "medical_reports", "failed_reason", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(statement, "medical_reports", "updated_at", "TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    fun <T> connection(block: (Connection) -> T): T = DriverManager.getConnection(jdbcUrl).use(block)

    private fun addColumnIfMissing(statement: java.sql.Statement, table: String, column: String, definition: String) {
        runCatching {
            statement.execute("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }
}
