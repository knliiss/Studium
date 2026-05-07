package dev.knalis.education.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        
        @NotBlank
        @Size(max = 100)
        String name,
        
        @Size(max = 1000)
        String description
) {
}
