# RAG 评估报告

- 测试数: 52（证据题/无证据拒绝题按组合统计）

## 指标对比

| 指标 | hybrid-single | hybrid-agentic |
|---|---|---|
| cases_evidence | 42 | 42 |
| cases_decline | 10 | 10 |
| avg_faithfulness | 0.984 | 0.99 |
| avg_coverage | 0.94 | 0.94 |
| avg_relevance | 0.999 | 1.0 |
| decline_pass_rate | 1.0 | 0.7 |
| citation_valid_rate | 1.0 | 1.0 |

## 逐题明细（hybrid-single）

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
| CX-08 | OK | 1 | None | None | None | True |
| CX-09 | OK | 1 | None | None | None | True |
| CX-10 | OK | 1 | None | None | None | True |
| ANX-01 | OK | 1 | 0.5 | 1.0 | 0.95 | True |
| ANX-02 | OK | 1 | 0.833 | 1.0 | 1.0 | True |
| ANX-03 | OK | 1 | None | None | None | True |
| H-01 | OK | 5 | 1.0 | 0.667 | 1.0 | True |
| H-02 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-03 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-04 | OK | 5 | 1.0 | 0.333 | 1.0 | True |
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
| H-13 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-14 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-15 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-16 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-17 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-18 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-19 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-20 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-21 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-22 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-23 | OK | 5 | 1.0 | 0.5 | 1.0 | True |
| H-24 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-25 | OK | 5 | None | None | None | True |
| H-26 | OK | 5 | None | None | None | True |
| H-27 | OK | 5 | 1.0 | 1.0 | 1.0 | True |

## 逐题明细（hybrid-agentic）

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
| US-09 | OK | 1 | None | None | None | False |
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
| CX-10 | OK | 1 | None | None | None | False |
| ANX-01 | OK | 1 | 0.6 | 1.0 | 1.0 | True |
| ANX-02 | OK | 1 | 1.0 | 1.0 | 1.0 | True |
| ANX-03 | OK | 1 | None | None | None | True |
| H-01 | OK | 5 | 1.0 | 0.667 | 1.0 | True |
| H-02 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-03 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-04 | OK | 5 | 1.0 | 0.333 | 1.0 | True |
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
| H-13 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-14 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-15 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-16 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-17 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-18 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-19 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-20 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-21 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-22 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-23 | OK | 5 | 1.0 | 0.5 | 1.0 | True |
| H-24 | OK | 5 | 1.0 | 1.0 | 1.0 | True |
| H-25 | OK | 5 | None | None | None | True |
| H-26 | OK | 5 | None | None | None | True |
| H-27 | OK | 5 | 1.0 | 1.0 | 1.0 | True |