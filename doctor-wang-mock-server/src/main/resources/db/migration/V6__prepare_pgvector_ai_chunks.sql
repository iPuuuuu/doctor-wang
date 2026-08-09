create extension if not exists vector;
create table if not exists ai_vector_chunk (
 id bigserial primary key, report_id bigint not null references medical_report(id) on delete cascade,
 user_id bigint not null references app_user(id) on delete cascade, namespace varchar(256) not null,
 chunk_id varchar(256) not null, content text not null, embedding vector(1536) not null,
 metadata_json text not null default '{}', index_version varchar(128) not null,
 created_at timestamptz not null default now(), unique(namespace, chunk_id)
);
create index if not exists idx_ai_vector_chunk_report on ai_vector_chunk(report_id, user_id);
create index if not exists idx_ai_vector_chunk_namespace on ai_vector_chunk(namespace);
create index if not exists idx_ai_vector_chunk_embedding on ai_vector_chunk using hnsw (embedding vector_cosine_ops);
