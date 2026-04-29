package dev.knalis.auth.service.mfa;

import dev.knalis.auth.exception.MfaMethodUnavailableException;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MfaMethodRegistry {
    
    private final Map<MfaMethodType, MfaMethodHandler> handlersByType;
    
    public MfaMethodRegistry(List<MfaMethodHandler> handlers) {
        this.handlersByType = new EnumMap<>(MfaMethodType.class);
        for (MfaMethodHandler handler : handlers) {
            handlersByType.put(handler.getMethodType(), handler);
        }
    }
    
    public MfaMethodHandler getRequired(MfaMethodType methodType) {
        MfaMethodHandler handler = handlersByType.get(methodType);
        if (handler == null) {
            throw new MfaMethodUnavailableException(methodType);
        }
        return handler;
    }
    
    public List<MfaMethodHandler> supportedHandlers() {
        return handlersByType.values().stream()
                .sorted(Comparator.comparingInt(handler -> handler.getMethodType().ordinal()))
                .toList();
    }
}
