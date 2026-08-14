---
id: ADR-0005
title: Backend Foundational Capabilities — report CRUD, user management, file upload
status: accepted
date: 2026-08-12
owners: [architect-agent]
---

# ADR-0005：后端基础能力设计决策

## 背景

AUTH-001 完成后，后端缺少用户注册、个人资料修改、密码修改、报告列表和文件下载能力。需要在不引入 Flyway migration、不增加依赖的前提下补齐。

## 决策

1. **所有新端点复用现有 schema** — V1-V8 migration 已包含所有需要的表和列，无需 V9。
2. **报告列表采用 Spring Data Page 分页** — `MedicalReportRepository.findAllByUser(user, Pageable)`，每页默认 20 条，user 隔离通过 repository 查询实现。
3. **文件下载流式返回** — `InputStreamResource` + `Content-Disposition: attachment`，复用 `FileStorageService.retrieve()` 接口。
4. **注册即登录** — `POST /api/register` 返回 JWT token + userId，减少客户端往返。
5. **个人资料部分更新** — `PUT /api/profile` 接受可空字段，仅更新非空字段。
6. **密码修改撤销其他 session** — 调用 `UserSessionRepository.revokeAllExcept()`，遵守 ADR-0003。
7. **ChatResponse 对齐 Android 契约** — 后端 `ChatResponse` 字段改为 `{message, warnings, citations, traceId}`。

## 取舍

- 注册即登录 vs 分离注册+登录：减少了客户端一次往返，但 session 创建逻辑在 register 和 login 中重复。MVP 阶段接受此重复。
- AppUserEntity var 化 vs reconstruct-and-save：var 化消除了每次更新都要重建对象的反模式，但增加了测试隔离复杂度（需要 @BeforeEach 重置）。

## 风险

- userId 生成使用 `timestamp.takeLast(8) + random(1000..9999)`，并发场景下存在理论碰撞风险。生产环境应改为 UUID。
- 文件下载缺少 Range 支持和缓存头（ETag/Last-Modified），大文件场景下需补充。
