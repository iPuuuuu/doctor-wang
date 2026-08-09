create table report_cleanup_task (
    id bigserial primary key,
    report_id bigint references medical_report(id) on delete set null,
    storage_key varchar(512) not null,
    reason varchar(64) not null,
    status varchar(32) not null default 'PENDING',
    attempt_count integer not null default 0,
    next_attempt_at timestamptz not null default now(),
    trace_id varchar(128) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_report_cleanup_task_schedule on report_cleanup_task(status, next_attempt_at);
