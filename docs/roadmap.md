# Roadmap（2026-08-15 更新）

## ✅ 已完成

### Phase 1: Harness 协作体系
- `.ai/` 协作文档、角色、任务生命周期、决策记录（ADR-0001~0009）齐备。

### Phase 2: 后端基础能力
- Spring Boot 3 + Kotlin/JVM 21 后端：JWT 可撤销会话、BCrypt、注册/登录/改密/登出、个人资料/健康档案、报告 CRUD（上传/列表/详情/状态/下载/删除/重试）、文件幂等与对象生命周期。
- PostgreSQL + Flyway V1~V11（含 pgvector 向量列、会话隔离）；90 项测试全绿。

### Phase 3: AI 能力层
- Python FastAPI 服务：LangGraph RAG、pgvector 向量检索、DeepSeek 生成 / 智谱 embedding-3（2048 维）/ 通义 qwen-vl-ocr 真实三链路（本地+生产双验证）。
- 安心话聊（疑病症/大病焦虑）：专用安全提示词、结合健康档案+报告、循证引用、fail-closed。
- 报告解读：AI 解析完成后生成"摘要/重点发现/建议"，结构化解析 + AiSafetyGate 校验 + 诚实兜底。
- 97 项 AI 测试全绿。

### Phase 4: Android 客户端
- Kotlin + Compose + Material3 + MVVM + Hilt：登录/注册、首页、安心话聊、AI 问诊、报告上传（含检查类型/日期）、列表/详情/下载/删除、个人资料/设置。
- 15 项单测全绿；默认直连生产（https://120.55.186.220/）。

### Phase 5: 质量与治理
- GitHub Actions CI 全绿（后端单测 + Testcontainers 集成矩阵 + Android 构建 + AI 测试）。
- 生产端到端冒烟脚本 `deploy/smoke-test.sh`（10 项，实测全过）。

### Phase 6: 生产部署（2026-08-15）
- 阿里云 ECS 120.55.186.220 全栈上线：PostgreSQL+pgvector / 后端 / AI 服务 / Nginx TLS。
- OOM 加固：2GB swap、内核参数、Docker 资源限制、健康巡检 cron。
- 一键恢复脚本 `deploy/recover-server.sh`；离线重建技巧已记录（RUNBOOK §6.1.1）。

## ⏳ 下一步（按优先级）

1. **Android 真机验证**（P1）：用户安装 v2 APK 实测全部流程；反馈后迭代。
2. **域名 + ICP 备案**（P1，审批 1~3 周）：绑定域名、Let's Encrypt 正式证书、消除自签警告；指南见 `docs/guide-domain-and-release.md`。
3. **正式签名 release APK**（P2）：依赖域名证书完成后打包发布。
4. **ClamAV 病毒扫描**（P2）：1.6GB 内存装不下全量病毒库（实测），升级 4GB+ 后开启 `VIRUS_SCAN_ENABLED=true` + scan profile。
5. **DEPLOY-001b 收尾**（P3）：监控告警通道（脚本已备）、备份策略、HA（多副本/托管数据库）。
