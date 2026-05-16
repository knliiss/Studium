package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotLocale;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import dev.knalis.telegrambot.bot.model.BotUserContext;
import dev.knalis.telegrambot.bot.model.BotUserRole;
import dev.knalis.telegrambot.client.NotificationTelegramInternalClient;
import dev.knalis.telegrambot.dto.InternalTelegramAdminUsersRequest;
import dev.knalis.telegrambot.dto.InternalTelegramAdminUsersResponse;
import dev.knalis.telegrambot.dto.InternalTelegramBotStatsResponse;
import dev.knalis.telegrambot.dto.InternalTelegramBotUserActionRequest;
import dev.knalis.telegrambot.dto.InternalTelegramConnectRequest;
import dev.knalis.telegrambot.dto.InternalTelegramConnectResponse;
import dev.knalis.telegrambot.dto.InternalTelegramContextRequest;
import dev.knalis.telegrambot.dto.InternalTelegramContextResponse;
import dev.knalis.telegrambot.dto.InternalTelegramMarkAllReadRequest;
import dev.knalis.telegrambot.dto.InternalTelegramNotificationItem;
import dev.knalis.telegrambot.dto.InternalTelegramNotificationsRequest;
import dev.knalis.telegrambot.dto.InternalTelegramNotificationsResponse;
import dev.knalis.telegrambot.dto.InternalTelegramScheduleDayRequest;
import dev.knalis.telegrambot.dto.InternalTelegramScheduleDayResponse;
import dev.knalis.telegrambot.dto.InternalTelegramStatusRequest;
import dev.knalis.telegrambot.dto.InternalTelegramStatusResponse;
import dev.knalis.telegrambot.dto.InternalTelegramTogglePreferenceRequest;
import dev.knalis.telegrambot.dto.InternalTelegramTogglePreferenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudiumBotUxService {

    private final NotificationTelegramInternalClient notificationTelegramInternalClient;
    private final BotLocalizationService botLocalizationService;
    private final BotHtmlEscaper botHtmlEscaper;
    private final BotImageRenderer botImageRenderer;
    private final BotLinkFactory botLinkFactory;

    public BotScreen start(BotRequestContext context, String commandText) {
        BotUserContext user = context.userContext();
        BotLocale locale = user.locale();

        String[] parts = commandText.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            StringBuilder textBuilder = new StringBuilder(header(locale, "menu.subtitle"))
                    .append(botLocalizationService.get(locale, "connect.noToken"));
            if (!botLinkFactory.hasPublicFrontendBaseUrl()) {
                textBuilder.append("\n\n")
                        .append(botLocalizationService.get(locale, "connect.noToken.local"));
            }
            return textScreen(textBuilder.toString(), connectHelpKeyboard(locale), false);
        }

        try {
            InternalTelegramConnectResponse response = notificationTelegramInternalClient.connect(new InternalTelegramConnectRequest(
                    parts[1].trim(),
                    user.telegramUserId(),
                    user.chatId(),
                    user.telegramUsername(),
                    user.firstName(),
                    user.lastName()
            ));
            String status = response == null ? "TOKEN_INVALID" : response.status();
            String resultText = connectResultText(locale, status);
            if ("CONNECTED".equals(status) || "ALREADY_CONNECTED".equals(status)) {
                return mainMenu(context, false, resultText);
            }
            return textScreen(
                    appendLinkNoticeIfLocal(header(locale, "menu.subtitle") + resultText, locale),
                    connectHelpKeyboard(locale),
                    false
            );
        } catch (Exception exception) {
            log.warn("Failed to execute /start connect");
            return errorScreen(locale, false);
        }
    }

    public BotScreen status(BotRequestContext context) {
        BotUserContext user = context.userContext();
        BotLocale locale = user.locale();
        try {
            InternalTelegramStatusResponse response = notificationTelegramInternalClient.status(
                    new InternalTelegramStatusRequest(user.telegramUserId(), user.chatId())
            );
            String message = response != null && response.connected()
                    ? botLocalizationService.get(locale, "status.connected")
                    : botLocalizationService.get(locale, "status.notConnected");
            return textScreen(appendLinkNoticeIfLocal(header(locale, "menu.subtitle") + message, locale), unknownKeyboard(locale), false);
        } catch (Exception exception) {
            log.warn("Failed to execute /status");
            return errorScreen(locale, false);
        }
    }

    public BotScreen help(BotRequestContext context) {
        BotLocale locale = context.userContext().locale();
        return textScreen(
                appendLinkNoticeIfLocal(header(locale, "menu.subtitle") + botLocalizationService.get(locale, "help.text"), locale),
                unknownKeyboard(locale),
                false
        );
    }

    public BotScreen mainMenu(BotRequestContext context, boolean preferEdit) {
        return mainMenu(context, preferEdit, null);
    }

    private BotScreen mainMenu(BotRequestContext context, boolean preferEdit, String notice) {
        BotUserContext user = context.userContext();
        BotLocale locale = user.locale();

        String text = header(locale, "menu.subtitle")
                + (notice == null || notice.isBlank() ? "" : notice + "\n\n")
                + "━━━━━━━━━━━━━━━━━━━━\n"
                + "👤 <b>" + userLabel(user) + "</b>\n"
                + "🧩 Роль: <b>" + botHtmlEscaper.escape(user.role().name()) + "</b>\n\n"
                + botLocalizationService.get(locale, "menu.hint");

        InlineKeyboardMarkup keyboard = switch (user.role()) {
            case OWNER, ADMIN -> adminKeyboard(locale);
            case STUDENT, TEACHER -> userKeyboard(locale);
            case UNKNOWN -> unknownKeyboard(locale);
        };

        return BotScreen.builder()
                .image(botImageRenderer.renderMainMenuBanner(user.telegramUserId(), locale))
                .captionHtml(appendLinkNoticeIfLocal(text, locale))
                .keyboard(keyboard)
                .preferEdit(preferEdit)
                .build();
    }

    public BotScreen scheduleDay(BotRequestContext context, LocalDate date, boolean preferEdit) {
        BotUserContext user = context.userContext();
        BotLocale locale = user.locale();
        InternalTelegramScheduleDayResponse scheduleDay;
        try {
            scheduleDay = notificationTelegramInternalClient.scheduleDay(
                    new InternalTelegramScheduleDayRequest(user.telegramUserId(), date)
            );
        } catch (Exception exception) {
            log.warn("Failed to load schedule day");
            scheduleDay = new InternalTelegramScheduleDayResponse(date, false, List.of());
        }

        int lessonsCount = scheduleDay.lessons() == null ? 0 : scheduleDay.lessons().size();
        String caption = "<b>Studium</b>\n"
                + "<i>" + botLocalizationService.get(locale, "schedule.title") + "</i>\n\n"
                + "📅 <b>" + botHtmlEscaper.escape(formatDate(locale, date)) + "</b>\n"
                + "Пари: " + lessonsCount + "\n\n"
                + botLocalizationService.get(locale, "menu.hint");

        return BotScreen.builder()
                .image(botImageRenderer.renderScheduleDay(user.telegramUserId(), locale, date, scheduleDay))
                .captionHtml(appendLinkNoticeIfLocal(caption, locale))
                .keyboard(scheduleKeyboard(locale, date))
                .preferEdit(preferEdit)
                .build();
    }

    public BotScreen scheduleWeekStub(BotRequestContext context, boolean preferEdit) {
        BotLocale locale = context.userContext().locale();
        String text = header(locale, "schedule.title")
                + "Тижневий режим буде доступний у наступному оновленні.\n\n"
                + "Поки що використовуйте перегляд за днями.";
        return textScreen(text, backMenuKeyboard(locale, "menu:schedule"), preferEdit);
    }

    public BotScreen notifications(BotRequestContext context, boolean preferEdit) {
        BotUserContext user = context.userContext();
        BotLocale locale = user.locale();
        try {
            InternalTelegramNotificationsResponse response = notificationTelegramInternalClient.unreadNotifications(
                    new InternalTelegramNotificationsRequest(user.telegramUserId(), 5)
            );
            List<InternalTelegramNotificationItem> items = response == null || response.unreadItems() == null
                    ? List.of()
                    : response.unreadItems();

            StringBuilder textBuilder = new StringBuilder();
            textBuilder.append(header(locale, "notifications.title"));
            if (items.isEmpty()) {
                textBuilder.append(botLocalizationService.get(locale, "notifications.empty"));
            } else {
                int index = 1;
                for (InternalTelegramNotificationItem item : items) {
                    textBuilder.append(index)
                            .append(". <b>")
                            .append(botHtmlEscaper.escape(item.title()))
                            .append("</b>\n")
                            .append(botHtmlEscaper.escape(trim(item.body(), 120)))
                            .append("\n\n");
                    index++;
                }
            }
            return textScreen(appendLinkNoticeIfLocal(textBuilder.toString(), locale), notificationsKeyboard(locale), preferEdit);
        } catch (Exception exception) {
            log.warn("Failed to load notifications");
            return errorScreen(locale, preferEdit);
        }
    }

    public BotScreen markAllRead(BotRequestContext context) {
        BotUserContext user = context.userContext();
        BotLocale locale = user.locale();
        try {
            notificationTelegramInternalClient.markAllNotificationsRead(
                    new InternalTelegramMarkAllReadRequest(user.telegramUserId())
            );
            return textScreen(
                    header(locale, "notifications.title") + botLocalizationService.get(locale, "notifications.marked"),
                    notificationsKeyboard(locale),
                    true
            );
        } catch (Exception exception) {
            log.warn("Failed to mark notifications as read");
            return errorScreen(locale, true);
        }
    }

    public BotScreen settings(BotRequestContext context, boolean preferEdit) {
        BotUserContext user = context.userContext();
        BotLocale locale = user.locale();
        InternalTelegramContextResponse freshContext = reloadContext(user);

        boolean assignments = freshContext == null || freshContext.notifyAssignments();
        boolean tests = freshContext == null || freshContext.notifyTests();
        boolean grades = freshContext == null || freshContext.notifyGrades();
        boolean schedule = freshContext == null || freshContext.notifySchedule();
        boolean materials = freshContext == null || freshContext.notifyMaterials();

        String text = header(locale, "settings.title")
                + "🔔 " + botLocalizationService.get(locale, "settings.enabled") + ": <b>"
                + (freshContext != null && !freshContext.telegramEnabled()
                ? botLocalizationService.get(locale, "settings.off")
                : botLocalizationService.get(locale, "settings.on"))
                + "</b>\n\n"
                + prefLine("assignments", assignments)
                + prefLine("tests", tests)
                + prefLine("grades", grades)
                + prefLine("schedule", schedule)
                + prefLine("materials", materials);
        return textScreen(text, settingsKeyboard(locale), preferEdit);
    }

    public BotScreen togglePreference(BotRequestContext context, String category) {
        BotUserContext user = context.userContext();
        BotLocale locale = user.locale();
        try {
            InternalTelegramTogglePreferenceResponse response = notificationTelegramInternalClient.togglePreference(
                    new InternalTelegramTogglePreferenceRequest(user.telegramUserId(), category)
            );
            String text = header(locale, "settings.title")
                    + botLocalizationService.get(locale, "settings.updated") + ": <b>"
                    + botHtmlEscaper.escape(localizedCategory(locale, category)) + "</b>\n"
                    + "Telegram: <b>"
                    + (response.telegramEnabled() ? botLocalizationService.get(locale, "settings.on") : botLocalizationService.get(locale, "settings.off"))
                    + "</b>";
            return textScreen(text, settingsKeyboard(locale), true);
        } catch (Exception exception) {
            log.warn("Failed to toggle Telegram preference");
            return errorScreen(locale, true);
        }
    }

    public BotScreen assignmentsStub(BotRequestContext context, boolean preferEdit) {
        BotLocale locale = context.userContext().locale();
        String text = header(locale, "menu.assignments")
                + botLocalizationService.get(locale, "stub.assignments");
        return textScreen(appendLinkNoticeIfLocal(text, locale), openAndBackKeyboard(locale, "/assignments", "menu:root"), preferEdit);
    }

    public BotScreen testsStub(BotRequestContext context, boolean preferEdit) {
        BotLocale locale = context.userContext().locale();
        String text = header(locale, "menu.tests")
                + botLocalizationService.get(locale, "stub.tests");
        return textScreen(appendLinkNoticeIfLocal(text, locale), openAndBackKeyboard(locale, "/tests", "menu:root"), preferEdit);
    }

    public BotScreen gradesStub(BotRequestContext context, boolean preferEdit) {
        BotLocale locale = context.userContext().locale();
        String text = header(locale, "menu.grades")
                + botLocalizationService.get(locale, "stub.grades");
        return textScreen(appendLinkNoticeIfLocal(text, locale), openAndBackKeyboard(locale, "/grades", "menu:root"), preferEdit);
    }

    public BotScreen groupStub(BotRequestContext context, boolean preferEdit) {
        BotLocale locale = context.userContext().locale();
        String text = header(locale, "menu.group")
                + botLocalizationService.get(locale, "stub.group");
        return textScreen(appendLinkNoticeIfLocal(text, locale), openAndBackKeyboard(locale, "/my-group", "menu:root"), preferEdit);
    }

    public BotScreen adminUsers(BotRequestContext context, int page, boolean preferEdit) {
        BotLocale locale = context.userContext().locale();
        if (!isAdmin(context.userContext().role())) {
            return textScreen(header(locale, "admin.users.title") + botLocalizationService.get(locale, "admin.access.denied"), adminBackKeyboard(locale), preferEdit);
        }
        try {
            InternalTelegramAdminUsersResponse response = notificationTelegramInternalClient.adminUsers(
                    new InternalTelegramAdminUsersRequest(Math.max(page, 0), 5)
            );
            StringBuilder textBuilder = new StringBuilder();
            textBuilder.append(header(locale, "admin.users.title"));
            if (response == null || response.items() == null || response.items().isEmpty()) {
                textBuilder.append(botLocalizationService.get(locale, "admin.users.empty"));
            } else {
                int number = 1 + page * 5;
                for (var item : response.items()) {
                    textBuilder.append(number)
                            .append(". <b>")
                            .append(botHtmlEscaper.escape(item.studiumUsername() == null ? item.userId().toString() : item.studiumUsername()))
                            .append("</b>\n")
                            .append("@")
                            .append(botHtmlEscaper.escape(item.telegramUsername() == null ? "unknown" : item.telegramUsername()))
                            .append(" • ")
                            .append(item.active() ? "active" : "inactive")
                            .append("\n")
                            .append("linkId: <code>")
                            .append(item.linkId())
                            .append("</code>\n\n");
                    number++;
                }
            }
            return textScreen(textBuilder.toString(), adminUsersKeyboard(locale, response), preferEdit);
        } catch (Exception exception) {
            log.warn("Failed to load admin bot users");
            return errorScreen(locale, preferEdit);
        }
    }

    public BotScreen adminStats(BotRequestContext context, boolean preferEdit) {
        BotLocale locale = context.userContext().locale();
        if (!isAdmin(context.userContext().role())) {
            return textScreen(header(locale, "admin.stats.title") + botLocalizationService.get(locale, "admin.access.denied"), adminBackKeyboard(locale), preferEdit);
        }
        try {
            InternalTelegramBotStatsResponse stats = notificationTelegramInternalClient.adminStats();
            String text = header(locale, "admin.stats.title")
                    + "Connected: <b>" + stats.connectedUsersCount() + "</b>\n"
                    + "Active links: <b>" + stats.activeLinksCount() + "</b>\n"
                    + "Disabled links: <b>" + stats.disabledLinksCount() + "</b>\n"
                    + "Delivery failures: <b>" + stats.deliveryFailuresCount() + "</b>\n"
                    + "Sent to Telegram: <b>" + stats.telegramSentCount() + "</b>";
            return textScreen(text, adminBackKeyboard(locale), preferEdit);
        } catch (Exception exception) {
            log.warn("Failed to load admin bot stats");
            return errorScreen(locale, preferEdit);
        }
    }

    public BotScreen adminDisableLink(BotRequestContext context, UUID linkId) {
        return adminAction(context, linkId, "disable");
    }

    public BotScreen adminEnableLink(BotRequestContext context, UUID linkId) {
        return adminAction(context, linkId, "enable");
    }

    public BotScreen adminSendTest(BotRequestContext context, UUID linkId) {
        return adminAction(context, linkId, "test");
    }

    private BotScreen adminAction(BotRequestContext context, UUID linkId, String action) {
        BotLocale locale = context.userContext().locale();
        if (!isAdmin(context.userContext().role())) {
            return textScreen(header(locale, "menu.botManage") + botLocalizationService.get(locale, "admin.access.denied"), adminBackKeyboard(locale), true);
        }

        try {
            InternalTelegramBotUserActionRequest request = new InternalTelegramBotUserActionRequest(linkId);
            switch (action) {
                case "disable" -> notificationTelegramInternalClient.adminDisableLink(request);
                case "enable" -> notificationTelegramInternalClient.adminEnableLink(request);
                case "test" -> notificationTelegramInternalClient.adminSendTestMessage(request);
                default -> {
                }
            }
            String text = header(locale, "menu.botManage")
                    + "Action: <b>" + botHtmlEscaper.escape(action) + "</b>\n"
                    + "linkId: <code>" + linkId + "</code>";
            return textScreen(text, adminBackKeyboard(locale), true);
        } catch (Exception exception) {
            log.warn("Failed to execute admin action");
            return errorScreen(locale, true);
        }
    }

    public BotScreen errorScreen(BotLocale locale, boolean preferEdit) {
        String text = header(locale, "menu.subtitle")
                + botLocalizationService.get(locale, "error.generic");
        return textScreen(appendLinkNoticeIfLocal(text, locale), unknownKeyboard(locale), preferEdit);
    }

    public BotScreen openInStudiumInfo(BotRequestContext context, String pageKey) {
        BotLocale locale = context.userContext().locale();
        String path = resolveStudiumPath(pageKey);
        String text = header(locale, "menu.subtitle")
                + "ℹ️ <b>" + botLocalizationService.get(locale, "actions.howOpen") + "</b>\n"
                + botLocalizationService.get(locale, "links.openInBrowser") + " <code>" + path + "</code>";
        return textScreen(text, backMenuKeyboard(locale, "menu:root"), true);
    }

    public BotScreen disconnectInfo(BotRequestContext context) {
        BotLocale locale = context.userContext().locale();
        String text = header(locale, "menu.subtitle")
                + botLocalizationService.get(locale, "disconnect.hint");
        return textScreen(appendLinkNoticeIfLocal(text, locale), connectHelpKeyboard(locale), false);
    }

    private InternalTelegramContextResponse reloadContext(BotUserContext userContext) {
        try {
            return notificationTelegramInternalClient.context(
                    new InternalTelegramContextRequest(userContext.telegramUserId(), userContext.chatId(), userContext.telegramUsername())
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private String header(BotLocale locale, String subtitleKey) {
        return "<b>" + botLocalizationService.get(locale, "app.title") + "</b>\n"
                + "<i>" + botLocalizationService.get(locale, subtitleKey) + "</i>\n\n";
    }

    private InlineKeyboardMarkup userKeyboard(BotLocale locale) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button(botLocalizationService.get(locale, "menu.schedule"), "menu:schedule")));
        rows.add(List.of(
                button(botLocalizationService.get(locale, "menu.assignments"), "menu:assignments"),
                button(botLocalizationService.get(locale, "menu.tests"), "menu:tests")
        ));
        rows.add(List.of(
                button(botLocalizationService.get(locale, "menu.notifications"), "menu:notifications"),
                button(botLocalizationService.get(locale, "menu.grades"), "menu:grades")
        ));
        rows.add(List.of(
                button(botLocalizationService.get(locale, "menu.group"), "menu:group"),
                button(botLocalizationService.get(locale, "menu.settings"), "menu:settings")
        ));
        return keyboard(rows);
    }

    private InlineKeyboardMarkup adminKeyboard(BotLocale locale) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button(botLocalizationService.get(locale, "menu.notifications"), "menu:notifications")));
        rows.add(List.of(button(botLocalizationService.get(locale, "menu.botUsers"), "admin:users:0")));
        rows.add(List.of(button(botLocalizationService.get(locale, "menu.botStats"), "admin:stats")));
        rows.add(List.of(
                button(botLocalizationService.get(locale, "menu.botManage"), "admin:manage"),
                button(botLocalizationService.get(locale, "menu.settings"), "menu:settings")
        ));
        return keyboard(rows);
    }

    private InlineKeyboardMarkup unknownKeyboard(BotLocale locale) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("/status", "command:status"),
                button("/help", "command:help")
        ));
        addStudiumLinkRowOrFallback(rows, locale, "actions.openStudium", "/profile");
        return keyboard(rows);
    }

    private InlineKeyboardMarkup connectHelpKeyboard(BotLocale locale) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        addStudiumLinkRowOrFallback(rows, locale, "actions.openProfile", "/profile");
        rows.add(List.of(
                button("/help", "command:help"),
                button("/menu", "menu:root")
        ));
        return keyboard(rows);
    }

    private InlineKeyboardMarkup scheduleKeyboard(BotLocale locale, LocalDate date) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button(botLocalizationService.get(locale, "actions.yesterday"), "schedule:day:" + date.minusDays(1)),
                button(botLocalizationService.get(locale, "actions.today"), "schedule:day:" + LocalDate.now()),
                button(botLocalizationService.get(locale, "actions.tomorrow"), "schedule:day:" + date.plusDays(1))
        ));
        rows.add(List.of(
                button(botLocalizationService.get(locale, "actions.week"), "schedule:week"),
                button(botLocalizationService.get(locale, "actions.menu"), "menu:root")
        ));
        addStudiumLinkRowOrFallback(rows, locale, "actions.openStudium", "/schedule");
        return keyboard(rows);
    }

    private InlineKeyboardMarkup notificationsKeyboard(BotLocale locale) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button(botLocalizationService.get(locale, "actions.markRead"), "notifications:mark-all-read")));
        addStudiumLinkRowOrFallback(rows, locale, "actions.openCenter", "/notifications");
        rows.add(List.of(
                button(botLocalizationService.get(locale, "actions.back"), "menu:root"),
                button(botLocalizationService.get(locale, "actions.menu"), "menu:root")
        ));
        return keyboard(rows);
    }

    private InlineKeyboardMarkup settingsKeyboard(BotLocale locale) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button(botLocalizationService.get(locale, "settings.category.assignments"), "settings:toggle:assignments"),
                button(botLocalizationService.get(locale, "settings.category.tests"), "settings:toggle:tests"),
                button(botLocalizationService.get(locale, "settings.category.grades"), "settings:toggle:grades")
        ));
        rows.add(List.of(
                button(botLocalizationService.get(locale, "settings.category.schedule"), "settings:toggle:schedule"),
                button(botLocalizationService.get(locale, "settings.category.materials"), "settings:toggle:materials")
        ));
        rows.add(List.of(
                button(botLocalizationService.get(locale, "actions.back"), "menu:root"),
                button(botLocalizationService.get(locale, "actions.menu"), "menu:root")
        ));
        return keyboard(rows);
    }

    private InlineKeyboardMarkup adminUsersKeyboard(BotLocale locale, InternalTelegramAdminUsersResponse response) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (response != null && response.items() != null) {
            for (var item : response.items().stream().limit(3).toList()) {
                rows.add(List.of(
                        button("⛔ " + shortLink(item.linkId()), "admin:disable:" + item.linkId()),
                        button("✅ " + shortLink(item.linkId()), "admin:enable:" + item.linkId()),
                        button("📨 " + shortLink(item.linkId()), "admin:test:" + item.linkId())
                ));
            }
        }
        rows.add(List.of(
                button(botLocalizationService.get(locale, "actions.back"), "menu:root"),
                button(botLocalizationService.get(locale, "actions.menu"), "menu:root")
        ));
        return keyboard(rows);
    }

    private InlineKeyboardMarkup adminBackKeyboard(BotLocale locale) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button(botLocalizationService.get(locale, "actions.back"), "menu:root")));
        return keyboard(rows);
    }

    private InlineKeyboardMarkup backMenuKeyboard(BotLocale locale, String callback) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button(botLocalizationService.get(locale, "actions.back"), callback),
                button(botLocalizationService.get(locale, "actions.menu"), "menu:root")
        ));
        return keyboard(rows);
    }

    private InlineKeyboardMarkup openAndBackKeyboard(BotLocale locale, String path, String backCallback) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        addStudiumLinkRowOrFallback(rows, locale, "actions.openStudium", path);
        rows.add(List.of(
                button(botLocalizationService.get(locale, "actions.back"), backCallback),
                button(botLocalizationService.get(locale, "actions.menu"), "menu:root")
        ));
        return keyboard(rows);
    }

    private BotScreen textScreen(String html, InlineKeyboardMarkup keyboard, boolean preferEdit) {
        return BotScreen.builder()
                .textHtml(html)
                .keyboard(keyboard)
                .preferEdit(preferEdit)
                .build();
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private InlineKeyboardButton urlButton(String text, String url) {
        return InlineKeyboardButton.builder()
                .text(text)
                .url(url)
                .build();
    }

    private void addStudiumLinkRowOrFallback(
            List<List<InlineKeyboardButton>> rows,
            BotLocale locale,
            String labelKey,
            String path
    ) {
        BotLinkFactory.LinkDecision linkDecision = botLinkFactory.resolve(path);
        if (linkDecision.allowed()) {
            rows.add(List.of(urlButton(botLocalizationService.get(locale, labelKey), linkDecision.url())));
            return;
        }

        rows.add(List.of(button(
                botLocalizationService.get(locale, "actions.howOpen"),
                "info:open:" + pageKeyFromPath(path)
        )));
    }

    private String userLabel(BotUserContext userContext) {
        String username = userContext.telegramUsername();
        if (username != null && !username.isBlank()) {
            return "@" + botHtmlEscaper.escape(username);
        }
        if (userContext.firstName() != null && !userContext.firstName().isBlank()) {
            return botHtmlEscaper.escape(userContext.firstName());
        }
        return String.valueOf(userContext.telegramUserId());
    }

    private String prefLine(String key, boolean enabled) {
        return "• " + key + ": " + (enabled ? "✅" : "❌") + "\n";
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(maxLength - 1, 1)) + "…";
    }

    private String formatDate(BotLocale locale, LocalDate date) {
        Locale javaLocale = switch (locale) {
            case EN -> Locale.ENGLISH;
            case PL -> Locale.forLanguageTag("pl-PL");
            case UK -> Locale.forLanguageTag("uk-UA");
        };
        String value = DateTimeFormatter.ofPattern("EEEE, d MMMM", javaLocale).format(date);
        if (value.isBlank()) {
            return date.toString();
        }
        return value.substring(0, 1).toUpperCase(javaLocale) + value.substring(1);
    }

    private String shortLink(UUID linkId) {
        String value = linkId.toString();
        return value.substring(0, 8);
    }

    private boolean isAdmin(BotUserRole role) {
        return role == BotUserRole.OWNER || role == BotUserRole.ADMIN;
    }

    private String localizedCategory(BotLocale locale, String category) {
        return switch (category) {
            case "assignments" -> botLocalizationService.get(locale, "settings.category.assignments");
            case "tests" -> botLocalizationService.get(locale, "settings.category.tests");
            case "grades" -> botLocalizationService.get(locale, "settings.category.grades");
            case "schedule" -> botLocalizationService.get(locale, "settings.category.schedule");
            case "materials" -> botLocalizationService.get(locale, "settings.category.materials");
            default -> category;
        };
    }

    private String connectResultText(BotLocale locale, String status) {
        return switch (status) {
            case "CONNECTED" -> botLocalizationService.get(locale, "connect.success");
            case "ALREADY_CONNECTED" -> botLocalizationService.get(locale, "connect.alreadyConnected");
            case "USER_ALREADY_HAS_LINK" -> botLocalizationService.get(locale, "connect.userAlreadyHasLink");
            case "LINKED_TO_ANOTHER_ACCOUNT" -> botLocalizationService.get(locale, "connect.linkedToAnother");
            case "TOKEN_EXPIRED" -> botLocalizationService.get(locale, "connect.tokenExpired");
            case "TOKEN_USED" -> botLocalizationService.get(locale, "connect.tokenUsed");
            case "TOKEN_REVOKED" -> botLocalizationService.get(locale, "connect.tokenRevoked");
            default -> botLocalizationService.get(locale, "connect.tokenInvalid");
        };
    }

    private String appendLinkNoticeIfLocal(String text, BotLocale locale) {
        if (botLinkFactory.hasPublicFrontendBaseUrl()) {
            return text;
        }
        return text + "\n\n<i>" + botLocalizationService.get(locale, "links.unavailable.local") + "</i>";
    }

    private String pageKeyFromPath(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return "profile";
        }
        return normalized.replace("/", "-");
    }

    private String resolveStudiumPath(String pageKey) {
        return switch (pageKey) {
            case "profile" -> "/profile";
            case "schedule" -> "/schedule";
            case "assignments" -> "/assignments";
            case "tests" -> "/tests";
            case "notifications" -> "/notifications";
            case "my-group" -> "/my-group";
            case "grades" -> "/grades";
            default -> "/profile";
        };
    }

    private InlineKeyboardMarkup keyboard(List<List<InlineKeyboardButton>> rows) {
        List<InlineKeyboardRow> keyboardRows = rows.stream()
                .map(InlineKeyboardRow::new)
                .toList();
        return new InlineKeyboardMarkup(keyboardRows);
    }
}
