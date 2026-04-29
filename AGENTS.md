# STUDIUM ENGINEERING GUIDELINES (SOURCE OF TRUTH)

---

## 1. PROJECT OVERVIEW

Studium is a Java 21 microservice-based platform.

Architecture:

* microservices
* event-driven communication (Kafka)
* API gateway entrypoint
* strict service isolation

Goal:
Transform system into scalable LMS without breaking existing architecture.

---

## 2. CORE PRINCIPLES (MANDATORY)

1. DO NOT BREAK EXISTING ARCHITECTURE
2. DO NOT REWRITE WORKING CODE
3. FOLLOW EXISTING CODE STYLE EXACTLY
4. EACH SERVICE OWNS ITS DATA
5. NO DIRECT DB ACCESS BETWEEN SERVICES
6. COMMUNICATION:

    * sync → REST (via gateway/internal)
    * async → Kafka

---

## 3. CODE STYLE (CRITICAL)

Agent MUST replicate patterns from:

* auth-service
* profile-service

Rules:

* same package structure
* same naming style
* same DTO patterns
* same controller/service/repository layering
* same exception handling
* same config style

If uncertain → COPY existing implementation pattern.

### Java Import Style

* Java code must not use fully qualified class names inside method bodies, fields, parameters, annotations, or expressions.
* All external types must be declared in import statements.
* Code must use simple class names after import.
* Wildcard imports are forbidden.
* Unused imports are forbidden.
* Fully qualified names are allowed only in package declarations, import declarations, JavaDoc links, or unavoidable class-name collisions.
* Agent must clean imports before completing any Java task.
* If a fully qualified class name remains because of a real class-name collision, the reason must be documented in the task NOTES.

Bad:

```java
.map(org.springframework.security.core.GrantedAuthority::getAuthority)
```

Good:

```java
import org.springframework.security.core.GrantedAuthority;

.map(GrantedAuthority::getAuthority)
```

Bad:

```java
return java.util.UUID.randomUUID();
```

Good:

```java
import java.util.UUID;

return UUID.randomUUID();
```

---

## 4. SERVICE ARCHITECTURE

### Existing (DO NOT CHANGE)

* gateway
* auth-service
* profile-service
* file-service
* notification-service

---

### New (TO ADD)

* education-service
* content-service
* assignment-service
* testing-service

---

## 5. DOMAIN BOUNDARIES

### education-service

Owns:

* Group
* Subject
* Topic
* GroupStudent

---

### content-service

Owns:

* Lecture
* LectureContent

---

### assignment-service

Owns:

* Assignment
* Submission
* Grade

---

### testing-service

Owns:

* Test
* Question
* Answer
* TestResult

---

## 6. DATA OWNERSHIP RULE

STRICT:

Service A CANNOT:

* query DB of service B
* reuse entities of service B

ONLY:

* IDs
* API calls
* Kafka events

---

## 7. IDENTITY RULE

User data is NOT duplicated.

Always use:

* userId (from JWT)

---

## 8. FILE STORAGE RULE

All files handled ONLY via file-service.

Store:

* fileId
* entityType
* entityId
* ownerId

NEVER:

* store files locally
* duplicate file metadata

---

## 9. EVENT-DRIVEN MODEL

Kafka is REQUIRED for domain events.

Events MUST:

* be versioned
* be stored in shared module

Naming:

* subject.created.v1
* assignment.created.v1
* submission.created.v1

---

## 10. OUTBOX PATTERN (MANDATORY)

All services that publish events MUST implement outbox.

Flow:

1. DB transaction
2. save entity
3. save event to outbox table
4. background publisher → Kafka

---

## 11. API RULES

ALL requests go through gateway.

Prefixes:

