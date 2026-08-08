/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiThemes;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
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
 * В отличие от старого Starfield (который рисовал (ширина*высота)/16 звёзд —
 * порядка 130 тысяч fill на кадр и вызывал лаги), здесь всего 200 мерцающих
 * и дрейфующих звёзд + один fillGradient — рендер практически бесплатный.
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

    // Кнопка ченжлога (левый верхний угол)
    private int changelogX, changelogY, changelogW, changelogH;

    private static final String PASTEBIN_URL = "https://pastebin.com/v5WcmZNL";

    protected MainMenuScreen() {
        super(Text.of("MainMenu"));

        loadTitle();

        buttons.add(new MainMenuButton(-110, -70, I18n.translate("menu.singleplayer").toUpperCase(Locale.ROOT), () -> mc.setScreen(new SelectWorldScreen(this))));
        buttons.add(new MainMenuButton(4, -70, I18n.translate("menu.multiplayer").toUpperCase(Locale.ROOT), () -> mc.setScreen(new MultiplayerScreen(this))));
        buttons.add(new MainMenuButton(-110, -29, I18n.translate("menu.options").toUpperCase(Locale.ROOT).replace(".", ""), () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        buttons.add(new MainMenuButton(4, -29, "MODULES", () -> mc.setScreen(GuiThemes.get().modulesScreen())));
        buttons.add(new MainMenuButton(-110, 12, I18n.translate("menu.quit").toUpperCase(Locale.ROOT), mc::scheduleStop, true));

        generateStars();
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
        int halfOfWidth = mc.getWindow().getScaledWidth() / 2;
        int halfOfHeight = mc.getWindow().getScaledHeight() / 2;

        renderStarrySky(context);

        // Панель по центру
        int panelX = halfOfWidth - 120;
        int panelY = halfOfHeight - 80;
        int panelW = 240;
        int panelH = 140;
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC0A0E1F);
        drawBorder(context, panelX, panelY, panelX + panelW, panelY + panelH, 0xFF3A5CFF);

        // Кнопки
        buttons.forEach(b -> b.render(context, mouseX, mouseY));

        // Логотип (x2 к ванильному шрифту, подсветка при наведении, клик меняет название)
        boolean hoveredLogo = isHovered(mouseX, mouseY, halfOfWidth - 120, halfOfHeight - 130, 210, 50);
        String logo = title;
        int logoW = mc.textRenderer.getWidth(logo);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(2.0f, 2.0f);
        context.drawText(mc.textRenderer, logo,
            (halfOfWidth - logoW) / 2,
            (halfOfHeight - 129) / 2,
            hoveredLogo ? 0xE6FFFFFF : 0xB4FFFFFF, false);
        context.getMatrices().popMatrix();

        // Подсказка: клик по названию открывает окно смены названия
        String hint = "\u00abclick to rename\u00bb";
        context.drawText(mc.textRenderer, hint,
            halfOfWidth - mc.textRenderer.getWidth(hint) / 2, halfOfHeight - 104,
            hoveredLogo ? 0x99FFFFFF : 0x66FFFFFF, false);

        // Возврат в ванильное меню
        boolean hovered = isHovered(mouseX, mouseY, halfOfWidth - 50, halfOfHeight + 70, 100, 10);
        String back = "<-- Back to default menu";
        context.drawText(mc.textRenderer, back,
            halfOfWidth - mc.textRenderer.getWidth(back) / 2, halfOfHeight + 70,
            hovered ? -1 : 0x99FFFFFF, false);

        // Сборка внизу по центру
        String build = "build " + (MeteorClient.ADDON != null && MeteorClient.ADDON.getCommit() != null
            ? MeteorClient.ADDON.getCommit().substring(0, Math.min(7, MeteorClient.ADDON.getCommit().length()))
            : "—");
        context.drawText(mc.textRenderer, build,
            halfOfWidth - mc.textRenderer.getWidth(build) / 2, halfOfHeight * 2 - 15, 0xFFB0B8D0, false);

        // Кнопка ченжлога (слева сверху)
        String changelog = "Changelog »";
        changelogX = 8;
        changelogY = 6;
        changelogW = mc.textRenderer.getWidth(changelog);
        changelogH = mc.textRenderer.fontHeight;
        boolean hoverChangelog = isHovered(mouseX, mouseY, changelogX, changelogY - 1, changelogW, changelogH + 2);
        context.drawText(mc.textRenderer, changelog, changelogX, changelogY,
            hoverChangelog ? 0xFF8FE6FF : 0xFF55D0FF, false);

        // Аддоны (справа сверху)
        int totalAddonsLoaded = AddonManager.ADDONS.size();
        String addonsText = "Addons Loaded: " + totalAddonsLoaded;
        int screenWidth = mc.getWindow().getScaledWidth();
        int textWidth = mc.textRenderer.getWidth(addonsText);
        int textX = screenWidth - textWidth - 5;
        context.drawText(mc.textRenderer, addonsText, textX, 5, 0xFFFFFFFF, false);

        int offset = 0;
        for (MeteorAddon addon : AddonManager.ADDONS) {
            String addonName = addon.name + " |";
            textWidth = mc.textRenderer.getWidth(addonName);
            textX = screenWidth - textWidth - 5;
            context.drawText(mc.textRenderer, addonName, textX, 13 + offset, 0xFFAAAAAA, false);
            offset += 9;
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int mouseX = (int) click.x();
            int mouseY = (int) click.y();

            buttons.forEach(b -> b.onClick(mouseX, mouseY));

            int halfOfWidth = mc.getWindow().getScaledWidth() / 2;
            int halfOfHeight = mc.getWindow().getScaledHeight() / 2;

            // Клик по названию — окно смены названия
            if (isHovered(mouseX, mouseY, halfOfWidth - 120, halfOfHeight - 130, 210, 50)) {
                mc.setScreen(new EditMainMenuTitleScreen(GuiThemes.get()));
            }

            if (isHovered(mouseX, mouseY, halfOfWidth - 50, halfOfHeight + 70, 100, 10)) {
                confirm = true;
                mc.setScreen(new TitleScreen());
                confirm = false;
            }

            if (isHovered(mouseX, mouseY, changelogX, changelogY - 1, changelogW, changelogH + 2)) {
                openChangelog();
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void openChangelog() {
        if (MeteorClient.ADDON != null && MeteorClient.ADDON.getRepo() != null && MeteorClient.ADDON.getCommit() != null) {
            mc.setScreen(new CommitsScreen(GuiThemes.get(), MeteorClient.ADDON));
        } else {
            // Если репо/коммит недоступны — открываем Pastebin как запасной вариант.
            Util.getOperatingSystem().open(PASTEBIN_URL);
        }
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

    /** Кнопка главного меню; координаты — смещения от центра экрана. */
    private static class MainMenuButton {
        private static final int WIDTH = 216;
        private static final int HEIGHT = 35;

        private final int x, y;
        private final String text;
        private final Runnable action;
        private final boolean danger;

        MainMenuButton(int x, int y, String text, Runnable action) {
            this(x, y, text, action, false);
        }

        MainMenuButton(int x, int y, String text, Runnable action, boolean danger) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.action = action;
            this.danger = danger;
        }

        void render(DrawContext context, int mouseX, int mouseY) {
            int bx = mc.getWindow().getScaledWidth() / 2 + x;
            int by = mc.getWindow().getScaledHeight() / 2 + y;
            boolean hovered = mouseX >= bx && mouseX <= bx + WIDTH && mouseY >= by && mouseY <= by + HEIGHT;

            int bg = hovered ? 0xE61A2A55 : 0xE6101830;
            int border = hovered ? 0xFF6A8CFF : (danger ? 0xFFB03A3A : 0xFF3A5CFF);

            context.fill(bx, by, bx + WIDTH, by + HEIGHT, bg);
            drawBorder(context, bx, by, bx + WIDTH, by + HEIGHT, border);

            int textX = bx + (WIDTH - mc.textRenderer.getWidth(text)) / 2;
            int textY = by + (HEIGHT - mc.textRenderer.fontHeight) / 2 + 1;
            context.drawText(mc.textRenderer, text, textX, textY, 0xFFFFFFFF, false);
        }

        void onClick(int mouseX, int mouseY) {
            int bx = mc.getWindow().getScaledWidth() / 2 + x;
            int by = mc.getWindow().getScaledHeight() / 2 + y;
            if (mouseX >= bx && mouseX <= bx + WIDTH && mouseY >= by && mouseY <= by + HEIGHT) {
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
