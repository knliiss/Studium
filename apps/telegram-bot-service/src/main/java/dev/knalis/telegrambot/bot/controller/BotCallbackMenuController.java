package dev.knalis.telegrambot.bot.controller;

import dev.knalis.telegrambot.bot.annotation.BotCallback;
import dev.knalis.telegrambot.bot.annotation.BotCallbackController;
import dev.knalis.telegrambot.bot.annotation.CallbackPathVariable;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import dev.knalis.telegrambot.bot.service.StudiumBotUxService;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@BotCallbackController
@RequiredArgsConstructor
public class BotCallbackMenuController {

    private final StudiumBotUxService studiumBotUxService;

    @BotCallback("menu:root")
    public BotScreen mainMenu(BotRequestContext context) {
        return studiumBotUxService.mainMenu(context, true);
    }

    @BotCallback("menu:schedule")
    public BotScreen schedule(BotRequestContext context) {
        return studiumBotUxService.scheduleDay(context, LocalDate.now(), true);
    }

    @BotCallback("schedule:day:{date}")
    public BotScreen scheduleDay(BotRequestContext context, @CallbackPathVariable("date") LocalDate date) {
        return studiumBotUxService.scheduleDay(context, date, true);
    }

    @BotCallback("schedule:week")
    public BotScreen scheduleWeek(BotRequestContext context) {
        return studiumBotUxService.scheduleWeekStub(context, true);
    }

    @BotCallback("menu:notifications")
    public BotScreen notifications(BotRequestContext context) {
        return studiumBotUxService.notifications(context, true);
    }

    @BotCallback("notifications:mark-all-read")
    public BotScreen markAllRead(BotRequestContext context) {
        return studiumBotUxService.markAllRead(context);
    }

    @BotCallback("menu:assignments")
    public BotScreen assignments(BotRequestContext context) {
        return studiumBotUxService.assignmentsStub(context, true);
    }

    @BotCallback("menu:tests")
    public BotScreen tests(BotRequestContext context) {
        return studiumBotUxService.testsStub(context, true);
    }

    @BotCallback("menu:grades")
    public BotScreen grades(BotRequestContext context) {
        return studiumBotUxService.gradesStub(context, true);
    }

    @BotCallback("menu:group")
    public BotScreen group(BotRequestContext context) {
        return studiumBotUxService.groupStub(context, true);
    }

    @BotCallback("menu:settings")
    public BotScreen settings(BotRequestContext context) {
        return studiumBotUxService.settings(context, true);
    }

    @BotCallback("settings:toggle:{category}")
    public BotScreen toggleSettings(BotRequestContext context, @CallbackPathVariable("category") String category) {
        return studiumBotUxService.togglePreference(context, category);
    }

    @BotCallback("command:status")
    public BotScreen status(BotRequestContext context) {
        return studiumBotUxService.status(context);
    }

    @BotCallback("command:help")
    public BotScreen help(BotRequestContext context) {
        return studiumBotUxService.help(context);
    }

    @BotCallback("info:open:{page}")
    public BotScreen openInfo(BotRequestContext context, @CallbackPathVariable("page") String page) {
        return studiumBotUxService.openInStudiumInfo(context, page);
    }

    @BotCallback("admin:users:{page}")
    public BotScreen adminUsers(BotRequestContext context, @CallbackPathVariable("page") int page) {
        return studiumBotUxService.adminUsers(context, page, true);
    }

    @BotCallback("admin:stats")
    public BotScreen adminStats(BotRequestContext context) {
        return studiumBotUxService.adminStats(context, true);
    }

    @BotCallback("admin:manage")
    public BotScreen adminManage(BotRequestContext context) {
        return studiumBotUxService.adminUsers(context, 0, true);
    }

    @BotCallback("admin:disable:{linkId}")
    public BotScreen adminDisable(BotRequestContext context, @CallbackPathVariable("linkId") UUID linkId) {
        return studiumBotUxService.adminDisableLink(context, linkId);
    }

    @BotCallback("admin:enable:{linkId}")
    public BotScreen adminEnable(BotRequestContext context, @CallbackPathVariable("linkId") UUID linkId) {
        return studiumBotUxService.adminEnableLink(context, linkId);
    }

    @BotCallback("admin:test:{linkId}")
    public BotScreen adminTest(BotRequestContext context, @CallbackPathVariable("linkId") UUID linkId) {
        return studiumBotUxService.adminSendTest(context, linkId);
    }
}
