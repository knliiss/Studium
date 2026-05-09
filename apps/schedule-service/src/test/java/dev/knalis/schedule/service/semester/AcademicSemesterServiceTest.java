package dev.knalis.schedule.service.semester;

import dev.knalis.schedule.dto.request.CreateAcademicSemesterRequest;
import dev.knalis.schedule.dto.response.AcademicSemesterResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.factory.semester.AcademicSemesterFactory;
import dev.knalis.schedule.mapper.AcademicSemesterMapper;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicSemesterServiceTest {

    @Mock
    private AcademicSemesterRepository academicSemesterRepository;

    @Mock
    private AcademicSemesterMapper academicSemesterMapper;

    private AcademicSemesterService academicSemesterService;

    @BeforeEach
    void setUp() {
        academicSemesterService = new AcademicSemesterService(
                academicSemesterRepository,
                new AcademicSemesterFactory(),
                academicSemesterMapper,
                new AcademicSemesterCalendar(),
                Clock.fixed(Instant.parse("2026-04-29T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createSemesterSavesTrimmedName() {
        UUID semesterId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 12, 31);
        LocalDate weekOneStartDate = LocalDate.of(2026, 9, 7);

        AcademicSemester savedSemester = new AcademicSemester();
        savedSemester.setId(semesterId);
        savedSemester.setName("Autumn 2026");
        savedSemester.setStartDate(startDate);
        savedSemester.setEndDate(endDate);
        savedSemester.setWeekOneStartDate(weekOneStartDate);
        savedSemester.setSemesterNumber(1);
        savedSemester.setActive(true);
        savedSemester.setPublished(true);
        savedSemester.setCreatedAt(now);
        savedSemester.setUpdatedAt(now);

        AcademicSemesterResponse response = new AcademicSemesterResponse(
                semesterId,
                "Autumn 2026",
                startDate,
                endDate,
                weekOneStartDate,
                1,
                true,
                true,
                now,
                now
        );

        when(academicSemesterRepository.save(any(AcademicSemester.class))).thenReturn(savedSemester);
        when(academicSemesterMapper.toResponse(savedSemester)).thenReturn(response);

        AcademicSemesterResponse result = academicSemesterService.createSemester(
                new CreateAcademicSemesterRequest("  Autumn 2026  ", startDate, endDate, weekOneStartDate, 1, true, false)
        );

        ArgumentCaptor<AcademicSemester> captor = ArgumentCaptor.forClass(AcademicSemester.class);
        verify(academicSemesterRepository).save(captor.capture());
        assertEquals("Autumn 2026", captor.getValue().getName());
        assertEquals(response, result);
    }

    @Test
    void createSemesterThrowsWhenAnotherActiveSemesterExists() {
        when(academicSemesterRepository.existsByActiveTrue()).thenReturn(true);

        assertThrows(
                ScheduleConflictException.class,
                () -> academicSemesterService.createSemester(
                        new CreateAcademicSemesterRequest(
                                "Spring 2027",
                                LocalDate.of(2027, 2, 1),
                                LocalDate.of(2027, 6, 30),
                                LocalDate.of(2027, 2, 1),
                                2,
                                true,
                                false
                        )
                )
        );
    }

    @Test
    void listSemestersReturnsStartDateDescending() {
        AcademicSemester spring = new AcademicSemester();
        spring.setId(UUID.randomUUID());
        spring.setName("Spring 2027");
        spring.setStartDate(LocalDate.of(2027, 2, 1));
        spring.setEndDate(LocalDate.of(2027, 6, 30));
        spring.setWeekOneStartDate(LocalDate.of(2027, 2, 1));
        spring.setSemesterNumber(2);
        spring.setActive(false);

        AcademicSemester autumn = new AcademicSemester();
        autumn.setId(UUID.randomUUID());
        autumn.setName("Autumn 2026");
        autumn.setStartDate(LocalDate.of(2026, 9, 1));
        autumn.setEndDate(LocalDate.of(2026, 12, 31));
        autumn.setWeekOneStartDate(LocalDate.of(2026, 9, 7));
        autumn.setSemesterNumber(1);
        autumn.setActive(true);

        AcademicSemesterResponse springResponse = new AcademicSemesterResponse(
                spring.getId(),
                spring.getName(),
                spring.getStartDate(),
                spring.getEndDate(),
                spring.getWeekOneStartDate(),
                spring.getSemesterNumber(),
                spring.isActive(),
                spring.isPublished(),
                Instant.now(),
                Instant.now()
        );
        AcademicSemesterResponse autumnResponse = new AcademicSemesterResponse(
                autumn.getId(),
                autumn.getName(),
                autumn.getStartDate(),
                autumn.getEndDate(),
                autumn.getWeekOneStartDate(),
                autumn.getSemesterNumber(),
                autumn.isActive(),
                autumn.isPublished(),
                Instant.now(),
                Instant.now()
        );

        when(academicSemesterRepository.findAllByOrderByStartDateDesc()).thenReturn(List.of(spring, autumn));
        when(academicSemesterMapper.toResponse(eq(spring))).thenReturn(springResponse);
        when(academicSemesterMapper.toResponse(eq(autumn))).thenReturn(autumnResponse);

        assertEquals(List.of(springResponse, autumnResponse), academicSemesterService.listSemesters());
    }

    @Test
    void getActiveSemesterReturnsExistingDetectedCurrentSemester() {
        UUID semesterId = UUID.randomUUID();
        Instant now = Instant.now();
        AcademicSemester semester = new AcademicSemester();
        semester.setId(semesterId);
        semester.setName("Semester 2 2025/2026");
        semester.setStartDate(LocalDate.of(2026, 2, 1));
        semester.setEndDate(LocalDate.of(2026, 8, 31));
        semester.setWeekOneStartDate(LocalDate.of(2026, 2, 1));
        semester.setSemesterNumber(2);
        semester.setActive(true);
        semester.setPublished(true);
        semester.setCreatedAt(now);
        semester.setUpdatedAt(now);

        AcademicSemesterResponse response = new AcademicSemesterResponse(
                semesterId,
                "Semester 2 2025/2026",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 2, 1),
                2,
                true,
                true,
                now,
                now
        );

        when(academicSemesterRepository.findFirstByActiveTrueOrderByStartDateDesc()).thenReturn(Optional.of(semester));
        when(academicSemesterMapper.toResponse(semester)).thenReturn(response);

        assertEquals(response, academicSemesterService.getActiveSemester());
        verify(academicSemesterRepository, never()).save(any(AcademicSemester.class));
    }

    @Test
    void getActiveSemesterCreatesDetectedCurrentSemesterWhenMissing() {
        UUID oldSemesterId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        Instant now = Instant.now();
        AcademicSemester oldSemester = new AcademicSemester();
        oldSemester.setId(oldSemesterId);
        oldSemester.setName("Semester 1 2025/2026");
        oldSemester.setActive(true);

        AcademicSemester savedSemester = new AcademicSemester();
        savedSemester.setId(semesterId);
        savedSemester.setName("Semester 2 2025/2026");
        savedSemester.setStartDate(LocalDate.of(2026, 2, 1));
        savedSemester.setEndDate(LocalDate.of(2026, 8, 31));
        savedSemester.setWeekOneStartDate(LocalDate.of(2026, 2, 1));
        savedSemester.setSemesterNumber(2);
        savedSemester.setActive(true);
        savedSemester.setPublished(true);
        savedSemester.setCreatedAt(now);
        savedSemester.setUpdatedAt(now);

        AcademicSemesterResponse response = new AcademicSemesterResponse(
                semesterId,
                "Semester 2 2025/2026",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 2, 1),
                2,
                true,
                true,
                now,
                now
        );

        when(academicSemesterRepository.findFirstByActiveTrueOrderByStartDateDesc()).thenReturn(Optional.empty());
        when(academicSemesterRepository.findFirstByNameOrderByStartDateDesc("Semester 2 2025/2026"))
                .thenReturn(Optional.empty());
        when(academicSemesterRepository.findAllByActiveTrue()).thenReturn(List.of(oldSemester));
        when(academicSemesterRepository.save(any(AcademicSemester.class))).thenReturn(savedSemester);
        when(academicSemesterMapper.toResponse(savedSemester)).thenReturn(response);

        AcademicSemesterResponse result = academicSemesterService.getActiveSemester();

        ArgumentCaptor<AcademicSemester> captor = ArgumentCaptor.forClass(AcademicSemester.class);
        verify(academicSemesterRepository).save(captor.capture());
        assertEquals("Semester 2 2025/2026", captor.getValue().getName());
        assertEquals(LocalDate.of(2026, 2, 1), captor.getValue().getStartDate());
        assertEquals(LocalDate.of(2026, 8, 31), captor.getValue().getEndDate());
        assertEquals(LocalDate.of(2026, 2, 1), captor.getValue().getWeekOneStartDate());
        assertEquals(2, captor.getValue().getSemesterNumber());
        assertEquals(response, result);
    }
}
