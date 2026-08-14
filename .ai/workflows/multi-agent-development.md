---
name: multi-agent-development
description: Doctor-Wang 多 Agent 协作开发主流程
metadata:
  type: harness-workflow
---

# Multi-Agent Development Workflow

```text
User Request
  → Architect scope and decision
  → Lead task decomposition
  → Engineer implementation
  → QA verification ─┐
  → Security review ─┤→ Architect delivery decision
                     └→ evidence and decision record
```

## 1. Intake

Architect 记录目标、非目标、成功标准、受影响模块、敏感数据和所需角色。只读分析不修改文件；变更任务必须等待用户确认或明确授权。

## 2. Design

Architect 输出方案和替代方案；对应 Lead 将方案拆成小的、可验证的垂直切片。涉及 API、数据库、AI、安全或 Android 体验时，必须明确契约、migration 和回滚策略。

## 3. Implement

Engineer 只修改批准文件，先补回归测试，再实现最小变更；每个切片完成后立即运行局部检查。

## 4. Verify

QA 验证正常、边界、异常、越权、隐私、医疗安全、性能和兼容性。Security Reviewer 独立检查威胁、密钥、服务认证、日志和数据访问。

## 5. Deliver

Architect 只在代码、测试、契约、文档、migration（如有）和风险证据齐全时做最终决定。结论必须是 `ACCEPT`、`REWORK` 或 `BLOCK`，不得把未执行检查记为通过。

## 协作约束

- 每个任务只有一个 owner，依赖和阻塞状态必须登记到 `.ai/task-board.md`。
- Agent 通过结构化交付物交接，不依赖隐式全局上下文。
- 业务代码、数据库、依赖和部署配置不属于 Harness 改造范围，除非任务明确批准。
