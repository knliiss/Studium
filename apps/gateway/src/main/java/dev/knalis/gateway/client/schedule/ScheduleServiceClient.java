package dev.knalis.gateway.client.schedule;

import dev.knalis.gateway.client.schedule.dto.AcademicSemesterResponse;
import dev.knalis.gateway.client.schedule.dto.LessonSlotResponse;
import dev.knalis.gateway.dto.ResolvedLessonResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleServiceClient {
    
    Mono<List<ResolvedLessonResponse>> getGroupWeek(
            String bearerToken,
            String requestId,
            UUID groupId,
            LocalDate startDate
    );
    
    Mono<List<ResolvedLessonResponse>> getGroupRange(
            String bearerToken,
            String requestId,
            UUID groupId,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    Mono<List<ResolvedLessonResponse>> getTeacherRange(
            String bearerToken,
            String requestId,
            UUID teacherId,
            LocalDate dateFrom,
            LocalDate dateTo
    );
    
    Mono<AcademicSemesterResponse> getActiveSemester(String bearerToken, String requestId);
    
    Mono<List<LessonSlotResponse>> getLessonSlots(String bearerToken, String requestId);
}
