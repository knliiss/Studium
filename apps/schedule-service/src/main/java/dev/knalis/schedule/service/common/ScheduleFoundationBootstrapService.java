package dev.knalis.schedule.service.common;

import dev.knalis.schedule.config.ScheduleBootstrapProperties;
import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.factory.semester.AcademicSemesterFactory;
import dev.knalis.schedule.factory.slot.LessonSlotFactory;
import dev.knalis.schedule.repository.AcademicSemesterRepository;
import dev.knalis.schedule.repository.LessonSlotRepository;
import dev.knalis.schedule.service.semester.AcademicSemesterCalendar;
import dev.knalis.schedule.service.semester.AcademicSemesterPeriod;
import dev.knalis.schedule.service.slot.CanonicalLessonSlots;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleFoundationBootstrapService implements ApplicationRunner {

    private final ScheduleBootstrapProperties scheduleBootstrapProperties;
    private final LessonSlotRepository lessonSlotRepository;
    private final LessonSlotFactory lessonSlotFactory;
    private final AcademicSemesterRepository academicSemesterRepository;
    private final AcademicSemesterFactory academicSemesterFactory;
    private final AcademicSemesterCalendar academicSemesterCalendar;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!scheduleBootstrapProperties.isEnabled()) {
            log.info("Schedule foundation bootstrap is disabled.");
            return;
        }

        bootstrapCanonicalSlots();
        bootstrapSemesters();
    }

    private void bootstrapCanonicalSlots() {
        Map<Integer, LessonSlot> slotsByNumber = lessonSlotRepository.findAllByOrderByNumberAsc().stream()
                .peek(this::deactivateNonCanonicalNumber)
                .filter(lessonSlot -> CanonicalLessonSlots.isCanonicalNumber(lessonSlot.getNumber()))
                .collect(Collectors.toMap(LessonSlot::getNumber, Function.identity()));

        for (Integer number : CanonicalLessonSlots.numbers()) {
            LessonSlot lessonSlot = slotsByNumber.get(number);
            if (lessonSlot == null) {
                lessonSlotRepository.save(lessonSlotFactory.newLessonSlot(
                        number,
                        CanonicalLessonSlots.startTime(number),
                        CanonicalLessonSlots.endTime(number),
                        true
                ));
                continue;
            }

            if (!CanonicalLessonSlots.isCanonicalActiveSlot(lessonSlot)) {
                lessonSlot.setStartTime(CanonicalLessonSlots.startTime(number));
                lessonSlot.setEndTime(CanonicalLessonSlots.endTime(number));
                lessonSlot.setActive(true);
                lessonSlotRepository.save(lessonSlot);
            }
        }
    }

    private void deactivateNonCanonicalNumber(LessonSlot lessonSlot) {
        if (CanonicalLessonSlots.isCanonicalNumber(lessonSlot.getNumber()) || !lessonSlot.isActive()) {
            return;
        }

        lessonSlot.setActive(false);
        lessonSlotRepository.save(lessonSlot);
    }

    private void bootstrapSemesters() {
        LocalDate today = LocalDate.now(clock);
        AcademicSemesterPeriod current = academicSemesterCalendar.current(today);
        AcademicSemesterPeriod future = academicSemesterCalendar.future(today);

        AcademicSemester activeSemester = academicSemesterRepository.findFirstByActiveTrueOrderByStartDateDesc()
                .orElseGet(() -> activateOrCreateCurrentSemester(current));
        if (!activeSemester.isPublished()) {
            activeSemester.setPublished(true);
            academicSemesterRepository.save(activeSemester);
        }

        ensureFutureSemester(future);
    }

    private AcademicSemester activateOrCreateCurrentSemester(AcademicSemesterPeriod current) {
        return academicSemesterRepository.findFirstByNameOrderByStartDateDesc(current.name())
                .map(semester -> {
                    semester.setActive(true);
                    semester.setPublished(true);
                    return academicSemesterRepository.save(semester);
                })
                .orElseGet(() -> academicSemesterRepository.save(academicSemesterFactory.newAcademicSemester(
                        current.name(),
                        current.startDate(),
                        current.endDate(),
                        current.weekOneStartDate(),
                        true,
                        true
                )));
    }

    private void ensureFutureSemester(AcademicSemesterPeriod future) {
        AcademicSemester semester = academicSemesterRepository.findFirstByNameOrderByStartDateDesc(future.name())
                .orElse(null);
        if (semester == null) {
            academicSemesterRepository.save(academicSemesterFactory.newAcademicSemester(
                    future.name(),
                    future.startDate(),
                    future.endDate(),
                    future.weekOneStartDate(),
                    false,
                    false
            ));
            return;
        }

        if (sameDates(semester, future)) {
            return;
        }

        semester.setStartDate(future.startDate());
        semester.setEndDate(future.endDate());
        semester.setWeekOneStartDate(future.weekOneStartDate());
        academicSemesterRepository.save(semester);
    }

    private boolean sameDates(AcademicSemester semester, AcademicSemesterPeriod period) {
        return Objects.equals(semester.getStartDate(), period.startDate())
                && Objects.equals(semester.getEndDate(), period.endDate())
                && Objects.equals(semester.getWeekOneStartDate(), period.weekOneStartDate());
    }
}
