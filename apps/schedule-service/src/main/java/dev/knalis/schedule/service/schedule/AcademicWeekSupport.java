package dev.knalis.schedule.service.schedule;

import dev.knalis.schedule.entity.AcademicSemester;
import dev.knalis.schedule.entity.ScheduleTemplate;
import dev.knalis.schedule.entity.WeekType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class AcademicWeekSupport {
    
    private AcademicWeekSupport() {
    }
    
    public static boolean isWithinSemester(AcademicSemester semester, LocalDate date) {
        return !date.isBefore(semester.getStartDate()) && !date.isAfter(semester.getEndDate());
    }
    
    public static boolean isOnOrAfterWeekOne(AcademicSemester semester, LocalDate date) {
        return !date.isBefore(semester.getWeekOneStartDate());
    }
    
    public static int weekNumber(AcademicSemester semester, LocalDate date) {
        long daysBetween = ChronoUnit.DAYS.between(semester.getWeekOneStartDate(), date);
        return Math.toIntExact(daysBetween / 7) + 1;
    }
    
    public static WeekType resolvedWeekType(AcademicSemester semester, LocalDate date) {
        return weekNumber(semester, date) % 2 == 0 ? WeekType.EVEN : WeekType.ODD;
    }
    
    public static boolean matchesWeekType(AcademicSemester semester, LocalDate date, WeekType templateWeekType) {
        if (!isWithinSemester(semester, date) || !isOnOrAfterWeekOne(semester, date)) {
            return false;
        }
        
        WeekType resolvedWeekType = resolvedWeekType(semester, date);
        return templateWeekType == WeekType.ALL || templateWeekType == resolvedWeekType;
    }
    
    public static boolean isTemplateOccurrence(AcademicSemester semester, ScheduleTemplate template, LocalDate date) {
        return template.isActive()
                && template.getDayOfWeek() == date.getDayOfWeek()
                && matchesWeekType(semester, date, template.getWeekType());
    }
}
