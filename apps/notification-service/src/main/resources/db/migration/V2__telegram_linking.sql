create table if not exists telegram_links (
    id uuid primary key,
    user_id uuid not null,
    telegram_user_id bigint not null,
    chat_id bigint not null,
    telegram_username varchar(120),
    active boolean not null default true,
    telegram_enabled boolean not null default true,
    notify_assignments boolean not null default true,
    notify_tests boolean not null default true,
    notify_grades boolean not null default true,
    notify_schedule boolean not null default true,
    notify_materials boolean not null default true,
    notify_system boolean not null default true,
    connected_at timestamp(6) with time zone,
    disconnected_at timestamp(6) with time zone,
    last_seen_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_telegram_links_user_id_active on telegram_links (user_id, active);
create index if not exists idx_telegram_links_telegram_user_id_active on telegram_links (telegram_user_id, active);
create index if not exists idx_telegram_links_chat_id_active on telegram_links (chat_id, active);

create unique index if not exists uq_telegram_links_user_active_true
    on telegram_links (user_id)
    where active = true;

create unique index if not exists uq_telegram_links_telegram_user_active_true
    on telegram_links (telegram_user_id)
    where active = true;

create unique index if not exists uq_telegram_links_chat_active_true
    on telegram_links (chat_id)
    where active = true;

create table if not exists telegram_connect_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(128) not null unique,
    status varchar(20) not null,
    expires_at timestamp(6) with time zone not null,
    used_at timestamp(6) with time zone,
    revoked_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_telegram_connect_tokens_user_id on telegram_connect_tokens (user_id);
create index if not exists idx_telegram_connect_tokens_status_expires on telegram_connect_tokens (status, expires_at);
create unique index if not exists idx_telegram_connect_tokens_token_hash on telegram_connect_tokens (token_hash);
