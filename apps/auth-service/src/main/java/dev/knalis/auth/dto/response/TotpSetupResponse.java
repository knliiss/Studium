package dev.knalis.auth.dto.response;

public record TotpSetupResponse(
        String secret,
        String otpauthUri
) {
}
