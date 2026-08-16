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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["vector", "hybrid"], default="vector")
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

    print(f"[eval] mode={args.mode} cases={len(dataset)} db_url={'yes' if settings.database_url else 'no'}")
    results = []
    for case in dataset:
        request = RagRequest(
            requestId=f"eval-{case['id']}",
            userId=1,
            reportId=case["reportId"],
            question=case["question"],
            namespaceHints=[case["namespace"]],
            topK=5,
            traceId=f"eval-{case['id']}",
        )
        resp = query_rag(request, store, embedder, generator)
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
        if resp.status == "OK":
            row.update(score_faithfulness(generator, answer, context))
            row.update(score_coverage(generator, answer, case["golden"]))
            row.update(score_relevance(generator, case["question"], answer))
            row["decline_ok"] = True
        else:
            # INSUFFICIENT_EVIDENCE / SAFETY_BLOCKED：按无证据处理路径评估
            row["faithfulness"] = None
            row.update(score_coverage(generator, answer, case["golden"]))
            row["relevance"] = None
            row.update(score_decline(generator, case["question"], answer))
            row["status_expected"] = "decline-path"
        results.append(row)
        print(f"  [{row['id']}] {resp.status} ans={len(answer)} chars citations={row['citations']}")

    # 聚合
    ok_rows = [r for r in results if r["status"] == "OK"]
    decline_rows = [r for r in results if r["status"] != "OK"]
    f = [r["faithfulness"] for r in ok_rows if r.get("faithfulness") is not None]
    c = [r["coverage"] for r in ok_rows if r.get("coverage") is not None]
    rv = [r["relevance"] for r in ok_rows if r.get("relevance") is not None]
    decline_pass = [r for r in decline_rows if r.get("decline_ok")]
    metrics = {
        "mode": args.mode,
        "cases_total": len(results),
        "cases_ok": len(ok_rows),
        "cases_decline_path": len(decline_rows),
        "avg_faithfulness": round(sum(f) / len(f), 3) if f else None,
        "avg_coverage": round(sum(c) / len(c), 3) if c else None,
        "avg_relevance": round(sum(rv) / len(rv), 3) if rv else None,
        "decline_pass_rate": round(len(decline_pass) / len(decline_rows), 3) if decline_rows else None,
        "citation_valid_rate": round(sum(1 for r in results if r.get("citation_valid")) / len(results), 3),
    }
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# RAG 评估报告", "", f"- 模式: {args.mode}", f"- 测试数: {metrics['cases_total']}（证据题 {metrics['cases_ok']} / 无证据拒绝题 {metrics['cases_decline_path']}）", ""]
    lines += ["| 指标 | 数值 |", "|---|---|"]
    for k, v in metrics.items():
        lines.append(f"| {k} | {v} |")
    lines += ["", "## 逐题明细", "", "| ID | 状态 | 引用 | 忠实度 | 覆盖 | 相关性 | 拒绝通过 |", "|---|---|---|---|---|---|---|"]
    for r in results:
        lines.append(f"| {r['id']} | {r['status']} | {r['citations']} | {r.get('faithfulness', '-')} | {r.get('coverage', '-')} | {r.get('relevance', '-')} | {r.get('decline_ok', '-')} |")
    (out_dir / "report.md").write_text("\n".join(lines), encoding="utf-8")
    print("\n== 指标 ==")
    for k, v in metrics.items():
        print(f"  {k}: {v}")
    print(f"[eval] 报告已写入 {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
