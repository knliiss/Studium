package dev.knalis.assignment.service.common;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SubmissionFileTypePolicy {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed"
    );

    private static final List<String> ALLOWED_CONTENT_TYPES_VIEW = ALLOWED_CONTENT_TYPES.stream()
            .sorted()
            .toList();

    private SubmissionFileTypePolicy() {
    }

    public static List<String> allowedContentTypes() {
        return ALLOWED_CONTENT_TYPES_VIEW;
    }

    public static Set<String> allowedContentTypeSet() {
        return ALLOWED_CONTENT_TYPES;
    }

    public static boolean isAllowed(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        return ALLOWED_CONTENT_TYPES.contains(contentType.trim().toLowerCase(Locale.ROOT));
    }
}
