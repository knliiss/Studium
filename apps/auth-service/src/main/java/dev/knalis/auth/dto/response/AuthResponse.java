package dev.knalis.auth.dto.response;

public record AuthResponse(
        AuthStatus status,
        String accessToken,
        String refreshToken,
        UserAuthResponse user,
        MfaChallengeResponse mfaChallenge
) {
    
    public static AuthResponse authenticated(String accessToken, String refreshToken, UserAuthResponse user) {
        return new AuthResponse(AuthStatus.AUTHENTICATED, accessToken, refreshToken, user, null);
    }
    
    public static AuthResponse mfaRequired(MfaChallengeResponse challenge) {
        return new AuthResponse(AuthStatus.MFA_REQUIRED, null, null, null, challenge);
    }
}
