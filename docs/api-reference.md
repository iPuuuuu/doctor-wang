# Doctor-Wang API Reference

**Base URL (debug):** `http://10.0.2.2:8080` (emulator) / `http://<LAN_IP>:8080` (device)
**Base URL (release):** `https://<deployed-host>/`
**Auth:** `Authorization: Bearer <JWT>` (except public endpoints)
**Content-Type:** `application/json` (except multipart upload)

---

## Public Endpoints

### POST /api/login
Login with account + password.

**Request:** `{"account": "demo@doctorwang.app", "password": "123456"}`
**Response 200:** `{"token": "jwt...", "userId": "u1001"}`
**Errors:** 400 (empty fields), 401 (AUTHENTICATION_FAILED), 429 (LOGIN_LOCKED 暴力破解临时锁定)

### POST /api/register
Register new account. Auto-login on success.

**Request:** `{"account": "new@test.com", "password": "password1", "name": "张三", "age": 25, "height": 170, "weight": 70.0}`
**Response 200:** `{"token": "jwt...", "userId": "u2002"}`
**Errors:** 400 (validation), 409 (ACCOUNT_EXISTS)

### GET /healthz
**Response 200:** `{"status": "ok"}`

---

## Profile Endpoints (Bearer required)

### GET /api/profile
**Response 200:** `{"userId": "u1001", "name": "王先生", "age": 25, "height": 170, "weight": 73.0, "historyDiseases": ["轻度脂肪肝"], "records": [{"date": "2026-06", "type": "腹部超声"}]}`

### PUT /api/profile
Partial update. Only non-null fields are updated.

**Request:** `{"name": "新名字", "weight": 70.0}`
**Response 200:** (updated UserProfileResponse)

### GET /api/health/profile
Same response shape as GET /api/profile. Includes disease and record history.

### DELETE /api/account
注销当前账号（永久删除：健康档案、全部报告、聊天记录、会话；报告原文件与向量索引一并清理）。不可恢复。

**Response 204:** no content
**Errors:** 401 (未登录)

### POST /api/password/change
**Request:** `{"oldPassword": "current", "newPassword": "newpass123"}`
**Response 204:** (no content)
**Errors:** 400 (short password), 401 (PASSWORD_MISMATCH)

---

## Chat Endpoints (Bearer required)

### GET /api/chat/history
**Response 200:** `[{"id": "1", "content": "最近胃疼", "role": "user", "timestamp": 1723000000000}, ...]`

### POST /api/chat
Send message. When `AI_SERVICE_ENABLED=true` and user has reports, queries AI Service RAG with latest report as context. Falls back to mock keyword-based reply otherwise.

**Request:** `{"message": "胃疼怎么办"}`
**Response 200:**
```json
{
  "message": "根据你的健康档案...",
  "warnings": ["仅供健康辅助参考，不构成诊断意见"],
  "citations": [{"sourceType": "report", "sourceId": "10001", "pageNo": 1, "excerpt": "...", "confidence": 0.95}],
  "traceId": "abc-123"
}
```

### GET /api/v1/chat/anxiety/history
安心话聊会话记录（与普通问诊历史独立）。

### POST /api/v1/chat/anxiety
安心话聊（疑病症/大病焦虑缓解）。结合用户健康档案（年龄/身高/体重/既往史）与最近报告，由 AI 以专门的安全提示词回应，帮助区分"实际检查结果"与"担心的疾病"；无报告时返回上传引导。仍遵循安全门（高风险症状拦截）与医疗边界（建议就医/心理科）。

**Request:** `{"message": "我总担心自己得了重病"}`
**Response 200:** 同 `/api/chat` 结构（message/warnings/citations/traceId）。

---

## Report Endpoints V1 (Bearer required)

### POST /api/v1/reports
Upload a medical report file.

