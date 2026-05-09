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

best_effort_api_json() {
  api_json "$@" >/dev/null 2>&1 || true
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

best_effort_internal_json() {
  internal_json "$@" >/dev/null 2>&1 || true
}

notification_internal_json() {
  local method="$1"
  local path="$2"
  local secret="$3"
  local body="$4"
  local out http_code resp_body
  out=$(printf '%s' "$body" | compose exec -T notification-service curl -sS -w "\n%{http_code}" -X "$method" \
    -H "X-Internal-Secret: $secret" \
    -H "Content-Type: application/json" \
    --data @- \
    "http://localhost:${NOTIFICATION_PORT:-8084}${path}")
  http_code="${out##*$'\n'}"
  resp_body="${out%$'\n'*}"
  if [[ "$out" != *$'\n'* ]]; then
    resp_body=""
    http_code="$out"
  fi

  if [[ "$http_code" -ge 400 ]]; then
    echo "NOTIFICATION INTERNAL API ERROR [$http_code] $method $path -> $resp_body" >&2
    return 1
  fi
  printf '%s' "$resp_body"
}

best_effort_notification_internal_json() {
  notification_internal_json "$@" >/dev/null 2>&1 || true
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

sync_subject_binding() {
  local token="$1"
  local subject_id="$2"
  local name="$3"
  local primary_group_id="$4"
  local group_ids_json="$5"
  local teacher_ids_json="$6"
  local description="$7"
  api_json "PUT" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/subjects/${subject_id}" "$token" "$(cat <<JSON
{"name":"${name}","groupId":"${primary_group_id}","groupIds":${group_ids_json},"teacherIds":${teacher_ids_json},"description":"${description}"}
JSON
)" >/dev/null
}

ensure_schedule_template() {
  local token="$1"
  local semester_id="$2"
  local group_id="$3"
  local subject_id="$4"
  local teacher_id="$5"
  local day_of_week="$6"
  local slot_id="$7"
  local week_type="$8"
  local subgroup="$9"
  local lesson_type="${10}"
  local lesson_format="${11}"
  local room_id="${12}"
  local online_meeting_url="${13}"
  local notes="${14}"

  local existing_id
  existing_id="$(sql_scalar "select id::text from ${SCHEDULE_DB_SCHEMA:-schedule}.schedule_templates where semester_id = '${semester_id}' and group_id = '${group_id}' and subject_id = '${subject_id}' and teacher_id = '${teacher_id}' and day_of_week = '${day_of_week}' and slot_id = '${slot_id}' and week_type = '${week_type}' and subgroup = '${subgroup}' and lesson_type = '${lesson_type}' and lesson_format = '${lesson_format}' and status <> 'ARCHIVED' limit 1;")"
  if [[ -n "$existing_id" ]]; then
    printf '%s' "$existing_id"
    return 0
  fi

  local body
  if [[ "$lesson_format" == "OFFLINE" ]]; then
    body=$(cat <<JSON
{"semesterId":"${semester_id}","groupId":"${group_id}","subjectId":"${subject_id}","teacherId":"${teacher_id}","dayOfWeek":"${day_of_week}","slotId":"${slot_id}","weekType":"${week_type}","subgroup":"${subgroup}","lessonType":"${lesson_type}","lessonFormat":"${lesson_format}","roomId":"${room_id}","notes":"${notes}","active":true}
JSON
)
  else
    body=$(cat <<JSON
{"semesterId":"${semester_id}","groupId":"${group_id}","subjectId":"${subject_id}","teacherId":"${teacher_id}","dayOfWeek":"${day_of_week}","slotId":"${slot_id}","weekType":"${week_type}","subgroup":"${subgroup}","lessonType":"${lesson_type}","lessonFormat":"${lesson_format}","onlineMeetingUrl":"${online_meeting_url}","notes":"${notes}","active":true}
JSON
)
  fi

  printf '%s' "$(api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/schedule/templates" "$token" "$body")" | json_get id
}

ensure_schedule_extra_override() {
  local token="$1"
  local semester_id="$2"
  local date="$3"
  local group_id="$4"
  local subject_id="$5"
  local teacher_id="$6"
  local slot_id="$7"
  local subgroup="$8"
  local lesson_type="$9"
  local lesson_format="${10}"
  local online_meeting_url="${11}"
  local notes="${12}"

  local existing_id
  existing_id="$(sql_scalar "select id::text from ${SCHEDULE_DB_SCHEMA:-schedule}.schedule_overrides where semester_id = '${semester_id}' and date = '${date}' and override_type = 'EXTRA' and group_id = '${group_id}' and subject_id = '${subject_id}' and teacher_id = '${teacher_id}' and slot_id = '${slot_id}' and subgroup = '${subgroup}' limit 1;")"
  if [[ -n "$existing_id" ]]; then
    printf '%s' "$existing_id"
    return 0
  fi

  printf '%s' "$(api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/schedule/overrides" "$token" "$(cat <<JSON
{"semesterId":"${semester_id}","overrideType":"EXTRA","date":"${date}","groupId":"${group_id}","subjectId":"${subject_id}","teacherId":"${teacher_id}","slotId":"${slot_id}","subgroup":"${subgroup}","lessonType":"${lesson_type}","lessonFormat":"${lesson_format}","onlineMeetingUrl":"${online_meeting_url}","notes":"${notes}"}
JSON
)")" | json_get id
}

