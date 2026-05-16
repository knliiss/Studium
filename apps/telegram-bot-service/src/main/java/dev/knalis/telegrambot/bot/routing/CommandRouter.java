package dev.knalis.telegrambot.bot.routing;

import dev.knalis.telegrambot.bot.annotation.BotCommand;
import dev.knalis.telegrambot.bot.annotation.BotCommandHandler;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandRouter implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final Map<String, CommandRoute> routes = new HashMap<>();

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> controllerBeans = applicationContext.getBeansWithAnnotation(BotCommandHandler.class);
        for (Object bean : controllerBeans.values()) {
            if (bean == this || bean instanceof CommandRouter) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getDeclaredMethods()) {
                BotCommand annotation = method.getAnnotation(BotCommand.class);
                if (annotation == null) {
                    continue;
                }
                method.setAccessible(true);
                String normalizedCommand = normalizeCommand(annotation.value());
                routes.put(normalizedCommand, new CommandRoute(bean, method));
            }
        }
        log.info("Registered {} Telegram bot command routes", routes.size());
    }

    public BotScreen route(BotRequestContext requestContext, String commandText) {
        String[] parts = commandText.trim().split("\\s+", 2);
        String normalizedCommand = normalizeCommand(parts[0]);
        CommandRoute route = routes.get(normalizedCommand);
        if (route == null) {
            return null;
        }
        return route.invoke(requestContext, commandText);
    }

    private String normalizeCommand(String command) {
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private record CommandRoute(
            Object bean,
            Method method
    ) {
        private BotScreen invoke(BotRequestContext context, String commandText) {
            Object[] args = new Object[method.getParameterCount()];
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int index = 0; index < parameterTypes.length; index++) {
                Class<?> parameterType = parameterTypes[index];
                if (parameterType.equals(BotRequestContext.class)) {
                    args[index] = context;
                } else if (parameterType.equals(Update.class)) {
                    args[index] = context.update();
                } else if (parameterType.equals(String.class)) {
                    args[index] = commandText;
                } else {
                    throw new IllegalStateException("Unsupported command handler parameter type: " + parameterType.getName());
                }
            }
            try {
                return (BotScreen) method.invoke(bean, args);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to invoke command handler: " + method.getName(), exception);
            }
        }
    }
}