**Headers:** `Idempotency-Key: <UUID>` (optional, deduplicates within 24h)
**Body:** `multipart/form-data`
| Part | Type | Required |
|---|---|---|
| file | binary | yes |
| reportDate | text | no |
| reportType | text | no |

**Response 202:**
```json
{"reportId": 10002, "processingJobId": "uuid", "status": "STORED", "statusUrl": "/api/v1/reports/10002/status"}
```
**Errors:** 400 (FILE_EMPTY), 413 (FILE_TOO_LARGE, >20MB), 415 (UNSUPPORTED_MEDIA_TYPE), 422 (MALWARE_DETECTED)

### GET /api/v1/reports
List reports for current user (paginated).

**Query:** `?page=0&size=20`
**Response 200:**
```json
{"items": [{"reportId": 10001, "title": "腹部超声", "reportDate": "2026-06-17", "reportType": "影像报告", "status": "COMPLETED", "summary": "...", "createdAt": "2026-06-17T..."}], "page": 0, "size": 20, "totalPages": 1, "totalElements": 3}
```

### GET /api/v1/reports/{reportId}
Report detail with findings and suggestions.

**Response 200:**
```json
{"reportId": 10001, "title": "腹部超声", "reportDate": "2026-06-17", "reportType": "影像报告", "status": "COMPLETED", "summary": "AI 初步判断存在轻度脂肪肝倾向...", "findings": ["轻度脂肪肝"], "suggestions": ["控制体重"], "disclaimer": "本内容仅用于健康辅助..."}
```

### GET /api/v1/reports/{reportId}/status
Processing job status.

**Response 200:**
```json
{"reportId": 10001, "processingJobId": "uuid", "status": "COMPLETED", "stage": "FINISHED", "retryable": false, "errorCode": null, "updatedAt": "...", "traceId": "..."}
```

### GET /api/v1/reports/{reportId}/file
Download original uploaded file.

**Response 200:** binary stream with `Content-Type` and `Content-Disposition: attachment`

### POST /api/v1/reports/{reportId}/retry
Retry failed processing (max 3 attempts).

**Response 200:** (ReportStatusResponse)
**Errors:** 409 (REPORT_NOT_RETRYABLE, RETRY_LIMIT_EXCEEDED)

### DELETE /api/v1/reports/{reportId}
Delete report and associated file.

**Response 204:** (no content)

---

## Error Response Format

All errors follow:
```json
{"code": "ERROR_CODE", "message": "Human-readable message", "traceId": "uuid"}
```

| HTTP Status | Common Error Codes |
|---|---|
| 400 | INVALID_REQUEST, FILE_REQUIRED |
| 401 | AUTHENTICATION_REQUIRED, AUTHENTICATION_FAILED, PASSWORD_MISMATCH |
| 404 | REPORT_NOT_FOUND |
| 409 | ACCOUNT_EXISTS, REPORT_NOT_RETRYABLE, RETRY_LIMIT_EXCEEDED, IDEMPOTENCY_KEY_REUSED |
| 413 | FILE_TOO_LARGE |
| 415 | UNSUPPORTED_MEDIA_TYPE |
| 422 | MALWARE_DETECTED |
| 502 | AI_RESULT_INVALID |
| 503 | AUTH_CONFIGURATION_INVALID, AI_SERVICE_UNAVAILABLE, STORAGE_FAILED |
| 504 | PROCESSING_TIMEOUT |

---

## Internal Endpoints (Backend → AI Service)

These are NOT exposed through Nginx. Backend calls AI Service directly within the Docker network.

| Method | Path | Description |
|---|---|---|
| POST | /internal/v1/ingestions | Submit document for processing |
| GET | /internal/v1/ingestions/{jobId} | Check ingestion job status |
| POST | /internal/v1/ingestions/{jobId}/retry | Retry failed ingestion |
| POST | /internal/v1/rag/query | RAG query with safety gate |
| DELETE | /internal/v1/indexes/{namespace} | Delete vector index |

All require `X-Internal-Token` header.
