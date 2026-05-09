create table if not exists user_profiles (
    id uuid primary key,
    user_id uuid not null unique,
    username varchar(100) not null,
    email varchar(255),
    display_name varchar(100) not null,
    avatar_file_key varchar(255),
    locale varchar(20),
    timezone varchar(50),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists idx_user_profiles_user_id on user_profiles (user_id);
