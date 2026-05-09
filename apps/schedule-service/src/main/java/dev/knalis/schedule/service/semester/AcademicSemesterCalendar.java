package dev.knalis.schedule.service.semester;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AcademicSemesterCalendar {

    public AcademicSemesterPeriod current(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= 9) {
            return firstSemester(date.getYear());
        }
        if (month == 1) {
            return firstSemester(date.getYear() - 1);
        }

        int academicYearStart = date.getYear() - 1;
        int academicYearEnd = date.getYear();
        return new AcademicSemesterPeriod(
                "Semester 2 " + academicYearStart + "/" + academicYearEnd,
                LocalDate.of(academicYearEnd, 2, 1),
                LocalDate.of(academicYearEnd, 8, 31),
                LocalDate.of(academicYearEnd, 2, 1),
                2
        );
    }

    public AcademicSemesterPeriod future(LocalDate date) {
        AcademicSemesterPeriod current = current(date);
        return current(current.endDate().plusDays(1));
    }

    private AcademicSemesterPeriod firstSemester(int academicYearStart) {
        int academicYearEnd = academicYearStart + 1;
        return new AcademicSemesterPeriod(
                "Semester 1 " + academicYearStart + "/" + academicYearEnd,
                LocalDate.of(academicYearStart, 9, 1),
                LocalDate.of(academicYearEnd, 1, 31),
                LocalDate.of(academicYearStart, 9, 1),
                1
        );
    }
}
