alter table if exists academic_semesters
    drop constraint if exists uk_academic_semesters_semester_number;

alter table if exists academic_semesters
    drop constraint if exists academic_semesters_semester_number_key;

drop index if exists uk_academic_semesters_semester_number;
drop index if exists academic_semesters_semester_number_key;
