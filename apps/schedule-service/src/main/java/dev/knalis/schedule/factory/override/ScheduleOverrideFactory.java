package dev.knalis.schedule.factory.override;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.ScheduleOverride;
import dev.knalis.schedule.entity.Subgroup;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class ScheduleOverrideFactory {

    public ScheduleOverride newScheduleOverride(
            UUID semesterId,
            UUID templateId,
            OverrideType overrideType,
            LocalDate date,
            UUID groupId,
            UUID subjectId,
            UUID teacherId,
            UUID slotId,
            Subgroup subgroup,
            LessonType lessonType,
            LessonFormat lessonFormat,
            UUID roomId,
            String onlineMeetingUrl,
            String notes,
            UUID createdByUserId
    ) {
        ScheduleOverride scheduleOverride = new ScheduleOverride();
        scheduleOverride.setSemesterId(semesterId);
        scheduleOverride.setTemplateId(templateId);
        scheduleOverride.setOverrideType(overrideType);
        scheduleOverride.setDate(date);
        scheduleOverride.setGroupId(groupId);
        scheduleOverride.setSubjectId(subjectId);
        scheduleOverride.setTeacherId(teacherId);
        scheduleOverride.setSlotId(slotId);
        scheduleOverride.setSubgroup(subgroup == null ? Subgroup.ALL : subgroup);
        scheduleOverride.setLessonType(lessonType);
        scheduleOverride.setLessonFormat(lessonFormat);
        scheduleOverride.setRoomId(roomId);
        scheduleOverride.setOnlineMeetingUrl(normalize(onlineMeetingUrl));
        scheduleOverride.setNotes(normalize(notes));
        scheduleOverride.setCreatedByUserId(createdByUserId);
        return scheduleOverride;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
