package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class GroupCurriculumOverrideNotFoundException extends AppException {

    public GroupCurriculumOverrideNotFoundException(UUID overrideId) {
        super(
                HttpStatus.NOT_FOUND,
                "GROUP_CURRICULUM_OVERRIDE_NOT_FOUND",
                "Group curriculum override was not found",
                Map.of("overrideId", overrideId)
        );
    }
}
