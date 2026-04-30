package dev.knalis.schedule.service.slot;

import dev.knalis.schedule.dto.request.CreateLessonSlotRequest;
import dev.knalis.schedule.dto.request.UpdateLessonSlotRequest;
import dev.knalis.schedule.dto.response.LessonSlotResponse;
import dev.knalis.schedule.entity.LessonSlot;
import dev.knalis.schedule.exception.LessonSlotNotFoundException;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.exception.ScheduleValidationException;
import dev.knalis.schedule.factory.slot.LessonSlotFactory;
import dev.knalis.schedule.mapper.LessonSlotMapper;
import dev.knalis.schedule.repository.LessonSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonSlotService {

    private final LessonSlotRepository lessonSlotRepository;
    private final LessonSlotFactory lessonSlotFactory;
    private final LessonSlotMapper lessonSlotMapper;

    @Transactional
    public LessonSlotResponse createLessonSlot(CreateLessonSlotRequest request) {
        validateTimeRange(request.startTime(), request.endTime());
        validateCanonicalSlot(request.number(), request.startTime(), request.endTime(), request.active());
        assertNumberAvailable(request.number(), null);

        LessonSlot lessonSlot = lessonSlotFactory.newLessonSlot(
                request.number(),
                request.startTime(),
                request.endTime(),
                request.active()
        );

        return lessonSlotMapper.toResponse(lessonSlotRepository.save(lessonSlot));
    }

    @Transactional(readOnly = true)
    public List<LessonSlotResponse> getLessonSlots() {
        return lessonSlotRepository.findAllByOrderByNumberAsc().stream()
                .filter(CanonicalLessonSlots::isCanonicalActiveSlot)
                .map(lessonSlotMapper::toResponse)
                .toList();
    }

    @Transactional
    public LessonSlotResponse updateLessonSlot(UUID slotId, UpdateLessonSlotRequest request) {
        LessonSlot lessonSlot = lessonSlotRepository.findById(slotId)
                .orElseThrow(() -> new LessonSlotNotFoundException(slotId));

        validateTimeRange(request.startTime(), request.endTime());
        validateCanonicalSlot(request.number(), request.startTime(), request.endTime(), request.active());
        assertNumberAvailable(request.number(), slotId);

        lessonSlot.setNumber(request.number());
        lessonSlot.setStartTime(request.startTime());
        lessonSlot.setEndTime(request.endTime());
        lessonSlot.setActive(request.active());

        return lessonSlotMapper.toResponse(lessonSlotRepository.save(lessonSlot));
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ScheduleValidationException(
                    "INVALID_LESSON_SLOT_TIME_RANGE",
                    "Lesson slot start time must be before end time",
                    Map.of(
                            "startTime", startTime.toString(),
                            "endTime", endTime.toString()
                    )
            );
        }
    }

    private void validateCanonicalSlot(Integer number, LocalTime startTime, LocalTime endTime, boolean active) {
        if (!CanonicalLessonSlots.isCanonicalNumber(number)) {
            throw new ScheduleValidationException(
                    "INVALID_LESSON_SLOT_NUMBER",
                    "Lesson slot number must be one of the canonical pairs 1..8",
                    Map.of("number", number)
            );
        }
        if (!CanonicalLessonSlots.isCanonicalTime(number, startTime, endTime)) {
            throw new ScheduleValidationException(
                    "INVALID_CANONICAL_LESSON_SLOT_TIME",
                    "Lesson slot time must match the canonical pair timetable",
                    Map.of(
                            "number", number,
                            "expectedStartTime", CanonicalLessonSlots.startTime(number).toString(),
                            "expectedEndTime", CanonicalLessonSlots.endTime(number).toString(),
                            "startTime", startTime.toString(),
                            "endTime", endTime.toString()
                    )
            );
        }
        if (!active) {
            throw new ScheduleValidationException(
                    "CANONICAL_LESSON_SLOT_MUST_BE_ACTIVE",
                    "Canonical lesson slots must remain active",
                    Map.of("number", number)
            );
        }
    }

    private void assertNumberAvailable(Integer number, UUID slotId) {
        boolean exists = slotId == null
                ? lessonSlotRepository.existsByNumber(number)
                : lessonSlotRepository.existsByNumberAndIdNot(number, slotId);

        if (exists) {
            throw new ScheduleConflictException(
                    "LESSON_SLOT_NUMBER_ALREADY_EXISTS",
                    "Lesson slot number already exists",
                    Map.of("number", number)
            );
        }
    }
}
