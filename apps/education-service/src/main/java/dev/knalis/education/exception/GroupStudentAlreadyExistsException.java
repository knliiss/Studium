package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class GroupStudentAlreadyExistsException extends AppException {

    public GroupStudentAlreadyExistsException(UUID groupId, UUID userId) {
        super(
                HttpStatus.CONFLICT,
                "GROUP_STUDENT_ALREADY_EXISTS",
                "Student is already assigned to this group",
                Map.of(
                        "groupId", groupId,
                        "userId", userId
                )
        );
    }
}
