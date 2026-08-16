# Doctor-Wang RAG 评估报告（2026-08-16，生产环境实测）

> 方法：37 个中文医疗问答用例（29 证据题 + 8 无证据拒绝题），覆盖 4 份真实入库报告
> （腹部超声、胸部X线、生化全套、15 段综合体检报告——后者 6 个 chunk，制造 top-K 截断压力）。
> 每题走生产管线（风险分流→检索→生成），由 DeepSeek 作为 LLM-judge 按 RAGAS 风格打分：
> 忠实度（答案声明是否都被检索上下文支持）、证据覆盖（黄金证据点是否被回答覆盖）、
> 相关性、拒绝正确性（无证据时是否诚实拒答+就医边界）。数据与脚本见 `ai-service/evaluation/`。

## 指标对比（vector 基线 vs hybrid 混合检索）

| 指标 | vector（基线） | hybrid | 变化 |
|---|---|---|---|
| avg_faithfulness 忠实度 | 0.987 | 0.983 | -0.004（噪声内） |
| avg_coverage 证据覆盖 | 0.954 | **0.966** | **+0.012** |
| avg_relevance 相关性 | 0.998 | 0.972 | -0.026（见下方噪声说明） |
| decline_pass_rate 拒绝正确率 | 0.875 | 0.875 | 0 |
| citation_valid_rate 引用有效 | 1.0 | 1.0 | 0 |
| cases_evidence / cases_decline | 29 / 8 | 29 / 8 | — |

## 核心发现

1. **词法锚定查询上的实质提升**：H-01「我的甘油三酯和总胆固醇是多少？」
   （6-chunk 长报告，血脂段）证据覆盖 **0.667 → 1.0**——字符二元组词法通道精准
   命中"血脂"分块，RRF 融合将其排入 top-K。这是混合检索对纯向量的真实增益场景。

2. **拒绝正确率 87.5%（7/8）**：无证据问题（"我应该吃什么药""报告能证明我绝对健康吗"）
   绝大多数被诚实拒绝并给出就医边界；少量用例由 LLM-judge 判定波动（见下）。

3. **引用有效性 100%**：所有回答的 citation sourceId 均与报告一致（AiSafetyGate 强制），
   无跨报告/编造引用。

4. **单 chunk 用例存在 judge 噪声**：CX-06（报告日期）相关性 1.0→0.3、CX-08/CX-10 拒绝
   判定翻转均发生在 1-chunk 报告上（两模式检索结果完全相同），属 LLM-judge 的评分方差，
   而非检索回归——重跑会小幅波动。

## 局限与后续

- 测试集 37 题偏小，置信区间宽；扩到 100+ 题（含多类型报告、对抗样本）后重跑。
- judge 为单一 LLM，可引入二次 judge 投票或 RAGAS 官方库交叉验证。
- 混合检索对长报告（多 chunk）价值最大；生产 top-K=5 下，>5 chunk 的报告即受益。

## 复现

```bash
# 需真实 DW_AI_* 环境（LLM/embedding/DB）
cd ai-service && python -m evaluation.run_evaluation --mode both
# 输出 evaluation/report/metrics.json 与 report.md
```
