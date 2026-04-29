package dev.knalis.auth.dto.response;

public record AcceptedActionResponse(
        String status,
        String message
) {

    public static AcceptedActionResponse accepted(String message) {
        return new AcceptedActionResponse("ACCEPTED", message);
    }
}
