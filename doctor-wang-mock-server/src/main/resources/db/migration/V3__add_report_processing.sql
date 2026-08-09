create table medical_report_file (
    id bigserial primary key,
    report_id bigint not null references medical_report(id) on delete cascade,
    storage_key varchar(512) not null unique,
    original_file_name varchar(255) not null,
    mime_type varchar(128) not null,
    file_size bigint not null,
    sha256 varchar(64) not null,
    created_at timestamptz not null default now()
);
create index idx_medical_report_file_report_id on medical_report_file(report_id);

create table report_processing_job (
    id uuid primary key,
    report_id bigint not null references medical_report(id) on delete cascade,
    status varchar(32) not null,
    stage varchar(32) not null,
    attempt_count integer not null default 0,
    retryable boolean not null default false,
    error_code varchar(64),
    trace_id varchar(128) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
);
create index idx_report_processing_job_report_id on report_processing_job(report_id);
create index idx_report_processing_job_status on report_processing_job(status);

create table medical_report_text (
    id bigserial primary key,
    report_id bigint not null references medical_report(id) on delete cascade,
    source_file_id bigint not null references medical_report_file(id) on delete cascade,
    text_content text not null,
    extraction_method varchar(64) not null,
    extraction_confidence numeric(5,4),
    language varchar(16),
    created_at timestamptz not null default now()
);
create index idx_medical_report_text_report_id on medical_report_text(report_id);

create table medical_report_chunk (
    id bigserial primary key,
    report_id bigint not null references medical_report(id) on delete cascade,
    text_id bigint not null references medical_report_text(id) on delete cascade,
    chunk_index integer not null,
    content_hash varchar(64) not null,
    content_ref varchar(512),
    token_count integer,
    embedding_model varchar(128),
    vector_namespace varchar(256) not null,
    created_at timestamptz not null default now(),
    unique(report_id, chunk_index)
);
create index idx_medical_report_chunk_report_id on medical_report_chunk(report_id);

create table medical_report_citation (
    id bigserial primary key,
    report_id bigint not null references medical_report(id) on delete cascade,
    source_type varchar(32) not null,
    source_ref varchar(512) not null,
    quote_or_span text,
    document_version varchar(128),
    created_at timestamptz not null default now()
);
create index idx_medical_report_citation_report_id on medical_report_citation(report_id);

create table ai_audit_event (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    report_id bigint references medical_report(id) on delete set null,
    job_id uuid references report_processing_job(id) on delete set null,
    event_type varchar(64) not null,
    model_version varchar(128),
    prompt_version varchar(128),
    safety_policy_version varchar(128),
    trace_id varchar(128) not null,
    metadata_json text,
    created_at timestamptz not null default now()
);
create index idx_ai_audit_event_user_id on ai_audit_event(user_id);
create index idx_ai_audit_event_report_id on ai_audit_event(report_id);
