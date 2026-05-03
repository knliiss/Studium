#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/infra/docker/docker-compose.local.yml"
ENV_FILE="$ROOT_DIR/.env"

set -a
source "$ENV_FILE"
set +a

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

json_get() {
  local path="$1"
  python3 -c '
import json
import sys

data = sys.stdin.read().strip()
if not data:
    print("")
    sys.exit(0)

try:
    path = sys.argv[1].split(".")
    value = json.loads(data)
    for part in path:
        if part.isdigit():
            value = value[int(part)]
        else:
            value = value[part]
    if isinstance(value, (dict, list)):
        print(json.dumps(value))
    elif value is True:
        print("true")
    elif value is False:
        print("false")
    elif value is None:
        print("")
    else:
        print(value)
except Exception:
    print("")
' "$path"
}

json_length() {
  local path="$1"
  python3 -c '
import json
import sys

data = sys.stdin.read().strip()
if not data:
    print("0")
    sys.exit(0)

try:
    path = sys.argv[1].split(".")
    value = json.loads(data)
    for part in path:
        if part.isdigit():
            value = value[int(part)]
        else:
            value = value[part]
    print(len(value) if isinstance(value, (list, dict, str)) else 0)
except Exception:
    print("0")
' "$path"
}

new_uuid() {
  python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
}

instant_shift() {
  local offset_days="$1"
  python3 - "$offset_days" <<'PY'
from datetime import datetime, timedelta, timezone
import sys

offset_days = int(sys.argv[1])
value = datetime.now(timezone.utc) + timedelta(days=offset_days)
print(value.replace(microsecond=0).isoformat().replace("+00:00", "Z"))
PY
}

read -r TODAY PAST_SEMESTER_START PAST_SEMESTER_END PAST_WEEK_ONE_START CURRENT_SEMESTER_START CURRENT_SEMESTER_END CURRENT_WEEK_ONE_START NEXT_SEMESTER_START NEXT_SEMESTER_END NEXT_WEEK_ONE_START <<<"$(python3 <<'PY'
from datetime import date, timedelta

today = date.today()
current_semester_start = today - timedelta(days=today.weekday() + 14)
current_semester_end = today + timedelta(days=120)
past_semester_start = current_semester_start - timedelta(days=140)
past_semester_end = current_semester_start - timedelta(days=1)
next_semester_start = current_semester_end + timedelta(days=1)
next_semester_end = next_semester_start + timedelta(days=140)
print(
    today.isoformat(),
    past_semester_start.isoformat(),
    past_semester_end.isoformat(),
    past_semester_start.isoformat(),
    current_semester_start.isoformat(),
    current_semester_end.isoformat(),
    current_semester_start.isoformat(),
    next_semester_start.isoformat(),
    next_semester_end.isoformat(),
    next_semester_start.isoformat(),
)
PY
)"

api_json() {
  local method="$1"
  local url="$2"
  local token="$3"
  local body="$4"
  local forwarded_for="${5:-}"
  local -a args=(-sS -X "$method")
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" --data "$body")
  fi
  if [[ -n "$forwarded_for" ]]; then
    args+=(-H "X-Forwarded-For: $forwarded_for")
  fi

  local out http_code resp_body
  out=$(curl -w "\n%{http_code}" "${args[@]}" "$url")
  http_code="${out##*$'\n'}"
  resp_body="${out%$'\n'*}"
  if [[ "$out" != *$'\n'* ]]; then
    resp_body=""
    http_code="$out"
  fi

  if [[ "$http_code" -ge 400 ]]; then
    echo "API ERROR [$http_code] $method $url -> $resp_body" >&2
    return 1
  fi
  printf '%s' "$resp_body"
}

internal_json() {
  local method="$1"
  local url="$2"
  local secret="$3"
  local body="$4"

  local out http_code resp_body
  out=$(curl -sS -w "\n%{http_code}" -X "$method" \
    -H "X-Internal-Secret: $secret" \
    -H "Content-Type: application/json" \
    --data "$body" \
    "$url")
  http_code="${out##*$'\n'}"
  resp_body="${out%$'\n'*}"
  if [[ "$out" != *$'\n'* ]]; then
    resp_body=""
    http_code="$out"
  fi

  if [[ "$http_code" -ge 400 ]]; then
    echo "INTERNAL API ERROR [$http_code] $method $url -> $resp_body" >&2
    return 1
  fi
  printf '%s' "$resp_body"
}

