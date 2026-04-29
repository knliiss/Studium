package dev.knalis.schedule.factory.slot;

import dev.knalis.schedule.entity.LessonSlot;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class LessonSlotFactory {
    
    public LessonSlot newLessonSlot(Integer number, LocalTime startTime, LocalTime endTime, boolean active) {
        LessonSlot lessonSlot = new LessonSlot();
        lessonSlot.setNumber(number);
        lessonSlot.setStartTime(startTime);
        lessonSlot.setEndTime(endTime);
        lessonSlot.setActive(active);
        return lessonSlot;
    }
}
