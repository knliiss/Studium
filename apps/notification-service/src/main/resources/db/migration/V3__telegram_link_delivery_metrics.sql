alter table telegram_links
    add column if not exists telegram_sent_count bigint not null default 0,
    add column if not exists delivery_failure_count bigint not null default 0,
    add column if not exists last_delivery_failure varchar(300),
    add column if not exists last_delivery_failure_at timestamp(6) with time zone,
    add column if not exists last_delivered_at timestamp(6) with time zone;