upload_file() {
  local token="$1"
  local file_path="$2"
  local file_kind="$3"

  local out http_code resp_body
  out=$(curl -sS -w "\n%{http_code}" \
    -H "Authorization: Bearer $token" \
    -F "file=@${file_path};type=text/plain" \
    -F "fileKind=${file_kind}" \
    "http://localhost:${GATEWAY_PORT:-8080}/api/files")
  http_code="${out##*$'\n'}"
  resp_body="${out%$'\n'*}"
  if [[ "$out" != *$'\n'* ]]; then
    resp_body=""
    http_code="$out"
  fi

  if [[ "$http_code" -ge 400 ]]; then
    echo "UPLOAD ERROR [$http_code] -> $resp_body" >&2
    return 1
  fi
  printf '%s' "$resp_body"
}

sql_exec() {
  local statement="$1"
  compose exec -T postgres psql \
    -U "${POSTGRES_USER:-postgres}" \
    -d "${POSTGRES_DB:-postgres}" \
    -v ON_ERROR_STOP=1 \
    -c "$statement" >/dev/null
}

sql_scalar() {
  local statement="$1"
  compose exec -T postgres psql \
    -U "${POSTGRES_USER:-postgres}" \
    -d "${POSTGRES_DB:-postgres}" \
    -tA \
    -c "$statement" | tr -d '[:space:]'
}

ensure_schedule_semester() {
  local name="$1"
  local start_date="$2"
  local end_date="$3"
  local week_one_start_date="$4"
  local active="$5"
  local published="${6:-$active}"
  local existing_id

  existing_id="$(sql_scalar "select id::text from ${SCHEDULE_DB_SCHEMA:-schedule}.academic_semesters where name = '${name}' limit 1;")"
  if [[ -z "$existing_id" ]]; then
    existing_id="$(new_uuid)"
    sql_exec "insert into ${SCHEDULE_DB_SCHEMA:-schedule}.academic_semesters (id, name, start_date, end_date, week_one_start_date, active, published, created_at, updated_at) values ('${existing_id}', '${name}', '${start_date}', '${end_date}', '${week_one_start_date}', ${active}, ${published}, now(), now());"
  else
    sql_exec "update ${SCHEDULE_DB_SCHEMA:-schedule}.academic_semesters set start_date = '${start_date}', end_date = '${end_date}', week_one_start_date = '${week_one_start_date}', active = ${active}, published = ${published}, updated_at = now() where id = '${existing_id}';"
  fi

  printf '%s' "$existing_id"
}

