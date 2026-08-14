---
id: ADR-0003
title: Close AUTH-001 release gates with enforced TLS, portable BCrypt migration, and user-scoped logout
status: accepted-by-architect
date: 2026-08-10
owners: [architect-agent]
---

# ADR-0003：认证发布门禁收口方案

## 背景

AUTH-001 已具备短期 JWT、服务端 JTI session、BCrypt verifier 与 Android 401 本地清理，但独立审查确认：当前 Compose 部署只提供 HTTP、V8 仅能处理 demo 账户，且 `revoked_at` 没有用户可调用的撤销路径。当前 V8 仍是未发布的工作树 migration；它尚无 Docker/PostgreSQL 执行证据，不能视为已上线历史。

## 决策

1. **TLS 由仓库内 Nginx 强制终止。** Compose 对外发布 80/443；80 仅返回到相同 host 的 HTTPS 重定向，443 使用以只读方式挂载的证书与私钥，代理至内部 backend:8080。健康数据与 bearer token 不经 HTTP 代理到后端。生产环境必须提供证书文件和安全的 `.env`，否则 Nginx 不应成为可用发布入口。
2. **V8 在首次应用前改为对所有既有账户作 BCrypt 迁移。** 在 PostgreSQL 中启用 `pgcrypto`，将每个非空旧 `password` 转为 cost 12 的 bcrypt `password_hash`，再设置非空和删除旧列。不要使用固定 demo hash；旧 session 全部删除，用户重新登录。数据库角色必须有启用 `pgcrypto` 的权限；不满足时 migration fail-closed，不启动半迁移服务。
3. **提供已认证、用户作用域的 `POST /api/logout`。** Filter 已验证的 session JTI 是唯一可撤销目标；服务端仅撤销当前 token 对应 session，响应成功后 Android 无条件清理本地 session。重试须幂等：已撤销 session 仍返回成功，不泄露其他会话状态。
4. **JWT 仍使用 1–86,400 秒 TTL。** 服务端 logout 是主动撤销通道，短 TTL 是泄露窗口上限；两者不互相替代。

## 备选方案与取舍

- 在云 LB/CDN 终止 TLS：可行，但部署资产无法验证真实终止策略，也会把安全关键配置移出版本化范围；选择 Nginx 内置 TLS 作为最小可审计基线。上游 LB 可以在未来叠加，但不得重新暴露 HTTP。
- 强制全部用户重置、删除旧密码：避免读取旧 plaintext，但会破坏所有账号且没有现有邮件/身份验证恢复流程；不选择。
- 为每个用户固定 demo hash：不安全且会导致凭据等价；不选择。
- 仅依赖 JWT TTL、没有 logout API：不能快速应对设备丢失或 token 泄露；不选择。

## 迁移与回滚

- 执行前：在受控维护窗口备份 PostgreSQL、验证恢复、检查 `app_user.password` 非空、确认 `pgcrypto` 可用并记录用户数量；不在仓库或日志中记录密码、导出或患者数据。
- 执行中：Flyway 事务失败即停止，保持旧 schema；不会启动兼容的半迁移服务。
- 执行后：旧 password 列删除不可从同一数据库回滚，因此回滚路径是停止部署并从已验证备份恢复；所有旧 token 被刻意失效，客户端必须登录。
- 不得修改已经在任何共享/生产数据库应用的 migration。若 V8 已被应用，必须新建受控迁移与人工 runbook，并将该实例列为 BLOCKED；当前仓库尚缺这项应用证据。

## 验收标准

- Nginx `nginx -t` / Compose config 可验证；HTTP 仅重定向，HTTPS 可代理，证书/私钥不进入仓库或镜像，backend 不发布宿主机端口。
- PostgreSQL integration matrix 验证 V8：全部既有用户获得 bcrypt hash、`password` 不存在、`password_hash` 非空、旧 sessions 不存在、JTI/hash/revocation schema 有效，并能使用原凭据登录。
- logout HTTP contract 验证：无 token 401、当前 JTI 被 revoke、撤销后同 token 401、重复 logout 幂等、不能撤销其他用户 session。
- Android 验证：logout 无论网络结果均清本地 session；release URL 为 HTTPS，且 release 不允许 cleartext。
- QA 与 Security Reviewer 均不保留 P1；Docker-capable CI 的 PostgreSQL/Flyway/Hibernate 证据必须归档。

## 非目标

- 自动证书签发、密钥轮换服务、高可用、Java 21 升级、历史 V2 migration 改写、真实用户数据迁移执行与生产发布。
