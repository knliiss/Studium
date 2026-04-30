package dev.knalis.schedule.factory.template;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.ScheduleTemplateStatus;
import dev.knalis.schedule.entity.Subgroup;
import dev.knalis.schedule.entity.WeekType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.UUID;

@Component
public class ScheduleTemplateFactory {

    public ScheduleTemplate newScheduleTemplate(
            UUID semesterId,
            UUID groupId,
            UUID subjectId,
            UUID teacherId,
            DayOfWeek dayOfWeek,
            UUID slotId,
            WeekType weekType,
            Subgroup subgroup,
            LessonType lessonType,
            LessonFormat lessonFormat,
            UUID roomId,
            String onlineMeetingUrl,
            String notes,
            boolean active
    ) {
        ScheduleTemplate scheduleTemplate = new ScheduleTemplate();
        scheduleTemplate.setSemesterId(semesterId);
        scheduleTemplate.setGroupId(groupId);
        scheduleTemplate.setSubjectId(subjectId);
        scheduleTemplate.setTeacherId(teacherId);
        scheduleTemplate.setDayOfWeek(dayOfWeek);
        scheduleTemplate.setSlotId(slotId);
        scheduleTemplate.setWeekType(weekType);
        scheduleTemplate.setSubgroup(subgroup == null ? Subgroup.ALL : subgroup);
        scheduleTemplate.setLessonType(lessonType);
        scheduleTemplate.setLessonFormat(lessonFormat);
        scheduleTemplate.setRoomId(roomId);
        scheduleTemplate.setOnlineMeetingUrl(normalize(onlineMeetingUrl));
        scheduleTemplate.setNotes(normalize(notes));
        scheduleTemplate.setStatus(active ? ScheduleTemplateStatus.ACTIVE : ScheduleTemplateStatus.DRAFT);
        scheduleTemplate.setActive(active);
        return scheduleTemplate;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
