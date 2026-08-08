/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Кастомное главное меню — порт MainMenuScreen (ThunderHack) под Meteor.
 *
 * Слева — changelog (текст с pastebin.com/v5WcmZNL, без кнопок),
 * по центру — логотип и кнопки колонкой вниз от середины экрана,
 * со скруглёнными углами.
 */
public class MainMenuScreen extends Screen {
    private static MainMenuScreen INSTANCE = new MainMenuScreen();

    private final List<MainMenuButton> buttons = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final Random random = new Random();

    /** Ставится в true на время открытия ванильного TitleScreen ("Back to default menu"). */
    public static boolean confirm = false;
    public static int ticksActive;

    /** Название меню (логотип). Меняется по клику — см. {@link EditMainMenuTitleScreen}. */
    public static String title = "METEOR CLIENT";

    // Changelog (текст с pastebin, подгружается один раз и просто рисуется слева)
    private static final String PASTEBIN_URL = "https://pastebin.com/raw/v5WcmZNL";
    private static final int MAX_CHANGELOG_LINES = 12;
    private final List<String> changelogLines = new ArrayList<>();
    private boolean changelogLoading = true;
    private boolean changelogFailed = false;

    // Кнопки (ThunderHack-style): обычные 107x38, выход 222x38
    private static final int BUTTON_WIDTH = 107;
    private static final int BUTTON_EXIT_WIDTH = 222;
    private static final int BUTTON_HEIGHT = 38;
    private static final int BUTTON_GAP = 10;

    // Скругление углов
    private static final int CORNER_RADIUS = 5;
    private static final List<int[]> CORNER_POINTS = new ArrayList<>();

    static {
        for (int dx = -CORNER_RADIUS; dx <= CORNER_RADIUS; dx++) {
            for (int dy = -CORNER_RADIUS; dy <= CORNER_RADIUS; dy++) {
                if (dx * dx + dy * dy <= CORNER_RADIUS * CORNER_RADIUS) {
                    CORNER_POINTS.add(new int[] {dx, dy});
                }
            }
        }
    }

