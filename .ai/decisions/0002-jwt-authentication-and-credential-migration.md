---
id: ADR-0002
title: Use short-lived JWTs with revocable sessions and hashed credentials
status: accepted-by-architect
date: 2026-08-09
owners: [architect-agent]
---

# ADR-0002：JWT 认证与用户凭据迁移

## 背景

现有登录接口已经存在，但使用可猜测结构的随机 session token，并在 `app_user.password` 中保存明文密码。项目需要 Android 登录、后端 JWT 认证和可审计的用户会话，同时不能破坏既有用户隔离边界。

## 决策

- 登录成功后签发 HS256 JWT，包含 `sub`、`uid`、`jti`、`iss`、`aud`、`iat`、`exp`。
- JWT 采用短期有效期；服务端保留 `user_session` 记录，用 JTI、token hash、过期和撤销字段控制会话。
- 认证过滤器同时校验签名、issuer、audience、过期时间、用户 ID、JTI、token hash 和撤销状态。
- 用户凭据迁移到 BCrypt 哈希；历史 migration 不修改，新增 V8 migration。
- Android 只把 JWT 当作认证凭据交给统一网络层，不在客户端自行决定权限。
- 401 响应清理本地 session 并回到登录流程。

## 取舍

纯无状态 JWT 的实现更简单，但不能主动撤销泄露凭据，也不利于医疗数据访问审计；保留服务端会话记录增加数据库读操作，但满足撤销、审计和用户隔离要求。

## 兼容性与迁移

- V8 将 demo 用户迁移至哈希凭据，并将既有 session token 列迁移为 token hash/JTI 结构。
- 既有 opaque token 在迁移后失效，客户端重新登录获取 JWT。
- 真实存量用户密码不得通过明文复制迁移，生产迁移必须采用受控重置或批准的逐用户迁移流程。

## 风险与门禁

- `APP_AUTH_JWT_SECRET` 必须由环境安全配置提供，长度不足时认证失败。
- PostgreSQL/Flyway 集成测试、JWT 伪造/过期测试、密码哈希测试、用户隔离测试和日志脱敏审查通过前，不解除发布阻断。
