create table app_user (
    id bigserial primary key,
    user_id varchar(64) not null unique,
    account varchar(255) not null unique,
    password varchar(255) not null,
    name varchar(255) not null,
    age integer not null,
    height integer not null,
    weight numeric(5,2) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table user_session (
    id bigserial primary key,
    token varchar(255) not null unique,
    user_id bigint not null references app_user(id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);

create index idx_user_session_user_id on user_session(user_id);
create index idx_user_session_expires_at on user_session(expires_at);

create table health_profile_record (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    record_date varchar(32) not null,
    record_type varchar(255) not null,
    created_at timestamptz not null default now()
);

create index idx_health_profile_record_user_id on health_profile_record(user_id);

create table health_profile_disease (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    disease_name varchar(255) not null,
    created_at timestamptz not null default now()
);

create index idx_health_profile_disease_user_id on health_profile_disease(user_id);

create table chat_message (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    role varchar(32) not null,
    content text not null,
    created_at timestamptz not null default now()
);

create index idx_chat_message_user_id on chat_message(user_id);
create index idx_chat_message_created_at on chat_message(created_at);

create table medical_report (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    title varchar(255) not null,
    report_date varchar(32) not null,
    report_type varchar(255) not null,
    summary text not null,
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_medical_report_user_id on medical_report(user_id);

create table medical_report_finding (
    id bigserial primary key,
    report_id bigint not null references medical_report(id) on delete cascade,
    finding_text text not null,
    created_at timestamptz not null default now()
);

create index idx_medical_report_finding_report_id on medical_report_finding(report_id);

create table medical_report_suggestion (
    id bigserial primary key,
    report_id bigint not null references medical_report(id) on delete cascade,
    suggestion_text text not null,
    created_at timestamptz not null default now()
);

create index idx_medical_report_suggestion_report_id on medical_report_suggestion(report_id);
