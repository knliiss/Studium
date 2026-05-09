package dev.knalis.schedule.factory.semester;

import dev.knalis.schedule.entity.AcademicSemester;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AcademicSemesterFactory {

    public AcademicSemester newAcademicSemester(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate weekOneStartDate,
            Integer semesterNumber,
            boolean active,
            boolean published
    ) {
        AcademicSemester semester = new AcademicSemester();
        semester.setName(name.trim());
        semester.setStartDate(startDate);
        semester.setEndDate(endDate);
        semester.setWeekOneStartDate(weekOneStartDate);
        semester.setSemesterNumber(semesterNumber);
        semester.setActive(active);
        semester.setPublished(active || published);
        return semester;
    }
}
