package com.ipuuuuu.doctorwang.mock.integration

import com.ipuuuuu.doctorwang.mock.persistence.entity.*
import com.ipuuuuu.doctorwang.mock.persistence.repository.*
import com.ipuuuuu.doctorwang.mock.service.CompensationCleanupService
import com.ipuuuuu.doctorwang.mock.service.PasswordHasher
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PostgresIntegrationMatrixTest {
    @Autowired lateinit var flyway: Flyway
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var users: AppUserRepository
    @Autowired lateinit var reports: MedicalReportRepository
    @Autowired lateinit var jobs: ReportProcessingJobRepository
    @Autowired lateinit var idempotency: ReportUploadIdempotencyRepository
    @Autowired lateinit var cleanup: CompensationCleanupService
    @Autowired lateinit var cleanupTasks: ReportCleanupTaskRepository
    @Autowired lateinit var passwordHasher: PasswordHasher

    @Test
    fun `flyway v1 through v8 and hibernate validation start successfully`() {
        val versions = flyway.info().applied().map { it.version?.version }
        assertTrue((1..8).all { it.toString() in versions })
        assertEquals(1, jdbc.queryForObject("select count(*) from app_user where account = 'demo@doctorwang.app'", Int::class.java) ?: fail("demo user must exist"))
        val demoHash = jdbc.queryForObject("select password_hash from app_user where account = 'demo@doctorwang.app'", String::class.java)
            ?: fail("demo password hash must exist")
        assertTrue(demoHash.startsWith("${'$'}2b${'$'}12${'$'}"), "all migrated passwords must be BCrypt cost 12")
        assertTrue(passwordHasher.matches("123456", demoHash), "the pre-migration credential must remain usable")
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'app_user' and column_name = 'password'", Int::class.java) ?: fail("legacy password column check returned null"))
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_name = 'report_cleanup_task'", Int::class.java) ?: fail("report cleanup table must exist"))
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'app_user' and column_name = 'password_hash' and is_nullable = 'NO'", Int::class.java) ?: fail("password_hash must be non-null"))
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'user_session' and column_name = 'token_hash' and character_maximum_length = 64", Int::class.java) ?: fail("token_hash must be varchar(64)"))
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'user_session' and column_name = 'jti' and is_nullable = 'NO'", Int::class.java) ?: fail("jti must be non-null"))
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.table_constraints where table_name = 'user_session' and constraint_name = 'uk_user_session_jti' and constraint_type = 'UNIQUE'", Int::class.java) ?: fail("JTI uniqueness constraint must exist"))
    }

    @Test
    fun `idempotency replay conflict expiry and user isolation are persisted`() {
        val first = users.findByAccount("demo@doctorwang.app")!!
        val second = users.save(AppUserEntity(userId = "integration-user", account = "integration@example.test", passwordHash = "{noop}x", name = "Integration", age = 30, height = 170, weight = 70f))
        val report = reports.save(MedicalReportEntity(user = first, title = "idempotency", reportDate = "", reportType = "", summary = "", status = "PROCESSING"))
        val job = jobs.save(ReportProcessingJobEntity(UUID.randomUUID(), report, status = "STORED", stage = "STORED", traceId = "integration", nextAttemptAt = OffsetDateTime.now()))
        idempotency.save(ReportUploadIdempotencyEntity(user = first, idempotencyKey = "replay-key", requestDigest = "a".repeat(64), report = report, processingJobId = job.id, responseSnapshot = "{}", expiresAt = OffsetDateTime.now().plusHours(1)))
        assertNotNull(idempotency.findByUserAndIdempotencyKeyAndExpiresAtAfter(first, "replay-key", OffsetDateTime.now()))
        assertNull(idempotency.findByUserAndIdempotencyKeyAndExpiresAtAfter(second, "replay-key", OffsetDateTime.now()))
        assertThrows(Exception::class.java) { idempotency.save(ReportUploadIdempotencyEntity(user = first, idempotencyKey = "replay-key", requestDigest = "b".repeat(64), report = report, processingJobId = job.id, responseSnapshot = "{}", expiresAt = OffsetDateTime.now().plusHours(1))); idempotency.flush() }
        val expired = idempotency.save(ReportUploadIdempotencyEntity(user = first, idempotencyKey = "expired-key", requestDigest = "c".repeat(64), report = report, processingJobId = job.id, responseSnapshot = "{}", expiresAt = OffsetDateTime.now().minusMinutes(1)))
        assertNull(idempotency.findByUserAndIdempotencyKeyAndExpiresAtAfter(first, expired.idempotencyKey, OffsetDateTime.now()))
        assertTrue(cleanup.cleanupExpiredIdempotency() >= 1)
    }

    @Test
    fun `cleanup task runs and report deletion cascades related metadata`() {
        val user = users.findByAccount("demo@doctorwang.app")!!
        val report = reports.save(MedicalReportEntity(user = user, title = "cleanup", reportDate = "", reportType = "", summary = "", status = "PROCESSING"))
        val job = jobs.save(ReportProcessingJobEntity(UUID.randomUUID(), report, status = "STORED", stage = "STORED", traceId = "cleanup", nextAttemptAt = OffsetDateTime.now()))
        idempotency.save(ReportUploadIdempotencyEntity(user = user, idempotencyKey = "cleanup-key-${UUID.randomUUID()}", requestDigest = "d".repeat(64), report = report, processingJobId = job.id, responseSnapshot = "{}", expiresAt = OffsetDateTime.now().plusHours(1)))
        cleanup.record("cleanup-test/orphan.bin", "INTEGRATION")
        assertTrue(cleanup.cleanupPending() >= 1)
        reports.delete(report)
        reports.flush()
        assertTrue(idempotency.findAll().none { it.report.id == report.id })
        assertTrue(cleanupTasks.findAll().any { it.status == "COMPLETED" })
    }

    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg16")
        private val storageRoot = Files.createTempDirectory("doctor-wang-it-storage")
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("app.file-storage.root") { storageRoot.toString() }
            registry.add("app.report-processing.worker-enabled") { "false" }
        }
    }
}
