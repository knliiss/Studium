package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class GroupStudentNotFoundException extends AppException {

    public GroupStudentNotFoundException(UUID groupId, UUID userId) {
        super(
                HttpStatus.NOT_FOUND,
                "GROUP_STUDENT_NOT_FOUND",
                "Student membership was not found for this group",
                Map.of(
                        "groupId", groupId,
                        "userId", userId
                )
        );
    }
}
