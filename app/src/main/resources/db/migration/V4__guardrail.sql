create table guardrail_inspection (
    trace_id           varchar(64)  primary key,
    subject_id         varchar(200) not null,
    action             varchar(100) not null,
    status             varchar(20)  not null,
    reasons            text[]       not null default '{}',
    injection          boolean      not null default false,
    pii_masked         boolean      not null default false,
    providers          text[]       not null default '{}',
    sanitized_message  text,
    semantic_risk      numeric,
    semantic_label     varchar(20),
    created_at         timestamptz  not null default now()
);