get_assignment_status() {
  local token="$1"
  local assignment_id="$2"
  local response
  response="$(api_json "GET" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${assignment_id}" "$token" "")"
  printf '%s' "$response" | json_get status
}

ensure_assignment_published() {
  local token="$1"
  local assignment_id="$2"
  local status
  status="$(get_assignment_status "$token" "$assignment_id")"
  case "$status" in
    DRAFT)
      api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${assignment_id}/publish" "$token" "" >/dev/null
      ;;
    PUBLISHED)
      ;;
    *)
      echo "WARN: Assignment ${assignment_id} status=${status}, cannot transition to PUBLISHED via API" >&2
      ;;
  esac
}

ensure_assignment_archived() {
  local token="$1"
  local assignment_id="$2"
  local status
  status="$(get_assignment_status "$token" "$assignment_id")"
  case "$status" in
    DRAFT)
      api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${assignment_id}/publish" "$token" "" >/dev/null
      api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${assignment_id}/archive" "$token" "" >/dev/null
      ;;
    PUBLISHED)
      api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${assignment_id}/archive" "$token" "" >/dev/null
      ;;
    ARCHIVED)
      ;;
    *)
      echo "WARN: Assignment ${assignment_id} unexpected status=${status}" >&2
      ;;
  esac
}

upsert_assignment_availability() {
  local token="$1"
  local assignment_id="$2"
  local group_id="$3"
  local deadline="$4"
  local allow_late="$5"
  local max_submissions="$6"
  local allow_resubmit="$7"
  api_json "PUT" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments/${assignment_id}/availability" "$token" "$(cat <<JSON
{"groupId":"${group_id}","visible":true,"availableFrom":null,"deadline":"${deadline}","allowLateSubmissions":${allow_late},"maxSubmissions":${max_submissions},"allowResubmit":${allow_resubmit}}
JSON
)" >/dev/null
}

get_test_status() {
  local token="$1"
  local test_id="$2"
  local response
  response="$(api_json "GET" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${test_id}" "$token" "")"
  printf '%s' "$response" | json_get status
}

ensure_test_published() {
  local token="$1"
  local test_id="$2"
  local status
  status="$(get_test_status "$token" "$test_id")"
  case "$status" in
    DRAFT)
      api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${test_id}/publish" "$token" "" >/dev/null
      ;;
    PUBLISHED)
      ;;
    *)
      echo "WARN: Test ${test_id} status=${status}, cannot transition to PUBLISHED via API" >&2
      ;;
  esac
}

ensure_test_closed() {
  local token="$1"
  local test_id="$2"
  local status
  status="$(get_test_status "$token" "$test_id")"
  case "$status" in
    DRAFT)
      api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${test_id}/publish" "$token" "" >/dev/null
      api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${test_id}/close" "$token" "" >/dev/null
      ;;
    PUBLISHED)
      api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${test_id}/close" "$token" "" >/dev/null
      ;;
    CLOSED)
      ;;
    *)
      echo "WARN: Test ${test_id} unexpected status=${status}" >&2
      ;;
  esac
}

upsert_test_availability() {
  local token="$1"
  local test_id="$2"
  local group_id="$3"
  local available_from="$4"
  local available_until="$5"
  local deadline="$6"
  local max_attempts="$7"
  api_json "PUT" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${test_id}/availability" "$token" "$(cat <<JSON
{"groupId":"${group_id}","visible":true,"availableFrom":"${available_from}","availableUntil":"${available_until}","deadline":"${deadline}","maxAttempts":${max_attempts}}
JSON
)" >/dev/null
}

