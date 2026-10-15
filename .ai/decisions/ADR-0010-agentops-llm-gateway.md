---
name: ADR-0010-agentops-llm-gateway
description: AgentOps-J 作为可选 LLM 网关的最小集成决策
metadata:
  type: architecture-decision
  status: accepted
  date: 2026-10-15
---

# ADR-0010: AgentOps-J 作为可选 LLM 网关

## Context

Doctor-Wang AI Service 已通过 OpenAI-compatible 接口调用生成模型。AgentOps-J 提供路由、重试、fallback、trace 和 metrics，但当前演示默认值会在 provider 未配置时产生确定性模拟内容，并且旧请求模型不能保留 system message。医疗辅助链路不能接受这两种行为。

## Decision

AgentOps-J 作为 AI Service 与生成模型之间的可选 HTTP 网关，不作为源码依赖。集成启用时：

- AI Service 仍负责 RAG、证据校验和医疗安全 gate。
- AgentOps 负责生成模型路由和运行时可观测性。
- system/user message 与受支持生成参数必须保真转发。
- 必须设置 `AGENTOPS_ALLOW_SIMULATED_PROVIDERS=false`；缺失或故障的真实 provider 返回 503。
- AgentOps 仅在 Compose 内网暴露，不经 Nginx 对公网发布。
- 通过独立 Compose override 启用；移除 override 即回滚为现有直连配置。

## Alternatives

1. 将 AgentOps 代码作为 Java/Kotlin 依赖加入后端：会跨越既有 `Spring Boot -> AI Service -> Provider` 边界，拒绝。
2. 将 RAG、OCR 和 embedding 全部迁入 AgentOps：范围过大且会重复医疗安全逻辑，拒绝。
3. 仅把 AI Service endpoint 改到当前 AgentOps：会丢 system prompt，并可能返回模拟内容，不安全，拒绝。

## Consequences

- 优点：最小改动获得路由和可观测性；HTTP 契约解耦；回滚简单。
- 代价：增加一个 JVM 服务和内存占用；1.6GB VPS 上启用前必须重新评估容量。
- 限制：本切片只路由 generation，不包含 embedding/OCR。

## Verification

- AgentOps 单元/HTTP 集成测试。
- Doctor-Wang Compose 静态校验（环境允许时）。
- 脱敏 stub provider 的端到端 smoke，检查 chat response、trace 和 metrics。