ensure_schedule_slot() {
  local number="$1"
  local start_time="$2"
  local end_time="$3"
  local existing_id

  existing_id="$(sql_scalar "select id::text from ${SCHEDULE_DB_SCHEMA:-schedule}.lesson_slots where number = ${number} limit 1;")"
  if [[ -z "$existing_id" ]]; then
    existing_id="$(new_uuid)"
    sql_exec "insert into ${SCHEDULE_DB_SCHEMA:-schedule}.lesson_slots (id, number, start_time, end_time, active, created_at, updated_at) values ('${existing_id}', ${number}, '${start_time}', '${end_time}', true, now(), now());"
  else
    sql_exec "update ${SCHEDULE_DB_SCHEMA:-schedule}.lesson_slots set start_time = '${start_time}', end_time = '${end_time}', active = true, updated_at = now() where id = '${existing_id}';"
  fi

  printf '%s' "$existing_id"
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

login_user() {
  local username="$1"
  local password="$2"
  local forwarded_for="${3:-10.0.0.1}"
  local body
  body=$(cat <<JSON
{"username":"${username}","password":"${password}"}
JSON
)
  api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/auth/login" "" "$body" "$forwarded_for"
}

wait_for_owner_login() {
  for _ in $(seq 1 60); do
    if response=$(login_user "${AUTH_OWNER_USERNAME:-owner}" "${AUTH_OWNER_PASSWORD:-ChangeMe123!}" "10.0.0.1" 2>/dev/null); then
      printf '%s' "$response"
      return 0
    fi
    sleep 2
  done
  echo "Owner login did not become ready in time" >&2
  exit 1
}

register_user() {
  local username="$1"
  local email="$2"
  local password="$3"
  local forwarded_for="$4"
  local body
  body=$(cat <<JSON
{"username":"${username}","email":"${email}","password":"${password}"}
JSON
)
  api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/auth/register" "" "$body" "$forwarded_for"
}

ensure_user() {
  local username="$1"
  local email="$2"
  local password="$3"
  local forwarded_for="$4"
  local auth
  if auth=$(login_user "$username" "$password" "$forwarded_for" 2>/dev/null); then
    printf '%s' "$auth"
  else
    register_user "$username" "$email" "$password" "$forwarded_for"
  fi
}

ensure_entity() {
  local table="$1"
  local field_name="$2"
  local field_value="$3"
  local create_url="$4"
  local create_token="$5"
  local create_body="$6"
  local existing_id

  existing_id="$(sql_scalar "select id::text from ${table} where ${field_name} = '${field_value}' limit 1;" 2>/dev/null || true)"

  if [[ -n "$existing_id" ]]; then
    printf '{"id":"%s"}' "$existing_id"
  else
    api_json "POST" "$create_url" "$create_token" "$create_body"
  fi
}

roles_body() {
  local first="true"
  printf '{"roles":['
  for role in "$@"; do
    if [[ "$first" == "true" ]]; then
      first="false"
    else
      printf ','
    fi
    printf '"%s"' "$role"
  done
  printf ']}'
}

update_roles() {
  local token="$1"
  local user_id="$2"
  shift 2
  api_json \
    "PATCH" \
    "http://localhost:${GATEWAY_PORT:-8080}/api/admin/users/${user_id}/roles" \
    "$token" \
    "$(roles_body "$@")" >/dev/null || true
}

create_temp_file() {
  local filename="$1"
  local body="$2"
  local path
  path="$(mktemp "${TMPDIR:-/tmp}/studium-demo-${filename}-XXXX.txt")"
  printf '%s\n' "$body" >"$path"
  printf '%s' "$path"
}

wait_for_student_risk() {
  local user_id="$1"
  local expected_risk="$2"
  local token="$3"
  for _ in $(seq 1 60); do
    if response=$(api_json "GET" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/analytics/students/${user_id}/risk" "$token" "" 2>/dev/null); then
      if [[ "$(printf '%s' "$response" | json_get riskLevel)" == "$expected_risk" ]]; then
        return 0
      fi
    fi
    sleep 2
  done
  echo "Timed out waiting for analytics risk ${expected_risk} for user ${user_id}" >&2
  exit 1
}

wait_for_teacher_groups() {
  local teacher_id="$1"
  local token="$2"
  for _ in $(seq 1 60); do
    if response=$(api_json "GET" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/analytics/teachers/${teacher_id}/groups-at-risk" "$token" "" 2>/dev/null); then
      if [[ "$(printf '%s' "$response" | python3 -c 'import json,sys; data=sys.stdin.read().strip(); print(len(json.loads(data)) if data else 0)')" != "0" ]]; then
        return 0
      fi
    fi
    sleep 2
  done
  echo "Timed out waiting for teacher group-risk analytics" >&2
  exit 1
}

wait_for_notifications() {
  local token="$1"
  local variable_name="$2"
  for _ in $(seq 1 60); do
    if response=$(api_json "GET" "http://localhost:${GATEWAY_PORT:-8080}/api/notifications?page=0&size=20" "$token" "" 2>/dev/null); then
      if [[ "$(printf '%s' "$response" | json_length items)" != "0" ]]; then
        printf -v "$variable_name" '%s' "$response"
        return 0
      fi
    fi
    sleep 2
  done
  echo "Timed out waiting for notifications" >&2
  exit 1
}

if [[ "${DEMO_SEED_ENABLED:-true}" != "true" ]]; then
  echo "Demo seed is disabled."
  exit 0
fi

wait_for_gateway

owner_auth="$(wait_for_owner_login)"
owner_token="$(printf '%s' "$owner_auth" | json_get accessToken)"

admin_auth="$(ensure_user "admin.demo" "admin.demo@example.com" "DemoPass123!" "10.0.0.10")"
teacher_alpha_auth="$(ensure_user "teacher.alpha" "teacher.alpha@example.com" "DemoPass123!" "10.0.0.11")"
teacher_beta_auth="$(ensure_user "teacher.beta" "teacher.beta@example.com" "DemoPass123!" "10.0.0.12")"
student_one_auth="$(ensure_user "student.one" "student.one@example.com" "DemoPass123!" "10.0.0.13")"
student_two_auth="$(ensure_user "student.two" "student.two@example.com" "DemoPass123!" "10.0.0.14")"
student_three_auth="$(ensure_user "student.three" "student.three@example.com" "DemoPass123!" "10.0.0.15")"
student_four_auth="$(ensure_user "student.four" "student.four@example.com" "DemoPass123!" "10.0.0.16")"
student_five_auth="$(ensure_user "student.five" "student.five@example.com" "DemoPass123!" "10.0.0.17")"

admin_id="$(printf '%s' "$admin_auth" | json_get user.id)"
teacher_alpha_id="$(printf '%s' "$teacher_alpha_auth" | json_get user.id)"
teacher_beta_id="$(printf '%s' "$teacher_beta_auth" | json_get user.id)"
student_one_id="$(printf '%s' "$student_one_auth" | json_get user.id)"
student_two_id="$(printf '%s' "$student_two_auth" | json_get user.id)"
student_three_id="$(printf '%s' "$student_three_auth" | json_get user.id)"
student_four_id="$(printf '%s' "$student_four_auth" | json_get user.id)"
student_five_id="$(printf '%s' "$student_five_auth" | json_get user.id)"

update_roles "$owner_token" "$admin_id" "ADMIN" "USER"
update_roles "$owner_token" "$teacher_alpha_id" "TEACHER" "USER"
update_roles "$owner_token" "$teacher_beta_id" "TEACHER" "USER"
update_roles "$owner_token" "$student_one_id" "STUDENT" "USER"
update_roles "$owner_token" "$student_two_id" "STUDENT" "USER"
update_roles "$owner_token" "$student_three_id" "STUDENT" "USER"
update_roles "$owner_token" "$student_four_id" "STUDENT" "USER"
update_roles "$owner_token" "$student_five_id" "STUDENT" "USER"

admin_token="$(printf '%s' "$(login_user "admin.demo" "DemoPass123!" "10.0.1.10")" | json_get accessToken)"
teacher_alpha_token="$(printf '%s' "$(login_user "teacher.alpha" "DemoPass123!" "10.0.1.11")" | json_get accessToken)"
teacher_beta_token="$(printf '%s' "$(login_user "teacher.beta" "DemoPass123!" "10.0.1.12")" | json_get accessToken)"
student_one_token="$(printf '%s' "$(login_user "student.one" "DemoPass123!" "10.0.1.13")" | json_get accessToken)"
student_two_token="$(printf '%s' "$(login_user "student.two" "DemoPass123!" "10.0.1.14")" | json_get accessToken)"
student_three_token="$(printf '%s' "$(login_user "student.three" "DemoPass123!" "10.0.1.15")" | json_get accessToken)"
student_four_token="$(printf '%s' "$(login_user "student.four" "DemoPass123!" "10.0.1.16")" | json_get accessToken)"
student_five_token="$(printf '%s' "$(login_user "student.five" "DemoPass123!" "10.0.1.17")" | json_get accessToken)"

group_one_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.groups" "name" "CS-101" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/groups" "$admin_token" '{"name":"CS-101"}')" | json_get id)"
group_two_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.groups" "name" "CS-102" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/groups" "$admin_token" '{"name":"CS-102"}')" | json_get id)"

sql_exec "$(cat <<SQL
insert into ${EDUCATION_DB_SCHEMA:-education}.group_students (id, group_id, user_id, role, subgroup, created_at, updated_at) values
  ('$(new_uuid)', '${group_one_id}', '${student_one_id}', 'STUDENT', 'ALL', now(), now()),
  ('$(new_uuid)', '${group_one_id}', '${student_two_id}', 'STUDENT', 'ALL', now(), now()),
  ('$(new_uuid)', '${group_one_id}', '${student_three_id}', 'STUDENT', 'ALL', now(), now()),
  ('$(new_uuid)', '${group_two_id}', '${student_four_id}', 'STUDENT', 'ALL', now(), now()),
  ('$(new_uuid)', '${group_two_id}', '${student_five_id}', 'STUDENT', 'ALL', now(), now())
on conflict on constraint uk_group_students_group_id_user_id do nothing;
SQL
)"

