---
taskId: ANDROID-001
title: 完成 Android 客户端
owner: Architect
domain: android
status: ACCEPTED
dependencies: [BACKEND-001]
blocks: []
createdAt: 2026-08-12
---

# ANDROID-001: 完成 Android 客户端

## Goal
完成可验收的 Android 客户端：Repository、ViewModel、Screens、Navigation 与自动化测试。

## Slices
- A1 Repository — DONE
- A2 ViewModels — DONE
- A3 Screens — DONE
- A4 Navigation — DONE
- A5 单元测试（API 契约序列化）— EXECUTED PASS：15 tests / 0 failures（夹具已按 QA 修正为后端真实形状）
- A6 代码审查 — P1/P2/P3 修复完成（含首次编译暴露的 2 个编译错误）
- A7 编译修复（本会话）— summary 可空兜底、SettingsScreen 改密对话框
- A8 QA 反馈修复闭环（本会话）— P1-1 requiresReview 派生、P1-2 disclaimer/边界声明、P2-1/P2-2 错误透出、9 项 P3

## Acceptance Criteria
- [x] A1–A4 客户端功能切片完成（Repository / ViewModels / Screens / Navigation）
- [x] P1 ReportStatus DTO 契约对齐后端（retryable/errorCode；requiresReview 由 errorCode 派生）
- [x] P1 健康辅助边界声明：详情页 disclaimer 渲染 + 聊天页静态边界文案
- [x] P2 注册/编辑数值字段校验（含上界 1..150/30..250/1..500 与 isFinite）
- [x] P2 报告列表分页按 totalPages + 副作用移出组合（ReportPage + LaunchedEffect + 重试按钮）
- [x] P2 改密会话：changePassword 成功后本地清 session + Settings 修改密码对话框
- [x] P3 布局 fillMaxSize → fillMaxWidth（Register/ProfileEdit）
- [x] A5 单元测试执行通过：15/15 PASS（含真实后端形状与 disclaimer 断言）
- [x] `:app:testDebugUnitTest` 全绿（BUILD SUCCESSFUL）
- [x] `:app:assembleDebug` 全绿（debug APK 产出 15.35MB）
- [x] `git diff --check` 通过
- [x] QA 两轮独立复核：P1/P2 全部修复闭环；剩余 P3 记录为已知限制

## Allowed Files / Modules
- `app/src/test/**`（新增测试，不改业务代码）
- `.ai/state/**`、`.ai/handoffs/ANDROID-001/**`（harness 状态）

## Evidence
- 测试报告：`app/build/test-results/testDebugUnitTest/TEST-com.ipuuuuu.doctorwang.data.api.DoctorWangApiContractTest.xml`（15 tests / 0 failures / 0 errors）
- QA 报告：`.ai/reports/android/ANDROID-001-final-qa.md`
- Receipts：`.ai/handoffs/ANDROID-001/architect-a7-compile-fixes.md`、`architect-a8-qa-fix-round.md`
- 执行命令（本环境 Gradle 沙箱适配）：`ANDROID_USER_HOME=<ws>/.android-home GRADLE_USER_HOME=<ws>/.gradle-home <cached>/gradle-8.10.2/bin/gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon -Dkotlin.compiler.execution.strategy=in-process` → BUILD SUCCESSFUL
- 契约核对：Android DTO 与后端 `ApiModels.kt` / `ReportApiModels.kt` / controllers 端点路径逐一比对一致

## Risks
- 改密对话框、详情页边界声明、轮询等 UI 未经真机/预览验证 — 环境延期项
- 已知限制（记录待后续切片）：P3-5 ReportList/ProfileEdit 401 自动导航；P3-7 allowBackup+DataStore token；P3-9 后端 retryable 恒 false；P3-10 ReportPage 暴露 API DTO

## Blockers
- 无（本地可执行验证全部通过；真机/模拟器、Docker 相关项按项目状态延期）

## Agent Receipts
- `.ai/handoffs/ANDROID-001/architect-a5.md`
- `.ai/handoffs/ANDROID-001/architect-review-a1a4.md`
- `.ai/handoffs/ANDROID-001/android-p1-contract-fix.md`
- `.ai/handoffs/ANDROID-001/android-p2p3-fixes.md`
- `.ai/handoffs/ANDROID-001/architect-a7-compile-fixes.md`
- `.ai/handoffs/ANDROID-001/architect-a8-qa-fix-round.md`
- `.ai/reports/android/ANDROID-001-final-qa.md`
