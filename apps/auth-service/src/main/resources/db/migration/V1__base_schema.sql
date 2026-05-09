create table if not exists users (
    id uuid primary key,
    username varchar(50) not null unique,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    force_password_change boolean not null default false
);

alter table if exists users
    add column if not exists force_password_change boolean not null default false;

create table if not exists user_roles (
    user_id uuid not null,
    role varchar(20) not null,
    primary key (user_id, role)
);

create index if not exists idx_user_roles_user_id on user_roles (user_id);

create table if not exists refresh_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(512) not null unique,
    expires_at timestamp(6) with time zone not null,
    revoked boolean not null default false,
    mfa_verified boolean not null default false,
    mfa_method varchar(30),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

alter table if exists refresh_tokens
    add column if not exists mfa_verified boolean not null default false;

alter table if exists refresh_tokens
    add column if not exists mfa_method varchar(30);

create index if not exists idx_refresh_tokens_user_id on refresh_tokens (user_id);
create index if not exists idx_refresh_tokens_token_hash on refresh_tokens (token_hash);

create table if not exists password_reset_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(512) not null unique,
    expires_at timestamp(6) with time zone not null,
    used boolean not null default false,
    revoked boolean not null default false,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_password_reset_tokens_user_id on password_reset_tokens (user_id);
create index if not exists idx_password_reset_tokens_token on password_reset_tokens (token_hash);

create table if not exists user_bans (
    id uuid primary key,
    user_id uuid not null,
    reason varchar(500) not null,
    expires_at timestamp(6) with time zone,
    active boolean not null default true,
    created_by uuid not null,
    created_at timestamp(6) with time zone not null
);

create index if not exists idx_user_bans_user_id on user_bans (user_id);
create index if not exists idx_user_bans_active on user_bans (active);

create table if not exists auth_outbox_events (
    id uuid primary key,
    topic varchar(255) not null,
    message_key varchar(255) not null,
    event_type varchar(255) not null,
    payload_type varchar(500) not null,
    payload_json text not null,
    status varchar(20) not null,
    attempt_count integer not null,
    next_attempt_at timestamp(6) with time zone not null,
    processing_started_at timestamp(6) with time zone,
    published_at timestamp(6) with time zone,
    last_error varchar(2000),
    published_partition integer,
    published_offset bigint,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_auth_outbox_status_next_attempt on auth_outbox_events (status, next_attempt_at);
create index if not exists idx_auth_outbox_created_at on auth_outbox_events (created_at);
create index if not exists idx_auth_outbox_processing_started_at on auth_outbox_events (processing_started_at);

create table if not exists user_mfa_methods (
    id uuid primary key,
    user_id uuid not null,
    method_type varchar(30) not null,
    enabled boolean not null,
    preferred boolean not null,
    secret_encrypted varchar(2048),
    metadata_json text,
    enabled_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists uk_user_mfa_methods_user_method on user_mfa_methods (user_id, method_type);
create index if not exists idx_user_mfa_methods_user_enabled on user_mfa_methods (user_id, enabled);

create table if not exists mfa_challenges (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(128) not null unique,
    available_methods_csv varchar(200) not null,
    selected_method varchar(30),
    status varchar(30) not null,
    verification_code_hash varchar(128),
    verification_code_expires_at timestamp(6) with time zone,
    dispatch_count integer not null,
    verification_attempts integer not null,
    challenge_number integer,
    ip_address varchar(128),
    user_agent varchar(1000),
    selected_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index if not exists idx_mfa_challenges_token_hash on mfa_challenges (token_hash);
create index if not exists idx_mfa_challenges_user_status on mfa_challenges (user_id, status);
create index if not exists idx_mfa_challenges_expires_at on mfa_challenges (expires_at);