ensure_test_question() {
  local token="$1"
  local test_id="$2"
  local question_text="$3"
  local order_index="$4"
  local points="$5"
  local existing_id
  existing_id="$(sql_scalar "select id::text from ${TESTING_DB_SCHEMA:-testing}.questions where test_id = '${test_id}' and text = '${question_text}' limit 1;")"
  if [[ -n "$existing_id" ]]; then
    printf '%s' "$existing_id"
    return 0
  fi
  printf '%s' "$(api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/questions" "$token" "$(cat <<JSON
{"testId":"${test_id}","text":"${question_text}","type":"SINGLE_CHOICE","points":${points},"orderIndex":${order_index},"required":true}
JSON
)")" | json_get id
}

ensure_test_answer() {
  local token="$1"
  local question_id="$2"
  local answer_text="$3"
  local is_correct="$4"
  local existing_id
  existing_id="$(sql_scalar "select id::text from ${TESTING_DB_SCHEMA:-testing}.answers where question_id = '${question_id}' and text = '${answer_text}' limit 1;")"
  if [[ -n "$existing_id" ]]; then
    printf '%s' "$existing_id"
    return 0
  fi
  printf '%s' "$(api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/answers" "$token" "$(cat <<JSON
{"questionId":"${question_id}","text":"${answer_text}","isCorrect":${is_correct}}
JSON
)")" | json_get id
}

