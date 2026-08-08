/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.GithubRepo;
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
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Кастомное главное меню — порт MainMenuScreen (ThunderHack) под Meteor.
 *
 * Слева — всегда видимый changelog (последние коммиты с GitHub, без кнопок),
 * по центру — логотип и кнопки, выложенные в одну линию.
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

    // Changelog (подгружается с GitHub один раз и просто рисуется слева)
    private final List<ChangelogEntry> changelog = new ArrayList<>();
    private boolean changelogLoading = true;
    private boolean changelogFailed = false;

    // Кнопки (ThunderHack-style: 107x38, зазор 7px)
    private static final int BUTTON_WIDTH = 107;
    private static final int BUTTON_HEIGHT = 38;
    private static final int BUTTON_GAP = 7;

    private static final int MAX_CHANGELOG_COMMITS = 8;

    protected MainMenuScreen() {
        super(Text.of("MainMenu"));

        loadTitle();

        buttons.add(new MainMenuButton(I18n.translate("menu.singleplayer").toUpperCase(Locale.ROOT), () -> mc.setScreen(new SelectWorldScreen(this))));
        buttons.add(new MainMenuButton(I18n.translate("menu.multiplayer").toUpperCase(Locale.ROOT), () -> mc.setScreen(new MultiplayerScreen(this))));
        buttons.add(new MainMenuButton(I18n.translate("menu.options").toUpperCase(Locale.ROOT).replace(".", ""), () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        buttons.add(new MainMenuButton("MODULES", () -> mc.setScreen(GuiThemes.get().modulesScreen())));
        buttons.add(new MainMenuButton(I18n.translate("menu.quit").toUpperCase(Locale.ROOT), mc::scheduleStop, true));

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

    /** Асинхронно тянет последние коммиты с GitHub и складывает в {@link #changelog}. */
    private void loadChangelog() {
        MeteorExecutor.execute(() -> {
            try {
                if (MeteorClient.ADDON == null || MeteorClient.ADDON.getRepo() == null) {
                    changelogFailed = true;
                    return;
                }

                GithubRepo repo = MeteorClient.ADDON.getRepo();
                Http.Request request = Http.get("https://api.github.com/repos/%s/commits?sha=%s&per_page=%d".formatted(repo.getOwnerName(), repo.branch(), MAX_CHANGELOG_COMMITS));
                repo.authenticate(request);
                HttpResponse<Commit[]> res = request.sendJsonResponse(Commit[].class);

                if (res.statusCode() == Http.SUCCESS && res.body() != null) {
                    List<ChangelogEntry> entries = new ArrayList<>();
                    for (Commit commit : res.body()) {
                        if (commit == null || commit.commit == null || commit.commit.committer == null) continue;

                        String message = commit.commit.message.replace('\r', ' ').replace('\n', ' ').trim();
                        if (message.isEmpty()) message = "(no message)";

                        String sha = commit.sha == null ? "" : commit.sha;
                        if (sha.length() > 7) sha = sha.substring(0, 7);

                        String date = "";
                        try {
                            date = DateTimeFormatter.ofPattern("dd.MM.yyyy").format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(commit.commit.committer.date));
                        } catch (Exception ignored) {
                        }

                        entries.add(new ChangelogEntry(message, sha, date));
                    }

                    changelog.clear();
                    changelog.addAll(entries);
                } else {
                    changelogFailed = true;
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

        // Чанжлог слева — просто текст, ничего открывать не надо
        renderChangelog(context);

        // Кнопки в одну линию (по центру экрана)
        int[] row = buttonRow();
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).render(context, mouseX, mouseY, row[0] + i * (row[2] + BUTTON_GAP), row[1], row[2], row[3]);
        }

        // Логотип (x2 к ванильному шрифту, подсветка при наведении, клик меняет название)
        boolean hoveredLogo = isHovered(mouseX, mouseY, halfOfWidth - 120, halfOfHeight - 140, 240, 44);
        String logo = title;
        int logoW = mc.textRenderer.getWidth(logo);
        float logoScale = 2.0f;
        if (logoW * logoScale > width - 280) {
            logoScale = Math.max(0.8f, (width - 280f) / logoW); // не даём логотипу налезть на чанжлог
        }
        int logoX = (int) (halfOfWidth / logoScale - logoW / 2f);
        int logoY = (int) ((halfOfHeight - 130) / logoScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(logoScale, logoScale);
        context.drawText(mc.textRenderer, logo, logoX, logoY, hoveredLogo ? 0xE6FFFFFF : 0xB4FFFFFF, false);
        context.getMatrices().popMatrix();

        // Подсказка: клик по названию открывает окно смены названия
        String hint = "\u00abclick to rename\u00bb";
        context.drawText(mc.textRenderer, hint,
            halfOfWidth - mc.textRenderer.getWidth(hint) / 2, halfOfHeight - 104,
            hoveredLogo ? 0x99FFFFFF : 0x66FFFFFF, false);

        // Возврат в ванильное меню
        boolean hovered = isHovered(mouseX, mouseY, halfOfWidth - 50, halfOfHeight + 22, 100, 10);
        String back = "<-- Back to default menu";
        context.drawText(mc.textRenderer, back,
            halfOfWidth - mc.textRenderer.getWidth(back) / 2, halfOfHeight + 22,
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

    /** Геометрия ряда кнопок: {startX, y, buttonWidth, buttonHeight}. */
    private int[] buttonRow() {
        int screenWidth = mc.getWindow().getScaledWidth();
        int count = buttons.size();
        int bw = Math.max(1, Math.min(BUTTON_WIDTH, (screenWidth - 40 - (count - 1) * BUTTON_GAP) / count));
        int totalW = bw * count + (count - 1) * BUTTON_GAP;
        int startX = (screenWidth - totalW) / 2;
        int y = mc.getWindow().getScaledHeight() / 2 - 30;
        return new int[] { startX, y, bw, BUTTON_HEIGHT };
    }

    /** Панель changelog слева: заголовок + список последних коммитов. */
    private void renderChangelog(DrawContext context) {
        int panelX = 8;
        int panelY = 8;
        int panelW = 252;
        int pad = 10;

        int entries = changelogLoading ? 1 : (changelogFailed || changelog.isEmpty() ? 2 : Math.min(changelog.size(), MAX_CHANGELOG_COMMITS));
        int panelH = 36 + entries * 18 + 8;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC0A0E1F);
        drawBorder(context, panelX, panelY, panelX + panelW, panelY + panelH, 0xFF3A5CFF);

        int contentX = panelX + pad;
        context.drawText(mc.textRenderer, "Changelog", contentX, panelY + 8, 0xFFE0E7FF, true);
        context.drawText(mc.textRenderer, "Meteor Client", contentX, panelY + 21, 0xFFB0B8D0, true);

        int y = panelY + 36;
        if (changelogLoading) {
            context.drawText(mc.textRenderer, "Loading...", contentX, y, 0xFF8FA3C8, false);
        } else if (changelogFailed || changelog.isEmpty()) {
            context.drawText(mc.textRenderer, "Failed to load", contentX, y, 0xFF8FA3C8, false);
            context.drawText(mc.textRenderer, truncate("https://pastebin.com/v5WcmZNL", 30), contentX, y + 10, 0xFF6A6F8C, false);
        } else {
            for (int i = 0; i < Math.min(changelog.size(), MAX_CHANGELOG_COMMITS); i++) {
                ChangelogEntry entry = changelog.get(i);
                context.drawText(mc.textRenderer, "» " + truncate(entry.message, 33), contentX, y, 0xFFE8EEFF, false);
                String meta = entry.date.isEmpty() ? entry.sha : entry.date + "  " + entry.sha;
                context.drawText(mc.textRenderer, "   " + meta, contentX, y + 9, 0xFF7A8BB0, false);
                y += 18;
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int mouseX = (int) click.x();
            int mouseY = (int) click.y();

            int[] row = buttonRow();
            for (int i = 0; i < buttons.size(); i++) {
                buttons.get(i).onClick(mouseX, mouseY, row[0] + i * (row[2] + BUTTON_GAP), row[1], row[2], row[3]);
            }

            int halfOfWidth = mc.getWindow().getScaledWidth() / 2;
            int halfOfHeight = mc.getWindow().getScaledHeight() / 2;

            // Клик по названию — окно смены названия
            if (isHovered(mouseX, mouseY, halfOfWidth - 120, halfOfHeight - 140, 240, 44)) {
                mc.setScreen(new EditMainMenuTitleScreen(GuiThemes.get()));
            }

            // Возврат в ванильное меню
            if (isHovered(mouseX, mouseY, halfOfWidth - 50, halfOfHeight + 22, 100, 10)) {
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

    private static void drawBorder(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        ctx.fill(x0, y0, x1 + 1, y0 + 1, color);
        ctx.fill(x0, y1, x1 + 1, y1 + 1, color);
        ctx.fill(x0, y0, x0 + 1, y1 + 1, color);
        ctx.fill(x1, y0, x1 + 1, y1 + 1, color);
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars - 1) + "…";
    }

    /** Кнопка главного меню; размеры — как в ThunderHack (107x38), позиция передаётся при рендере. */
    private static class MainMenuButton {
        private final String text;
        private final Runnable action;
        private final boolean danger;

        MainMenuButton(String text, Runnable action) {
            this(text, action, false);
        }

        MainMenuButton(String text, Runnable action, boolean danger) {
            this.text = text;
            this.action = action;
            this.danger = danger;
        }

        void render(DrawContext context, int mouseX, int mouseY, int x, int y, int w, int h) {
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

            int bg = hovered ? 0xE61A2A55 : 0xE6101830;
            int border = hovered ? 0xFF6A8CFF : (danger ? 0xFFB03A3A : 0xFF3A5CFF);

            context.fill(x, y, x + w, y + h, bg);
            drawBorder(context, x, y, x + w, y + h, border);

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

    private record ChangelogEntry(String message, String sha, String date) {}

    private static class Commit {
        public String sha;
        public CommitInner commit;
    }

    private static class CommitInner {
        public Committer committer;
        public String message;
    }

    private static class Committer {
        public String date;
    }
}
