-- V9: 对齐 ai_vector_chunk 与 AI Service PgVectorStore 契约（ADR-0006 / RAG-001 生产化）。
--
-- 背景：V6 创建了固定 1536 维、metadata_json text 的表；生产栈使用可配置 embedding
-- （智谱 embedding-3 = 2048 维）与 jsonb metadata。V6 的表从未被写入（AI Service
-- 此前从未连接 PostgreSQL），因此 drop-recreate 是安全的纠偏迁移；
-- 既有 V6 文件本身禁止修改（AGENTS.md）。
drop table if exists ai_vector_chunk;

create table ai_vector_chunk (
    id bigserial primary key,
    namespace varchar(256) not null,
    chunk_id varchar(256) not null,
    content text not null,
    embedding vector(2048) not null,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    unique (namespace, chunk_id)
);

create index if not exists idx_ai_vector_chunk_namespace on ai_vector_chunk(namespace);
-- 注意：pgvector 的 HNSW/ivfflat ANN 索引上限 2000 维；vector(2048)（智谱 embedding-3）
-- 不能建 ANN 索引，故此处仅保留 namespace btree 索引，检索走精确扫描
-- （ORDER BY embedding <=> 查询向量，个人健康场景按 namespace 数据量小，精确扫描足够）。
-- 未来若需 ANN：切换 ≤2000 维的 embedding provider 后再加
--   create index ... using hnsw (embedding vector_cosine_ops);