ensure_schedule_semester() {
  local name="$1"
  local start_date="$2"
  local end_date="$3"
  local week_one_start_date="$4"
  local active="$5"
  local published="${6:-$active}"
  local semester_number="${7:-}"
  local existing_id

  if [[ -z "$semester_number" ]]; then
    local month="${start_date:5:2}"
    month=$((10#$month))
    if (( month >= 9 || month == 1 )); then
      semester_number=1
    else
      semester_number=2
    fi
  fi

  existing_id="$(sql_scalar "select id::text from ${SCHEDULE_DB_SCHEMA:-schedule}.academic_semesters where name = '${name}' limit 1;")"
  if [[ -z "$existing_id" ]]; then
    existing_id="$(new_uuid)"
    sql_exec "insert into ${SCHEDULE_DB_SCHEMA:-schedule}.academic_semesters (id, name, start_date, end_date, week_one_start_date, semester_number, active, published, created_at, updated_at) values ('${existing_id}', '${name}', '${start_date}', '${end_date}', '${week_one_start_date}', ${semester_number}, ${active}, ${published}, now(), now());"
  else
    sql_exec "update ${SCHEDULE_DB_SCHEMA:-schedule}.academic_semesters set start_date = '${start_date}', end_date = '${end_date}', week_one_start_date = '${week_one_start_date}', semester_number = ${semester_number}, active = ${active}, published = ${published}, updated_at = now() where id = '${existing_id}';"
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

wait_for_analytics_service() {
  local token="$1"
  for _ in $(seq 1 60); do
    if curl -fsS \
      -H "Authorization: Bearer ${token}" \
      "http://localhost:${GATEWAY_PORT:-8080}/api/v1/analytics/dashboard/overview" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "WARN: Analytics service did not become ready in time" >&2
  return 1
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

ensure_topic() {
  local token="$1"
  local subject_id="$2"
  local title="$3"
  local order_index="$4"
  local existing_id

  existing_id="$(sql_scalar "select id::text from ${EDUCATION_DB_SCHEMA:-education}.topics where subject_id = '${subject_id}' and title = '${title}' limit 1;" 2>/dev/null || true)"
  if [[ -n "$existing_id" ]]; then
    printf '%s' "$existing_id"
    return 0
  fi

  printf '%s' "$(api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/education/topics" "$token" "$(cat <<JSON
{"subjectId":"${subject_id}","title":"${title}","orderIndex":${order_index}}
JSON
)")" | json_get id
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
  local _expected_risk="$2"
  local token="$3"
  local last_status=""
  local last_error=""
  local out
  local response
  local status
  local risk_level
  for _ in $(seq 1 60); do
    out=$(curl -sS -w "\n%{http_code}" -X GET \
      -H "Authorization: Bearer ${token}" \
      "http://localhost:${GATEWAY_PORT:-8080}/api/v1/analytics/students/${user_id}/risk" 2>/dev/null || true)
    status="${out##*$'\n'}"
    response="${out%$'\n'*}"
    if [[ "$out" != *$'\n'* ]]; then
      response=""
      status=""
    fi
    if [[ "$status" == "200" ]]; then
      risk_level="$(printf '%s' "$response" | json_get riskLevel)"
      if [[ -n "$risk_level" ]]; then
        return 0
      fi
    fi
    if [[ -n "$status" ]]; then
      last_status="$status"
      last_error="$(printf '%s' "$response" | json_get message)"
    fi
    sleep 2
  done
  if [[ -n "$last_status" ]]; then
    echo "WARN: Timed out waiting for analytics risk endpoint for user ${user_id} (last status=${last_status}, message=${last_error:-n/a})" >&2
  else
    echo "WARN: Timed out waiting for analytics risk endpoint for user ${user_id}" >&2
  fi
  return 1
}

wait_for_teacher_groups() {
  local teacher_id="$1"
  local token="$2"
  for _ in $(seq 1 60); do
    if api_json "GET" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/analytics/teachers/${teacher_id}/groups-at-risk" "$token" "" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "WARN: Timed out waiting for teacher group-risk analytics endpoint" >&2
  return 1
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
  echo "WARN: Timed out waiting for notifications" >&2
  return 1
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
on conflict (group_id, user_id) do nothing;
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

sync_subject_binding "$admin_token" "$algorithms_subject_id" "Algorithms" "$group_one_id" "[\"${group_one_id}\"]" "[\"${teacher_alpha_id}\"]" "Core algorithmic thinking for frontend demos."
sync_subject_binding "$admin_token" "$databases_subject_id" "Databases" "$group_one_id" "[\"${group_one_id}\"]" "[\"${teacher_alpha_id}\"]" "Relational data modeling and SQL practice."
sync_subject_binding "$admin_token" "$networks_subject_id" "Networks" "$group_two_id" "[\"${group_two_id}\"]" "[\"${teacher_beta_id}\"]" "Networking fundamentals and routing basics."

sorting_topic_id="$(ensure_topic "$admin_token" "$algorithms_subject_id" "Sorting Basics" "0")"
graphs_topic_id="$(ensure_topic "$admin_token" "$algorithms_subject_id" "Graph Traversal" "1")"
sql_topic_id="$(ensure_topic "$admin_token" "$databases_subject_id" "SQL Foundations" "0")"
transactions_topic_id="$(ensure_topic "$admin_token" "$databases_subject_id" "Transactions" "1")"
osi_topic_id="$(ensure_topic "$admin_token" "$networks_subject_id" "OSI Model" "0")"
routing_topic_id="$(ensure_topic "$admin_token" "$networks_subject_id" "Routing Fundamentals" "1")"

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

ensure_schedule_template "$admin_token" "$semester_id" "$group_one_id" "$algorithms_subject_id" "$teacher_alpha_id" "MONDAY" "$slot_one_id" "ODD" "ALL" "LECTURE" "OFFLINE" "$room_one_id" "" "Odd week in-person lecture." >/dev/null
ensure_schedule_template "$admin_token" "$semester_id" "$group_one_id" "$databases_subject_id" "$teacher_alpha_id" "TUESDAY" "$slot_two_id" "EVEN" "ALL" "PRACTICAL" "ONLINE" "" "https://meet.studium.local/sql-demo" "Even week online practical." >/dev/null
ensure_schedule_template "$admin_token" "$semester_id" "$group_two_id" "$networks_subject_id" "$teacher_beta_id" "WEDNESDAY" "$slot_three_id" "ALL" "ALL" "LABORATORY" "OFFLINE" "$room_two_id" "" "Weekly lab for second group." >/dev/null

ensure_schedule_extra_override "$admin_token" "$semester_id" "$TODAY" "$group_one_id" "$algorithms_subject_id" "$teacher_alpha_id" "$slot_one_id" "ALL" "LECTURE" "ONLINE" "https://meet.studium.local/extra-lecture" "Extra live dashboard lesson." >/dev/null

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
ensure_assignment_published "$admin_token" "$published_assignment_id"
upsert_assignment_availability "$admin_token" "$published_assignment_id" "$group_one_id" "$published_assignment_deadline" "false" "1" "false"

graded_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Graph Paths Exercise" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${graphs_topic_id}","title":"Graph Paths Exercise","description":"Assignment used for graded submission seed data.","deadline":"${graded_assignment_deadline}","allowLateSubmissions":false,"maxSubmissions":1,"allowResubmit":false,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"
ensure_assignment_published "$admin_token" "$graded_assignment_id"
upsert_assignment_availability "$admin_token" "$graded_assignment_id" "$group_one_id" "$graded_assignment_deadline" "false" "1" "false"

late_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Late SQL Reflection" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${sql_topic_id}","title":"Late SQL Reflection","description":"Assignment intentionally backdated for late submission analytics.","deadline":"${late_assignment_initial_deadline}","allowLateSubmissions":true,"maxSubmissions":2,"allowResubmit":true,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"
ensure_assignment_published "$admin_token" "$late_assignment_id"
upsert_assignment_availability "$admin_token" "$late_assignment_id" "$group_one_id" "$late_assignment_initial_deadline" "true" "2" "true"

draft_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Draft Transaction Checklist" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${transactions_topic_id}","title":"Draft Transaction Checklist","description":"Draft assignment for teacher dashboard.","deadline":"${draft_assignment_deadline}","allowLateSubmissions":false,"maxSubmissions":1,"allowResubmit":false,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"
upsert_assignment_availability "$admin_token" "$draft_assignment_id" "$group_one_id" "$draft_assignment_deadline" "false" "1" "false"

archived_assignment_id="$(printf '%s' "$(ensure_entity "${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments" "title" "Archived Complexity Notes" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/assignments" "$admin_token" "$(cat <<JSON
{"topicId":"${sorting_topic_id}","title":"Archived Complexity Notes","description":"Archived assignment example.","deadline":"${archived_assignment_deadline}","allowLateSubmissions":false,"maxSubmissions":1,"allowResubmit":false,"acceptedFileTypes":["text/plain"],"maxFileSizeMb":2}
JSON
)")" | json_get id)"
ensure_assignment_archived "$admin_token" "$archived_assignment_id"
upsert_assignment_availability "$admin_token" "$archived_assignment_id" "$group_one_id" "$archived_assignment_deadline" "false" "1" "false"

sql_exec "update ${ASSIGNMENT_DB_SCHEMA:-assignment}.assignments set deadline = '${late_assignment_actual_deadline}', updated_at = now() where id = '${late_assignment_id}';" || true
sql_exec "update ${ASSIGNMENT_DB_SCHEMA:-assignment}.assignment_group_availability set deadline = '${late_assignment_actual_deadline}', updated_at = now() where assignment_id = '${late_assignment_id}' and group_id = '${group_one_id}';" || true

student_one_file_path="$(create_temp_file "student-one-graph" "Student One graph exercise answer.")"
student_two_file_path="$(create_temp_file "student-two-late" "Student Two late SQL reflection.")"
trap 'rm -f "${student_one_file_path}" "${student_two_file_path}"' EXIT

student_one_file_id="$(printf '%s' "$(upload_file "$student_one_token" "$student_one_file_path" "ATTACHMENT")" | json_get id)"
student_two_file_id="$(printf '%s' "$(upload_file "$student_two_token" "$student_two_file_path" "ATTACHMENT")" | json_get id)"

graded_submission_id="$(sql_scalar "select id::text from ${ASSIGNMENT_DB_SCHEMA:-assignment}.submissions where assignment_id = '${graded_assignment_id}' and user_id = '${student_one_id}' order by submitted_at desc limit 1;" 2>/dev/null || echo "")"
if [[ -z "$graded_submission_id" ]]; then
  api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/submissions" "$student_one_token" "$(cat <<JSON
{"assignmentId":"${graded_assignment_id}","fileId":"${student_one_file_id}"}
JSON
)" >/dev/null
  graded_submission_id="$(sql_scalar "select id::text from ${ASSIGNMENT_DB_SCHEMA:-assignment}.submissions where assignment_id = '${graded_assignment_id}' and user_id = '${student_one_id}' order by submitted_at desc limit 1;" 2>/dev/null || echo "")"
fi

late_submission_id="$(sql_scalar "select id::text from ${ASSIGNMENT_DB_SCHEMA:-assignment}.submissions where assignment_id = '${late_assignment_id}' and user_id = '${student_two_id}' order by submitted_at desc limit 1;" 2>/dev/null || echo "")"
if [[ -z "$late_submission_id" ]]; then
  api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/submissions" "$student_two_token" "$(cat <<JSON
{"assignmentId":"${late_assignment_id}","fileId":"${student_two_file_id}"}
JSON
)" >/dev/null
  late_submission_id="$(sql_scalar "select id::text from ${ASSIGNMENT_DB_SCHEMA:-assignment}.submissions where assignment_id = '${late_assignment_id}' and user_id = '${student_two_id}' order by submitted_at desc limit 1;" 2>/dev/null || echo "")"
fi

if [[ -n "$graded_submission_id" ]]; then
  graded_submission_grade_id="$(sql_scalar "select id::text from ${ASSIGNMENT_DB_SCHEMA:-assignment}.grades where submission_id = '${graded_submission_id}' limit 1;" 2>/dev/null || echo "")"
  if [[ -z "$graded_submission_grade_id" ]]; then
    api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/grades" "$admin_token" "$(cat <<JSON
{"submissionId":"${graded_submission_id}","score":95,"feedback":"Strong work and clear explanation."}
JSON
)" >/dev/null
  fi
fi

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

published_test_status="$(get_test_status "$admin_token" "$published_test_id")"
if [[ "$published_test_status" == "DRAFT" ]]; then
  question_one_id="$(ensure_test_question "$admin_token" "$published_test_id" "What is the average time complexity of merge sort?" 0 10)"
  question_two_id="$(ensure_test_question "$admin_token" "$published_test_id" "Which algorithm is stable by default in this module?" 1 10)"
  ensure_test_answer "$admin_token" "$question_one_id" "O(n log n)" "true" >/dev/null
  ensure_test_answer "$admin_token" "$question_one_id" "O(n^2)" "false" >/dev/null
  ensure_test_answer "$admin_token" "$question_two_id" "Merge sort" "true" >/dev/null
  ensure_test_answer "$admin_token" "$question_two_id" "Selection sort" "false" >/dev/null
fi

upsert_test_availability "$admin_token" "$published_test_id" "$group_one_id" "$published_test_available_from" "$published_test_available_until" "$published_test_available_until" "2"
upsert_test_availability "$admin_token" "$closed_test_id" "$group_one_id" "$published_test_available_from" "$closed_test_available_until" "$closed_test_available_until" "1"

ensure_test_published "$admin_token" "$published_test_id"
ensure_test_closed "$admin_token" "$closed_test_id"

student_two_test_result_id="$(sql_scalar "select id::text from ${TESTING_DB_SCHEMA:-testing}.test_results where test_id = '${published_test_id}' and user_id = '${student_two_id}' limit 1;" 2>/dev/null || echo "")"
if [[ -z "$student_two_test_result_id" ]]; then
  api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/tests/${published_test_id}/start" "$student_two_token" "" >/dev/null
  api_json "POST" "http://localhost:${GATEWAY_PORT:-8080}/api/v1/testing/results" "$student_two_token" "$(cat <<JSON
{"testId":"${published_test_id}","score":20}
JSON
)" >/dev/null
fi

best_effort_notification_internal_json "POST" "/internal/notifications/users/${student_one_id}/reminders/assignments/deadline" "${NOTIFICATION_INTERNAL_SHARED_SECRET:-change-me-notification-internal}" "$(cat <<JSON
{"assignmentId":"${published_assignment_id}","title":"Merge Sort Walkthrough","deadline":"${published_assignment_deadline}","reminderAt":"${reminder_at}"}
JSON
)"

best_effort_notification_internal_json "POST" "/internal/notifications/users/${student_two_id}/reminders/tests/deadline" "${NOTIFICATION_INTERNAL_SHARED_SECRET:-change-me-notification-internal}" "$(cat <<JSON
{"testId":"${published_test_id}","title":"Sorting Quiz","deadline":"${published_test_available_until}","reminderAt":"${reminder_at}"}
JSON
)"

wait_for_analytics_service "$admin_token" || true
wait_for_student_risk "$student_one_id" "LOW" "$admin_token" || true
wait_for_student_risk "$student_two_id" "HIGH" "$admin_token" || true
wait_for_teacher_groups "$teacher_alpha_id" "$admin_token" || true

student_one_notifications=""
wait_for_notifications "$student_one_token" student_one_notifications || true
student_one_first_notification_id="$(printf '%s' "$student_one_notifications" | json_get items.0.id)"
if [[ -n "$student_one_first_notification_id" ]]; then
  best_effort_api_json "PATCH" "http://localhost:${GATEWAY_PORT:-8080}/api/notifications/${student_one_first_notification_id}/read" "$student_one_token" ""
fi

echo "Demo seed completed."
