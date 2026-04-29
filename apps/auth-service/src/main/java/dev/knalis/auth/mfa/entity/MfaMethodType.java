package dev.knalis.auth.mfa.entity;

public enum MfaMethodType {
    TOTP,
    TELEGRAM_PUSH;
    
    public String amrValue() {
        return name().toLowerCase();
    }
}
