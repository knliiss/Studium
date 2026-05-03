package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public record UpdateAssignmentRequest(
        
        @NotBlank
        @Size(max = 200)
        String title,
        
        @Size(max = 2000)
        String description,
        
        @NotNull
        @Future
        Instant deadline,
        
        Boolean allowLateSubmissions,
        
        @Min(1)
        Integer maxSubmissions,
        
        Boolean allowResubmit,
        
        Set<@Size(max = 100) String> acceptedFileTypes,

        @Min(1)
        Integer maxFileSizeMb,

        @Min(0)
        Integer maxPoints
) {
}
