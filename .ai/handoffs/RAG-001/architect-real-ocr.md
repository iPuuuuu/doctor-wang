## Agent Receipt

Task ID: RAG-001 (real OCR verification round)
Agent: architect
Role: Architect（用户提供通义 DashScope key）
Date: 2026-08-14

### Status
ACCEPT（OCR 真实化完成；全链路真实；仅 pgvector 生产向量库待 Docker）

### Summary
用户提供通义（DashScope）key（仅环境变量使用，未入库/未落日志）：
1. Key 验证：embeddings（text-embedding-v4 1024 维）与 chat（qwen-turbo）均可用
2. **qwen-vl-ocr 真实识别验证**：脱敏测试图 → 提取出报告文字
3. **代码**：新增 `DashScopeOcrProvider`（OpenAI 兼容多模态 chat 适配器，`DW_AI_OCR_MODEL` 可配），替换通用 ConfiguredOcrProvider；`config.py` 增 `ocr_model`；`main.py` 接线；README 更新
4. **完整 E2E**：脱敏图片 → qwen-vl-ocr（COMPLETED）→ 智谱 embedding-3 → 语义检索命中 → DeepSeek 生成**基于 OCR 原文的准确回答**（血压数值+建议，零编造）
5. fail-closed 复核：OCR 文本不足 20 字符时正确走 REVIEW_REQUIRED（安全语义保持）
6. 清理：服务停止、临时产物删除、仓库零密钥残留

### Changed Files
- `ai-service/app/providers.py` — DashScopeOcrProvider（新增，替换 ConfiguredOcrProvider）
- `ai-service/app/config.py` — ocr_model 配置
- `ai-service/app/main.py` — OCR provider 接线
- `ai-service/README.md` — OCR 配置说明更新
- `ai-service/tests/test_ocr_provider.py` — 新增 3 用例

### Tests Run
```
cd ai-service && pytest   → 89 passed（86 + 3 OCR provider 用例）
真实 E2E：uvicorn（OCR+embedding+LLM 全真实 env）→ 图片 ingest → rag/query
```

### Test Result
PASS — 89 pytest 全绿；真实 OCR E2E：INGEST COMPLETED → QUERY OK（citation 1，答案基于 OCR 原文）。

### Evidence
- qwen-vl-ocr 提取 "体检报告 血压：收缩压 150mmHg 舒张压 95mmHg 建议：低盐饮食…"（脱敏）
- 查询回答准确引用 OCR 内容且含边界声明

### Known Risks
- qwen-vl-ocr 不返回逐字置信度（固定 0.9 默认）— P3，可后续用二次校验替代
- OCR 输出将图片内容发送给通义 — 生产接真实报告前需确认合规（脱敏开发无碍）
- pgvector 生产向量库未验证（当前内存库）— P1，需 Docker

### Blockers
- 无（本验证完成）

### Unfinished Items
- pgvector 生产向量库 + PostgreSQL 集成验证 — 需 Docker
- 全栈 E2E（Android→Backend→AI）— 需 Docker 或本地全栈

### Recommended Next Action
RAG-001 全链路真实（OCR+embedding+LLM）已验证；部署时按 deploy/.env.example 注入 DW_AI_* secrets（含 DW_AI_OCR_*）；pgvector 随 Docker 环境补齐。
