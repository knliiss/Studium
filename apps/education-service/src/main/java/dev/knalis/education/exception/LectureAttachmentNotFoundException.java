package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureAttachmentNotFoundException extends AppException {

    public LectureAttachmentNotFoundException(UUID attachmentId) {
        super(
                HttpStatus.NOT_FOUND,
                "LECTURE_ATTACHMENT_NOT_FOUND",
                "Lecture attachment was not found",
                Map.of("attachmentId", attachmentId)
        );
    }
}

