package dev.knalis.telegrambot.bot.routing;

import java.util.HashMap;
import java.util.Map;

public class CallbackRoutePattern {

    private final String[] segments;

    public CallbackRoutePattern(String pattern) {
        this.segments = pattern.split(":");
    }

    public Map<String, String> match(String value) {
        String[] valueSegments = value.split(":");
        if (segments.length != valueSegments.length) {
            return null;
        }

        Map<String, String> variables = new HashMap<>();
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            String actual = valueSegments[index];
            if (isVariable(segment)) {
                variables.put(variableName(segment), actual);
                continue;
            }
            if (!segment.equals(actual)) {
                return null;
            }
        }
        return variables;
    }

    private boolean isVariable(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private String variableName(String segment) {
        return segment.substring(1, segment.length() - 1);
    }
}
