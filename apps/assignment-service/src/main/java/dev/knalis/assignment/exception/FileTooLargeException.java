package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class FileTooLargeException extends AppException {

    public FileTooLargeException(
            UUID assignmentId,
            UUID fileId,
            long sizeBytes,
            int maxFileSizeMb
    ) {
        super(
                HttpStatus.BAD_REQUEST,
                "FILE_TOO_LARGE",
                "Submission file exceeds the assignment file size limit",
                Map.of(
                        "assignmentId", assignmentId,
                        "fileId", fileId,
                        "sizeBytes", sizeBytes,
                        "maxFileSizeMb", maxFileSizeMb
                )
        );
    }
}
