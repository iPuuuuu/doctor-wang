# Review Workflow: Doctor-Wang 代码与架构审查

## Review order

1. 读取 `.ai/context.md` 和变更涉及的 role 文件。
2. 查看 diff、调用链、数据流、数据库 migration 和 API 变化。
3. 优先寻找会导致数据泄漏、越权、错误医疗建议、数据丢失或不可逆迁移的问题。
4. 再检查正确性、并发、事务、性能、可维护性、可观测性和测试覆盖。
5. 检查是否有无关修改、过度设计或破坏当前兼容性的行为。

## Required review output

```markdown
## Review Summary
结论：APPROVE / REQUEST_CHANGES / BLOCK

## Findings
### [P0/P1/P2/P3] 标题
- 文件与位置：
- 问题：
- 影响：
- 证据：
- 建议：

## Security & Privacy
## Medical Safety
## API & Database Compatibility
## Test Gaps
## Performance & Reliability
## Regression Checklist
```

## Review rules

- 只报告可验证的问题，区分事实、风险和推测。
- P0：上线阻断或可能造成严重安全/医疗/数据事故。
- P1：应在当前迭代修复的高风险问题。
- P2：重要但可以排入后续迭代的工程问题。
- P3：可选改进或风格问题。
- 没有测试不能默认视为安全；没有证据不能声称测试通过。
- Review 默认只读，不直接修改代码，除非用户明确要求修复。