    protected MainMenuScreen() {
        super(Text.of("MainMenu"));

        loadTitle();

        buttons.add(new MainMenuButton(false, I18n.translate("menu.singleplayer").toUpperCase(Locale.ROOT), () -> mc.setScreen(new SelectWorldScreen(this))));
        buttons.add(new MainMenuButton(false, I18n.translate("menu.multiplayer").toUpperCase(Locale.ROOT), () -> mc.setScreen(new MultiplayerScreen(this))));
        buttons.add(new MainMenuButton(false, I18n.translate("menu.options").toUpperCase(Locale.ROOT).replace(".", ""), () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        buttons.add(new MainMenuButton(false, "MODULES", () -> mc.setScreen(GuiThemes.get().modulesScreen())));
        buttons.add(new MainMenuButton(true, I18n.translate("menu.quit").toUpperCase(Locale.ROOT), mc::scheduleStop));

        generateStars();
        loadChangelog();
    }

    public static MainMenuScreen getInstance() {
        ticksActive = 0;
        if (INSTANCE == null) {
            INSTANCE = new MainMenuScreen();
        }
        return INSTANCE;
    }

    private static Path titleFile() {
        return MeteorClient.FOLDER.toPath().resolve("main_menu_title.txt");
    }

    /** Загружает сохранённое название из файла (если есть). */
    private static void loadTitle() {
        try {
            Path file = titleFile();
            if (Files.exists(file)) {
                String loaded = Files.readString(file).trim();
                if (!loaded.isEmpty()) title = loaded;
            }
        } catch (IOException ignored) {
        }
    }

    /** Устанавливает название и сохраняет его в файл. Пустая строка сбрасывает на дефолт. */
    public static void setTitle(String newTitle) {
        String t = newTitle == null ? "" : newTitle.trim();
        if (t.isEmpty()) t = "METEOR CLIENT";
        title = t;

        try {
            Files.writeString(titleFile(), t);
        } catch (IOException ignored) {
        }
    }

    /** Асинхронно тянет текст ченжлога с pastebin и складывает в {@link #changelogLines}. */
    private void loadChangelog() {
        MeteorExecutor.execute(() -> {
            try {
                String raw = Http.get(PASTEBIN_URL).sendString();
                if (raw == null || raw.isBlank()) {
                    changelogFailed = true;
                    return;
                }

                List<String> lines = new ArrayList<>();
                for (String line : raw.split("\n")) {
                    String l = line.replace("\r", "").stripTrailing();
                    if (l.isEmpty()) continue;
                    lines.add(l);
                    if (lines.size() >= MAX_CHANGELOG_LINES) break;
                }

                if (lines.isEmpty()) {
                    changelogFailed = true;
                } else {
                    changelogLines.clear();
                    changelogLines.addAll(lines);
                }
            } catch (Exception e) {
                changelogFailed = true;
            } finally {
                changelogLoading = false;
            }
        });
    }

    private void generateStars() {
        stars.clear();
        for (int i = 0; i < 200; i++) {
            stars.add(new Star(
                random.nextFloat(),
                random.nextFloat(),
                0.5f + random.nextFloat() * 1.8f,
                0.3f + random.nextFloat() * 1.2f,
                (float) (random.nextFloat() * Math.PI * 2),
                random
            ));
        }
    }

    @Override
    public void tick() {
        ticksActive++;

        if (ticksActive > 400) {
            ticksActive = 0;
        }

        // Обновляем позиции звёзд для эффекта движения
        stars.forEach(Star::update);
    }

    private void renderStarrySky(DrawContext context) {
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        context.fillGradient(0, 0, width, height, 0xFF05050F, 0xFF0B0B1F);

        long time = System.currentTimeMillis();

        for (Star star : stars) {
            // Улучшенное мерцание: комбинация плавного синуса и случайных вспышек
            float baseBrightness = (float) (0.5 + 0.5 * Math.sin(time / 1000.0 * star.speed + star.phase));
            float twinkle = (float) (0.8 + 0.2 * Math.sin(time / 200.0 * star.twinkleSpeed + star.twinklePhase));

            // Добавляем случайные "вспышки" для некоторых звёзд
            float flash = 1.0f;
            if (star.size > 1.5f && random.nextFloat() < 0.01f) {
                flash = 1.5f; // Резкая вспышка яркости
            }

            float brightness = Math.min(1.0f, baseBrightness * twinkle * flash);
            int alpha = (int) (brightness * 255);

            int sx = (int) (star.x * width);
            int sy = (int) (star.y * height);
            int size = Math.max(1, Math.round(star.size));

            int color = 0xFFFFFF | (alpha << 24);
            context.fill(sx, sy, sx + size, sy + size, color);

            if (star.size > 1.6f && brightness > 0.75f) {
                int glowColor = 0xFFFFFF | ((alpha / 3) << 24);
                context.fill(sx - 1, sy - 1, sx + size + 1, sy + size + 1, glowColor);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        int halfOfWidth = width / 2;
        int halfOfHeight = height / 2;

        renderStarrySky(context);

        // Чанжлог слева — просто текст с pastebin, ничего открывать не надо
        renderChangelog(context);

        // Кнопки колонкой вниз от середины экрана
        int startX = (width - BUTTON_EXIT_WIDTH) / 2;
        int startY = buttonColumnStartY();
        int y = startY;
        for (MainMenuButton button : buttons) {
            int bw = button.isExit ? BUTTON_EXIT_WIDTH : BUTTON_WIDTH;
            int bx = startX + (BUTTON_EXIT_WIDTH - bw) / 2; // каждая кнопка по центру колонки
            button.render(context, mouseX, mouseY, bx, y, bw, BUTTON_HEIGHT);
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }

        // Логотип (x2 к ванильному шрифту, подсветка при наведении, клик меняет название)
        boolean hoveredLogo = isHovered(mouseX, mouseY, halfOfWidth - 120, halfOfHeight - 150, 240, 44);
        String logo = title;
        int logoW = mc.textRenderer.getWidth(logo);
        float logoScale = 2.0f;
        if (logoW * logoScale > width - 280) {
            logoScale = Math.max(0.8f, (width - 280f) / logoW); // не даём логотипу налезть на чанжлог
        }
        int logoX = (int) (halfOfWidth / logoScale - logoW / 2f);
        int logoY = (int) ((halfOfHeight - 150) / logoScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(logoScale, logoScale);
        context.drawText(mc.textRenderer, logo, logoX, logoY, hoveredLogo ? 0xE6FFFFFF : 0xB4FFFFFF, false);
        context.getMatrices().popMatrix();

        // Подсказка: клик по названию открывает окно смены названия
        String hint = "\u00abclick to rename\u00bb";
        context.drawText(mc.textRenderer, hint,
            halfOfWidth - mc.textRenderer.getWidth(hint) / 2, halfOfHeight - 122,
            hoveredLogo ? 0x99FFFFFF : 0x66FFFFFF, false);

        // Возврат в ванильное меню (под последней кнопкой)
        int backY = buttonColumnBottom() + 10;
        boolean hovered = isHovered(mouseX, mouseY, halfOfWidth - 50, backY, 100, 10);
        String back = "<-- Back to default menu";
        context.drawText(mc.textRenderer, back,
            halfOfWidth - mc.textRenderer.getWidth(back) / 2, backY,
            hovered ? -1 : 0x99FFFFFF, false);

        // Сборка внизу по центру
        String build = "build " + (MeteorClient.ADDON != null && MeteorClient.ADDON.getCommit() != null
            ? MeteorClient.ADDON.getCommit().substring(0, Math.min(7, MeteorClient.ADDON.getCommit().length()))
            : "—");
        context.drawText(mc.textRenderer, build,
            halfOfWidth - mc.textRenderer.getWidth(build) / 2, height - 15, 0xFFB0B8D0, false);

        // Аддоны (справа сверху)
        int totalAddonsLoaded = AddonManager.ADDONS.size();
        String addonsText = "Addons Loaded: " + totalAddonsLoaded;
        int textWidth = mc.textRenderer.getWidth(addonsText);
        int textX = width - textWidth - 5;
        context.drawText(mc.textRenderer, addonsText, textX, 5, 0xFFFFFFFF, false);

        int offset = 0;
        for (MeteorAddon addon : AddonManager.ADDONS) {
            String addonName = addon.name + " |";
            textWidth = mc.textRenderer.getWidth(addonName);
            textX = width - textWidth - 5;
            context.drawText(mc.textRenderer, addonName, textX, 13 + offset, 0xFFAAAAAA, false);
            offset += 9;
        }
    }

    /** Y начала колонки кнопок: от середины экрана вниз (с защитой от вылезания за край). */
    private int buttonColumnStartY() {
        int height = mc.getWindow().getScaledHeight();
        int totalH = buttonColumnHeight();
        int startY = height / 2;
        int maxBottom = height - 42;
        if (startY + totalH > maxBottom) startY = Math.max(20, maxBottom - totalH);
        return startY;
    }

    private int buttonColumnHeight() {
        return buttons.size() * BUTTON_HEIGHT + (buttons.size() - 1) * BUTTON_GAP;
    }

    private int buttonColumnBottom() {
        return buttonColumnStartY() + buttonColumnHeight();
    }

    /** Панель changelog слева: заголовок + текст с pastebin. */
    private void renderChangelog(DrawContext context) {
        int panelX = 8;
        int panelY = 8;
        int panelW = 270;
        int pad = 10;

        int lines = changelogLoading ? 1 : (changelogFailed || changelogLines.isEmpty() ? 2 : Math.min(changelogLines.size(), MAX_CHANGELOG_LINES));
        int panelH = 31 + lines * 10 + 10;

        drawRoundedBorder(context, panelX, panelY, panelW, panelH, 0xFF3A5CFF, 0xCC0A0E1F);

        int contentX = panelX + pad;
        context.drawText(mc.textRenderer, "Changelog", contentX, panelY + 7, 0xFFE0E7FF, true);

        int y = panelY + 21;
        if (changelogLoading) {
            context.drawText(mc.textRenderer, "Loading...", contentX, y, 0xFF8FA3C8, false);
        } else if (changelogFailed || changelogLines.isEmpty()) {
            context.drawText(mc.textRenderer, "Failed to load", contentX, y, 0xFF8FA3C8, false);
            context.drawText(mc.textRenderer, "pastebin.com/v5WcmZNL", contentX, y + 10, 0xFF6A6F8C, false);
        } else {
            int maxChars = (panelW - pad * 2) / 6; // ~6px на символ ванильного шрифта
            for (int i = 0; i < Math.min(changelogLines.size(), MAX_CHANGELOG_LINES); i++) {
                context.drawText(mc.textRenderer, truncate(changelogLines.get(i), maxChars), contentX, y, 0xFFE8EEFF, false);
                y += 10;
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int mouseX = (int) click.x();
            int mouseY = (int) click.y();

            int startX = (mc.getWindow().getScaledWidth() - BUTTON_EXIT_WIDTH) / 2;
            int y = buttonColumnStartY();
            for (MainMenuButton button : buttons) {
                int bw = button.isExit ? BUTTON_EXIT_WIDTH : BUTTON_WIDTH;
                int bx = startX + (BUTTON_EXIT_WIDTH - bw) / 2;
                button.onClick(mouseX, mouseY, bx, y, bw, BUTTON_HEIGHT);
                y += BUTTON_HEIGHT + BUTTON_GAP;
            }

            int halfOfWidth = mc.getWindow().getScaledWidth() / 2;
            int halfOfHeight = mc.getWindow().getScaledHeight() / 2;

            // Клик по названию — окно смены названия
            if (isHovered(mouseX, mouseY, halfOfWidth - 120, halfOfHeight - 150, 240, 44)) {
                mc.setScreen(new EditMainMenuTitleScreen(GuiThemes.get()));
            }

            // Возврат в ванильное меню
            if (isHovered(mouseX, mouseY, halfOfWidth - 50, buttonColumnBottom() + 10, 100, 10)) {
                confirm = true;
                mc.setScreen(new TitleScreen());
                confirm = false;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private static boolean isHovered(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    /** Скруглённый прямоугольник: центральные полосы + попиксельные углы. */
    private static void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + CORNER_RADIUS, y, x + w - CORNER_RADIUS, y + h, color);
        ctx.fill(x, y + CORNER_RADIUS, x + w, y + h - CORNER_RADIUS, color);

        for (int[] p : CORNER_POINTS) {
            int dx = p[0];
            int dy = p[1];

            // Верхний левый
            ctx.fill(x + CORNER_RADIUS + dx, y + CORNER_RADIUS + dy, x + CORNER_RADIUS + dx + 1, y + CORNER_RADIUS + dy + 1, color);
            // Верхний правый
            ctx.fill(x + w - CORNER_RADIUS - 1 + dx, y + CORNER_RADIUS + dy, x + w - CORNER_RADIUS + dx, y + CORNER_RADIUS + dy + 1, color);
            // Нижний левый
            ctx.fill(x + CORNER_RADIUS + dx, y + h - CORNER_RADIUS - 1 + dy, x + CORNER_RADIUS + dx + 1, y + h - CORNER_RADIUS + dy, color);
            // Нижний правый
            ctx.fill(x + w - CORNER_RADIUS - 1 + dx, y + h - CORNER_RADIUS - 1 + dy, x + w - CORNER_RADIUS + dx, y + h - CORNER_RADIUS + dy, color);
        }
    }

    /** Рамка со скруглёнными углами: внешний слой цвета border, внутри — заливка bg. */
    private static void drawRoundedBorder(DrawContext ctx, int x, int y, int w, int h, int border, int bg) {
        drawRoundedRect(ctx, x, y, w, h, border);
        drawRoundedRect(ctx, x + 1, y + 1, w - 2, h - 2, bg);
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars - 1) + "…";
    }

    /** Кнопка главного меню; размеры — как в ThunderHack (107x38, выход 222x38). */
    private static class MainMenuButton {
        private final boolean isExit;
        private final String text;
        private final Runnable action;

        MainMenuButton(boolean isExit, String text, Runnable action) {
            this.isExit = isExit;
            this.text = text;
            this.action = action;
        }

        void render(DrawContext context, int mouseX, int mouseY, int x, int y, int w, int h) {
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

            int bg = hovered ? 0xE61A2A55 : 0xE6101830;
            int border = hovered ? 0xFF6A8CFF : (isExit ? 0xFFB03A3A : 0xFF3A5CFF);

            drawRoundedBorder(context, x, y, w, h, border, bg);

            int textX = x + (w - mc.textRenderer.getWidth(text)) / 2;
            int textY = y + (h - mc.textRenderer.fontHeight) / 2 + 1;
            context.drawText(mc.textRenderer, text, textX, textY, 0xFFFFFFFF, false);
        }

        void onClick(int mouseX, int mouseY, int x, int y, int w, int h) {
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                action.run();
            }
        }
    }

    /** Звезда: координаты нормализованы 0..1, дрейф + мерцание + wrap-around. */
    private static class Star {
        float x, y;
        final float size;
        final float speed;
        final float phase;
        final float twinkleSpeed;
        final float twinklePhase;
        final float driftX;
        final float driftY;

        Star(float x, float y, float size, float speed, float phase, Random random) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.speed = speed;
            this.phase = phase;
            this.twinkleSpeed = 0.5f + random.nextFloat() * 2.5f; // Разная скорость мерцания
            this.twinklePhase = random.nextFloat() * (float) Math.PI * 2;

            // Дрейф зависит от размера (большие = ближе = движутся быстрее)
            float depthFactor = size / 2.0f;
            this.driftX = (random.nextFloat() - 0.5f) * 0.0008f * depthFactor;
            this.driftY = (random.nextFloat() - 0.5f) * 0.0008f * depthFactor - 0.0003f; // Лёгкое движение вверх
        }

        void update() {
            x += driftX;
            y += driftY;

            // Wrap around — когда звезда уходит за границу, появляется с другой стороны
            if (x > 1.0f) x -= 1.0f;
            if (x < 0.0f) x += 1.0f;
            if (y > 1.0f) y -= 1.0f;
            if (y < 0.0f) y += 1.0f;
        }
    }
}
