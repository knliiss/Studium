create table if not exists stored_files (
    id uuid primary key,
    owner_id uuid not null,
    original_file_name varchar(255) not null,
    object_key varchar(255) not null unique,
    bucket_name varchar(100) not null,
    content_type varchar(100) not null,
    size_bytes bigint not null,
    file_kind varchar(30) not null,
    access varchar(30) not null,
    status varchar(30) not null,
    deleted boolean not null default false,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    last_accessed_at timestamp(6) with time zone not null,
    scan_completed_at timestamp(6) with time zone,
    scan_status_message varchar(500),
    deleted_at timestamp(6) with time zone,
    deleted_by uuid
);

create index if not exists idx_stored_files_owner_id on stored_files (owner_id);
create index if not exists idx_stored_files_last_accessed_at on stored_files (last_accessed_at);
create index if not exists idx_stored_files_deleted on stored_files (deleted);
create unique index if not exists idx_stored_files_object_key on stored_files (object_key);
