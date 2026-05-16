package dev.knalis.gateway.service;

import dev.knalis.gateway.client.education.EducationServiceClient;
import dev.knalis.gateway.client.schedule.ScheduleServiceClient;
import dev.knalis.gateway.client.schedule.dto.LessonSlotResponse;
import dev.knalis.gateway.dto.ResolvedLessonResponse;
import dev.knalis.gateway.exception.InvalidDateRangeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyScheduleService {

    private final EducationServiceClient educationServiceClient;
    private final ScheduleServiceClient scheduleServiceClient;

    public Mono<List<ResolvedLessonResponse>> getMyWeek(
            UUID userId,
            String bearerToken,
            String requestId,
            LocalDate startDate,
            Set<String> currentRoles
    ) {
        return getMyRange(
                userId,
                bearerToken,
                requestId,
                startDate,
                startDate.plusDays(6),
                currentRoles
        );
    }

    public Mono<List<ResolvedLessonResponse>> getMyRange(
            UUID userId,
            String bearerToken,
            String requestId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Set<String> currentRoles
    ) {
        if (dateTo.isBefore(dateFrom)) {
            throw new InvalidDateRangeException("dateTo must be on or after dateFrom");
        }

        return getMySchedule(
                userId,
                bearerToken,
                requestId,
                dateFrom,
                dateTo,
                currentRoles
        );
    }

    public Mono<String> exportMyCalendar(
            UUID userId,
            String bearerToken,
            String requestId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Set<String> currentRoles
    ) {
        return resolveDateRange(bearerToken, requestId, dateFrom, dateTo)
                .flatMap(dateRange -> Mono.zip(
                        getMyRange(
                                userId,
                                bearerToken,
                                requestId,
                                dateRange.dateFrom(),
                                dateRange.dateTo(),
                                currentRoles
                        ),
                        scheduleServiceClient.getLessonSlots(bearerToken, requestId)
                ))
                .map(tuple -> toCalendar(tuple.getT1(), lessonSlotsById(tuple.getT2())));
    }

    private Mono<List<ResolvedLessonResponse>> getMySchedule(
            UUID userId,
            String bearerToken,
            String requestId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Set<String> currentRoles
    ) {
        boolean teacher = currentRoles.contains("ROLE_TEACHER");
        boolean student = currentRoles.contains("ROLE_STUDENT");
        boolean loadStudentSchedule = student || !teacher;

        Mono<List<ResolvedLessonResponse>> teacherLessonsMono = teacher
                ? scheduleServiceClient.getTeacherRange(
                        bearerToken,
                        requestId,
                        userId,
                        dateFrom,
                        dateTo
                )
                : Mono.just(List.of());

        Mono<List<ResolvedLessonResponse>> studentLessonsMono = loadStudentSchedule
                ? educationServiceClient.getGroupsByUser(bearerToken, requestId, userId)
                .flatMap(groupMemberships -> {
                    if (groupMemberships.isEmpty()) {
                        return Mono.just(List.of());
                    }
                    return Flux.fromIterable(groupMemberships)
                            .flatMap(groupMembership -> scheduleServiceClient.getGroupRange(
                                    bearerToken,
                                    requestId,
                                    groupMembership.groupId(),
                                    dateFrom,
                                    dateTo
                            ).map(lessons -> filterLessonsBySubgroup(lessons, groupMembership.subgroup())))
                            .flatMapIterable(items -> items)
                            .collectList();
                })
                : Mono.just(List.of());

        return Mono.zip(teacherLessonsMono, studentLessonsMono)
                .flatMap(tuple -> {
                    List<ResolvedLessonResponse> merged = mergeLessons(tuple.getT1(), tuple.getT2());
                    if (merged.isEmpty()) {
                        return Mono.just(List.of());
                    }

                    return scheduleServiceClient.getLessonSlots(bearerToken, requestId)
                            .map(this::slotNumbersById)
                            .map(slotNumbers -> sortLessons(merged, slotNumbers));
                });
    }

    private List<ResolvedLessonResponse> mergeLessons(
            List<ResolvedLessonResponse> teacherLessons,
            List<ResolvedLessonResponse> studentLessons
    ) {
        Map<String, ResolvedLessonResponse> merged = new LinkedHashMap<>();
        for (ResolvedLessonResponse lesson : teacherLessons) {
            merged.put(buildLessonKey(lesson), lesson);
        }
        for (ResolvedLessonResponse lesson : studentLessons) {
            merged.put(buildLessonKey(lesson), lesson);
        }
        return merged.values().stream().toList();
    }

    private String buildLessonKey(ResolvedLessonResponse lesson) {
        return lesson.date()
                + "|"
                + lesson.semesterId()
                + "|"
                + lesson.templateId()
                + "|"
                + lesson.groupId()
                + "|"
                + lesson.subjectId()
                + "|"
                + lesson.teacherId()
                + "|"
                + lesson.slotId()
                + "|"
                + lesson.subgroup()
                + "|"
                + lesson.sourceType()
                + "|"
                + lesson.overrideType();
    }

    private Map<UUID, Integer> slotNumbersById(List<LessonSlotResponse> lessonSlots) {
        return lessonSlots.stream()
                .collect(Collectors.toMap(LessonSlotResponse::id, LessonSlotResponse::number));
    }

    private Map<UUID, LessonSlotResponse> lessonSlotsById(List<LessonSlotResponse> lessonSlots) {
        return lessonSlots.stream()
                .collect(Collectors.toMap(LessonSlotResponse::id, lessonSlot -> lessonSlot));
    }

    private List<ResolvedLessonResponse> sortLessons(
            List<ResolvedLessonResponse> lessons,
            Map<UUID, Integer> slotNumbersById
    ) {
        return lessons.stream()
                .sorted(Comparator
                        .comparing(ResolvedLessonResponse::date)
                        .thenComparing(response -> slotNumbersById.getOrDefault(response.slotId(), Integer.MAX_VALUE))
                        .thenComparing(response -> response.groupId().toString())
                        .thenComparing(response -> response.teacherId().toString())
                        .thenComparing(response -> response.subjectId().toString())
                        .thenComparing(response -> response.slotId().toString()))
                .toList();
    }

    private List<ResolvedLessonResponse> filterLessonsBySubgroup(
            List<ResolvedLessonResponse> lessons,
            String membershipSubgroup
    ) {
        return lessons.stream()
                .filter(lesson -> subgroupVisible(membershipSubgroup, lesson.subgroup()))
                .toList();
    }

    private boolean subgroupVisible(String membershipSubgroup, String lessonSubgroup) {
        String normalizedLessonSubgroup = normalizeSubgroup(lessonSubgroup);
        if ("ALL".equals(normalizedLessonSubgroup)) {
            return true;
        }

        String normalizedMembershipSubgroup = normalizeSubgroup(membershipSubgroup);
        return "ALL".equals(normalizedMembershipSubgroup)
                || normalizedMembershipSubgroup.equals(normalizedLessonSubgroup);
    }

    private String normalizeSubgroup(String subgroup) {
        return subgroup == null || subgroup.isBlank() ? "ALL" : subgroup;
    }

    private Mono<DateRange> resolveDateRange(
            String bearerToken,
            String requestId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom != null && dateTo != null) {
            if (dateTo.isBefore(dateFrom)) {
                throw new InvalidDateRangeException("dateTo must be on or after dateFrom");
            }
            return Mono.just(new DateRange(dateFrom, dateTo));
        }

        return scheduleServiceClient.getActiveSemester(bearerToken, requestId)
                .map(semester -> new DateRange(
                        dateFrom != null ? dateFrom : semester.startDate(),
                        dateTo != null ? dateTo : semester.endDate()
                ))
                .map(this::validateDateRange);
    }

    private DateRange validateDateRange(DateRange dateRange) {
        if (dateRange.dateTo().isBefore(dateRange.dateFrom())) {
            throw new InvalidDateRangeException("dateTo must be on or after dateFrom");
        }
        return dateRange;
    }

    private String toCalendar(List<ResolvedLessonResponse> lessons, Map<UUID, LessonSlotResponse> lessonSlotsById) {
        StringBuilder calendar = new StringBuilder();
        calendar.append("BEGIN:VCALENDAR\r\n");
        calendar.append("VERSION:2.0\r\n");
        calendar.append("PRODID:-//Studium//My Schedule//EN\r\n");
        calendar.append("CALSCALE:GREGORIAN\r\n");

        for (ResolvedLessonResponse lesson : lessons) {
            LessonSlotResponse lessonSlot = lessonSlotsById.get(lesson.slotId());
            if (lessonSlot == null) {
                continue;
            }

            LocalDateTime start = LocalDateTime.of(lesson.date(), lessonSlot.startTime());
            LocalDateTime end = LocalDateTime.of(lesson.date(), lessonSlot.endTime());

            calendar.append("BEGIN:VEVENT\r\n");
            calendar.append("UID:").append(uid(lesson)).append("\r\n");
            calendar.append("DTSTAMP:").append(formatUtc(LocalDateTime.now())).append("\r\n");
            calendar.append("DTSTART:").append(formatUtc(start)).append("\r\n");
            calendar.append("DTEND:").append(formatUtc(end)).append("\r\n");
            calendar.append("SUMMARY:").append(escape(summary(lesson))).append("\r\n");
            calendar.append("DESCRIPTION:").append(escape(description(lesson))).append("\r\n");
            if (lesson.roomId() != null) {
                calendar.append("LOCATION:").append(lesson.roomId()).append("\r\n");
            }
            calendar.append("END:VEVENT\r\n");
        }

        calendar.append("END:VCALENDAR\r\n");
        return calendar.toString();
    }

    private String summary(ResolvedLessonResponse lesson) {
        String lessonType = lesson.lessonTypeDisplayName() == null ? "Lesson" : lesson.lessonTypeDisplayName();
        return lessonType + " - subject " + lesson.subjectId();
    }

    private String description(ResolvedLessonResponse lesson) {
        StringBuilder description = new StringBuilder();
        description.append("Lesson type: ").append(lesson.lessonType());
        description.append("\\nLesson format: ").append(lesson.lessonFormat());
        description.append("\\nSubject ID: ").append(lesson.subjectId());
        if (lesson.roomId() != null) {
            description.append("\\nRoom ID: ").append(lesson.roomId());
        }
        if (lesson.onlineMeetingUrl() != null) {
            description.append("\\nMeeting URL: ").append(lesson.onlineMeetingUrl());
        }
        if (lesson.notes() != null) {
            description.append("\\nNotes: ").append(lesson.notes());
        }
        return description.toString();
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }

    private String uid(ResolvedLessonResponse lesson) {
        return lesson.date()
                + "-"
                + lesson.groupId()
                + "-"
                + lesson.subjectId()
                + "-"
                + lesson.slotId()
                + "-"
                + lesson.sourceType()
                + "@studium";
    }

    private String formatUtc(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
    }

    private record DateRange(LocalDate dateFrom, LocalDate dateTo) {
    }
}
