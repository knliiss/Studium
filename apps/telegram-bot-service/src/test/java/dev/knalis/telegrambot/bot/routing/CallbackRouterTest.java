package dev.knalis.telegrambot.bot.routing;

import dev.knalis.telegrambot.bot.annotation.BotCallback;
import dev.knalis.telegrambot.bot.annotation.BotCallbackController;
import dev.knalis.telegrambot.bot.annotation.CallbackPathVariable;
import dev.knalis.telegrambot.bot.model.BotLocale;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import dev.knalis.telegrambot.bot.model.BotUserContext;
import dev.knalis.telegrambot.bot.model.BotUserRole;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CallbackRouterTest {

    @Test
    void shouldMatchCallbackPathVariable() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CallbackRouter.class, TestCallbackController.class);
            context.refresh();

            CallbackRouter router = context.getBean(CallbackRouter.class);
            BotScreen screen = router.route(testRequestContext(), "schedule:day:2026-05-16");

            assertNotNull(screen);
            assertEquals("day:2026-05-16", screen.textHtml());
        }
    }

    @Test
    void shouldReturnNullForUnknownCallback() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CallbackRouter.class, TestCallbackController.class);
            context.refresh();

            CallbackRouter router = context.getBean(CallbackRouter.class);
            BotScreen screen = router.route(testRequestContext(), "schedule:week");

            assertNull(screen);
        }
    }

    private BotRequestContext testRequestContext() {
        BotUserContext userContext = new BotUserContext(
                100L,
                200L,
                "tester",
                "Test",
                "User",
                "uk",
                false,
                null,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                BotUserRole.UNKNOWN,
                BotLocale.UK
        );
        return new BotRequestContext(new Update(), userContext);
    }

    @BotCallbackController
    static class TestCallbackController {

        @BotCallback("schedule:day:{date}")
        public BotScreen scheduleDay(@CallbackPathVariable("date") LocalDate date) {
            return BotScreen.builder().textHtml("day:" + date).build();
        }
    }
}
