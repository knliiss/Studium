package dev.knalis.gateway.service;

import dev.knalis.gateway.client.education.EducationServiceClient;
import dev.knalis.gateway.client.education.dto.GroupMembershipResponse;
import dev.knalis.gateway.client.schedule.ScheduleServiceClient;
import dev.knalis.gateway.client.schedule.dto.AcademicSemesterResponse;
import dev.knalis.gateway.client.schedule.dto.LessonSlotResponse;
import dev.knalis.gateway.dto.ResolvedLessonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MyScheduleServiceTest {
    
    @Mock
    private EducationServiceClient educationServiceClient;
    
    @Mock
    private ScheduleServiceClient scheduleServiceClient;
    
    private MyScheduleService myScheduleService;
    
    @BeforeEach
    void setUp() {
        myScheduleService = new MyScheduleService(educationServiceClient, scheduleServiceClient);
    }
    
    @Test
    void getMyWeekReturnsEmptyWhenUserHasNoGroups() {
        UUID userId = UUID.randomUUID();
        
        when(educationServiceClient.getGroupsByUser("token", "request-id", userId))
                .thenReturn(Mono.just(List.of()));
        
        List<ResolvedLessonResponse> result = myScheduleService.getMyWeek(
                userId,
                "token",
                "request-id",
                LocalDate.of(2026, 9, 7)
        ).block();
        
        assertEquals(List.of(), result);
        
        verifyNoInteractions(scheduleServiceClient);
    }
    
    @Test
    void getMyRangeMergesAndSortsLessonsByDateAndSlotNumber() {
        UUID userId = UUID.randomUUID();
        UUID firstGroupId = UUID.randomUUID();
        UUID secondGroupId = UUID.randomUUID();
        UUID firstSlotId = UUID.randomUUID();
        UUID secondSlotId = UUID.randomUUID();
        
        LessonSlotResponse firstSlot = new LessonSlotResponse(
                firstSlotId,
                1,
                LocalTime.of(8, 30),
                LocalTime.of(10, 0),
                true,
                Instant.now(),
                Instant.now()
        );
        LessonSlotResponse secondSlot = new LessonSlotResponse(
                secondSlotId,
                2,
                LocalTime.of(10, 15),
                LocalTime.of(11, 45),
                true,
                Instant.now(),
                Instant.now()
        );
        
        ResolvedLessonResponse laterSlot = new ResolvedLessonResponse(
                LocalDate.of(2026, 9, 8),
                UUID.randomUUID(),
                secondGroupId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                secondSlotId,
                1,
                "ODD",
                "PRACTICAL",
                "Practical",
                "ONLINE",
                null,
                null,
                "Second slot",
                "OVERRIDE",
                "EXTRA"
        );
        ResolvedLessonResponse earlierSlot = new ResolvedLessonResponse(
                LocalDate.of(2026, 9, 8),
                UUID.randomUUID(),
                firstGroupId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                firstSlotId,
                1,
                "ODD",
                "LECTURE",
                "Lecture",
                "OFFLINE",
                UUID.randomUUID(),
                null,
                "First slot",
                "TEMPLATE",
                null
        );
        
        when(educationServiceClient.getGroupsByUser("token", "request-id", userId))
                .thenReturn(Mono.just(List.of(
                        new GroupMembershipResponse(firstGroupId),
                        new GroupMembershipResponse(secondGroupId)
                )));
        when(scheduleServiceClient.getLessonSlots("token", "request-id"))
                .thenReturn(Mono.just(List.of(secondSlot, firstSlot)));
        when(scheduleServiceClient.getGroupRange(
                "token",
                "request-id",
                firstGroupId,
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9)
        )).thenReturn(Mono.just(List.of(laterSlot)));
        when(scheduleServiceClient.getGroupRange(
                "token",
                "request-id",
                secondGroupId,
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9)
        )).thenReturn(Mono.just(List.of(earlierSlot)));
        
        List<ResolvedLessonResponse> result = myScheduleService.getMyRange(
                userId,
                "token",
                "request-id",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9)
        ).block();
        
        assertEquals(List.of(earlierSlot, laterSlot), result);
    }

    @Test
    void exportMyCalendarUsesActiveSemesterWhenDateRangeIsMissing() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        LessonSlotResponse lessonSlot = new LessonSlotResponse(
                slotId,
                1,
                LocalTime.of(8, 30),
                LocalTime.of(10, 0),
                true,
                Instant.now(),
                Instant.now()
        );

        ResolvedLessonResponse lesson = new ResolvedLessonResponse(
                LocalDate.of(2026, 9, 8),
                UUID.randomUUID(),
                groupId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                slotId,
                1,
                "ODD",
                "LECTURE",
                "Lecture",
                "ONLINE",
                null,
                "https://meet.example/test",
                "Notes",
                "TEMPLATE",
                null
        );

        when(scheduleServiceClient.getActiveSemester("token", "request-id"))
                .thenReturn(Mono.just(new AcademicSemesterResponse(
                        UUID.randomUUID(),
                        "Autumn 2026",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 12, 31),
                        LocalDate.of(2026, 9, 7),
                        true,
                        Instant.now(),
                        Instant.now()
                )));
        when(educationServiceClient.getGroupsByUser("token", "request-id", userId))
                .thenReturn(Mono.just(List.of(new GroupMembershipResponse(groupId))));
        when(scheduleServiceClient.getLessonSlots("token", "request-id"))
                .thenReturn(Mono.just(List.of(lessonSlot)));
        when(scheduleServiceClient.getGroupRange(
                "token",
                "request-id",
                groupId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 31)
        )).thenReturn(Mono.just(List.of(lesson)));

        String calendar = myScheduleService.exportMyCalendar(
                userId,
                "token",
                "request-id",
                null,
                null
        ).block();

        assertTrue(calendar.contains("BEGIN:VCALENDAR"));
        assertTrue(calendar.contains("SUMMARY:Lecture - subject " + lesson.subjectId()));
        assertTrue(calendar.contains("Meeting URL: https://meet.example/test"));
    }
}
