package dev.knalis.telegrambot.config;

import dev.knalis.telegrambot.service.TelegramBotRuntimeInspector;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class TelegramBotPollingCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return TelegramBotRuntimeInspector.inspect(context.getEnvironment()).pollingEnabled();
    }
}
