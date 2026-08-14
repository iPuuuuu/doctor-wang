# ANDROID-001 最终 QA 复核报告（两轮）

日期: 2026-08-14
对象: ANDROID-001 A1–A8 全部变更
方式: 两个独立 QA 子代理只读复核 + Architect 修复闭环

## 第一轮 QA（子代理 7d8bf8ba）— 结论 REQUEST_CHANGES

无 P0。必须修复项：

### P1（已全部修复）
- P1-1 requiresReview 派生与后端契约不符：后端对需复核 AI 结果 fail closed（`PostgreSqlPollingWorker.fail()` → status=FAILED + errorCode=REVIEW_REQUIRED），客户端 `status=="REVIEW_REQUIRED"` 恒为 false。
  - 修复：`RemoteDoctorWangRepository.ReportStatusResponse.toDomain()` 改为 `requiresReview = requiresReview || failureCategory == "REVIEW_REQUIRED"`；A5 契约测试夹具改为真实形状（FAILED + REVIEW_REQUIRED + retryable=false）。
- P1-2 健康辅助边界提示缺失：后端 `ReportDetailV1Response.disclaimer` 未解析；详情页把 AI 摘要当权威内容展示且留有过时开发说明；聊天页无边界声明。
  - 修复：`MedicalReportResponse`/`MedicalReport` 增加 `disclaimer` 字段并映射；`ReportDetailScreen` 摘要上方新增边界声明卡（后端 disclaimer 优先 + 固定兜底文案），删除过时开发说明卡；`ChatScreen` 标题下新增静态边界声明；A5 测试补 disclaimer 解析断言。

### P2（已全部修复）
- P2-1 非 401 服务端错误 message 被丢弃（改密旧密码错误/账号已存在等显示误导性文案）。
  - 修复：`mapNetworkError` 对全部 4xx/5xx 提取服务端 message，401 仍走 SessionExpiredException。
- P2-2 上传本地校验错误（MIME/大小）被网络错误文案覆盖。
  - 修复：本地校验异常改抛 `IllegalArgumentException`（mapNetworkError `else` 分支透出原 message）。

### P3（已修复 9 项，其余 4 项记录为已知限制）
- 修复：改密注释与实现不符（revokeAllExcept 语义）；客户端数值校验缺上界（对齐后端 1..150/30..250/1..500）；weight NaN/Infinity 可绕过校验（isFinite）；networkCall runCatching 吞 CancellationException（改为 try/catch 重抛）；上传先查大小再读入（防 OOM）；幂等键由每次新 UUID 改为内容 SHA-256（重试可去重）；ReportListScreen 空列表垃圾 loadMore；PasswordChangeViewModel 缺 isLoading 守卫（补防重复提交）；main res 无引用的 cleartext 陷阱配置移至 debug res。
- 记录为已知限制（不在本任务范围内或需跨模块决策）：
  - P3-4 报告详情轮询：已实现有限轮询（PROCESSING/RETRYING 2s×5，与 UploadViewModel 一致）
  - P3-5 ReportList/ProfileEdit 缺 onSessionExpired 自动导航（401 时显示错误+重试，需手动回 Home）— 已记录，建议后续切片
  - P3-7 allowBackup=true + DataStore 存 token（adb backup 可提取）— 既有问题，建议 ADR 记录
  - P3-9 后端 retryable 永不为 true（V3 migration default false，无置 true 路径）— 后端 dead feature，需后端切片
  - P3-10 ReportPage.items 直接暴露 API DTO — 分层改进项，低优先级

## 第二轮 QA（子代理 75047f46，聚焦本会话改动）— 结论 APPROVE

- 改密弹窗状态流转、VM session 清理联动、分页 hasMore 边界、logout/upload 安全、NetworkProvider、契约测试配置一致性 全部通过。
- 附带 P3（已随第一轮修复吸收）：readBytes OOM、幂等键稳定性、空列表 loadMore、isLoading 守卫、CancellationException。

## 修复后验证（Architect 执行）

- `:app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL（35s），`DoctorWangApiContractTest` 15 tests / 0 failures / 0 errors（夹具已更新为真实后端形状）
- Debug APK 产出 15.35MB
- `git diff --check` PASS
- 后端 56 tests、AI 86 tests 本会话已重跑 PASS（未受本次 Android 修复影响）

## 最终结论

APPROVE（2 个 P1、2 个 P2 及主要 P3 已修复并通过构建验证；4 项 P3 记录为已知限制，随后续切片处理）。
