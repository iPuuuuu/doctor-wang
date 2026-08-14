## Agent Receipt

Task ID: RAG-001/pgvector + full-chain verification (Step 4/6)
Agent: architect
Role: Architect（本地无 Docker 环境，安装本地 PostgreSQL 17 + pgvector 实测）
Date: 2026-08-14

### Status
ACCEPT（pgvector 生产向量库 + 后端↔AI↔PostgreSQL 全链路本地实测通过；本轮抓获并修复 7 个真实生产 bug）

### Summary
本地安装 PostgreSQL 17 + pgvector 0.8.6（brew，工作区集群 .data/pg17，端口 55433），实测全链路并修复 7 个真实 bug：
1. V6__...undo.sql 被 Flyway 当作第二个 V6 → 删除（后端根本无法启动）
2. V9 HNSW 索引超 pgvector 2000 维上限（vector(2048)）→ 移除 ANN 索引，精确扫描
3. V10 app_user.weight numeric→real（Hibernate validate 不匹配，后端启动失败）
4. /actuator/health 被 AuthTokenFilter 拦截 → 加入公开路由（compose healthcheck 会挂）
5. PostgreSqlPollingWorker @Transactional 自调用失效 → 移到 @Scheduled poll()
6. 全部 12 个 JPA 实体缺无参构造器 → 补 protected constructor()
7. ChatService namespaceHints 用 user.userId（字符串）→ 改 user.id（数字）
配置契约：AI_SERVICE_* env 经 YAML 占位符不绑定 → 改用 APP_AI_SERVICE_*（relaxed binding，实测生效）

### Changed Files
- 删除 `V6__prepare_pgvector_ai_chunks.undo.sql`；新增 `V10__align_app_user_weight_type.sql`；修改 `V9`（去 HNSW）
- 12 个实体文件（补无参构造器）
- `PostgreSqlPollingWorker.kt`、`ChatService.kt`、`AuthTokenFilter.kt`（+测试）
- `application.yml`、`docker-compose.yml`（APP_AI_SERVICE_*）
- `PostgresIntegrationMatrixTest.kt`（V1–V10 + ai_vector_chunk + weight 断言）

### Tests Run
```
后端：gradle test bootJar（JDK 21）→ 74/74 PASS
本地 PG17+pgvector：后端启动（Flyway V1-V10 应用 + Hibernate validate 通过）
→ /healthz + /actuator/health UP
PgVectorStore（psycopg3）：upsert/search/delete 真实 PG 通过（2048 维）
全链路 E2E：注册→multipart 上传(202)→worker→AI ingestions(202)→COMPLETED
→ 聊天 citation 1（真实语义检索）+ DeepSeek 基于原文回答
```

### Test Result
PASS — 全部本地实测通过；仓库 74 backend + 93 AI + 15 Android tests 全绿。

### Evidence
- 真实 pgvector 表行：`ai_vector_chunk`（namespace report:6:10005，vector_dims=2048）
- AI 服务日志：POST /internal/v1/ingestions 202 + 状态轮询 200
- 聊天回答含 citation 1 + 医疗边界 warning

### Known Risks
- CI backend-integration（Testcontainers pgvector/pg16）仍待 GitHub Actions 启用后执行 — 本地已用 PG17+pgvector 等价验证
- 本地 PG17 与 CI pg16 的 Flyway "newer PostgreSQL" WARN 不影响功能
- pgvector ANN 索引（≤2000 维）需换 embedding provider 或降维后启用 — 已注释于 V9

### Blockers
- 无（本阶段完成；真机/ECS 部署仍待环境）

### Unfinished Items
- Android 真机验证、release APK — 待设备
- ECS 实际部署 — 待服务器
- TLS/Nginx 冒烟、ClamAV 真实扫描 — 待 Docker

### Recommended Next Action
更新状态文件（project-state/task-board）后，剩真机/部署/Docker 环境项；或转新需求。
