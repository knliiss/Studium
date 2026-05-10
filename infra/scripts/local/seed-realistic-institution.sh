#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/infra/docker/docker-compose.local.yml"
ENV_FILE="$ROOT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env is missing. Run ./infra/scripts/local/start-local.sh first or copy .env.example to .env." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

sql_scalar() {
  local statement="$1"
  compose exec -T postgres psql -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" -tA -c "$statement" | tr -d '[:space:]'
}

psql_file() {
  local file_path="$1"
  compose exec -T postgres psql -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" -v ON_ERROR_STOP=1 < "$file_path"
}

wait_for_gateway() {
  for _ in $(seq 1 60); do
    if curl -fsS "http://localhost:${GATEWAY_PORT:-8080}/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "Gateway did not become ready in time" >&2
  exit 1
}

REALISTIC_SEED_STUDENTS_PER_GROUP="${REALISTIC_SEED_STUDENTS_PER_GROUP:-12}"
REALISTIC_SEED_INCLUDE_SCHEDULE="${REALISTIC_SEED_INCLUDE_SCHEDULE:-true}"
REALISTIC_SEED_INCLUDE_ACTIVITY="${REALISTIC_SEED_INCLUDE_ACTIVITY:-false}"
REALISTIC_SEED_SPECIALTY_LIMIT="${REALISTIC_SEED_SPECIALTY_LIMIT:-}"
REALISTIC_SEED_GROUP_LIMIT="${REALISTIC_SEED_GROUP_LIMIT:-}"
REALISTIC_SEED_TEACHER_COUNT="${REALISTIC_SEED_TEACHER_COUNT:-32}"
REALISTIC_SEED_PASSWORD="${REALISTIC_SEED_PASSWORD:-DemoPass123!}"

SQL_FILE="$(mktemp)"
cleanup() { rm -f "$SQL_FILE"; }
trap cleanup EXIT

export REALISTIC_SEED_STUDENTS_PER_GROUP REALISTIC_SEED_INCLUDE_SCHEDULE REALISTIC_SEED_INCLUDE_ACTIVITY REALISTIC_SEED_SPECIALTY_LIMIT REALISTIC_SEED_GROUP_LIMIT REALISTIC_SEED_TEACHER_COUNT REALISTIC_SEED_PASSWORD
export AUTH_DB_SCHEMA PROFILE_DB_SCHEMA EDUCATION_DB_SCHEMA SCHEDULE_DB_SCHEMA ASSIGNMENT_DB_SCHEMA TESTING_DB_SCHEMA FILE_DB_SCHEMA MINIO_BUCKET_PRIVATE

python3 >"$SQL_FILE" <<'PY'
from __future__ import annotations
import os
import uuid
from datetime import date, timedelta

NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, "studium-realistic-institution-seed")
PASSWORD_HASH = "$2y$10$heT.PCewS4uMeBFvYF9sied4GOkE5oHiPg3ZDSENq9McXli1rRy/i"  # DemoPass123!
AUTH = os.getenv("AUTH_DB_SCHEMA", "auth")
PROFILE = os.getenv("PROFILE_DB_SCHEMA", "profile")
EDU = os.getenv("EDUCATION_DB_SCHEMA", "education")
SCHEDULE = os.getenv("SCHEDULE_DB_SCHEMA", "schedule")
ASSIGN = os.getenv("ASSIGNMENT_DB_SCHEMA", "assignment")
TESTING = os.getenv("TESTING_DB_SCHEMA", "testing")
FILE = os.getenv("FILE_DB_SCHEMA", "file")
BUCKET = os.getenv("MINIO_BUCKET_PRIVATE", "private")
STUDENTS_PER_GROUP = max(1, int(os.getenv("REALISTIC_SEED_STUDENTS_PER_GROUP", "12")))
TEACHER_COUNT = max(12, int(os.getenv("REALISTIC_SEED_TEACHER_COUNT", "32")))
INCLUDE_SCHEDULE = os.getenv("REALISTIC_SEED_INCLUDE_SCHEDULE", "true").lower() == "true"
SPECIALTY_LIMIT = os.getenv("REALISTIC_SEED_SPECIALTY_LIMIT", "").strip()
GROUP_LIMIT = os.getenv("REALISTIC_SEED_GROUP_LIMIT", "").strip()
SPECIALTY_LIMIT = int(SPECIALTY_LIMIT) if SPECIALTY_LIMIT else None
GROUP_LIMIT = int(GROUP_LIMIT) if GROUP_LIMIT else None
now = "now()"

def uid(key: str) -> str:
    return str(uuid.uuid5(NAMESPACE, key))

