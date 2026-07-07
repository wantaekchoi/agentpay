create table mandates (
    id            uuid primary key,
    user_id       uuid         not null references users(id),
    agent_id      uuid         not null references agents(id),
    currency      varchar(32)  not null,
    per_tx_limit  numeric(78,0) not null,
    total_limit   numeric(78,0) not null,
    spent         numeric(78,0) not null default 0,
    allow_any_payee boolean     not null default false,
    valid_from    bigint       not null,
    valid_until   bigint       not null,
    nonce         numeric(78,0) not null,
    user_signature varchar(200) not null,
    status        varchar(20)  not null,
    created_at    timestamptz  not null default now(),
    constraint uq_mandate_user_nonce unique (user_id, nonce)
);
create table mandate_allowed_payees (
    mandate_id uuid not null references mandates(id) on delete cascade,
    payee      varchar(64) not null,
    primary key (mandate_id, payee)
);
