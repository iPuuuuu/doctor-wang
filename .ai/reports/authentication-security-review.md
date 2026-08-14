---
name: authentication-security-review
description: AUTH-001 认证与凭据安全审查
metadata:
  type: security-report
status: BLOCKED
---

# AUTH-001 Security Review

## 当前审查结论（2026-08-09）

`BLOCK`。详见 `.ai/handoffs/AUTH-001/security-reviewer.md`。当前 V8 已删除旧 session 并将 `token_hash` 改为 64 字符，早期报告中的“旧 token 明文残留”和“varchar(255) 类型不匹配”不再成立；但生产 HTTP-only 部署、demo-only 凭据迁移和缺少 PostgreSQL/Android 验证仍阻断发布。

## 已确认的控制

- JWT 使用 HS256，并校验签名、issuer、audience、过期时间、subject、user database ID 和 JTI。
- 服务端保存 token hash，不保存原始 bearer token。
- session 记录支持过期和 revoked 状态。
- 密码迁移到 BCrypt hash；登录错误使用统一错误语义。
- Android 删除 HTTP BODY logging，并在 401 时清理本地 session。

## 阻断项

- PostgreSQL/Flyway V8 尚未在 Testcontainers 环境执行，`bash ./gradlew postgresIntegrationTest` 因 DockerClientProviderStrategy 初始化失败。
- V8 review identified and corrected in the working tree: legacy opaque sessions are deleted before the token column is renamed, and `token_hash` is constrained to `varchar(64)`; live PostgreSQL validation is still required.
- Android 当前没有实际 unit/UI 测试 source。
- 生产 JWT secret、轮换、吊销和备份恢复尚未完成受控环境验证。
- 生产部署仅发布 Nginx `80:80`，未强制 TLS；V8 只为 demo 账号回填固定 BCrypt hash，真实用户迁移方案未验证。

## Findings

- P1 BLOCK — `deploy/docker-compose.yml` 与 `deploy/nginx/default.conf` 仅提供 HTTP，Bearer token/健康数据可被窃听；必须在生产入口强制 HTTPS/TLS。
- P1 BLOCK — V8 对非 demo 用户设置 `password_hash NOT NULL` 前不回填，且 V2 历史 migration 含明文 demo 密码；需要受控 reset/backfill、备份和回滚证据。
- P2 — 无服务端 logout/revoke endpoint；`revoked_at` 仅在数据层和 filter 中生效，盗用 JWT 可用至过期。
- P2 — JWT TTL 无正数/上限校验。
- P2 — filter 将 `AUTH_CONFIGURATION_INVALID` 误映射为 401，运维无法可靠发现配置错误。
- P2 — 生产日志、代理脱敏和 Android release APK 的 cleartext 排除未执行验证。
- P3 — 未严格校验 JOSE `alg`/`typ` header（当前 HMAC 仍拒绝未签名 token）。

## 判定

`BLOCK`。当前实现可进入代码评审，但不能宣称认证功能已通过生产安全门禁。

Independent evidence is recorded in `.ai/handoffs/AUTH-001/security.md`; the correction requires a fresh Security Reviewer pass after a Docker-capable migration run.
