package dev.knalis.schedule.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoomRequest(
        
        @NotBlank
        @Size(max = 50)
        String code,
        
        @NotBlank
        @Size(max = 100)
        String building,
        
        @NotNull
        Integer floor,
        
        @NotNull
        @Min(1)
        Integer capacity,
        
        boolean active
) {
}