algorithms_subject_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.subjects" "name" "Algorithms" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/subjects" "$admin_token" "$(cat <<JSON
{"name":"Algorithms","groupId":"${group_one_id}","description":"Core algorithmic thinking for frontend demos."}
JSON
)")" | json_get id)"
databases_subject_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.subjects" "name" "Databases" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/subjects" "$admin_token" "$(cat <<JSON
{"name":"Databases","groupId":"${group_one_id}","description":"Relational data modeling and SQL practice."}
JSON
)")" | json_get id)"
networks_subject_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.subjects" "name" "Networks" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/subjects" "$admin_token" "$(cat <<JSON
{"name":"Networks","groupId":"${group_two_id}","description":"Networking fundamentals and routing basics."}
JSON
)")" | json_get id)"

sorting_topic_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.topics" "title" "Sorting Basics" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/topics" "$admin_token" "$(cat <<JSON
{"subjectId":"${algorithms_subject_id}","title":"Sorting Basics","orderIndex":0}
JSON
)")" | json_get id)"
graphs_topic_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.topics" "title" "Graph Traversal" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/topics" "$admin_token" "$(cat <<JSON
{"subjectId":"${algorithms_subject_id}","title":"Graph Traversal","orderIndex":1}
JSON
)")" | json_get id)"
sql_topic_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.topics" "title" "SQL Foundations" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/topics" "$admin_token" "$(cat <<JSON
{"subjectId":"${databases_subject_id}","title":"SQL Foundations","orderIndex":0}
JSON
)")" | json_get id)"
transactions_topic_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.topics" "title" "Transactions" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/topics" "$admin_token" "$(cat <<JSON
{"subjectId":"${databases_subject_id}","title":"Transactions","orderIndex":1}
JSON
)")" | json_get id)"
osi_topic_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.topics" "title" "OSI Model" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/topics" "$admin_token" "$(cat <<JSON
{"subjectId":"${networks_subject_id}","title":"OSI Model","orderIndex":0}
JSON
)")" | json_get id)"
routing_topic_id="$(printf '%s' "$(ensure_entity "${EDUCATION_DB_SCHEMA:-education}.topics" "title" "Routing Fundamentals" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/topics" "$admin_token" "$(cat <<JSON
{"subjectId":"${networks_subject_id}","title":"Routing Fundamentals","orderIndex":1}
JSON
)")" | json_get id)"

