# Doctor-Wang System Architecture

**Version:** 1.0
**Date:** 2026-08-12
**Status:** Living Document — updated as architecture evolves

---

## 1. System Overview

Doctor-Wang is an AI-powered personal health assistant. The system ingests medical reports (PDF, images), extracts and indexes content, and provides RAG-backed health Q&A with safety guardrails.

### 1.1 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                       Android Client                        │
│  Kotlin / Jetpack Compose / MVVM / Hilt / Retrofit / Room   │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS (TLS 1.2+)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     Nginx (TLS Termination)                  │
│                  port 443 → backend:8080                     │
│                  port 80  → 308 redirect to HTTPS            │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP (internal)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 Spring Boot Backend (Kotlin/JVM 17)          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │   Auth   │ │  Report  │ │   Chat   │ │    Profile    │  │
│  │ Service  │ │  Service │ │  Service │ │    Service    │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └───────┬───────┘  │
│       │            │            │               │           │
│  ┌────┴────────────┴────────────┴───────────────┴────────┐  │
│  │              PostgreSQL (16 tables, Flyway V1-V8)      │  │
│  │              pgvector extension (1536-dim HNSW)         │  │
│  └────────────────────────────────────────────────────────┘  │
│                           │                                  │
│              X-Internal-Token (service auth)                 │
│                           ▼                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           AI Service Client (RestClient)              │   │
│  │    /internal/v1/ingestions  /internal/v1/rag/query    │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP (internal)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              AI Service (Python FastAPI + LangGraph)         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
│  │Document  │ │ Chunking │ │Embedding │ │Vector Store  │  │
│  │ Parsing  │ │(page-    │ │Provider  │ │(InMemory /   │  │
│  │(pypdf/   │ │ wise,    │ │(Fake /   │ │ PgVector)    │  │
│  │ OCR TBD) │ │ 900 chars)│ │ OpenAI)  │ │              │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘  │
│                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────────┐    │
│  │ Safety   │ │  RAG     │ │  Job Repository (SQLite)  │    │
│  │ Gate     │ │  Graph   │ │  for async ingestion      │    │
│  │(3-tier)  │ │(LangGraph)│ │                          │    │
│  └──────────┘ └──────────┘ └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Data Flow

```text
1. Upload:   Android → POST /api/v1/reports (multipart)
               → Backend validates (size/MIME/magic/signature/virus)
               → Backend stores file (LocalFileStorageService)
               → Backend submits to AI Service (POST /internal/v1/ingestions)
               → AI Service extracts text, chunks, embeds, indexes
               → Backend polls job status

2. Chat:     Android → POST /api/chat
               → Backend ChatService
               → If AI_SERVICE_ENABLED + user has reports:
                   → AI Service POST /internal/v1/rag/query
                   → Safety gate check (HIGH/MEDIUM/LOW)
                   → Vector search → citations
                   → LLM generation (if configured) or static summary
               → Else: mock keyword-based reply
               → Response with citations, warnings, traceId

3. Auth:     Android → POST /api/login
               → BCrypt password verification
               → JWT HS256 issuance (TTL 1-86400s)
               → Server-side session (JTI, token hash, revocable)
               → AuthTokenFilter validates every request
```

---

## 2. Module Boundaries

### 2.1 Backend (`doctor-wang-mock-server/`)

| Layer | Package | Responsibility |
|---|---|---|
| Controller | `controller/` | HTTP routing, request validation, response formatting |
| Service | `service/` | Business logic, transaction management, orchestration |
| Repository | `persistence/repository/` | Data access, JPA queries |
| Entity | `persistence/entity/` | JPA entity mappings (ddl-auto: validate) |
| Config | `config/` | Auth filter, properties, exception handling |
| Model | `model/` | DTOs, API contracts, error models |

**Key Services:**

| Service | Scope | Dependencies |
|---|---|---|
| BackendService | Auth (login/logout/register) + Profile (CRUD) + Password | 6 repositories |
| ChatService | Chat history + RAG query (+ mock fallback) | ChatMessageRepo, MedicalReportRepo, AiReportClient |
| ReportProcessingService | Report V1 pipeline (upload/status/detail/retry/delete/list/download) | 8 dependencies |
| JwtTokenService | HS256 JWT issue/verify, token hash | AuthProperties |
| PasswordHasher | BCrypt hash/matches | — |
| FileStorageService | Store/retrieve/delete files with path-traversal guard | — |
| PostgreSqlPollingWorker | Scheduled AI job processing | AiReportClient, ReportProcessingService |

### 2.2 AI Service (`ai-service/`)

| Module | File | Responsibility |
|---|---|---|
| API | `main.py` | FastAPI routes, dependency injection |
| Config | `config.py` | Pydantic Settings (env prefix DW_AI_) |
| Schemas | `schemas.py` | Pydantic request/response models |
| Parsing | `parsing.py` | PDF text extraction (pypdf), OCR routing |
| Chunking | `chunking.py` | Page-wise chunking (900 chars, 120 overlap) |
| Embeddings | `embeddings.py` | FakeEmbeddingProvider / OpenAICompatibleEmbeddingProvider |
| Vector Store | `vectorstore.py` | InMemoryVectorStore / PgVectorStore |
| RAG | `rag.py` | query_rag: namespace auth → safety triage → search → generate |
| Safety | `safety.py` | 3-tier risk triage (HIGH/MEDIUM/LOW) |
| Graph | `graph.py` | LangGraph StateGraph (single-node RAG pipeline) |
| Jobs | `jobs.py` | SQLite job state for async ingestion |
| Providers | `providers.py` | OpenAI-compatible HTTP clients for embeddings/OCR/LLM |

### 2.3 Android (`app/`)

