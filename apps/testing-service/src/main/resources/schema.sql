alter table if exists tests
    add column if not exists max_points integer;

update tests
set max_points = 100
where max_points is null;

alter table if exists tests
    alter column max_points set default 100;

alter table if exists tests
    alter column max_points set not null;

alter table if exists tests
    add column if not exists order_index integer;

update tests
set order_index = 0
where order_index is null;

alter table if exists tests
    alter column order_index set default 0;

alter table if exists tests
    alter column order_index set not null;

alter table if exists questions
    add column if not exists type varchar(40);

update questions
set type = 'SINGLE_CHOICE'
where type is null;

alter table if exists questions
    alter column type set default 'SINGLE_CHOICE';

alter table if exists questions
    alter column type set not null;

alter table if exists questions
    add column if not exists points integer;

update questions
set points = 1
where points is null;

alter table if exists questions
    alter column points set default 1;

alter table if exists questions
    alter column points set not null;

alter table if exists questions
    add column if not exists order_index integer;

update questions
set order_index = 0
where order_index is null;

alter table if exists questions
    alter column order_index set default 0;

alter table if exists questions
    alter column order_index set not null;

alter table if exists questions
    add column if not exists required boolean;

update questions
set required = true
where required is null;

alter table if exists questions
    alter column required set default true;

alter table if exists questions
    alter column required set not null;