current_active_semester_id="$(sql_scalar "select id::text from ${SCHEDULE_DB_SCHEMA:-schedule}.academic_semesters where active = true order by start_date desc limit 1;")"
if [[ -z "$current_active_semester_id" ]]; then
  current_active_semester_id="$(ensure_schedule_semester "Demo Current Semester" "$CURRENT_SEMESTER_START" "$CURRENT_SEMESTER_END" "$CURRENT_WEEK_ONE_START" true true)"
fi
past_semester_id="$(ensure_schedule_semester "Demo Past Semester" "$PAST_SEMESTER_START" "$PAST_SEMESTER_END" "$PAST_WEEK_ONE_START" false true)"
next_semester_id="$(ensure_schedule_semester "Demo Next Semester" "$NEXT_SEMESTER_START" "$NEXT_SEMESTER_END" "$NEXT_WEEK_ONE_START" false false)"
semester_id="$current_active_semester_id"

sql_exec "update ${SCHEDULE_DB_SCHEMA:-schedule}.lesson_slots set active = false, updated_at = now() where number not between 1 and 8 and active = true;"
slot_one_id="$(ensure_schedule_slot 1 "08:30:00" "09:50:00")"
slot_two_id="$(ensure_schedule_slot 2 "10:05:00" "11:25:00")"
slot_three_id="$(ensure_schedule_slot 3 "11:40:00" "13:00:00")"
slot_four_id="$(ensure_schedule_slot 4 "13:15:00" "14:35:00")"
slot_five_id="$(ensure_schedule_slot 5 "14:50:00" "16:10:00")"
slot_six_id="$(ensure_schedule_slot 6 "16:25:00" "17:45:00")"
slot_seven_id="$(ensure_schedule_slot 7 "18:00:00" "19:20:00")"
slot_eight_id="$(ensure_schedule_slot 8 "19:35:00" "20:55:00")"
room_one_id="$(printf '%s' "$(ensure_entity "${SCHEDULE_DB_SCHEMA:-schedule}.rooms" "code" "A-101" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/schedule/rooms" "$admin_token" '{"code":"A-101","building":"North Campus","floor":1,"capacity":32,"active":true}')" | json_get id)"
room_two_id="$(printf '%s' "$(ensure_entity "${SCHEDULE_DB_SCHEMA:-schedule}.rooms" "code" "B-202" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/schedule/rooms" "$admin_token" '{"code":"B-202","building":"South Campus","floor":2,"capacity":28,"active":true}')" | json_get id)"

