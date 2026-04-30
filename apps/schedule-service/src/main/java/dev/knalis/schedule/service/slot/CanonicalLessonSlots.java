package dev.knalis.schedule.service.slot;

import dev.knalis.schedule.entity.LessonSlot;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

public final class CanonicalLessonSlots {

    public static final int FIRST_NUMBER = 1;
    public static final int LAST_NUMBER = 8;
    public static final int DURATION_MINUTES = 80;
    public static final int BREAK_MINUTES = 15;

    private static final LocalTime FIRST_START_TIME = LocalTime.of(8, 30);

    private CanonicalLessonSlots() {
    }

    public static List<Integer> numbers() {
        return IntStream.rangeClosed(FIRST_NUMBER, LAST_NUMBER)
                .boxed()
                .toList();
    }

    public static boolean isCanonicalNumber(Integer number) {
        return number != null && number >= FIRST_NUMBER && number <= LAST_NUMBER;
    }

    public static LocalTime startTime(Integer number) {
        return FIRST_START_TIME.plusMinutes(offsetMinutes(number));
    }

    public static LocalTime endTime(Integer number) {
        return startTime(number).plusMinutes(DURATION_MINUTES);
    }

    public static boolean isCanonicalTime(Integer number, LocalTime startTime, LocalTime endTime) {
        return isCanonicalNumber(number)
                && startTime(number).equals(startTime)
                && endTime(number).equals(endTime);
    }

    public static boolean isCanonicalActiveSlot(LessonSlot lessonSlot) {
        return lessonSlot != null
                && lessonSlot.isActive()
                && isCanonicalTime(lessonSlot.getNumber(), lessonSlot.getStartTime(), lessonSlot.getEndTime());
    }

    private static long offsetMinutes(Integer number) {
        return (long) (number - FIRST_NUMBER) * (DURATION_MINUTES + BREAK_MINUTES);
    }
}
