package dev.knalis.profile.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
        
        @Size(min = 1, max = 100)
        String displayName,
        
        @Size(max = 20)
        String locale,
        
        @Size(max = 50)
        String timezone
) {
}