api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/schedule/templates" "$admin_token" "$(cat <<JSON
{"semesterId":"${semester_id}","groupId":"${group_one_id}","subjectId":"${algorithms_subject_id}","teacherId":"${teacher_alpha_id}","dayOfWeek":"MONDAY","slotId":"${slot_one_id}","weekType":"ODD","subgroup":"ALL","lessonType":"LECTURE","lessonFormat":"OFFLINE","roomId":"${room_one_id}","notes":"Odd week in-person lecture.","active":true}
JSON
)" >/dev/null || true

api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/schedule/templates" "$admin_token" "$(cat <<JSON
{"semesterId":"${semester_id}","groupId":"${group_one_id}","subjectId":"${databases_subject_id}","teacherId":"${teacher_alpha_id}","dayOfWeek":"TUESDAY","slotId":"${slot_two_id}","weekType":"EVEN","subgroup":"ALL","lessonType":"PRACTICAL","lessonFormat":"ONLINE","onlineMeetingUrl":"https://meet.studium.local/sql-demo","notes":"Even week online practical.","active":true}
JSON
)" >/dev/null || true

api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/schedule/templates" "$admin_token" "$(cat <<JSON
{"semesterId":"${semester_id}","groupId":"${group_two_id}","subjectId":"${networks_subject_id}","teacherId":"${teacher_beta_id}","dayOfWeek":"WEDNESDAY","slotId":"${slot_three_id}","weekType":"ALL","subgroup":"ALL","lessonType":"LABORATORY","lessonFormat":"OFFLINE","roomId":"${room_two_id}","notes":"Weekly lab for second group.","active":true}
JSON
)" >/dev/null || true

api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/schedule/overrides" "$admin_token" "$(cat <<JSON
{"semesterId":"${semester_id}","overrideType":"EXTRA","date":"${TODAY}","groupId":"${group_one_id}","subjectId":"${algorithms_subject_id}","teacherId":"${teacher_alpha_id}","slotId":"${slot_one_id}","subgroup":"ALL","lessonType":"LECTURE","lessonFormat":"ONLINE","onlineMeetingUrl":"https://meet.studium.local/extra-lecture","notes":"Extra live dashboard lesson."}
JSON
)" >/dev/null || true

published_assignment_deadline="$(instant_shift 3)"
graded_assignment_deadline="$(instant_shift 5)"
late_assignment_initial_deadline="$(instant_shift 6)"
draft_assignment_deadline="$(instant_shift 8)"
archived_assignment_deadline="$(instant_shift 10)"
published_test_available_from="$(instant_shift -1)"
published_test_available_until="$(instant_shift 4)"
draft_test_available_until="$(instant_shift 9)"
closed_test_available_until="$(instant_shift 2)"
late_assignment_actual_deadline="$(instant_shift -2)"
reminder_at="$(instant_shift 1)"

published_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Merge Sort Walkthrough" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${sorting_topic_id}","title":"Merge Sort Walkthrough","description":"Published assignment that stays pending for dashboard cards.","deadline":"${published_assignment_deadline}","allowLateSubmissions":false,"maxSubmissions":1,"allowResubmit":false,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${published_assignment_id}/publish" "$admin_token" "" >/dev/null || true

graded_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Graph Paths Exercise" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${graphs_topic_id}","title":"Graph Paths Exercise","description":"Assignment used for graded submission seed data.","deadline":"${graded_assignment_deadline}","allowLateSubmissions":false,"maxSubmissions":1,"allowResubmit":false,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${graded_assignment_id}/publish" "$admin_token" "" >/dev/null || true

