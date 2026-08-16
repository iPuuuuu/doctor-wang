# RAG 评估报告

- 测试数: 37（证据题/无证据拒绝题按模式统计）

## 指标对比

| 指标 | vector（基线） | hybrid |
|---|---|---|
| cases_evidence | 29 | 29 |
| cases_decline | 8 | 8 |
| avg_faithfulness | 0.987 | 0.983 |
| avg_coverage | 0.954 | 0.966 |
| avg_relevance | 0.998 | 0.972 |
| decline_pass_rate | 0.875 | 0.875 |
| citation_valid_rate | 1.0 | 1.0 |

## 逐题明细（vector）

| ID | 状态 | 引用 | 忠实度 | 覆盖 | 相关性 | 拒绝通过 |
|---|---|---|---|---|---|---|
| US-01 | OK | 1 | 1.0 | 0.0 | 1.0 | True |
| US-02 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-03 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-04 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-05 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-06 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-07 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-08 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-09 | OK | 1 | None | None | None | True |
| US-10 | OK | 1 | None | None | None | True |
| CX-01 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-02 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-03 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-04 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-05 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-06 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-07 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-08 | OK | 1 | None | None | None | False |
| CX-09 | OK | 1 | None | None | None | True |
| CX-10 | OK | 1 | None | None | None | True |
| ANX-01 | OK | 1 | 0.75 | 1.0 | 1.0 | True |
| ANX-02 | OK | 1 | 0.875 | 1.0 | 0.95 | True |
| ANX-03 | OK | 1 | None | None | None | True |
| H-01 | OK | 5 | 1.0 | 0.667 | 1.0 | True |
| H-02 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-03 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-04 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-05 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-06 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-07 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-08 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-09 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-10 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-11 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-12 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-D1 | OK | 5 | None | None | None | True |
| H-D2 | OK | 5 | None | None | None | True |

## 逐题明细（hybrid）

| ID | 状态 | 引用 | 忠实度 | 覆盖 | 相关性 | 拒绝通过 |
|---|---|---|---|---|---|---|
| US-01 | OK | 1 | 1.0 | 0.0 | 1.0 | True |
| US-02 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-03 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-04 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-05 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-06 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-07 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-08 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| US-09 | OK | 1 | None | None | None | True |
| US-10 | OK | 1 | None | None | None | True |
| CX-01 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-02 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-03 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-04 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-05 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-06 | OK | 1 | 1.0 | 1.0 | 0.3 | True |
| CX-07 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| CX-08 | OK | 1 | None | None | None | True |
| CX-09 | OK | 1 | None | None | None | True |
| CX-10 | OK | 1 | None | None | None | False |
| ANX-01 | OK | 1 | 0.714 | 1.0 | 0.95 | True |
| ANX-02 | OK | 1 | 0.8 | 1.0 | 1.0 | True |
| ANX-03 | OK | 1 | None | None | None | True |
| H-01 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-02 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-03 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-04 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-05 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-06 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-07 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-08 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-09 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-10 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-11 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-12 | OK | 5 | 1.0 | 1.0 | 0.95 | True |
| H-D1 | OK | 5 | None | None | None | True |
| H-D2 | OK | 5 | None | None | None | True |