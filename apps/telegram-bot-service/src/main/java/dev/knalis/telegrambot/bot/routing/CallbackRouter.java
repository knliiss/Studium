package dev.knalis.telegrambot.bot.routing;

import dev.knalis.telegrambot.bot.annotation.BotCallback;
import dev.knalis.telegrambot.bot.annotation.BotCallbackController;
import dev.knalis.telegrambot.bot.annotation.CallbackPathVariable;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackRouter {

    private final ApplicationContext applicationContext;
    private final List<CallbackRoute> routes = new ArrayList<>();

    @PostConstruct
    public void initialize() {
        Map<String, Object> controllerBeans = applicationContext.getBeansWithAnnotation(BotCallbackController.class);
        for (Object bean : controllerBeans.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getDeclaredMethods()) {
                BotCallback annotation = method.getAnnotation(BotCallback.class);
                if (annotation == null) {
                    continue;
                }
                method.setAccessible(true);
                routes.add(new CallbackRoute(bean, method, new CallbackRoutePattern(annotation.value())));
            }
        }
        log.info("Registered {} Telegram bot callback routes", routes.size());
    }

    public BotScreen route(BotRequestContext context, String callbackData) {
        for (CallbackRoute route : routes) {
            Map<String, String> pathVariables = route.pattern().match(callbackData);
            if (pathVariables == null) {
                continue;
            }
            return route.invoke(context, callbackData, pathVariables);
        }
        return null;
    }

    private record CallbackRoute(
            Object bean,
            Method method,
            CallbackRoutePattern pattern
    ) {
        private BotScreen invoke(BotRequestContext context, String callbackData, Map<String, String> pathVariables) {
            Object[] args = new Object[method.getParameterCount()];
            Parameter[] parameters = method.getParameters();
            for (int index = 0; index < parameters.length; index++) {
                Parameter parameter = parameters[index];
                if (parameter.getType().equals(BotRequestContext.class)) {
                    args[index] = context;
                    continue;
                }
                if (parameter.getType().equals(Update.class)) {
                    args[index] = context.update();
                    continue;
                }
                if (parameter.getType().equals(String.class) && !parameter.isAnnotationPresent(CallbackPathVariable.class)) {
                    args[index] = callbackData;
                    continue;
                }
                CallbackPathVariable callbackPathVariable = parameter.getAnnotation(CallbackPathVariable.class);
                if (callbackPathVariable == null) {
                    throw new IllegalStateException("Unsupported callback handler parameter: " + parameter.getType().getName());
                }
                String rawValue = pathVariables.get(callbackPathVariable.value());
                args[index] = convertValue(rawValue, parameter.getType());
            }
            try {
                return (BotScreen) method.invoke(bean, args);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to invoke callback handler: " + method.getName(), exception);
            }
        }

        private Object convertValue(String rawValue, Class<?> targetType) {
            if (rawValue == null) {
                return null;
            }
            if (targetType.equals(String.class)) {
                return rawValue;
            }
            if (targetType.equals(LocalDate.class)) {
                return LocalDate.parse(rawValue);
            }
            if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
                return Integer.parseInt(rawValue);
            }
            if (targetType.equals(Long.class) || targetType.equals(long.class)) {
                return Long.parseLong(rawValue);
            }
            if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) {
                return Boolean.parseBoolean(rawValue);
            }
            if (targetType.equals(UUID.class)) {
                return UUID.fromString(rawValue);
            }
            throw new IllegalStateException("Unsupported callback path variable type: " + targetType.getName());
        }
    }
}