* /api/v1/education/**
* /api/v1/content/**
* /api/v1/assignments/**
* /api/v1/testing/**

Gateway CORS rule:

* frontend CORS must be configured at gateway/public entrypoint
* preflight `OPTIONS` requests must be permitted
* local frontend origins must be configurable
* frontend must not call downstream services directly to avoid CORS
* CORS fixes must not weaken production security

---

## 12. AGGREGATION RULE

Gateway acts as BFF.

Allowed:

* data aggregation
* response composition

Forbidden:

* business logic

Example:
GET /api/v1/subjects/{id}/full

---

## 13. DATABASE RULES

* PostgreSQL
* separate schema per service
* JPA (Hibernate)
* indexes required on:

    * userId
    * foreign keys

---

## 14. SECURITY MODEL

Reuse existing JWT system.

Roles:

* ROLE_STUDENT
* ROLE_TEACHER
* ROLE_ADMIN

Access:

* teacher → manage content
* student → consume + submit

---

## 15. VALIDATION RULES

* use jakarta validation
* validate DTOs only
* NEVER trust client input

---

## 16. DTO RULE

NEVER expose entities directly.

Always:
Entity → DTO → Response

---

## 17. ERROR HANDLING

Follow existing shared-web format.

DO NOT invent new response structures.

---

## 18. LOGGING

Use existing logging approach:

* structured logs
* correlationId

---

## 19. VERSIONING

Mandatory:

API:

* /api/v1/...

Events:

* *.v1

---

## 20. PERFORMANCE RULES

* use Redis for caching
* avoid N+1 queries
* paginate all list endpoints

---

## 21. WHAT IS FORBIDDEN

Agent MUST NOT:

* introduce new frameworks
* introduce CQRS
* introduce event sourcing
* use reactive stack outside gateway
* create shared DB tables
* bypass gateway
* duplicate logic across services

---

## 22. IMPLEMENTATION ORDER

1. education-service
2. assignment-service
3. content-service
4. testing-service
5. Kafka integration
6. gateway aggregation

---

## 23. DEFINITION OF DONE

Feature is complete ONLY if:

* follows architecture rules
* matches code style
* includes validation
* includes tests (basic)
* includes API endpoint
* includes DB schema
* includes events (if needed)

---

## 24. DECISION RULE

If unsure:

1. check existing services
2. reuse same pattern
3. DO NOT invent new approach

---

## 25. FRONTEND DOCUMENTATION RULE

Before implementing frontend UI, agents MUST read:

* `DESIGN.md`
* `ARCHITECTURE.md`
* `docs/API_CONTRACT.md`
* `docs/FRONTEND_API_START.md`
* `docs/FRONTEND_SCREEN_API_MAP.md` if present

Mandatory frontend implementation order:

1. `DESIGN.md`
2. `ARCHITECTURE.md`
3. API contract documentation
4. existing frontend conventions if present

Frontend agents MUST NOT:

* invent endpoints
* bypass gateway
* call downstream services directly
* expose screens that are unsupported by the current backend

---

## 26. FRONTEND LOCALIZATION RULES

Frontend localization is MANDATORY from the beginning.

Required initial languages:

* English
* Ukrainian
* Polish

Required locale codes:

* `en`
* `uk`
* `pl`

Mandatory rules:

* All user-facing frontend strings must be localized.
* Hardcoded UI text in frontend components is forbidden.
* The localization system must be extendable for adding more languages later.
* Translation files must be separated from UI components.
* Components must use translation keys instead of raw display strings.
* Navigation labels must be localized.
* Page titles and descriptions must be localized.
* Form labels, placeholders, validation messages, and buttons must be localized.
* Table headers and filter labels must be localized.
* Empty, loading, success, and error states must be localized.
* Backend enum values must not be shown directly to users.
* Backend enum values must remain canonical in API requests and responses.
* Frontend must localize enum display values separately.
* Dates and times must be formatted according to the selected locale.
* Backend ISO date values must not be changed before sending to the API.
* Backend error codes must be mapped to localized frontend messages where possible.
* Backend error message may be shown only as fallback.
* Notification UI must localize notification labels and payload rendering.
* Schedule UI must localize `lessonType`, `lessonFormat`, and `weekType`.
* Adding a new language must not require rewriting components.

Bad:

```tsx
<Button>Save</Button>
```

Good:

```tsx
<Button>{t("common.actions.save")}</Button>
```

Bad:

```tsx
<span>{lesson.lessonType}</span>
```

Good:

```tsx
<span>{t(`schedule.lessonType.${lesson.lessonType}`)}</span>
```

Bad:

```tsx
<p>Access denied</p>
```

Good:

```tsx
<p>{t("errors.ACCESS_DENIED")}</p>
```

Canonical backend values MUST remain unchanged in API requests and responses.
Frontend display text MUST be localized separately.

Examples of backend canonical values:

* `LECTURE`
* `PRACTICAL`
* `LABORATORY`
* `ONLINE`
* `OFFLINE`
* `ALL`
* `ODD`
* `EVEN`
* `DRAFT`
* `PUBLISHED`
* `ARCHIVED`
* `CLOSED`
* `LOW`
* `MEDIUM`
* `HIGH`

Example translation structure:

```json
{
  "schedule": {
    "lessonType": {
      "LECTURE": "Lecture",
      "PRACTICAL": "Practical",
      "LABORATORY": "Laboratory"
    },
    "lessonFormat": {
      "ONLINE": "Online",
      "OFFLINE": "Offline"
    }
  }
}
```

---

## FINAL DIRECTIVE

You are extending a production-grade system.

Your priorities:

* consistency > creativity
* stability > speed
* architecture > shortcuts
