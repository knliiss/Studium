create table if not exists notifications (
    id uuid primary key,
    user_id uuid not null,
    type varchar(30) not null,
    category varchar(30) not null,
    title varchar(200) not null,
    body varchar(1000) not null,
    payload_json text,
    status varchar(20) not null default 'UNREAD',
    source_event_id uuid unique,
    source_event_type varchar(150),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    read_at timestamp(6) with time zone
);

create index if not exists idx_notifications_user_status_created_at on notifications (user_id, status, created_at);
create index if not exists idx_notifications_user_created_at on notifications (user_id, created_at);
create unique index if not exists idx_notifications_source_event_id on notifications (source_event_id);
