package dev.knalis.auth.dto.response;

import java.util.List;

public record MfaMethodsResponse(
        List<MfaMethodResponse> methods
) {
}
