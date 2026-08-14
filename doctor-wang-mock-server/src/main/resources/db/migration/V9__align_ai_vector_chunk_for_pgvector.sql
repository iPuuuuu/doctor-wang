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
create index if not exists idx_ai_vector_chunk_embedding on ai_vector_chunk using hnsw (embedding vector_cosine_ops);
