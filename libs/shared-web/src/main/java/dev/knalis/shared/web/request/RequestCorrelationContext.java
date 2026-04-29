package dev.knalis.shared.web.request;

public final class RequestCorrelationContext {
    
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    
    private static final ThreadLocal<String> CURRENT_REQUEST_ID = new ThreadLocal<>();
    
    private RequestCorrelationContext() {
    }
    
    public static String getCurrentRequestId() {
        return CURRENT_REQUEST_ID.get();
    }
    
    public static void setCurrentRequestId(String requestId) {
        CURRENT_REQUEST_ID.set(requestId);
    }
    
    public static void clear() {
        CURRENT_REQUEST_ID.remove();
    }
}
