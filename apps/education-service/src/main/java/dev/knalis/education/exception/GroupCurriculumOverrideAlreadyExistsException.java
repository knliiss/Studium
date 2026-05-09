package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class GroupCurriculumOverrideAlreadyExistsException extends AppException {

    public GroupCurriculumOverrideAlreadyExistsException(UUID groupId, UUID subjectId) {
        super(
                HttpStatus.CONFLICT,
                "GROUP_CURRICULUM_OVERRIDE_ALREADY_EXISTS",
                "Group curriculum override already exists",
                Map.of(
                        "groupId", groupId,
                        "subjectId", subjectId
                )
        );
    }
}
