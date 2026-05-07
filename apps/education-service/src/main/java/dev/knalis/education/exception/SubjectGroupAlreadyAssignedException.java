package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubjectGroupAlreadyAssignedException extends AppException {

    public SubjectGroupAlreadyAssignedException(UUID subjectId, UUID groupId) {
        super(
                HttpStatus.CONFLICT,
                "SUBJECT_GROUP_ALREADY_ASSIGNED",
                "Group is already assigned to this subject",
                Map.of(
                        "subjectId", subjectId,
                        "groupId", groupId
                )
        );
    }
}
