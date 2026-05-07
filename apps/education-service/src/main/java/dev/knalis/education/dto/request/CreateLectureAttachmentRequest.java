package dev.knalis.education.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLectureAttachmentRequest(

        @NotNull
        UUID fileId,

        @Size(max = 255)
        String displayName
) {
}

