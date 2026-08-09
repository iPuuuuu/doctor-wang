alter table report_processing_job add column next_attempt_at timestamptz;
alter table report_processing_job add column locked_at timestamptz;
alter table report_processing_job add column locked_by varchar(128);
alter table report_processing_job add column lease_expires_at timestamptz;
alter table report_processing_job add column last_error_at timestamptz;
alter table report_processing_job add column result_digest varchar(64);
alter table report_processing_job add column result_version varchar(128);
create index idx_report_processing_job_schedule on report_processing_job(status, next_attempt_at);
create index idx_report_processing_job_lease on report_processing_job(status, lease_expires_at);
create unique index uq_report_processing_job_report_id on report_processing_job(report_id);

create table report_upload_idempotency (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    idempotency_key varchar(128) not null,
    request_digest varchar(64) not null,
    report_id bigint not null references medical_report(id) on delete cascade,
    processing_job_id uuid not null references report_processing_job(id) on delete cascade,
    response_snapshot text not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    unique(user_id, idempotency_key)
);
create index idx_report_upload_idempotency_expires_at on report_upload_idempotency(expires_at);
create index idx_report_upload_idempotency_report_id on report_upload_idempotency(report_id);
