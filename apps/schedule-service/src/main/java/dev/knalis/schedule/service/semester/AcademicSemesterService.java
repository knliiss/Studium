package dev.knalis.schedule.service.semester;

import dev.knalis.schedule.dto.request.CreateAcademicSemesterRequest;
import dev.knalis.schedule.dto.request.UpdateAcademicSemesterRequest;
import dev.knalis.schedule.dto.response.AcademicSemesterResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.exception.AcademicSemesterNotFoundException;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.exception.ScheduleValidationException;
import dev.knalis.schedule.factory.semester.AcademicSemesterFactory;
import dev.knalis.schedule.mapper.AcademicSemesterMapper;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AcademicSemesterService {
    
    private final AcademicSemesterRepository academicSemesterRepository;
    private final AcademicSemesterFactory academicSemesterFactory;
    private final AcademicSemesterMapper academicSemesterMapper;
    private final Clock clock;
    
    @Transactional
    public AcademicSemesterResponse createSemester(CreateAcademicSemesterRequest request) {
        validateDates(request.startDate(), request.endDate(), request.weekOneStartDate());
        assertActiveSemesterAllowed(null, request.active());
        
        AcademicSemester semester = academicSemesterFactory.newAcademicSemester(
                request.name(),
                request.startDate(),
                request.endDate(),
                request.weekOneStartDate(),
                request.active()
        );
        
        return academicSemesterMapper.toResponse(academicSemesterRepository.save(semester));
    }
    
    @Transactional(readOnly = true)
    public AcademicSemesterResponse getSemester(UUID semesterId) {
        return academicSemesterMapper.toResponse(requireSemester(semesterId));
    }
    
    @Transactional
    public AcademicSemesterResponse getActiveSemester() {
        CurrentAcademicSemester currentSemester = resolveCurrentAcademicSemester(LocalDate.now(clock));
        AcademicSemester semester = academicSemesterRepository
                .findFirstByNameOrderByStartDateDesc(currentSemester.name())
                .map(this::activateSemester)
                .orElseGet(() -> createCurrentSemester(currentSemester));
        return academicSemesterMapper.toResponse(semester);
    }

    @Transactional(readOnly = true)
    public List<AcademicSemesterResponse> listSemesters() {
        return academicSemesterRepository.findAllByOrderByStartDateDesc().stream()
                .map(academicSemesterMapper::toResponse)
                .toList();
    }
    
    @Transactional
    public AcademicSemesterResponse updateSemester(UUID semesterId, UpdateAcademicSemesterRequest request) {
        AcademicSemester semester = requireSemester(semesterId);
        validateDates(request.startDate(), request.endDate(), request.weekOneStartDate());
        assertActiveSemesterAllowed(semesterId, request.active());
        
        semester.setName(request.name().trim());
        semester.setStartDate(request.startDate());
        semester.setEndDate(request.endDate());
        semester.setWeekOneStartDate(request.weekOneStartDate());
        semester.setActive(request.active());
        
        return academicSemesterMapper.toResponse(academicSemesterRepository.save(semester));
    }
    
    private AcademicSemester requireSemester(UUID semesterId) {
        return academicSemesterRepository.findById(semesterId)
                .orElseThrow(() -> new AcademicSemesterNotFoundException(semesterId));
    }

    private AcademicSemester activateSemester(AcademicSemester semester) {
        deactivateActiveSemestersExcept(semester.getId());
        if (semester.isActive()) {
            return semester;
        }

        semester.setActive(true);
        return academicSemesterRepository.save(semester);
    }

    private AcademicSemester createCurrentSemester(CurrentAcademicSemester currentSemester) {
        deactivateActiveSemestersExcept(null);
        AcademicSemester semester = academicSemesterFactory.newAcademicSemester(
                currentSemester.name(),
                currentSemester.startDate(),
                currentSemester.endDate(),
                currentSemester.weekOneStartDate(),
                true
        );

        return academicSemesterRepository.save(semester);
    }

    private void deactivateActiveSemestersExcept(UUID semesterId) {
        List<AcademicSemester> semestersToDeactivate = new ArrayList<>();
        for (AcademicSemester activeSemester : academicSemesterRepository.findAllByActiveTrue()) {
            if (Objects.equals(activeSemester.getId(), semesterId)) {
                continue;
            }
            activeSemester.setActive(false);
            semestersToDeactivate.add(activeSemester);
        }

        if (!semestersToDeactivate.isEmpty()) {
            academicSemesterRepository.saveAll(semestersToDeactivate);
        }
    }

    private CurrentAcademicSemester resolveCurrentAcademicSemester(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= 9) {
            int academicYearStart = date.getYear();
            return firstSemester(academicYearStart);
        }
        if (month == 1) {
            int academicYearStart = date.getYear() - 1;
            return firstSemester(academicYearStart);
        }

        int academicYearStart = date.getYear() - 1;
        int academicYearEnd = date.getYear();
        return new CurrentAcademicSemester(
                "Semester 2 " + academicYearStart + "/" + academicYearEnd,
                LocalDate.of(academicYearEnd, 2, 1),
                LocalDate.of(academicYearEnd, 8, 31),
                LocalDate.of(academicYearEnd, 2, 1)
        );
    }

    private CurrentAcademicSemester firstSemester(int academicYearStart) {
        int academicYearEnd = academicYearStart + 1;
        return new CurrentAcademicSemester(
                "Semester 1 " + academicYearStart + "/" + academicYearEnd,
                LocalDate.of(academicYearStart, 9, 1),
                LocalDate.of(academicYearEnd, 1, 31),
                LocalDate.of(academicYearStart, 9, 1)
        );
    }
    
    private void validateDates(LocalDate startDate, LocalDate endDate, LocalDate weekOneStartDate) {
        if (endDate.isBefore(startDate)) {
            throw new ScheduleValidationException(
                    "INVALID_SEMESTER_DATE_RANGE",
                    "Semester end date must be on or after start date",
                    Map.of(
                            "startDate", startDate.toString(),
                            "endDate", endDate.toString()
                    )
            );
        }
        if (weekOneStartDate.isBefore(startDate) || weekOneStartDate.isAfter(endDate)) {
            throw new ScheduleValidationException(
                    "INVALID_WEEK_ONE_START_DATE",
                    "Week one start date must be within the semester date range",
                    Map.of(
                            "startDate", startDate.toString(),
                            "endDate", endDate.toString(),
                            "weekOneStartDate", weekOneStartDate.toString()
                    )
            );
        }
    }
    
    private void assertActiveSemesterAllowed(UUID semesterId, boolean active) {
        if (!active) {
            return;
        }
        
        boolean anotherActiveSemesterExists = semesterId == null
                ? academicSemesterRepository.existsByActiveTrue()
                : academicSemesterRepository.existsByActiveTrueAndIdNot(semesterId);
        
        if (anotherActiveSemesterExists) {
            Map<String, Object> details = new LinkedHashMap<>();
            if (semesterId != null) {
                details.put("semesterId", semesterId);
            }
            
            throw new ScheduleConflictException(
                    "ACTIVE_SEMESTER_ALREADY_EXISTS",
                    "Another active academic semester already exists",
                    details
            );
        }
    }

    private record CurrentAcademicSemester(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate weekOneStartDate
    ) {
    }
}
