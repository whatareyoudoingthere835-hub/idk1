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
import meteordevelopment.meteorclient.utils.render.Render2DEngine;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MainMenuScreen extends Screen {
    private final List<MainMenuButton> buttons = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final Random random = new Random();
    public static boolean confirm = false;
    public static int ticksActive;

    public static String title = "NIGGAHACK";

    private static final String PASTEBIN_URL = "https://pastebin.com/raw/v5WcmZNL";
    private static final List<String> changeLog = new ArrayList<>(List.of(
        "[+] Added custom main menu",
        "[*] Improved rendering engine",
        "[/] Updated modules & themes",
        "[+] Fixed crash bugs",
        "[*] Performance improvements"
    ));
    private static boolean changelogLoaded = false;

    protected MainMenuScreen() {
        super(Text.of("THMainMenuScreen"));
        INSTANCE = this;

        loadTitle();

        buttons.add(new MainMenuButton(-110, -70, I18n.translate("menu.singleplayer").toUpperCase(Locale.ROOT), () -> mc.setScreen(new SelectWorldScreen(this))));
        buttons.add(new MainMenuButton(4, -70, I18n.translate("menu.multiplayer").toUpperCase(Locale.ROOT), () -> mc.setScreen(new MultiplayerScreen(this))));
        buttons.add(new MainMenuButton(-110, -29, I18n.translate("menu.options")
                .toUpperCase(Locale.ROOT)
                .replace(".", ""), () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        buttons.add(new MainMenuButton(4, -29, "CLICKGUI", () -> mc.setScreen(GuiThemes.get().modulesScreen())));
        buttons.add(new MainMenuButton(-110, 12, I18n.translate("menu.quit").toUpperCase(Locale.ROOT), mc::scheduleStop, true));

        generateStars();
        loadChangelog();
    }

    private static MainMenuScreen INSTANCE = new MainMenuScreen();

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

    public static void setTitle(String newTitle) {
        String t = newTitle == null ? "" : newTitle.trim();
        if (t.isEmpty()) t = "NIGGAHACK";
        title = t;

        try {
            Files.writeString(titleFile(), t);
        } catch (IOException ignored) {
        }
    }

    private void loadChangelog() {
        if (changelogLoaded) return;
        MeteorExecutor.execute(() -> {
            try {
                String raw = Http.get(PASTEBIN_URL).sendString();
                if (raw != null && !raw.isBlank()) {
                    List<String> lines = new ArrayList<>();
                    for (String line : raw.split("\n")) {
                        String l = line.replace("\r", "").stripTrailing();
                        if (!l.isEmpty()) lines.add(l);
                    }
                    if (!lines.isEmpty()) {
                        changeLog.clear();
                        changeLog.addAll(lines);
                        changelogLoaded = true;
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void generateStars() {
        stars.clear();
        int starCount = 200;
        for (int i = 0; i < starCount; i++) {
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

            int color = new Color(255, 255, 255, alpha).getRGB();
            context.fill(sx, sy, sx + size, sy + size, color);

            if (star.size > 1.6f && brightness > 0.75f) {
                int glowColor = new Color(255, 255, 255, alpha / 3).getRGB();
                context.fill(sx - 1, sy - 1, sx + size + 1, sy + size + 1, glowColor);
            }
        }
    }

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
        float halfOfWidth = mc.getWindow().getScaledWidth() / 2f;
        float halfOfHeight = mc.getWindow().getScaledHeight() / 2f;

        float mainX = halfOfWidth - 120f;
        float mainY = halfOfHeight - 80f;
        float mainWidth = 240f;
        float mainHeight = 140;

        renderStarrySky(context);

        Render2DEngine.drawHudBase(context, mainX, mainY, mainWidth, mainHeight, 20);

        buttons.forEach(b -> b.onRender(context, mouseX, mouseY));

        boolean hoveredLogo = Render2DEngine.isHovered(mouseX, mouseY, (int) (halfOfWidth - 120), (int) (halfOfHeight - 130), 240, 50);

        context.getMatrices().pushMatrix();
        float logoScale = 2.0f;
        context.getMatrices().scale(logoScale, logoScale);
        String logoText = title;
        int logoW = mc.textRenderer.getWidth(logoText);
        float drawLogoX = (halfOfWidth / logoScale) - (logoW / 2f);
        float drawLogoY = (halfOfHeight - 122f) / logoScale;
        int logoColor = new Color(255, 255, 255, hoveredLogo ? 230 : 180).getRGB();
        context.drawTextWithShadow(mc.textRenderer, logoText, (int) drawLogoX, (int) drawLogoY, logoColor);
        context.getMatrices().popMatrix();

        boolean hovered = Render2DEngine.isHovered(mouseX, mouseY, halfOfWidth - 50, halfOfHeight + 70, 100, 10);

        int backColor = hovered ? -1 : Render2DEngine.applyOpacity(-1, 0.6f);
        String backText = "<-- Back to default menu";
        int backW = mc.textRenderer.getWidth(backText);
        context.drawTextWithShadow(mc.textRenderer, backText, (int) (halfOfWidth - backW / 2f), (int) (halfOfHeight + 70), backColor);

        onlineText:
        {
            String onlineUsers = String.format("online: %s%s", Formatting.DARK_GREEN, 1);
            int textW = mc.textRenderer.getWidth(onlineUsers);
            int textX = (int) (halfOfWidth - textW / 2f);
            int textY = (int) (halfOfHeight * 2 - 15);

            context.drawTextWithShadow(mc.textRenderer, onlineUsers, textX, textY, Color.GREEN.getRGB());

            float bloomX = halfOfWidth - 10 - textW / 2f;
            float bloomY = halfOfHeight * 2 - 11;

            Render2DEngine.drawBloom(context, bloomX, bloomY, Render2DEngine.applyOpacity(Color.GREEN, 0.6f), 9f);
            Render2DEngine.drawBloom(context, bloomX, bloomY, Render2DEngine.applyOpacity(Color.GREEN, (float) (0.5f + (Math.sin((double) System.currentTimeMillis() / 500)) / 2f)), 9f);
        }

        // Уменьшенный ченджлог: показываем только последние 5 записей с меньшим межстрочным интервалом
        int offsetY = 10;
        int maxLines = Math.min(5, changeLog.size());
        int startIndex = Math.max(0, changeLog.size() - maxLines);

        for (int i = startIndex; i < changeLog.size(); i++) {
            String change = changeLog.get(i);
            String prefix = getPrefix(change);
            context.drawTextWithShadow(mc.textRenderer, prefix, 10, offsetY, Render2DEngine.applyOpacity(-1, 0.4f));
            offsetY += 10; // Удобный межстрочный интервал
        }

        int totalAddonsLoaded = AddonManager.ADDONS.size();
        String addonsText = "Addons Loaded: " + totalAddonsLoaded;
        int screenWidth = mc.getWindow().getScaledWidth();
        int textWidth = (int) mc.textRenderer.getWidth(addonsText);
        int textX = screenWidth - textWidth - 5;
        context.drawTextWithShadow(mc.textRenderer, addonsText, textX, 5, Color.WHITE.getRGB());

        int offset = 0;
        for (MeteorAddon addon : AddonManager.ADDONS) {
            String addonLine = addon.name + Formatting.WHITE + " |";
            textWidth = (int) mc.textRenderer.getWidth(addonLine);
            textX = screenWidth - textWidth - 5;
            context.drawTextWithShadow(mc.textRenderer, addonLine, textX, 13 + offset, Color.GRAY.getRGB());
            offset += 9;
        }
    }

    private static @NotNull String getPrefix(@NotNull String change) {
        String prefix = "";
        if (change.contains("[+]")) {
            change = change.replace("[+] ", "");
            prefix = Formatting.GREEN + "[+] " + Formatting.RESET;
        } else if (change.contains("[-]")) {
            change = change.replace("[-] ", "");
            prefix = Formatting.RED + "[-] " + Formatting.RESET;
        } else if (change.contains("[/]")) {
            change = change.replace("[/] ", "");
            prefix = Formatting.LIGHT_PURPLE + "[/] " + Formatting.RESET;
        } else if (change.contains("[*]")) {
            change = change.replace("[*] ", "");
            prefix = Formatting.GOLD + "[*] " + Formatting.RESET;
        }
        return prefix + change;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float halfOfWidth = mc.getWindow().getScaledWidth() / 2f;
            float halfOfHeight = mc.getWindow().getScaledHeight() / 2f;
            int mouseX = (int) click.x();
            int mouseY = (int) click.y();

            buttons.forEach(b -> b.onClick(mouseX, mouseY));

            if (Render2DEngine.isHovered(mouseX, mouseY, halfOfWidth - 50, halfOfHeight + 70, 100, 10)) {
                confirm = true;
                mc.setScreen(new TitleScreen());
                confirm = false;
                return true;
            }

            if (Render2DEngine.isHovered(mouseX, mouseY, (int) (halfOfWidth - 120), (int) (halfOfHeight - 130), 240, 50)) {
                mc.setScreen(new EditMainMenuTitleScreen(GuiThemes.get()));
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float halfOfWidth = mc.getWindow().getScaledWidth() / 2f;
            float halfOfHeight = mc.getWindow().getScaledHeight() / 2f;

            buttons.forEach(b -> b.onClick((int) mouseX, (int) mouseY));

            if (Render2DEngine.isHovered(mouseX, mouseY, halfOfWidth - 50, halfOfHeight + 70, 100, 10)) {
                confirm = true;
                mc.setScreen(new TitleScreen());
                confirm = false;
                return true;
            }
        }
        return false;
    }

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

            // Wrap around - когда звезда уходит за границу, появляется с другой стороны
            if (x > 1.0f) x -= 1.0f;
            if (x < 0.0f) x += 1.0f;
            if (y > 1.0f) y -= 1.0f;
            if (y < 0.0f) y += 1.0f;
        }
    }
}