def q(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"

def emit(sql=""):
    print(sql)

def upsert_user(username, email, display_name, roles):
    user_id = uid(f"user:{username}")
    emit(f"insert into {AUTH}.users (id, username, email, password_hash, created_at, updated_at, force_password_change) values ({q(user_id)}, {q(username)}, {q(email)}, {q(PASSWORD_HASH)}, {now}, {now}, false) on conflict (username) do update set email = excluded.email, password_hash = excluded.password_hash, updated_at = now();")
    for role in sorted(set(roles + ["USER"])):
        emit(f"insert into {AUTH}.user_roles (user_id, role) values ({q(user_id)}, {q(role)}) on conflict do nothing;")
    emit(f"insert into {PROFILE}.user_profiles (id, user_id, username, email, display_name, avatar_file_key, locale, timezone, created_at, updated_at) values ({q(uid('profile:' + username))}, {q(user_id)}, {q(username)}, {q(email)}, {q(display_name)}, null, 'uk', 'Europe/Kyiv', {now}, {now}) on conflict (user_id) do update set username = excluded.username, email = excluded.email, display_name = excluded.display_name, locale = excluded.locale, timezone = excluded.timezone, updated_at = now();")
    return user_id

specialties = [
    ("LTC-SE", "Software Engineering", True, "Programming", {
        1: ["Mathematics I", "English for IT", "Programming Fundamentals", "Computer Architecture"],
        2: ["Object-Oriented Programming", "Algorithms and Data Structures", "Databases", "Web Development"],
        3: ["Software Architecture", "Testing and QA", "DevOps Fundamentals", "Project Management"],
        4: ["Cloud Applications", "Software Security", "Diploma Project Workshop", "Product Engineering"],
    }),
    ("LTC-CS", "Cybersecurity", True, "Cybersecurity", {
        1: ["Mathematics I", "English for IT", "Computer Networks", "Operating Systems"],
        2: ["Network Security", "Cryptography", "Linux Administration", "Security Monitoring"],
        3: ["Web Application Security", "Ethical Hacking Basics", "Digital Forensics", "Incident Response"],
        4: ["Cloud Security", "Security Architecture", "Diploma Security Lab", "Compliance Basics"],
    }),
    ("LTC-CE", "Computer Engineering", True, "Networks", {
        1: ["Mathematics I", "Digital Logic", "Computer Architecture", "English for IT"],
        2: ["Operating Systems", "Computer Networks", "Embedded Systems", "Electronics Basics"],
        3: ["Microcontrollers", "Network Infrastructure", "System Administration", "IoT Systems"],
        4: ["Industrial Automation", "Hardware Diagnostics", "Diploma Engineering Lab", "Technical Project Management"],
    }),
    ("LTC-DD", "Digital Design", False, "Design", {
        1: ["Design Fundamentals", "Graphic Design", "English for Design", "Academic Writing"],
        2: ["UI UX Design", "Web Design", "Typography", "Digital Illustration"],
        3: ["Motion Design", "Design Systems", "Product Design", "User Research"],
        4: ["Portfolio Studio", "Brand Systems", "Diploma Design Project", "Creative Management"],
    }),
    ("LTC-BA", "Business Analytics", False, "Business Analytics", {
        1: ["Economics", "Mathematics I", "English for Business", "Academic Writing"],
        2: ["Statistics", "Accounting Basics", "Data Analysis", "Business Communication"],
        3: ["Business Intelligence", "Databases for Analytics", "Financial Analytics", "Project Management"],
        4: ["Strategic Analytics", "Dashboard Design", "Diploma Analytics Project", "Data Storytelling"],
    }),
]
if SPECIALTY_LIMIT:
    specialties = specialties[:SPECIALTY_LIMIT]

teacher_departments = {
    "Programming": ["Andrii Melnyk", "Olena Kravets", "Taras Boyko", "Iryna Shevchenko", "Roman Hrytsenko", "Nazar Poliak"],
    "Databases": ["Marta Koval", "Serhii Danyliuk", "Yulia Bondar", "Petro Lytvyn"],
    "Networks": ["Viktor Marchenko", "Natalia Savchuk", "Oleh Klymenko", "Dmytro Rudenko"],
    "Cybersecurity": ["Bohdan Moroz", "Kateryna Tkachenko", "Mykola Sokolov", "Alina Pavlenko"],
    "Mathematics": ["Svitlana Horbach", "Vasyl Fedoruk", "Larysa Marchuk"],
    "English": ["Anna Collins", "Maryna Sydorenko", "Oleksandra Vovk"],
    "Design": ["Diana Hnatiuk", "Maksym Velychko", "Sofia Lysenko", "Yevhen Humen"],
    "Business Analytics": ["Ihor Mazur", "Tetiana Romaniuk", "Oksana Shpak", "Artem Bilous"],
    "Project Management": ["Pavlo Kostiuk", "Nina Fedorenko"],
}
teacher_ids_by_department = {}
count = 0
for department, names in teacher_departments.items():
    for name in names:
        count += 1
        if count > TEACHER_COUNT:
            break
        username = f"ltc.teacher.{count:02d}"
        teacher_ids_by_department.setdefault(department, []).append(upsert_user(username, f"{username}@ltc.local", name, ["TEACHER"]))
upsert_user("ltc.academic.admin", "ltc.academic.admin@ltc.local", "LTC Academic Administrator", ["ADMIN"])

def department_for_subject(title, fallback):
    rules = [("English", "English"), ("Academic", "English"), ("Mathematics", "Mathematics"), ("Statistics", "Mathematics"), ("Database", "Databases"), ("Network", "Networks"), ("Security", "Cybersecurity"), ("Cryptography", "Cybersecurity"), ("Design", "Design"), ("Typography", "Design"), ("Economics", "Business Analytics"), ("Accounting", "Business Analytics"), ("Analytics", "Business Analytics"), ("Business", "Business Analytics"), ("Project", "Project Management")]
    for needle, department in rules:
        if needle.lower() in title.lower():
            return department
    return fallback

emit("begin;")
subject_records = []
group_records = []
subject_teacher_map = {}
subject_groups_map = {}
group_count = 0

topic_titles = ["Course overview", "Core concepts", "Practice workshop", "Assessment preparation"]
for code, name, technical, fallback_department, subjects_by_year in specialties:
    specialty_id = uid(f"specialty:{code}")
    emit(f"insert into {EDU}.specialties (id, code, name, description, active, created_at, updated_at) values ({q(specialty_id)}, {q(code)}, {q(name)}, {q('Synthetic realistic specialty for Lviv Technical College.')}, true, {now}, {now}) on conflict (code) do update set name = excluded.name, description = excluded.description, active = true, updated_at = now();")
    for year in range(1, 5):
        stream_id = uid(f"stream:{code}:year:{year}")
        stream_name = f"{code}-Y{year}-STREAM"
        emit(f"insert into {EDU}.streams (id, name, specialty_id, study_year, active, created_at, updated_at) values ({q(stream_id)}, {q(stream_name)}, {q(specialty_id)}, {year}, true, {now}, {now}) on conflict (id) do update set name = excluded.name, specialty_id = excluded.specialty_id, study_year = excluded.study_year, active = true, updated_at = now();")
        for group_num in range(1, 3):
            if GROUP_LIMIT is not None and group_count >= GROUP_LIMIT:
                continue
            group_name = f"{code}-{year}{group_num}"
            group_id = uid(f"group:{group_name}")
            subgroup_mode = "TWO_SUBGROUPS" if technical else "NONE"
            group_records.append({"id": group_id, "name": group_name, "code": code, "name_full": name, "technical": technical, "year": year, "stream_id": stream_id, "subgroup_mode": subgroup_mode})
            group_count += 1
            emit(f"insert into {EDU}.groups (id, name, specialty_id, study_year, stream_id, subgroup_mode, created_at, updated_at) values ({q(group_id)}, {q(group_name)}, {q(specialty_id)}, {year}, {q(stream_id)}, {q(subgroup_mode)}, {now}, {now}) on conflict (id) do update set name = excluded.name, specialty_id = excluded.specialty_id, study_year = excluded.study_year, stream_id = excluded.stream_id, subgroup_mode = excluded.subgroup_mode, updated_at = now();")
            for sidx in range(1, STUDENTS_PER_GROUP + 1):
                username = f"ltc.{code.lower().replace('ltc-', '')}{year}{group_num}.{sidx:02d}"
                student_id = upsert_user(username, f"{username}@student.ltc.local", f"{group_name} Student {sidx:02d}", ["STUDENT"])
                subgroup = "ALL" if not technical else ("FIRST" if sidx <= (STUDENTS_PER_GROUP + 1) // 2 else "SECOND")
                emit(f"insert into {EDU}.group_students (id, group_id, user_id, role, subgroup, created_at, updated_at) values ({q(uid('group-student:' + group_id + ':' + student_id))}, {q(group_id)}, {q(student_id)}, 'STUDENT', {q(subgroup)}, {now}, {now}) on conflict (group_id, user_id) do update set subgroup = excluded.subgroup, role = excluded.role, updated_at = now();")
        for order, title in enumerate(subjects_by_year[year], start=1):
            semester_number = 1 if order <= 2 else 2
            subject_id = uid(f"subject:{code}:{year}:{title}")
            subject_name = f"{name} — {title}"
            emit(f"insert into {EDU}.subjects (id, name, group_id, description, created_at, updated_at) values ({q(subject_id)}, {q(subject_name)}, null, {q(title + ' course for realistic local data.')}, {now}, {now}) on conflict (id) do update set name = excluded.name, description = excluded.description, updated_at = now();")
            groups_for_subject = [g for g in group_records if g["code"] == code and g["year"] == year]
            subject_groups_map[subject_id] = [g["id"] for g in groups_for_subject]
            for g in groups_for_subject:
                emit(f"insert into {EDU}.subject_groups (id, subject_id, group_id, created_at, updated_at) values ({q(uid('subject-group:' + subject_id + ':' + g['id']))}, {q(subject_id)}, {q(g['id'])}, {now}, {now}) on conflict (subject_id, group_id) do update set updated_at = now();")
            department = department_for_subject(title, fallback_department)
            teachers = teacher_ids_by_department.get(department) or teacher_ids_by_department.get(fallback_department) or next(iter(teacher_ids_by_department.values()))
            selected_teachers = teachers[:2] if len(teachers) > 1 else teachers
            subject_teacher_map[subject_id] = selected_teachers
            for teacher_id in selected_teachers:
                emit(f"insert into {EDU}.subject_teachers (id, subject_id, teacher_id, created_at, updated_at) values ({q(uid('subject-teacher:' + subject_id + ':' + teacher_id))}, {q(subject_id)}, {q(teacher_id)}, {now}, {now}) on conflict (subject_id, teacher_id) do update set updated_at = now();")
            lab_count = 1 if technical and any(k in title.lower() for k in ['programming','database','web','security','network','systems','embedded','architecture','devops','cloud','testing']) else 0
            emit(f"insert into {EDU}.curriculum_plans (id, specialty_id, study_year, semester_number, subject_id, lecture_count, practice_count, lab_count, supports_stream_lecture, requires_subgroups_for_labs, active, created_at, updated_at) values ({q(uid('curriculum:' + code + ':' + str(year) + ':' + str(semester_number) + ':' + subject_id))}, {q(specialty_id)}, {year}, {semester_number}, {q(subject_id)}, 1, 1, {lab_count}, true, {q(lab_count > 0)}, true, {now}, {now}) on conflict (id) do update set lecture_count = excluded.lecture_count, practice_count = excluded.practice_count, lab_count = excluded.lab_count, supports_stream_lecture = true, requires_subgroups_for_labs = excluded.requires_subgroups_for_labs, active = true, updated_at = now();")
            subject_records.append({"id": subject_id, "title": title, "code": code, "name": name, "technical": technical, "year": year, "semester": semester_number})
            for topic_index, topic_title in enumerate(topic_titles, start=1):
                topic_id = uid(f"topic:{subject_id}:{topic_index}")
                emit(f"insert into {EDU}.topics (id, subject_id, title, order_index, created_at, updated_at) values ({q(topic_id)}, {q(subject_id)}, {q(topic_title)}, {topic_index}, {now}, {now}) on conflict (subject_id, order_index) do update set title = excluded.title, updated_at = now();")
                teacher = selected_teachers[0]
                emit(f"insert into {EDU}.lectures (id, subject_id, topic_id, title, content, status, order_index, created_by_user_id, created_at, updated_at) values ({q(uid('lecture:' + topic_id))}, {q(subject_id)}, {q(topic_id)}, {q(topic_title + ': ' + title)}, {q('Lecture notes, examples, and classroom discussion points for ' + title + '.')}, 'PUBLISHED', 1, {q(teacher)}, {now}, {now}) on conflict (id) do update set title = excluded.title, content = excluded.content, status = 'PUBLISHED', updated_at = now();")
                emit(f"insert into {EDU}.topic_materials (id, topic_id, title, description, type, url, file_id, original_file_name, content_type, size_bytes, visible, archived, order_index, created_by_user_id, created_at, updated_at) values ({q(uid('material:text:' + topic_id))}, {q(topic_id)}, 'Key points', 'Summary, keywords, and checklist for the topic.', 'TEXT', null, null, null, null, null, true, false, 2, {q(teacher)}, {now}, {now}) on conflict (id) do update set description = excluded.description, visible = true, archived = false, updated_at = now();")
                emit(f"insert into {EDU}.topic_materials (id, topic_id, title, description, type, url, file_id, original_file_name, content_type, size_bytes, visible, archived, order_index, created_by_user_id, created_at, updated_at) values ({q(uid('material:link:' + topic_id))}, {q(topic_id)}, 'Reference link', 'External reference for independent study.', 'LINK', {q('https://example.edu/resources/' + subject_id[:8] + '/' + str(topic_index))}, null, null, null, null, true, false, 3, {q(teacher)}, {now}, {now}) on conflict (id) do update set url = excluded.url, visible = true, archived = false, updated_at = now();")
                if topic_index in (2, 4):
                    assignment_id = uid(f"assignment:{topic_id}")
                    deadline_days = 7 + topic_index * 3 + year
                    emit(f"insert into {ASSIGN}.assignments (id, topic_id, created_by_user_id, title, description, deadline, order_index, status, allow_late_submissions, max_submissions, allow_resubmit, max_file_size_mb, max_points, created_at, updated_at) values ({q(assignment_id)}, {q(topic_id)}, {q(teacher)}, {q(title + ' practical task ' + str(topic_index))}, 'Complete the practical task and submit your result before the deadline.', now() + interval '{deadline_days} days', {topic_index}, 'PUBLISHED', true, 2, true, 20, 100, {now}, {now}) on conflict (id) do update set title = excluded.title, deadline = excluded.deadline, status = 'PUBLISHED', updated_at = now();")
                    for group_id in subject_groups_map[subject_id]:
                        emit(f"insert into {ASSIGN}.assignment_group_availability (id, assignment_id, group_id, visible, available_from, deadline, allow_late_submissions, max_submissions, allow_resubmit, created_at, updated_at) values ({q(uid('assignment-availability:' + assignment_id + ':' + group_id))}, {q(assignment_id)}, {q(group_id)}, true, now() - interval '1 day', now() + interval '{deadline_days} days', true, 2, true, {now}, {now}) on conflict (assignment_id, group_id) do update set visible = true, available_from = excluded.available_from, deadline = excluded.deadline, updated_at = now();")
                if topic_index == 4:
                    test_id = uid(f"test:{topic_id}")
                    emit(f"insert into {TESTING}.tests (id, topic_id, created_by_user_id, title, order_index, status, max_attempts, max_points, time_limit_minutes, available_from, available_until, show_correct_answers_after_submit, shuffle_questions, shuffle_answers, created_at, updated_at) values ({q(test_id)}, {q(topic_id)}, {q(teacher)}, {q(title + ' checkpoint test')}, 1, 'PUBLISHED', 2, 100, 30, now() - interval '1 day', now() + interval '21 days', true, false, false, {now}, {now}) on conflict (id) do update set status = 'PUBLISHED', available_from = excluded.available_from, available_until = excluded.available_until, updated_at = now();")
                    for group_id in subject_groups_map[subject_id]:
                        emit(f"insert into {TESTING}.test_group_availability (id, test_id, group_id, visible, available_from, available_until, deadline, max_attempts, created_at, updated_at) values ({q(uid('test-availability:' + test_id + ':' + group_id))}, {q(test_id)}, {q(group_id)}, true, now() - interval '1 day', now() + interval '21 days', now() + interval '21 days', 2, {now}, {now}) on conflict (test_id, group_id) do update set visible = true, available_from = excluded.available_from, available_until = excluded.available_until, deadline = excluded.deadline, max_attempts = excluded.max_attempts, updated_at = now();")
                    question_id = uid(f"question:{test_id}:single")
                    emit(f"insert into {TESTING}.questions (id, test_id, text, type, description, points, order_index, required, feedback, configuration_json, created_at, updated_at) values ({q(question_id)}, {q(test_id)}, 'Which option best matches the topic goal?', 'SINGLE_CHOICE', null, 10, 1, true, null, null, {now}, {now}) on conflict (id) do update set text = excluded.text, updated_at = now();")
                    for ans_idx, ans in enumerate([('Understand and apply the core concept', True), ('Ignore practical constraints', False)], start=1):
                        emit(f"insert into {TESTING}.answers (id, question_id, text, correct, created_at, updated_at) values ({q(uid('answer:' + question_id + ':' + str(ans_idx)))}, {q(question_id)}, {q(ans[0])}, {q(ans[1])}, {now}, {now}) on conflict (id) do update set text = excluded.text, correct = excluded.correct, updated_at = now();")

# All question types QA test.
if subject_records:
    subject = subject_records[0]
    topic_id = uid(f"topic:{subject['id']}:4")
    teacher = subject_teacher_map[subject['id']][0]
    all_test_id = uid("test:all-question-types-qa")
    emit(f"insert into {TESTING}.tests (id, topic_id, created_by_user_id, title, order_index, status, max_attempts, max_points, time_limit_minutes, available_from, available_until, show_correct_answers_after_submit, shuffle_questions, shuffle_answers, created_at, updated_at) values ({q(all_test_id)}, {q(topic_id)}, {q(teacher)}, 'All Question Types QA Test', 99, 'PUBLISHED', 3, 110, 45, now() - interval '1 day', now() + interval '30 days', true, false, false, {now}, {now}) on conflict (id) do update set status = 'PUBLISHED', available_from = excluded.available_from, available_until = excluded.available_until, updated_at = now();")
    for group_id in subject_groups_map[subject['id']]:
        emit(f"insert into {TESTING}.test_group_availability (id, test_id, group_id, visible, available_from, available_until, deadline, max_attempts, created_at, updated_at) values ({q(uid('test-availability:' + all_test_id + ':' + group_id))}, {q(all_test_id)}, {q(group_id)}, true, now() - interval '1 day', now() + interval '30 days', now() + interval '30 days', 3, {now}, {now}) on conflict (test_id, group_id) do update set visible = true, available_from = excluded.available_from, available_until = excluded.available_until, deadline = excluded.deadline, max_attempts = excluded.max_attempts, updated_at = now();")
    question_defs = [
        ('single','SINGLE_CHOICE','Choose the correct LMS term.',None),
        ('multi','MULTIPLE_CHOICE','Select all valid content types.',None),
        ('tf','TRUE_FALSE','A published test can be available to a group.',None),
        ('short','SHORT_ANSWER','Type the keyword: studium','{"acceptedAnswers":["studium"],"caseSensitive":false}'),
        ('long','LONG_TEXT','Explain why stable schedules matter.','{"rubric":"Teacher checks clarity and completeness."}'),
        ('numeric','NUMERIC','How many minutes are in one standard lesson?','{"correctValue":80,"tolerance":0,"unit":"minutes"}'),
        ('matching','MATCHING','Match item to category.','{"leftItems":[{"id":"left-lecture","text":"Lecture"},{"id":"left-lab","text":"Lab"}],"rightItems":[{"id":"right-theory","text":"Theory"},{"id":"right-practice","text":"Practice"}],"pairs":[{"leftId":"left-lecture","rightId":"right-theory"},{"leftId":"left-lab","rightId":"right-practice"}]}'),
        ('ordering','ORDERING','Order the delivery pipeline.','{"items":[{"id":"ord-plan","text":"Plan","orderIndex":1},{"id":"ord-build","text":"Build","orderIndex":2},{"id":"ord-release","text":"Release","orderIndex":3}]}'),
        ('fill','FILL_IN_THE_BLANK','Fill the missing words.','{"text":"A lesson has a ___ and a ___.","blanks":[{"id":"blank-subject","label":"first blank","acceptedAnswers":["subject"]},{"id":"blank-room","label":"second blank","acceptedAnswers":["room"]}]}'),
        ('file','FILE_ANSWER','Upload a short solution file.','{"allowedContentTypes":["text/plain","application/pdf"],"maxSizeBytes":5242880,"rubric":"Teacher reviews uploaded file."}'),
        ('manual','MANUAL_GRADING','Describe your learning plan.','{"rubric":"Teacher grades manually."}'),
    ]
    for idx, (key, qtype, text, cfg) in enumerate(question_defs, start=1):
        qid = uid(f"question:{all_test_id}:{key}")
        emit(f"insert into {TESTING}.questions (id, test_id, text, type, description, points, order_index, required, feedback, configuration_json, created_at, updated_at) values ({q(qid)}, {q(all_test_id)}, {q(text)}, {q(qtype)}, null, 10, {idx}, true, null, {q(cfg)}, {now}, {now}) on conflict (id) do update set text = excluded.text, type = excluded.type, configuration_json = excluded.configuration_json, updated_at = now();")
        if key == 'single':
            answers = [('Topic', True), ('Random string', False)]
        elif key == 'multi':
            answers = [('Lecture', True), ('Material', True), ('Conflict', False)]
        elif key == 'tf':
            answers = [('True', True), ('False', False)]
        else:
            answers = []
        for ans_idx, (text_value, correct) in enumerate(answers, start=1):
            emit(f"insert into {TESTING}.answers (id, question_id, text, correct, created_at, updated_at) values ({q(uid('answer:' + qid + ':' + str(ans_idx)))}, {q(qid)}, {q(text_value)}, {q(correct)}, {now}, {now}) on conflict (id) do update set text = excluded.text, correct = excluded.correct, updated_at = now();")

rooms = []
room_specs = [
    ('A-101','A',1,120,{'LECTURE':100,'PRACTICAL':40}),('A-102','A',1,90,{'LECTURE':90,'PRACTICAL':50}),('A-201','A',2,80,{'LECTURE':85,'PRACTICAL':50}),('A-202','A',2,70,{'LECTURE':80,'PRACTICAL':60}),
    ('B-101','B',1,45,{'PRACTICAL':100,'LECTURE':35}),('B-102','B',1,45,{'PRACTICAL':100}),('B-201','B',2,40,{'PRACTICAL':100}),('B-202','B',2,40,{'PRACTICAL':100,'LABORATORY':30}),
    ('C-101','C',1,35,{'PRACTICAL':90,'LECTURE':30}),('C-102','C',1,35,{'PRACTICAL':90}),('C-201','C',2,50,{'LECTURE':60,'PRACTICAL':80,'LABORATORY':30}),
    ('LAB-101','Lab',1,24,{'LABORATORY':100,'PRACTICAL':60}),('LAB-102','Lab',1,24,{'LABORATORY':100,'PRACTICAL':60}),('LAB-201','Lab',2,20,{'LABORATORY':100}),('LAB-202','Lab',2,20,{'LABORATORY':100}),('LAB-301','Lab',3,18,{'LABORATORY':95}),
    ('D-101','D',1,28,{'PRACTICAL':80,'LABORATORY':75}),('D-102','D',1,28,{'PRACTICAL':80,'LABORATORY':75}),('D-201','D',2,32,{'PRACTICAL':90}),('D-202','D',2,32,{'PRACTICAL':90}),
    ('E-101','E',1,60,{'LECTURE':70,'PRACTICAL':70}),('E-201','E',2,60,{'LECTURE':70,'PRACTICAL':70}),('CONF-1','Main',1,140,{'LECTURE':100}),('CONF-2','Main',2,100,{'LECTURE':90}),('ONLINE-1','Online',0,300,{'LECTURE':10,'PRACTICAL':10}),
]
for code, building, floor, capacity, caps in room_specs:
    room_id = uid(f"room:{code}")
    rooms.append({'id': room_id, 'code': code, 'capacity': capacity, 'caps': caps})
    emit(f"insert into {SCHEDULE}.rooms (id, code, building, floor, capacity, active, created_at, updated_at) values ({q(room_id)}, {q(code)}, {q(building)}, {floor}, {capacity}, true, {now}, {now}) on conflict (code) do update set building = excluded.building, floor = excluded.floor, capacity = excluded.capacity, active = true, updated_at = now();")
    for lt, priority in caps.items():
        emit(f"insert into {SCHEDULE}.room_capabilities (id, room_id, lesson_type, priority, active, created_at, updated_at) values ({q(uid('room-cap:' + code + ':' + lt))}, {q(room_id)}, {q(lt)}, {priority}, true, {now}, {now}) on conflict (room_id, lesson_type) do update set priority = excluded.priority, active = true, updated_at = now();")

today = date.today()
current_start = today - timedelta(days=today.weekday() + 14)
current_end = today + timedelta(days=120)
next_start = current_end + timedelta(days=1)
next_end = next_start + timedelta(days=140)
current_semester_number = 1 if today.month in [9,10,11,12,1] else 2
current_semester_id = uid('semester:current-realistic')
next_semester_id = uid('semester:next-realistic')
emit(f"insert into {SCHEDULE}.academic_semesters (id, name, start_date, end_date, week_one_start_date, semester_number, active, published, created_at, updated_at) values ({q(current_semester_id)}, 'LTC Current Semester', {q(current_start.isoformat())}, {q(current_end.isoformat())}, {q(current_start.isoformat())}, {current_semester_number}, true, true, {now}, {now}) on conflict (id) do update set start_date = excluded.start_date, end_date = excluded.end_date, week_one_start_date = excluded.week_one_start_date, semester_number = excluded.semester_number, active = true, published = true, updated_at = now();")
emit(f"insert into {SCHEDULE}.academic_semesters (id, name, start_date, end_date, week_one_start_date, semester_number, active, published, created_at, updated_at) values ({q(next_semester_id)}, 'LTC Next Semester', {q(next_start.isoformat())}, {q(next_end.isoformat())}, {q(next_start.isoformat())}, {2 if current_semester_number == 1 else 1}, false, false, {now}, {now}) on conflict (id) do update set start_date = excluded.start_date, end_date = excluded.end_date, week_one_start_date = excluded.week_one_start_date, semester_number = excluded.semester_number, active = false, published = false, updated_at = now();")
slot_ids = {}
for number, start, end in [(1,'08:30','09:50'),(2,'10:05','11:25'),(3,'11:40','13:00'),(4,'13:15','14:35'),(5,'14:50','16:10'),(6,'16:25','17:45'),(7,'18:00','19:20'),(8,'19:35','20:55')]:
    sid = uid(f"slot:{number}")
    slot_ids[number] = sid
    emit(f"insert into {SCHEDULE}.lesson_slots (id, number, start_time, end_time, active, created_at, updated_at) values ({q(sid)}, {number}, {q(start)}, {q(end)}, true, {now}, {now}) on conflict (number) do update set start_time = excluded.start_time, end_time = excluded.end_time, active = true, updated_at = now();")

if INCLUDE_SCHEDULE:
    room_by_type = {lt: sorted([r for r in rooms if lt in r['caps']], key=lambda r: (-r['caps'][lt], r['code'])) for lt in ['LECTURE','PRACTICAL','LABORATORY']}
    group_busy, teacher_busy, room_busy = set(), set(), set()
    teacher_load = {t: 0 for teachers in subject_teacher_map.values() for t in teachers}
    days = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY']
    slots = [1,2,3,4,5,6]
    def group_can_place(group_id, day, slot, week, subgroup):
        if subgroup == 'ALL':
            return not any((group_id, day, slot, week, sg) in group_busy for sg in ['ALL','FIRST','SECOND'])
        return (group_id, day, slot, week, 'ALL') not in group_busy and (group_id, day, slot, week, subgroup) not in group_busy
    def mark_group(group_id, day, slot, week, subgroup):
        if subgroup == 'ALL':
            for sg in ['ALL','FIRST','SECOND']:
                group_busy.add((group_id, day, slot, week, sg))
        else:
            group_busy.add((group_id, day, slot, week, subgroup))
    def choose_teacher(subject_id, day, slot, week):
        for teacher in sorted(subject_teacher_map.get(subject_id, []), key=lambda t: (teacher_load.get(t,0), t)):
            if (teacher, day, slot, week) not in teacher_busy:
                return teacher
        return None
    def choose_room(lesson_type, day, slot, week, group_size):
        for room in room_by_type.get(lesson_type, []):
            if room['capacity'] >= group_size and (room['id'], day, slot, week) not in room_busy:
                return room['id']
        for room in room_by_type.get(lesson_type, []):
            if (room['id'], day, slot, week) not in room_busy:
                return room['id']
        return None
    for group in group_records:
        group_subjects = [s for s in subject_records if s['code'] == group['code'] and s['year'] == group['year'] and s['semester'] == current_semester_number]
        requests = []
        for subject in group_subjects[:5]:
            requests += [(subject['id'],'LECTURE','ALL','ODD'),(subject['id'],'LECTURE','ALL','EVEN'),(subject['id'],'PRACTICAL','ALL','ODD'),(subject['id'],'PRACTICAL','ALL','EVEN')]
            is_lab = any(k in subject['title'].lower() for k in ['programming','database','web','security','network','systems','embedded','architecture','devops','cloud','testing'])
            if group['subgroup_mode'] == 'TWO_SUBGROUPS' and is_lab:
                requests += [(subject['id'],'LABORATORY','FIRST','ODD'),(subject['id'],'LABORATORY','SECOND','EVEN')]
        for subject_id, lesson_type, subgroup, week in requests:
            placed = False
            for day in days:
                if placed:
                    break
                for slot in slots:
                    if not group_can_place(group['id'], day, slot, week, subgroup):
                        continue
                    teacher = choose_teacher(subject_id, day, slot, week)
                    if not teacher:
                        continue
                    lesson_format = 'ONLINE' if lesson_type == 'LECTURE' and slot == 6 else 'OFFLINE'
                    room_id = None if lesson_format == 'ONLINE' else choose_room(lesson_type, day, slot, week, STUDENTS_PER_GROUP)
                    if lesson_format == 'OFFLINE' and not room_id:
                        continue
                    template_id = uid(f"schedule:{current_semester_id}:{group['id']}:{subject_id}:{day}:{slot}:{week}:{subgroup}:{lesson_type}")
                    online = f"https://meet.studium.local/{template_id[:8]}" if lesson_format == 'ONLINE' else None
                    emit(f"insert into {SCHEDULE}.schedule_templates (id, semester_id, group_id, subject_id, teacher_id, day_of_week, slot_id, week_type, subgroup, lesson_type, lesson_format, room_id, online_meeting_url, notes, status, active, created_at, updated_at) values ({q(template_id)}, {q(current_semester_id)}, {q(group['id'])}, {q(subject_id)}, {q(teacher)}, {q(day)}, {q(slot_ids[slot])}, {q(week)}, {q(subgroup)}, {q(lesson_type)}, {q(lesson_format)}, {q(room_id)}, {q(online)}, {q('Realistic seed ' + lesson_type.lower() + ' for ' + group['name'])}, 'ACTIVE', true, {now}, {now}) on conflict (id) do update set teacher_id = excluded.teacher_id, room_id = excluded.room_id, online_meeting_url = excluded.online_meeting_url, notes = excluded.notes, status = 'ACTIVE', active = true, updated_at = now();")
                    mark_group(group['id'], day, slot, week, subgroup)
                    teacher_busy.add((teacher, day, slot, week)); teacher_load[teacher] = teacher_load.get(teacher, 0) + 1
                    if room_id: room_busy.add((room_id, day, slot, week))
                    placed = True
                    break

# Synthetic file metadata for visible file material cards. Object download can be replaced with real uploads later.
for subject in subject_records[:10]:
    topic_id = uid(f"topic:{subject['id']}:1")
    owner = subject_teacher_map[subject['id']][0]
    file_id = uid(f"stored-file:{topic_id}:syllabus")
    object_key = f"realistic-seed/{file_id}.txt"
    original = subject['title'].replace(' ', '_').lower() + '_syllabus.txt'
    emit(f"insert into {FILE}.stored_files (id, owner_id, original_file_name, object_key, bucket_name, content_type, size_bytes, file_kind, access, status, deleted, created_at, updated_at, last_accessed_at) values ({q(file_id)}, {q(owner)}, {q(original)}, {q(object_key)}, {q(os.getenv('MINIO_BUCKET_PRIVATE','private'))}, 'text/plain', 512, 'DOCUMENT', 'PRIVATE', 'READY', false, {now}, {now}, {now}) on conflict (object_key) do update set original_file_name = excluded.original_file_name, owner_id = excluded.owner_id, status = 'READY', deleted = false, updated_at = now();")
    emit(f"insert into {EDU}.topic_materials (id, topic_id, title, description, type, url, file_id, original_file_name, content_type, size_bytes, visible, archived, order_index, created_by_user_id, created_at, updated_at) values ({q(uid('material:file:' + topic_id))}, {q(topic_id)}, 'Syllabus file', 'Synthetic syllabus file metadata for realistic local data.', 'FILE', null, {q(file_id)}, {q(original)}, 'text/plain', 512, true, false, 4, {q(owner)}, {now}, {now}) on conflict (id) do update set file_id = excluded.file_id, original_file_name = excluded.original_file_name, content_type = excluded.content_type, size_bytes = excluded.size_bytes, visible = true, archived = false, updated_at = now();")

emit('commit;')
PY

echo "Waiting for gateway..."
wait_for_gateway

echo "Applying realistic institution seed..."
psql_file "$SQL_FILE"

if [[ "$REALISTIC_SEED_INCLUDE_ACTIVITY" == "true" ]]; then
  echo "REALISTIC_SEED_INCLUDE_ACTIVITY=true is reserved for future analytics/activity generation. Skipping activity seed for now."
fi

echo "Summary:"
sql_scalar "select 'specialties=' || count(*) from ${EDUCATION_DB_SCHEMA:-education}.specialties where code like 'LTC-%';" || true
sql_scalar "select 'groups=' || count(*) from ${EDUCATION_DB_SCHEMA:-education}.groups where name like 'LTC-%';" || true
sql_scalar "select 'students=' || count(*) from ${AUTH_DB_SCHEMA:-auth}.users where username like 'ltc.%' and username not like 'ltc.teacher.%' and username <> 'ltc.academic.admin';" || true
sql_scalar "select 'teachers=' || count(*) from ${AUTH_DB_SCHEMA:-auth}.users where username like 'ltc.teacher.%';" || true
sql_scalar "select 'subjects=' || count(*) from ${EDUCATION_DB_SCHEMA:-education}.subjects where name like '%—%';" || true
sql_scalar "select 'schedule_templates=' || count(*) from ${SCHEDULE_DB_SCHEMA:-schedule}.schedule_templates st join ${EDUCATION_DB_SCHEMA:-education}.groups g on g.id = st.group_id where g.name like 'LTC-%';" || true

echo "Realistic institution seed finished. Default synthetic password: ${REALISTIC_SEED_PASSWORD}"