"""Doctor-Wang RAG 评估（RAGAS 风格：忠实度/证据覆盖/相关性/拒绝正确性）。

用法（在 ai-service 容器内，需 DW_AI_* 真实环境变量）：
    python -m evaluation.run_evaluation [--mode vector|hybrid] [--out evaluation/report]

流程：
1. 读取 evaluation/dataset.json 测试集
2. 对每题走真实生产管线（query_rag：风险分流→检索→生成），收集回答与引用
3. 用 DeepSeek 作为 judge 打分：忠实度（答案声明是否都被检索上下文支持）、
   证据覆盖（黄金证据点是否被回答覆盖）、相关性、拒绝正确性（无证据时是否诚实拒答）
4. 输出 metrics.json + report.md
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings  # noqa: E402
from app.providers import OpenAICompatibleEmbeddingProvider, OpenAICompatibleGenerationProvider  # noqa: E402
from app.rag import query_rag  # noqa: E402
from app.schemas import RagRequest  # noqa: E402
from app.vectorstore import create_vector_store  # noqa: E402

JUDGE_SYSTEM = "你是严格的医疗 RAG 评估员。只输出合法 JSON，不要输出其他文字。"


def _extract_json(text: str) -> dict:
    """从 LLM 输出中稳健提取 JSON 对象。"""
    m = re.search(r"\{.*\}", text, re.DOTALL)
    if not m:
        return {}
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return {}


def judge(generator: OpenAICompatibleGenerationProvider, prompt: str) -> dict:
    try:
        return _extract_json(generator.generate("请评估", prompt, system_prompt=JUDGE_SYSTEM))
    except Exception:
        return {}


def score_faithfulness(generator, answer: str, context: str) -> dict:
    prompt = (
        "请判断回答中的每个陈述是否都能被给出的上下文支持。\n"
        f"上下文:\n{context[:4000]}\n\n回答:\n{answer[:2000]}\n\n"
        "输出 JSON: {\"total_claims\": 数字, \"supported_claims\": 数字, \"score\": 0到1的小数}"
    )
    d = judge(generator, prompt)
    total = d.get("total_claims")
    supported = d.get("supported_claims")
    if isinstance(total, int) and isinstance(supported, int) and total > 0:
        return {"faithfulness": round(supported / total, 3), "claims": total, "supported": supported}
    return {"faithfulness": None, "claims": None, "supported": None}


def score_coverage(generator, answer: str, golden: list[str]) -> dict:
    if not golden:
        return {"coverage": None, "covered": None, "total": 0}
    prompt = (
        "回答是否覆盖了以下每个证据点？对每个证据点给出 true/false。\n"
        f"证据点: {json.dumps(golden, ensure_ascii=False)}\n\n回答:\n{answer[:2000]}\n\n"
        "输出 JSON: {\"covered_points\": [true/false...], \"covered_count\": 数字}"
    )
    d = judge(generator, prompt)
    points = d.get("covered_points")
    if isinstance(points, list) and len(points) == len(golden):
        covered = sum(1 for p in points if p is True)
        return {"coverage": round(covered / len(golden), 3), "covered": covered, "total": len(golden)}
    covered = d.get("covered_count")
    if isinstance(covered, int) and len(golden) > 0:
        return {"coverage": round(min(covered, len(golden)) / len(golden), 3), "covered": min(covered, len(golden)), "total": len(golden)}
    return {"coverage": None, "covered": None, "total": len(golden)}


def score_relevance(generator, question: str, answer: str) -> dict:
    prompt = (
        "回答与问题的相关性和完整度打分（0 到 1）：\n"
        f"问题: {question}\n回答: {answer[:2000]}\n\n"
        "输出 JSON: {\"score\": 0到1的小数}"
    )
    d = judge(generator, prompt)
    s = d.get("score")
    if isinstance(s, (int, float)):
        return {"relevance": round(float(s), 3)}
    return {"relevance": None}


def score_decline(generator, question: str, answer: str) -> dict:
    prompt = (
        "该问题没有可检索依据，判断回答是否正确处理：\n"
        "1) 是否明确说明没有证据/依据（而不是编造）？ 2) 是否给出了就医或安全边界提示？\n"
        f"问题: {question}\n回答: {answer[:2000]}\n\n"
        "输出 JSON: {\"declined\": true/false, \"gave_boundary\": true/false}"
    )
    d = judge(generator, prompt)
    return {
        "decline_ok": d.get("declined") is True and d.get("gave_boundary") is not False,
        "declined": d.get("declined") is True,
        "boundary": d.get("gave_boundary") is True,
    }


def run_mode(args, mode: str, engine: str, store, embedder, generator, dataset) -> tuple[list[dict], dict]:
    """跑一个 (检索模式 × 生成引擎) 组合，返回 (逐题明细, 聚合指标)。"""
    def vector_searcher(namespace, qv, qt, k):
        return store.search(namespace, qv, k)

    def hybrid_searcher(namespace, qv, qt, k):
        from app.rag import search
        return search(store, namespace, qv, qt, k)

    searcher = vector_searcher if mode == "vector" else hybrid_searcher
    print(f"[eval] mode={mode} engine={engine} cases={len(dataset)}")
    results = []
    for case in dataset:
        request = RagRequest(
            requestId=f"eval-{mode}-{engine}-{case['id']}",
            userId=1,
            reportId=case["reportId"],
            question=case["question"],
            namespaceHints=[case["namespace"]],
            topK=5,
            traceId=f"eval-{mode}-{engine}-{case['id']}",
        )
        if engine == "agentic":
            from app.agentic_rag import agentic_rag_graph
            resp = agentic_rag_graph.invoke({"request": request, "store": store, "embedder": embedder,
                                             "generator": generator, "searcher": searcher, "corrections": 0})["response"]
        else:
            resp = query_rag(request, store, embedder, generator, searcher=searcher)
        answer = resp.summary
        context = "\n".join(c.excerpt for c in resp.citations)
        row = {
            "id": case["id"],
            "question": case["question"],
            "status": resp.status,
            "answer": answer[:500],
            "citations": len(resp.citations),
            "citation_valid": all(c.sourceId == str(case["reportId"]) for c in resp.citations),
            "expectDecline": case["expectDecline"],
        }
        if case["expectDecline"]:
            # 无证据/越界类题目：评估拒绝正确性（诚实拒答 + 就医边界），不评忠实度
            row["faithfulness"] = None
            row["coverage"] = None
            row["relevance"] = None
            row.update(score_decline(generator, case["question"], answer))
        elif resp.status == "OK":
            row.update(score_faithfulness(generator, answer, context))
            row.update(score_coverage(generator, answer, case["golden"]))
            row.update(score_relevance(generator, case["question"], answer))
            row["decline_ok"] = True
        else:
            row["faithfulness"] = None
            row.update(score_coverage(generator, answer, case["golden"]))
            row["relevance"] = None
            row.update(score_decline(generator, case["question"], answer))
        results.append(row)
        print(f"  [{row['id']}] {resp.status} ans={len(answer)} chars citations={row['citations']}")
    evidence_rows = [r for r in results if not r["expectDecline"]]
    decline_rows = [r for r in results if r["expectDecline"]]
    f = [r["faithfulness"] for r in evidence_rows if r.get("faithfulness") is not None]
    c = [r["coverage"] for r in evidence_rows if r.get("coverage") is not None]
    rv = [r["relevance"] for r in evidence_rows if r.get("relevance") is not None]
    decline_pass = [r for r in decline_rows if r.get("decline_ok")]
    metrics = {
        "mode": mode,
        "engine": engine,
        "cases_total": len(results),
        "cases_evidence": len(evidence_rows),
        "cases_decline": len(decline_rows),
        "avg_faithfulness": round(sum(f) / len(f), 3) if f else None,
        "avg_coverage": round(sum(c) / len(c), 3) if c else None,
        "avg_relevance": round(sum(rv) / len(rv), 3) if rv else None,
        "decline_pass_rate": round(len(decline_pass) / len(decline_rows), 3) if decline_rows else None,
        "citation_valid_rate": round(sum(1 for r in results if r.get("citation_valid")) / len(results), 3),
    }
    return results, metrics


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["vector", "hybrid", "both"], default="both")
    parser.add_argument("--engine", choices=["single", "agentic", "both"], default="both")
    parser.add_argument("--dataset", default=str(Path(__file__).parent / "dataset.json"))
    parser.add_argument("--out", default=str(Path(__file__).parent / "report"))
    args = parser.parse_args()

    dataset = json.loads(Path(args.dataset).read_text(encoding="utf-8"))
    store = create_vector_store(settings.database_url)
    embedder = OpenAICompatibleEmbeddingProvider(
        settings.embedding_endpoint, settings.embedding_api_key, settings.embedding_model,
        dimensions=settings.embedding_dimensions, timeout=settings.provider_timeout_seconds,
    )
    generator = OpenAICompatibleGenerationProvider(
        settings.llm_endpoint, settings.llm_api_key, settings.llm_model, timeout=settings.provider_timeout_seconds,
    )

    modes = ["vector", "hybrid"] if args.mode == "both" else [args.mode]
    engines = ["single", "agentic"] if args.engine == "both" else [args.engine]
    all_metrics = []
    all_results = {}
    for mode in modes:
        for engine in engines:
            results, metrics = run_mode(args, mode, engine, store, embedder, generator, dataset)
            all_metrics.append(metrics)
            all_results[f"{mode}-{engine}"] = results

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "metrics.json").write_text(json.dumps(all_metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = ["# RAG 评估报告", "", f"- 测试数: {len(dataset)}（证据题/无证据拒绝题按组合统计）", "", "## 指标对比", ""]
    header = ["| 指标 |"] + [f" {m['mode']}-{m['engine']} |" for m in all_metrics]
    lines.append("".join(header))
    lines.append("|" + "---|" * (len(all_metrics) + 1))
    for key in ["cases_evidence", "cases_decline", "avg_faithfulness", "avg_coverage", "avg_relevance", "decline_pass_rate", "citation_valid_rate"]:
        lines.append(f"| {key} | " + " | ".join(str(m.get(key)) for m in all_metrics) + " |")
    for combo, results in all_results.items():
        lines += ["", f"## 逐题明细（{combo}）", "", "| ID | 状态 | 引用 | 忠实度 | 覆盖 | 相关性 | 拒绝通过 |", "|---|---|---|---|---|---|---|"]
        for r in results:
            lines.append(f"| {r['id']} | {r['status']} | {r['citations']} | {r.get('faithfulness', '-')} | {r.get('coverage', '-')} | {r.get('relevance', '-')} | {r.get('decline_ok', '-')} |")
    (out_dir / "report.md").write_text("\n".join(lines), encoding="utf-8")
    print("\n== 指标 ==")
    for m in all_metrics:
        print("  ", json.dumps(m, ensure_ascii=False))
    print(f"[eval] 报告已写入 {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
