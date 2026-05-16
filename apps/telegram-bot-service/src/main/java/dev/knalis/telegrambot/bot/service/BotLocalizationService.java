package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotLocale;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class BotLocalizationService {

    private final Map<BotLocale, Map<String, String>> messages = new HashMap<>();

    public BotLocalizationService() {
        messages.put(BotLocale.UK, ukrainianMessages());
        messages.put(BotLocale.EN, englishMessages());
        messages.put(BotLocale.PL, polishMessages());
    }

    public String get(BotLocale locale, String key) {
        Map<String, String> localeMessages = messages.getOrDefault(locale, messages.get(BotLocale.UK));
        return localeMessages.getOrDefault(key, messages.get(BotLocale.UK).getOrDefault(key, key));
    }

    public BotLocale resolveLocale(String localeCode) {
        if (localeCode == null || localeCode.isBlank()) {
            return BotLocale.UK;
        }
        String normalized = localeCode.toLowerCase();
        if (normalized.startsWith("en")) {
            return BotLocale.EN;
        }
        if (normalized.startsWith("pl")) {
            return BotLocale.PL;
        }
        return BotLocale.UK;
    }

    private Map<String, String> ukrainianMessages() {
        Map<String, String> values = new HashMap<>();
        values.put("app.title", "Studium");
        values.put("menu.subtitle", "Головне меню");
        values.put("menu.hint", "Оберіть дію нижче.");
        values.put("menu.schedule", "📅 Мій розклад");
        values.put("menu.assignments", "📝 Завдання");
        values.put("menu.tests", "🧪 Тести");
        values.put("menu.notifications", "🔔 Сповіщення");
        values.put("menu.grades", "🎓 Оцінки");
        values.put("menu.group", "👥 Моя група");
        values.put("menu.settings", "⚙️ Налаштування");
        values.put("menu.botUsers", "👤 Користувачі бота");
        values.put("menu.botStats", "📊 Статистика бота");
        values.put("menu.botManage", "⚙️ Керування ботом");
        values.put("actions.back", "◀️ Назад");
        values.put("actions.menu", "🏠 Меню");
        values.put("actions.openStudium", "Відкрити в Studium");
        values.put("actions.openProfile", "Відкрити профіль");
        values.put("actions.yesterday", "◀️ Вчора");
        values.put("actions.today", "Сьогодні");
        values.put("actions.tomorrow", "Завтра ▶️");
        values.put("actions.week", "Тиждень");
        values.put("actions.markRead", "Позначити всі прочитаними");
        values.put("actions.openCenter", "Відкрити центр");
        values.put("actions.howOpen", "ℹ️ Як відкрити в Studium");
        values.put("actions.disableLink", "Вимкнути привʼязку");
        values.put("actions.enableLink", "Увімкнути привʼязку");
        values.put("actions.sendTest", "Надіслати тест");
        values.put("links.unavailable.local", "Посилання на Studium недоступні в локальному середовищі.");
        values.put("links.openInBrowser", "Відкрийте Studium у браузері:");
        values.put("schedule.title", "Розклад");
        values.put("schedule.empty", "На цей день занять не знайдено.");
        values.put("schedule.unavailable", "Дані розкладу тимчасово недоступні. Відкрийте Studium для повного перегляду.");
        values.put("notifications.title", "Непрочитані сповіщення");
        values.put("notifications.empty", "Наразі немає непрочитаних сповіщень.");
        values.put("notifications.marked", "Усі сповіщення позначено прочитаними.");
        values.put("settings.title", "Налаштування Telegram");
        values.put("settings.enabled", "Telegram сповіщення");
        values.put("settings.on", "Увімкнено");
        values.put("settings.off", "Вимкнено");
        values.put("settings.category.assignments", "Завдання");
        values.put("settings.category.tests", "Тести");
        values.put("settings.category.grades", "Оцінки");
        values.put("settings.category.schedule", "Розклад");
        values.put("settings.category.materials", "Матеріали");
        values.put("settings.updated", "Оновлено");
        values.put("status.connected", "Акаунт Telegram підключено.");
        values.put("status.notConnected", "Telegram не підключено. Відкрийте профіль Studium і натисніть «Підключити Telegram».");
        values.put("help.text", "Команди: /start TOKEN, /menu, /status, /help, /disconnect");
        values.put("connect.noToken", "Щоб підключити Telegram, відкрийте профіль у Studium і натисніть «Підключити Telegram».");
        values.put("connect.noToken.local", "У локальному середовищі відкрийте Studium у браузері та перейдіть у профіль.");
        values.put("connect.success", "✅ Telegram підключено до Studium.");
        values.put("connect.alreadyConnected", "ℹ️ Telegram уже підключено до цього акаунта Studium.");
        values.put("connect.userAlreadyHasLink", "⚠️ У вашому профілі вже є інша привʼязка Telegram. Відʼєднайте її у профілі Studium.");
        values.put("connect.linkedToAnother", "⛔ Цей Telegram уже привʼязаний до іншого акаунта Studium. Спочатку відʼєднайте його там.");
        values.put("connect.tokenExpired", "⏳ Термін дії посилання минув. Згенеруйте нове у профілі Studium.");
        values.put("connect.tokenUsed", "♻️ Це посилання вже використано. Згенеруйте нове у профілі Studium.");
        values.put("connect.tokenRevoked", "🚫 Це посилання відкликано. Згенеруйте нове у профілі Studium.");
        values.put("connect.tokenInvalid", "❌ Посилання для підключення недійсне. Згенеруйте нове у профілі Studium.");
        values.put("disconnect.hint", "Керування відключенням доступне в профілі Studium.");
        values.put("error.generic", "Сталася тимчасова помилка. Спробуйте ще раз.");
        values.put("admin.users.title", "Користувачі Telegram-бота");
        values.put("admin.users.empty", "Поки що немає підключених користувачів.");
        values.put("admin.stats.title", "Статистика Telegram-бота");
        values.put("admin.access.denied", "Ця секція доступна лише для OWNER/ADMIN.");
        values.put("stub.assignments", "Список завдань доступний у Studium.");
        values.put("stub.tests", "Список тестів доступний у Studium.");
        values.put("stub.grades", "Оцінки доступні у Studium.");
        values.put("stub.group", "Інформація про групу доступна у Studium.");
        return values;
    }

    private Map<String, String> englishMessages() {
        Map<String, String> values = new HashMap<>();
        values.put("app.title", "Studium");
        values.put("menu.subtitle", "Main menu");
        values.put("menu.hint", "Choose an action below.");
        values.put("menu.schedule", "📅 My schedule");
        values.put("menu.assignments", "📝 Assignments");
        values.put("menu.tests", "🧪 Tests");
        values.put("menu.notifications", "🔔 Notifications");
        values.put("menu.grades", "🎓 Grades");
        values.put("menu.group", "👥 My group");
        values.put("menu.settings", "⚙️ Settings");
        values.put("menu.botUsers", "👤 Bot users");
        values.put("menu.botStats", "📊 Bot stats");
        values.put("menu.botManage", "⚙️ Bot management");
        values.put("actions.back", "◀️ Back");
        values.put("actions.menu", "🏠 Menu");
        values.put("actions.openStudium", "Open in Studium");
        values.put("actions.openProfile", "Open profile");
        values.put("actions.yesterday", "◀️ Yesterday");
        values.put("actions.today", "Today");
        values.put("actions.tomorrow", "Tomorrow ▶️");
        values.put("actions.week", "Week");
        values.put("actions.markRead", "Mark all as read");
        values.put("actions.openCenter", "Open center");
        values.put("actions.howOpen", "ℹ️ How to open in Studium");
        values.put("actions.disableLink", "Disable link");
        values.put("actions.enableLink", "Enable link");
        values.put("actions.sendTest", "Send test");
        values.put("links.unavailable.local", "Studium links are unavailable in local environment.");
        values.put("links.openInBrowser", "Open Studium in your browser:");
        values.put("schedule.title", "Schedule");
        values.put("schedule.empty", "No lessons found for this day.");
        values.put("schedule.unavailable", "Schedule data is temporarily unavailable. Open Studium for full details.");
        values.put("notifications.title", "Unread notifications");
        values.put("notifications.empty", "No unread notifications right now.");
        values.put("notifications.marked", "All notifications marked as read.");
        values.put("settings.title", "Telegram settings");
        values.put("settings.enabled", "Telegram notifications");
        values.put("settings.on", "Enabled");
        values.put("settings.off", "Disabled");
        values.put("settings.category.assignments", "Assignments");
        values.put("settings.category.tests", "Tests");
        values.put("settings.category.grades", "Grades");
        values.put("settings.category.schedule", "Schedule");
        values.put("settings.category.materials", "Materials");
        values.put("settings.updated", "Updated");
        values.put("status.connected", "Telegram account is connected.");
        values.put("status.notConnected", "Telegram is not connected. Open your Studium profile and press \"Connect Telegram\".");
        values.put("help.text", "Commands: /start TOKEN, /menu, /status, /help, /disconnect");
        values.put("connect.noToken", "To connect Telegram, open your Studium profile and press \"Connect Telegram\".");
        values.put("connect.noToken.local", "In local environment, open Studium in your browser and go to your profile.");
        values.put("connect.success", "✅ Telegram is connected to Studium.");
        values.put("connect.alreadyConnected", "ℹ️ Telegram is already connected to this Studium account.");
        values.put("connect.userAlreadyHasLink", "⚠️ Your profile already has another Telegram link. Disconnect it in Studium profile first.");
        values.put("connect.linkedToAnother", "⛔ This Telegram account is linked to another Studium account. Disconnect it there first.");
        values.put("connect.tokenExpired", "⏳ This connection link has expired. Generate a new one in Studium profile.");
        values.put("connect.tokenUsed", "♻️ This connection link was already used. Generate a new one in Studium profile.");
        values.put("connect.tokenRevoked", "🚫 This connection link was revoked. Generate a new one in Studium profile.");
        values.put("connect.tokenInvalid", "❌ This connection link is invalid. Generate a new one in Studium profile.");
        values.put("disconnect.hint", "Disconnect management is available in your Studium profile.");
        values.put("error.generic", "Temporary error. Please try again.");
        values.put("admin.users.title", "Telegram bot users");
        values.put("admin.users.empty", "No connected users yet.");
        values.put("admin.stats.title", "Telegram bot statistics");
        values.put("admin.access.denied", "This section is for OWNER/ADMIN only.");
        values.put("stub.assignments", "Assignments are available in Studium.");
        values.put("stub.tests", "Tests are available in Studium.");
        values.put("stub.grades", "Grades are available in Studium.");
        values.put("stub.group", "Group details are available in Studium.");
        return values;
    }

    private Map<String, String> polishMessages() {
        Map<String, String> values = new HashMap<>();
        values.put("app.title", "Studium");
        values.put("menu.subtitle", "Menu główne");
        values.put("menu.hint", "Wybierz działanie poniżej.");
        values.put("menu.schedule", "📅 Mój plan");
        values.put("menu.assignments", "📝 Zadania");
        values.put("menu.tests", "🧪 Testy");
        values.put("menu.notifications", "🔔 Powiadomienia");
        values.put("menu.grades", "🎓 Oceny");
        values.put("menu.group", "👥 Moja grupa");
        values.put("menu.settings", "⚙️ Ustawienia");
        values.put("menu.botUsers", "👤 Użytkownicy bota");
        values.put("menu.botStats", "📊 Statystyki bota");
        values.put("menu.botManage", "⚙️ Zarządzanie botem");
        values.put("actions.back", "◀️ Wstecz");
        values.put("actions.menu", "🏠 Menu");
        values.put("actions.openStudium", "Otwórz w Studium");
        values.put("actions.openProfile", "Otwórz profil");
        values.put("actions.yesterday", "◀️ Wczoraj");
        values.put("actions.today", "Dzisiaj");
        values.put("actions.tomorrow", "Jutro ▶️");
        values.put("actions.week", "Tydzień");
        values.put("actions.markRead", "Oznacz wszystkie");
        values.put("actions.openCenter", "Otwórz centrum");
        values.put("actions.howOpen", "ℹ️ Jak otworzyć w Studium");
        values.put("actions.disableLink", "Wyłącz powiązanie");
        values.put("actions.enableLink", "Włącz powiązanie");
        values.put("actions.sendTest", "Wyślij test");
        values.put("links.unavailable.local", "Linki do Studium są niedostępne w środowisku lokalnym.");
        values.put("links.openInBrowser", "Otwórz Studium w przeglądarce:");
        values.put("schedule.title", "Plan zajęć");
        values.put("schedule.empty", "Brak zajęć na ten dzień.");
        values.put("schedule.unavailable", "Dane planu są chwilowo niedostępne. Otwórz Studium.");
        values.put("notifications.title", "Nieprzeczytane powiadomienia");
        values.put("notifications.empty", "Brak nieprzeczytanych powiadomień.");
        values.put("notifications.marked", "Wszystkie powiadomienia oznaczono jako przeczytane.");
        values.put("settings.title", "Ustawienia Telegram");
        values.put("settings.enabled", "Powiadomienia Telegram");
        values.put("settings.on", "Włączone");
        values.put("settings.off", "Wyłączone");
        values.put("settings.category.assignments", "Zadania");
        values.put("settings.category.tests", "Testy");
        values.put("settings.category.grades", "Oceny");
        values.put("settings.category.schedule", "Plan");
        values.put("settings.category.materials", "Materiały");
        values.put("settings.updated", "Zaktualizowano");
        values.put("status.connected", "Konto Telegram jest połączone.");
        values.put("status.notConnected", "Telegram nie jest połączony. Otwórz profil Studium i kliknij \"Połącz Telegram\".");
        values.put("help.text", "Komendy: /start TOKEN, /menu, /status, /help, /disconnect");
        values.put("connect.noToken", "Aby połączyć Telegram, otwórz profil w Studium i kliknij \"Połącz Telegram\".");
        values.put("connect.noToken.local", "W środowisku lokalnym otwórz Studium w przeglądarce i przejdź do profilu.");
        values.put("connect.success", "✅ Telegram jest połączony ze Studium.");
        values.put("connect.alreadyConnected", "ℹ️ Telegram jest już połączony z tym kontem Studium.");
        values.put("connect.userAlreadyHasLink", "⚠️ Twój profil ma już inne powiązanie Telegram. Najpierw odłącz je w profilu Studium.");
        values.put("connect.linkedToAnother", "⛔ To konto Telegram jest połączone z innym kontem Studium. Najpierw odłącz je tam.");
        values.put("connect.tokenExpired", "⏳ Link połączenia wygasł. Wygeneruj nowy w profilu Studium.");
        values.put("connect.tokenUsed", "♻️ Ten link połączenia został już użyty. Wygeneruj nowy w profilu Studium.");
        values.put("connect.tokenRevoked", "🚫 Ten link połączenia został odwołany. Wygeneruj nowy w profilu Studium.");
        values.put("connect.tokenInvalid", "❌ Ten link połączenia jest nieprawidłowy. Wygeneruj nowy w profilu Studium.");
        values.put("disconnect.hint", "Zarządzanie odłączeniem jest dostępne w profilu Studium.");
        values.put("error.generic", "Wystąpił tymczasowy błąd. Spróbuj ponownie.");
        values.put("admin.users.title", "Użytkownicy bota Telegram");
        values.put("admin.users.empty", "Brak połączonych użytkowników.");
        values.put("admin.stats.title", "Statystyki bota Telegram");
        values.put("admin.access.denied", "Ta sekcja jest tylko dla OWNER/ADMIN.");
        values.put("stub.assignments", "Zadania są dostępne w Studium.");
        values.put("stub.tests", "Testy są dostępne w Studium.");
        values.put("stub.grades", "Oceny są dostępne w Studium.");
        values.put("stub.group", "Szczegóły grupy są dostępne w Studium.");
        return values;
    }
}
