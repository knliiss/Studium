package dev.knalis.telegrambot.bot.routing;

import dev.knalis.telegrambot.bot.annotation.BotCommand;
import dev.knalis.telegrambot.bot.annotation.BotCommandHandler;
import dev.knalis.telegrambot.bot.model.BotLocale;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import dev.knalis.telegrambot.bot.model.BotUserContext;
import dev.knalis.telegrambot.bot.model.BotUserRole;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandRouterTest {

    @Test
    void shouldResolveMenuRoute() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CommandRouter.class, TestCommandHandler.class, NonCommandControllerHandler.class);
            context.refresh();

            CommandRouter router = context.getBean(CommandRouter.class);
            BotScreen screen = router.route(testRequestContext(), "/menu");

            assertNotNull(screen);
            assertEquals("menu-ok", screen.textHtml());
        }
    }

    @Test
    void shouldResolveStartRoute() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CommandRouter.class, TestCommandHandler.class, NonCommandControllerHandler.class);
            context.refresh();

            CommandRouter router = context.getBean(CommandRouter.class);
            BotScreen screen = router.route(testRequestContext(), "/start token123");

            assertNotNull(screen);
            assertEquals("start-ok:/start token123", screen.textHtml());
        }
    }

    @Test
    void shouldReturnNullForUnknownCommand() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CommandRouter.class, TestCommandHandler.class, NonCommandControllerHandler.class);
            context.refresh();

            CommandRouter router = context.getBean(CommandRouter.class);
            BotScreen screen = router.route(testRequestContext(), "/unknown");

            assertNull(screen);
        }
    }

    @Test
    void shouldIgnoreBeansWithoutBotCommandHandlerAnnotation() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CommandRouter.class, TestCommandHandler.class, NonCommandControllerHandler.class);
            context.refresh();

            CommandRouter router = context.getBean(CommandRouter.class);
            BotScreen screen = router.route(testRequestContext(), "/hidden");

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

    @BotCommandHandler
    static class TestCommandHandler {

        @BotCommand("/menu")
        public BotScreen menu() {
            return BotScreen.builder().textHtml("menu-ok").build();
        }

        @BotCommand("/start")
        public BotScreen start(BotRequestContext context, String commandText) {
            return BotScreen.builder().textHtml("start-ok:" + commandText).build();
        }
    }

    static class NonCommandControllerHandler {

        @BotCommand("/hidden")
        public BotScreen hidden() {
            return BotScreen.builder().textHtml("hidden").build();
        }
    }
}