| Layer | Package | Responsibility |
|---|---|---|
| API | `data/api/` | Retrofit interface + kotlinx.serialization DTOs |
| Repository | `data/repository/` | RemoteRepository (network) + FakeRepository (offline demo) |
| Model | `data/model/` | Domain models (UserProfile, MedicalReport, Message) |
| Database | `database/` | Room database (MessageEntity, MessageDao) |
| DI | `di/` | Hilt module (AppModule) |
| Network | `network/` | NetworkProvider, OkHttpClient |
| Storage | `storage/` | SessionManager (DataStore) |
| ViewModel | `viewmodel/` | 11 ViewModels (MVVM pattern) |
| UI | `ui/` | Compose screens (11) + navigation + theme |

---

## 3. Database Schema (16 tables, Flyway V1-V8)

| Table | Purpose | Key Indexes |
|---|---|---|
| app_user | User accounts | UNIQUE(user_id), UNIQUE(account) |
| user_session | JWT sessions with revoke support | UNIQUE(jti), UNIQUE(token_hash) |
| health_profile_record | Health history records | FK user CASCADE |
| health_profile_disease | Disease history | FK user CASCADE |
| chat_message | Chat history | FK user CASCADE, idx(created_at) |
| medical_report | Uploaded reports | FK user CASCADE |
| medical_report_file | Uploaded file metadata | UNIQUE(storage_key), FK report CASCADE |
| medical_report_finding | AI-extracted findings | FK report CASCADE |
| medical_report_suggestion | AI-generated suggestions | FK report CASCADE |
| medical_report_text | Extracted text content | FK report CASCADE |
| medical_report_chunk | Text chunks for RAG | UNIQUE(report_id, chunk_index) |
| medical_report_citation | AI citations | FK report CASCADE |
| report_processing_job | Async AI job state | UUID PK, UNIQUE(report_id) |
| report_upload_idempotency | Upload dedup | UNIQUE(user_id, idempotency_key) |
| ai_vector_chunk | pgvector embeddings | HNSW cosine index, UNIQUE(namespace, chunk_id) |
| ai_audit_event | AI audit trail | FK user CASCADE |
| report_cleanup_task | Async file cleanup | idx(status, next_attempt_at) |

---

## 4. Security Architecture

### 4.1 Authentication
- JWT HS256 with short TTL (configurable 1-86400s)
- Server-side session tracking (jti, token_hash, revoked_at)
- BCrypt password hashing (cost 12) via pgcrypto / PasswordHasher
- AuthTokenFilter: exact path+method matching for public routes
- Password change revokes all other sessions (ADR-0003)

### 4.2 Authorization
- All data access scoped to authenticated user via AuthContext
- Report namespace: `report:{userId}:{reportId}` → enforced by AI Service
- AI Service requires X-Internal-Token for backend-to-AI auth
- Android never calls AI Service directly

### 4.3 Medical Safety
- 3-tier risk triage: HIGH (blocked) / MEDIUM (needsReview) / LOW (disclaimer)
- 27 risk terms (20 HIGH + 13 MEDIUM) across Chinese and English
- AI output always carries: citations, warnings, traceId, disclaimer
- HIGH risk → "立即联系急救服务（120）" — never proceeds to AI generation

### 4.4 Data Protection
- No patient data in logs (fileBase64 excluded from job state)
- Path-traversal guard on file storage
- File upload validation: MIME, magic number, size (20MB), virus scan hook
- HTTPS enforced via Nginx TLS termination (HSTS)

---

## 5. API Inventory (17 endpoints)

### Auth
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/login | public | JWT login |
| POST | /api/logout | Bearer | Session revoke |
| POST | /api/register | public | User registration |

### Profile
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/profile | Bearer | Get profile |
| PUT | /api/profile | Bearer | Update profile (partial) |
| GET | /api/health/profile | Bearer | Health profile with history |

### Password
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/password/change | Bearer | Change password |

### Chat
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/chat/history | Bearer | Chat history |
| POST | /api/chat | Bearer | Send message (mock or AI RAG) |

### Reports V1
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/v1/reports | Bearer | Upload report (multipart) |
| GET | /api/v1/reports | Bearer | List reports (paginated) |
| GET | /api/v1/reports/{id} | Bearer | Report detail + findings |
| GET | /api/v1/reports/{id}/status | Bearer | Processing status |
| GET | /api/v1/reports/{id}/file | Bearer | Download original file |
| POST | /api/v1/reports/{id}/retry | Bearer | Retry failed processing |
| DELETE | /api/v1/reports/{id} | Bearer | Delete report + file |

### Health
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /healthz | public | Health check |
| GET | / | public | Service info |

---

## 6. Key Design Decisions

See [.ai/decisions/](/ai/decisions/) for full ADRs:
- **ADR-0001**: Multi-Agent Development Harness
- **ADR-0002**: JWT + BCrypt + revocable sessions
- **ADR-0003**: TLS termination, pgcrypto migration, logout API
- **ADR-0004**: Harness v2 (state directory, task lifecycle, agent receipts)

---

## 7. Current Limitations

| Area | Limitation | Mitigation |
|---|---|---|
| Embeddings | Fake SHA-256 based (not semantic) | Feature-flagged: real OpenAI embeddings when API key available |
| Vector Store | InMemory (doesn't survive restart) | PgVectorStore factory wired, needs PostgreSQL connection |
| OCR | Not implemented | OCR provider configured when API key available |
| LLM | Static summary text | Generator configured when API key available |
| Chat | Mock fallback when AI_SERVICE_ENABLED=false | — |
| Android Tests | NOT_RUN (no SDK) | Deferred to SDK-capable environment |
| Integration Tests | NOT_RUN (no Docker) | Deferred to Docker-capable CI |
| TLS | Nginx config exists, not smoke-tested | Deferred to Docker Compose environment |
