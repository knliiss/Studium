package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class FileContentTypeNotAllowedException extends AppException {
    
    public FileContentTypeNotAllowedException(String contentType, List<String> allowedContentTypes) {
        super(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "FILE_CONTENT_TYPE_NOT_ALLOWED",
                "File content type is not allowed",
                Map.of(
                        "contentType", contentType,
                        "allowedContentTypes", allowedContentTypes
                )
        );
    }
}
