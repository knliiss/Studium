package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FileTypeNotAllowedException extends AppException {

    public FileTypeNotAllowedException(
            UUID assignmentId,
            UUID fileId,
            String contentType,
            Set<String> acceptedFileTypes
    ) {
        super(
                HttpStatus.BAD_REQUEST,
                "FILE_TYPE_NOT_ALLOWED",
                "Submission file type is not allowed for this assignment",
                Map.of(
                        "assignmentId", assignmentId,
                        "fileId", fileId,
                        "contentType", contentType,
                        "acceptedFileTypes", acceptedFileTypes
                )
        );
    }
}
