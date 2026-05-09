create table if not exists audit_events (
    id uuid primary key,
    actor_user_id uuid not null,
    action varchar(100) not null,
    entity_type varchar(100) not null,
    entity_id uuid not null,
    old_value_json text,
    new_value_json text,
    occurred_at timestamp(6) with time zone not null,
    request_id varchar(100),
    source_service varchar(100) not null
);

create index if not exists idx_audit_events_actor_user_id on audit_events (actor_user_id);
create index if not exists idx_audit_events_entity_type_entity_id on audit_events (entity_type, entity_id);
create index if not exists idx_audit_events_occurred_at on audit_events (occurred_at);
create index if not exists idx_audit_events_source_service on audit_events (source_service);
