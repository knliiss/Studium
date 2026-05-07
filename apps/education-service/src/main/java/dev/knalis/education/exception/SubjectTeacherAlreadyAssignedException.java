package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubjectTeacherAlreadyAssignedException extends AppException {

    public SubjectTeacherAlreadyAssignedException(UUID subjectId, UUID teacherId) {
        super(
                HttpStatus.CONFLICT,
                "SUBJECT_TEACHER_ALREADY_ASSIGNED",
                "Teacher is already assigned to this subject",
                Map.of(
                        "subjectId", subjectId,
                        "teacherId", teacherId
                )
        );
    }
}
