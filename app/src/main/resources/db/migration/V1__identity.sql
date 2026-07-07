create table users (
    id         uuid primary key,
    alias      varchar(100) not null,
    public_key varchar(200) not null,
    address    varchar(64)  not null unique
);

create table agents (
    id            uuid primary key,
    owner_user_id uuid         not null references users(id),
    public_key    varchar(200) not null,
    address       varchar(64)  not null unique,
    did           varchar(200) not null,
    alias         varchar(100) not null,
    status        varchar(20)  not null
);
