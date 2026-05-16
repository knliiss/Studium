package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotImage;
import dev.knalis.telegrambot.bot.model.BotLocale;
import dev.knalis.telegrambot.dto.InternalTelegramScheduleDayResponse;
import dev.knalis.telegrambot.dto.InternalTelegramScheduleLessonItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DefaultBotImageRenderer implements BotImageRenderer {

    private final BotImageCache botImageCache;
    private final BotLocalizationService botLocalizationService;

    @Override
    public BotImage renderMainMenuBanner(long telegramUserId, BotLocale locale) {
        String key = "banner:menu:" + telegramUserId + ":" + locale.name();
        BotImage cached = botImageCache.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage image = new BufferedImage(1280, 420, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        setupGraphics(graphics);
        drawBackground(graphics, image.getWidth(), image.getHeight(), new Color(16, 27, 45), new Color(40, 18, 62));

        graphics.setColor(new Color(230, 241, 255));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 72));
        graphics.drawString("Studium", 90, 170);

        graphics.setColor(new Color(176, 201, 235));
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 34));
        graphics.drawString(botLocalizationService.get(locale, "menu.subtitle"), 90, 235);

        graphics.setColor(new Color(255, 189, 89));
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 28));
        graphics.drawString("LMS Telegram", 90, 300);
        graphics.dispose();

        BotImage rendered = new BotImage("menu-banner.png", toPngBytes(image));
        botImageCache.put(key, rendered);
        return rendered;
    }

    @Override
    public BotImage renderScheduleDay(
            long telegramUserId,
            BotLocale locale,
            LocalDate date,
            InternalTelegramScheduleDayResponse scheduleDay
    ) {
        String key = "schedule:" + telegramUserId + ":" + locale.name() + ":" + date + ":" + dataHash(scheduleDay);
        BotImage cached = botImageCache.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage image = new BufferedImage(1280, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        setupGraphics(graphics);
        drawBackground(graphics, image.getWidth(), image.getHeight(), new Color(18, 26, 41), new Color(36, 48, 74));

        graphics.setColor(new Color(243, 248, 255));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 50));
        graphics.drawString(botLocalizationService.get(locale, "schedule.title"), 70, 110);

        String dateLine = DateTimeFormatter.ofPattern("EEEE, d MMMM", toLocale(locale)).format(date);
        graphics.setColor(new Color(180, 207, 241));
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 30));
        graphics.drawString(capitalize(dateLine), 70, 160);

        List<InternalTelegramScheduleLessonItem> lessons = scheduleDay == null ? List.of() : scheduleDay.lessons();
        if (lessons.isEmpty()) {
            graphics.setColor(new Color(245, 207, 153));
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 34));
            String emptyTextKey = scheduleDay != null && !scheduleDay.available()
                    ? "schedule.unavailable"
                    : "schedule.empty";
            drawMultiline(graphics, botLocalizationService.get(locale, emptyTextKey), 70, 260, 60);
        } else {
            int y = 220;
            int maxLessons = Math.min(lessons.size(), 6);
            for (int index = 0; index < maxLessons; index++) {
                InternalTelegramScheduleLessonItem lesson = lessons.get(index);
                drawLessonCard(graphics, lesson, 70, y);
                y += 105;
            }
            if (lessons.size() > maxLessons) {
                int rest = lessons.size() - maxLessons;
                graphics.setColor(new Color(233, 206, 152));
                graphics.setFont(new Font("SansSerif", Font.PLAIN, 26));
                graphics.drawString("+ " + rest + " ще у Studium", 90, y + 20);
            }
        }

        graphics.dispose();
        BotImage rendered = new BotImage("schedule-" + date + ".png", toPngBytes(image));
        botImageCache.put(key, rendered);
        return rendered;
    }

    private void drawLessonCard(Graphics2D graphics, InternalTelegramScheduleLessonItem lesson, int x, int y) {
        graphics.setColor(new Color(31, 45, 72, 230));
        graphics.fillRoundRect(x, y, 1140, 82, 24, 24);

        graphics.setColor(new Color(255, 199, 100));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 24));
        graphics.drawString(trimText(lesson.time(), 18), x + 24, y + 34);

        graphics.setColor(new Color(235, 243, 255));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 24));
        graphics.drawString(trimText(lesson.subject(), 56), x + 190, y + 34);

        graphics.setColor(new Color(181, 205, 240));
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 20));
        String secondary = trimText(lesson.lessonType() + " • " + lesson.location(), 74);
        graphics.drawString(secondary, x + 190, y + 62);

        if (lesson.counterpart() != null && !lesson.counterpart().isBlank()) {
            graphics.setColor(new Color(151, 184, 226));
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 19));
            graphics.drawString(trimText(lesson.counterpart(), 52), x + 770, y + 62);
        }
    }

    private void drawBackground(Graphics2D graphics, int width, int height, Color topColor, Color bottomColor) {
        GradientPaint gradientPaint = new GradientPaint(0, 0, topColor, width, height, bottomColor);
        graphics.setPaint(gradientPaint);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(255, 255, 255, 28));
        graphics.fillOval(width - 280, -120, 420, 420);
        graphics.fillOval(-160, height - 220, 360, 360);
    }

    private void setupGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private byte[] toPngBytes(BufferedImage image) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render PNG", exception);
        }
    }

    private Locale toLocale(BotLocale locale) {
        return switch (locale) {
            case EN -> Locale.ENGLISH;
            case PL -> Locale.forLanguageTag("pl-PL");
            case UK -> Locale.forLanguageTag("uk-UA");
        };
    }

    private String trimText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(maxLength - 1, 1)) + "…";
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(toLocale(BotLocale.UK)) + value.substring(1);
    }

    private int dataHash(InternalTelegramScheduleDayResponse scheduleDay) {
        if (scheduleDay == null || scheduleDay.lessons() == null) {
            return 0;
        }
        return Objects.hash(scheduleDay.available(), scheduleDay.lessons().stream()
                .map(item -> item.time() + "|" + item.subject() + "|" + item.lessonType() + "|" + item.location() + "|" + item.counterpart())
                .toList());
    }

    private void drawMultiline(Graphics2D graphics, String text, int x, int y, int lineStep) {
        String[] parts = text.split("\\n");
        int lineY = y;
        for (String part : parts) {
            graphics.drawString(trimText(part, 95), x, lineY);
            lineY += lineStep;
        }
    }
}
