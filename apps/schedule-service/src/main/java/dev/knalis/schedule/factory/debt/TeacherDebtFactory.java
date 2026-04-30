package dev.knalis.schedule.factory.debt;

import dev.knalis.schedule.entity.ScheduleOverride;
import dev.knalis.schedule.entity.TeacherDebt;
import dev.knalis.schedule.entity.TeacherDebtStatus;
import org.springframework.stereotype.Component;

@Component
public class TeacherDebtFactory {

    public TeacherDebt newOpenDebt(ScheduleOverride scheduleOverride) {
        TeacherDebt teacherDebt = new TeacherDebt();
        teacherDebt.setScheduleOverrideId(scheduleOverride.getId());
        teacherDebt.setTeacherId(scheduleOverride.getTeacherId());
        teacherDebt.setGroupId(scheduleOverride.getGroupId());
        teacherDebt.setSubjectId(scheduleOverride.getSubjectId());
        teacherDebt.setDate(scheduleOverride.getDate());
        teacherDebt.setSlotId(scheduleOverride.getSlotId());
        teacherDebt.setLessonType(scheduleOverride.getLessonType());
        teacherDebt.setSubgroup(scheduleOverride.getSubgroup());
        teacherDebt.setReason(scheduleOverride.getNotes());
        teacherDebt.setStatus(TeacherDebtStatus.OPEN);
        return teacherDebt;
    }
}
