# AgentOps-J 最小集成

Doctor-Wang 可选择让 AI Service 的生成请求经过 AgentOps-J：

```text
Android -> Spring Boot -> AI Service -> AgentOps-J -> LLM provider
                              |              |
                         医疗安全/RAG     路由/重试/trace/metrics
```

默认部署仍然直连 LLM。使用独立 Compose override 才会启用 AgentOps，因此回滚只需移除该 override。Embedding 和 OCR 不经过 AgentOps。

## 准备

默认目录结构为：

```text
code/
  doctor-wang/
  agentops-j/
```

在 `deploy/.env` 中保留现有 Doctor-Wang 配置，并新增：

```dotenv
AGENTOPS_PRIMARY_ENDPOINT=https://api.deepseek.com/chat/completions
AGENTOPS_PRIMARY_API_KEY=replace-with-provider-key
AGENTOPS_PRIMARY_MODEL=deepseek-v4-flash
AGENTOPS_MAX_RETRIES=1
AGENTOPS_PROVIDER_TIMEOUT_MILLIS=60000
```

可选 fallback 使用 `AGENTOPS_FALLBACK_ENDPOINT`、`AGENTOPS_FALLBACK_API_KEY` 和 `AGENTOPS_FALLBACK_MODEL`。未配置 fallback 时会明确失败，不会生成模拟回答。

## 启动

```bash
cd deploy
docker compose \
  --env-file .env \
  -f docker-compose.yml \
  -f docker-compose.agentops.yml \
  up -d --build
```

AgentOps 只通过 Compose 内网的 `8080` 端口提供服务，不映射宿主机端口，也不经 Nginx 暴露。AI Service 使用路由名 `balanced`，真实上游模型由 `AGENTOPS_PRIMARY_MODEL` 决定。

## 展示与验证

在容器内查看健康、最近 trace 和 Prometheus metrics：

```bash
docker compose -f docker-compose.yml -f docker-compose.agentops.yml exec agentops \
  java -version
docker compose -f docker-compose.yml -f docker-compose.agentops.yml logs --tail=50 agentops
docker compose -f docker-compose.yml -f docker-compose.agentops.yml exec ai-service \
  python -c "import json,urllib.request; print(json.load(urllib.request.urlopen('http://agentops:8080/health')))"
docker compose -f docker-compose.yml -f docker-compose.agentops.yml exec ai-service \
  python -c "import urllib.request; print(urllib.request.urlopen('http://agentops:8080/metrics?format=prometheus').read().decode())"
docker compose -f docker-compose.yml -f docker-compose.agentops.yml exec ai-service \
  python -c "import json,urllib.request; print(json.load(urllib.request.urlopen('http://agentops:8080/traces')))"
```

随后在 Doctor-Wang App 发起一次普通问诊。`agentops_requests_total` 和 provider 调用计数应增加，`/traces` 应出现 `agent.chat`。不要在工单或日志中粘贴 prompt、报告原文或 API key。

## 安全与回滚

- override 强制 `AGENTOPS_ALLOW_SIMULATED_PROVIDERS=false`；真实 provider 缺失或全部失败时返回 503。
- system prompt、user message、`temperature` 和 `max_tokens` 会保真转发。
- AI Service 原有高风险拦截、RAG evidence gate 和后端审计保持不变。
- 1.6GB VPS 增加 JVM 服务可能触发内存压力；上线前检查可用内存，必要时保持直连或升级实例。
- 回滚时用原命令仅加载 `docker-compose.yml`，AI Service 会恢复读取 `DW_AI_LLM_*` 的直连配置。