late_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Late SQL Reflection" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${sql_topic_id}","title":"Late SQL Reflection","description":"Assignment intentionally backdated for late submission analytics.","deadline":"${late_assignment_initial_deadline}","allowLateSubmissions":true,"maxSubmissions":2,"allowResubmit":true,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${late_assignment_id}/publish" "$admin_token" "" >/dev/null || true

draft_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Draft Transaction Checklist" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${transactions_topic_id}","title":"Draft Transaction Checklist","description":"Draft assignment for teacher dashboard.","deadline":"${draft_assignment_deadline}","allowLateSubmissions":false,"maxSubmissions":1,"allowResubmit":false,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"

archived_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Archived Complexity Notes" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${sorting_topic_id}","title":"Archived Complexity Notes","description":"Archived assignment example.","deadline":"${archived_assignment_deadline}","allowLateSubmissions":false,"maxSubmissions":1,"allowResubmit":false,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${archived_assignment_id}/publish" "$admin_token" "" >/dev/null || true
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${archived_assignment_id}/archive" "$admin_token" "" >/dev/null || true

sql_exec "update ${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments set deadline = '${late_assignment_actual_deadline}', updated_at = now() where id = '${late_assignment_id}';" || true

student_one_file_path="$(create_temp_file "student-one-graph" "Student One graph exercise answer.")"
student_two_file_path="$(create_temp_file "student-two-late" "Student Two late SQL reflection.")"
trap 'rm -f "${student_one_file_path}" "${student_two_file_path}"' EXIT

student_one_file_id="$(printf '%s' "$(upload_file "$student_one_token" "$student_one_file_path" "ATTACHMENT")" | json_get id)"
student_two_file_id="$(printf '%s' "$(upload_file "$student_two_token" "$student_two_file_path" "ATTACHMENT")" | json_get id)"

graded_submission_id="$(sql_scalar "select id::text from ${ASSIGNMENT_DB_SCHEMA:-assignment}.submissions where assignment_id = '${graded_assignment_id}' limit 1;" 2>/dev/null || echo "")"
if [[ -z "$graded_submission_id" ]]; then
  graded_submission_id="$(printf '%s' "$(api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/submissions" "$student_one_token" "$(cat <<JSON
{"assignmentId":"${graded_assignment_id}","fileId":"${student_one_file_id}"}
JSON
)")" | json_get id)"
fi

late_submission_id="$(sql_scalar "select id::text from ${ASSIGNMENT_DB_SCHEMA:-assignment}.submissions where assignment_id = '${late_assignment_id}' limit 1;" 2>/dev/null || echo "")"
if [[ -z "$late_submission_id" ]]; then
  late_submission_id="$(printf '%s' "$(api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/submissions" "$student_two_token" "$(cat <<JSON
{"assignmentId":"${late_assignment_id}","fileId":"${student_two_file_id}"}
JSON
)")" | json_get id)"
fi

api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/grades" "$admin_token" "$(cat <<JSON
{"submissionId":"${graded_submission_id}","score":95,"feedback":"Strong work and clear explanation."}
JSON
)" >/dev/null || true

published_test_id="$(printf '%s' "$(ensure_entity "${TESTING_DB_SCHEMA:-testing}.tests" "title" "Sorting Quiz" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests" "$admin_token" "$(cat <<JSON
{"topicId":"${sorting_topic_id}","title":"Sorting Quiz","maxAttempts":2,"timeLimitMinutes":30,"availableFrom":"${published_test_available_from}","availableUntil":"${published_test_available_until}","showCorrectAnswersAfterSubmit":true,"shuffleQuestions":false,"shuffleAnswers":false}
JSON
)")" | json_get id)"

draft_test_id="$(printf '%s' "$(ensure_entity "${TESTING_DB_SCHEMA:-testing}.tests" "title" "Draft SQL Quiz" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests" "$admin_token" "$(cat <<JSON
{"topicId":"${sql_topic_id}","title":"Draft SQL Quiz","maxAttempts":1,"timeLimitMinutes":20,"availableFrom":"${published_test_available_from}","availableUntil":"${draft_test_available_until}","showCorrectAnswersAfterSubmit":false,"shuffleQuestions":true,"shuffleAnswers":true}
JSON
)")" | json_get id)"

