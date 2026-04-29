package dev.knalis.auth.service.mfa;

import dev.knalis.auth.entity.User;
import dev.knalis.auth.mfa.entity.MfaChallenge;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import dev.knalis.auth.mfa.entity.UserMfaMethod;

public interface MfaMethodHandler {
    
    MfaMethodType getMethodType();
    
    default boolean requiresDispatch() {
        return false;
    }
    
    default String deliveryHint(User user, UserMfaMethod method) {
        return null;
    }
    
    default void dispatch(MfaChallenge challenge, User user, UserMfaMethod method) {
    }
    
    void verify(MfaChallenge challenge, User user, UserMfaMethod method, String code);
}
