## Agent Receipt

Task ID: ANDROID-001 (A1–A4 Review)
Agent: architect
Role: Architect
Date: 2026-08-12

### Status
REWORK — 发现 1 个 P1 契约不匹配与若干 P2/P3 问题，需修复后复核

### Summary
对 A1–A4（Repository / ViewModels / Screens / Navigation）做只读代码审查，
重点核对 Android↔后端契约、会话过期处理、安全与分页逻辑。
未修改任何业务代码。

### Review Conclusion
REQUEST_CHANGES

### Findings

#### P1 — ReportStatus DTO 与后端契约不匹配
- 文件：`app/src/main/java/com/ipuuuuu/doctorwang/data/api/DoctorWangApi.kt`（ReportStatusResponse）
- 问题：客户端声明 `canRetry` / `requiresReview` / `failureCategory`，后端实际返回 `retryable` / `errorCode`（无 requiresReview / failureCategory）
- 影响：`ReportDetailViewModel` 轮询状态时，"可重试"/"需要人工复核" 永远不显示，报告状态呈现错误
- 证据：后端 `ReportApiModels.kt` `ReportStatusResponse(reportId, processingJobId, status, stage, retryable, errorCode, updatedAt, traceId)` vs 客户端 DTO
- 建议：对齐客户端 DTO（retryable→canRetry、errorCode→failureCategory，或直接改名匹配），并补后端真实 shape 的契约测试

#### P2 — 注册/编辑资料数值字段静默置 0
- 文件：`RegisterViewModel.kt`、`ProfileEditViewModel.kt`
- 问题：`toIntOrNull() ?: 0` 使非法输入静默变成 0 提交，无用户反馈；负值也可提交
- 影响：健康数据被错误写入档案
- 建议：字段级校验 + 错误提示；后端同步增加 age/height/weight 边界校验

#### P2 — 报告列表分页硬编码且组合期副作用
- 文件：`ReportListViewModel.kt`、`ReportListScreen.kt`
- 问题：`hasMore = items.size >= 20` 忽略响应中的 totalPages/totalElements；`loadMore()` 在组合期间被调用（应使用 LaunchedEffect/snapshotFlow）；初始加载失败只有错误文本、无重试按钮
- 影响：分页边界错误、重复请求风险、失败恢复体验差
- 建议：基于 totalPages 判断 hasMore；副作用移出组合；补空态/失败态交互

#### P2 — 密码修改后客户端会话处理
- 文件：`PasswordChangeViewModel.kt`、`SettingsScreen.kt`
- 问题：后端 changePassword 会吊销当前 JTI（ADR-0003），客户端仅显示成功，仍持有失效 token 直到下次 401
- 影响：修改密码后用户以为仍在登录态，下一次操作才被踢出
- 建议：成功修改密码后主动清 session 并引导重新登录（需 UX 决策）

#### P3 — 滚动 Column 内控件使用 fillMaxSize
- 文件：`RegisterScreen.kt`、`ProfileEditScreen.kt`
- 问题：OutlinedTextField/Button 使用 `fillMaxSize()`（应 fillMaxWidth）
- 影响：多数情况因无界高度约束不放大，但属错误惯用法，存在布局风险

#### PASS — 安全与会话处理
- 新增代码无 token/报告内容日志；NetworkProvider 仅非空 token 才带 Bearer；401 清 session；密码输入框正确使用 PasswordVisualTransformation + KeyboardType.Password；release URL 强制 HTTPS（SessionManager）
- Home/Profile/Chat/Upload 均正确响应 SessionExpiredException 并清栈回登录页

### Tests Run
```
未执行 — NOT RUN（沙箱禁止 socket，Gradle 无法运行）
```

### Test Result
NOT_RUN

### Known Risks
- A5 契约测试基于客户端当前 DTO 形状，P1 修复后需同步更新
- 审查基于源码阅读，未运行/编译验证

### Blockers
- 沙箱禁止 socket/网络，Gradle 构建不可运行

### Unfinished Items
- P1 DTO 契约修复 — 需用户决策（立即修 vs 延后）
- A5 测试执行 — 环境阻塞
- QA 独立复核 — 待修复+执行后

### Recommended Next Action
1. 修复 P1 ReportStatus DTO 契约并对齐后端；2. 在可执行环境跑测试；3. QA 复核