closed_test_id="$(printf '%s' "$(ensure_entity "${TESTING_DB_SCHEMA:-testing}.tests" "title" "Closed Graph Quiz" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests" "$admin_token" "$(cat <<JSON
{"topicId":"${graphs_topic_id}","title":"Closed Graph Quiz","maxAttempts":1,"timeLimitMinutes":15,"availableFrom":"${published_test_available_from}","availableUntil":"${closed_test_available_until}","showCorrectAnswersAfterSubmit":false,"shuffleQuestions":false,"shuffleAnswers":false}
JSON
)")" | json_get id)"

question_one_id="$(printf '%s' "$(ensure_entity "${TESTING_DB_SCHEMA:-testing}.questions" "text" "What is the average time complexity of merge sort?" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/questions" "$admin_token" "$(cat <<JSON
{"testId":"${published_test_id}","text":"What is the average time complexity of merge sort?"}
JSON
)")" | json_get id)"

question_two_id="$(printf '%s' "$(ensure_entity "${TESTING_DB_SCHEMA:-testing}.questions" "text" "Which algorithm is stable by default in this module?" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/questions" "$admin_token" "$(cat <<JSON
{"testId":"${published_test_id}","text":"Which algorithm is stable by default in this module?"}
JSON
)")" | json_get id)"

api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/answers" "$admin_token" "$(cat <<JSON
{"questionId":"${question_one_id}","text":"O(n log n)","isCorrect":true}
JSON
)" >/dev/null || true
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/answers" "$admin_token" "$(cat <<JSON
{"questionId":"${question_one_id}","text":"O(n^2)","isCorrect":false}
JSON
)" >/dev/null || true
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/answers" "$admin_token" "$(cat <<JSON
{"questionId":"${question_two_id}","text":"Merge sort","isCorrect":true}
JSON
)" >/dev/null || true
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/answers" "$admin_token" "$(cat <<JSON
{"questionId":"${question_two_id}","text":"Selection sort","isCorrect":false}
JSON
)" >/dev/null || true

api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${published_test_id}/publish" "$admin_token" "" >/dev/null || true
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${closed_test_id}/publish" "$admin_token" "" >/dev/null || true
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${closed_test_id}/close" "$admin_token" "" >/dev/null || true

api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${published_test_id}/start" "$student_two_token" "" >/dev/null || true
api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/results" "$student_two_token" "$(cat <<JSON
{"testId":"${published_test_id}","score":20}
JSON
)" >/dev/null || true

internal_json "POST" "http://localhost:${NOTIFICATION_PORT:-8084}/internal/notifications/users/${student_one_id}/reminders/assignments/deadline" "${NOTIFICATION_INTERNAL_SHARED_SECRET:-change-me-notification-internal}" "$(cat <<JSON
{"assignmentId":"${published_assignment_id}","title":"Merge Sort Walkthrough","deadline":"${published_assignment_deadline}","reminderAt":"${reminder_at}"}
JSON
)" >/dev/null || true

internal_json "POST" "http://localhost:${NOTIFICATION_PORT:-8084}/internal/notifications/users/${student_two_id}/reminders/tests/deadline" "${NOTIFICATION_INTERNAL_SHARED_SECRET:-change-me-notification-internal}" "$(cat <<JSON
{"testId":"${published_test_id}","title":"Sorting Quiz","deadline":"${published_test_available_until}","reminderAt":"${reminder_at}"}
JSON
)" >/dev/null || true

wait_for_student_risk "$student_one_id" "LOW" "$admin_token"
wait_for_student_risk "$student_two_id" "HIGH" "$admin_token"
wait_for_teacher_groups "$teacher_alpha_id" "$admin_token"

student_one_notifications=""
wait_for_notifications "$student_one_token" student_one_notifications
student_one_first_notification_id="$(printf '%s' "$student_one_notifications" | json_get items.0.id)"
if [[ -n "$student_one_first_notification_id" ]]; then
  api_json "PATCH" "http://localhost:${GATEWAY_PORT:-8080}/api/notifications/${student_one_first_notification_id}/read" "$student_one_token" "" >/dev/null || true
fi

echo "Demo seed completed."