package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class GroupNotFoundException extends AppException {
    
    public GroupNotFoundException(UUID groupId) {
        super(
                HttpStatus.NOT_FOUND,
                "GROUP_NOT_FOUND",
                "Group was not found",
                Map.of("groupId", groupId)
        );
    }
}
