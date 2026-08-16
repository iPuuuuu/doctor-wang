package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.AiAuditEventEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AiAuditEventRepository : JpaRepository<AiAuditEventEntity, Long>
