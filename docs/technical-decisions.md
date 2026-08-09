# Technical Decisions

## 2026-08-08 — AI Harness structure

Decision: Use `.ai/` as the coordination layer and `docs/` as the human-facing reference layer.

Why: This keeps operational instructions close to the agents while preserving durable project documentation.

How to apply: New agent roles, workflows, state, and reports go under `.ai/`; project-level summaries and roadmaps go under `docs/`.

## 2026-08-08 — Preserve existing context files

Decision: Keep the existing `AGENTS.md` and `.ai/context.md` contents and extend them rather than replacing them with unrelated text.

Why: The repository already carries useful project constraints and baseline context.

How to apply: Future updates should append or refine, not erase, unless the content becomes incorrect.