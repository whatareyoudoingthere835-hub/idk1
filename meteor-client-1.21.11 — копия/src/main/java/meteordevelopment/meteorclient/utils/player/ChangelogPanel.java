/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.player;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.CommitsScreen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Левая панель «Changelog» на главном меню.
 * - Кнопка открывает встроенный {@link CommitsScreen} для основного аддона Meteor.
 * - Ниже — кликабельная ссылка на Pastebin с ченжлогом.
 */
public final class ChangelogPanel {
    private static final int MARGIN = 8;
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_PADDING = 10;
    private static final int PANEL_BG = 0xCC0A0E1F;   // полупрозрачный тёмно-синий
    private static final int PANEL_BORDER = 0xFF3A5CFF; // бирюзово-синий
    private static final int TITLE_COLOR = 0xFFE0E7FF;

    private static final String PASTEBIN_URL = "https://pastebin.com/v5WcmZNL";

    // Кэшированные hitbox'ы для текущего кадра.
    private static int openChangelogX, openChangelogY, openChangelogW, openChangelogH;
    private static int openLinkX, openLinkY, openLinkW, openLinkH;

    private ChangelogPanel() {}

    public static void render(DrawContext context, int screenWidth, int screenHeight) {
        int panelX = MARGIN;
        int panelY = MARGIN;
        int contentX = panelX + PANEL_PADDING;

        // --- Сначала вычислим layout (позиции), ничего не рисуя ---
        int contentY = panelY + PANEL_PADDING;
        int titleBottom = contentY + mc.textRenderer.fontHeight;
        int subtitleBottom = titleBottom + 2 + mc.textRenderer.fontHeight;
        int buttonY = subtitleBottom + 8;
        int buttonW = PANEL_WIDTH - PANEL_PADDING * 2;
        int buttonH = 18;
        int linkY = buttonY + buttonH + 10;
        Text linkLabel = Text.literal("Pastebin »").formatted(Formatting.AQUA, Formatting.UNDERLINE);
        int linkH = mc.textRenderer.fontHeight;
        int contentBottom = linkY + linkH + PANEL_PADDING + mc.textRenderer.fontHeight + 6; // +hint
        int panelH = contentBottom - panelY;

        // --- Фон панели (под всем содержимым) ---
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, PANEL_BG);
        smoothCorners(context, panelX, panelY, panelX + PANEL_WIDTH - 1, panelY + panelH - 1);
        drawBorder(context, panelX, panelY, panelX + PANEL_WIDTH - 1, panelY + panelH - 1, PANEL_BORDER);

        // --- Контент ---
        // Заголовок "Changelog"
        Text title = Text.literal("Changelog").formatted(Formatting.BOLD).styled(s -> s.withColor(TITLE_COLOR));
        context.drawTextWithShadow(mc.textRenderer, title, contentX, contentY, TITLE_COLOR);

        // Подзаголовок (аддон)
        Text subtitle = Text.literal("Meteor Client").formatted(Formatting.GRAY);
        context.drawTextWithShadow(mc.textRenderer, subtitle, contentX, titleBottom + 2, 0xFFB0B8D0);

        // Кнопка "Открыть ченжлог" — открывает CommitsScreen
        openChangelogX = contentX;
        openChangelogY = buttonY;
        openChangelogW = buttonW;
        openChangelogH = buttonH;
        boolean hoverOpen = isMouseOver(openChangelogX, openChangelogY, openChangelogW, openChangelogH);
        int btnBg = hoverOpen ? 0xE61A2A55 : 0xE6101830;
        context.fill(openChangelogX, openChangelogY,
            openChangelogX + openChangelogW, openChangelogY + openChangelogH, btnBg);
        drawBorder(context, openChangelogX, openChangelogY,
            openChangelogX + openChangelogW - 1, openChangelogY + openChangelogH - 1,
            hoverOpen ? 0xFF6A8CFF : PANEL_BORDER);

        MutableText openLabel = Text.literal("Открыть ченжлог").formatted(Formatting.WHITE);
        int labelW = mc.textRenderer.getWidth(openLabel);
        context.drawText(mc.textRenderer, openLabel,
            openChangelogX + (openChangelogW - labelW) / 2,
            openChangelogY + (openChangelogH - mc.textRenderer.fontHeight) / 2 + 1,
            0xFFFFFFFF, false);

        // Ссылка на Pastebin
        openLinkX = contentX;
        openLinkY = linkY;
        openLinkW = mc.textRenderer.getWidth(linkLabel);
        openLinkH = linkH;
        boolean hoverLink = isMouseOver(openLinkX, openLinkY - 1, openLinkW, openLinkH + 2);
        context.drawTextWithShadow(mc.textRenderer, linkLabel, openLinkX, openLinkY,
            hoverLink ? 0xFF8FE6FF : 0xFF55D0FF);

        // Подсказка версии внизу панели
        Text hint = Text.literal("build " + (MeteorClient.ADDON != null && MeteorClient.ADDON.getCommit() != null
            ? MeteorClient.ADDON.getCommit().substring(0, Math.min(7, MeteorClient.ADDON.getCommit().length()))
            : "—")).formatted(Formatting.DARK_GRAY);
        context.drawTextWithShadow(mc.textRenderer, hint,
            contentX, panelY + panelH - mc.textRenderer.fontHeight - 6, 0xFF6A6F8C);
    }

    public static boolean onClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        if (isMouseOver(openChangelogX, openChangelogY, openChangelogW, openChangelogH)) {
            if (MeteorClient.ADDON != null
                && MeteorClient.ADDON.getRepo() != null
                && MeteorClient.ADDON.getCommit() != null) {
                mc.setScreen(new CommitsScreen(GuiThemes.get(), MeteorClient.ADDON));
                return true;
            } else {
                // Если репо/коммит недоступны — открываем Pastebin как запасной вариант.
                Util.getOperatingSystem().open(PASTEBIN_URL);
                return true;
            }
        }

        if (isMouseOver(openLinkX, openLinkY - 1, openLinkW, openLinkH + 2)) {
            Util.getOperatingSystem().open(PASTEBIN_URL);
            return true;
        }

        return false;
    }

    private static boolean isMouseOver(int x, int y, int w, int h) {
        if (mc.currentScreen == null) return false;
        // TitleScreenMixin передаёт координаты в screen-space.
        // Используем последние известные координаты мыши из Mixin-контекста
        // — они хранятся в `mc.mouse`. Однако у нас нет прямого доступа,
        // поэтому запрашиваем через GLFW.
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), mx, my);
        double scale = mc.getWindow().getScaleFactor();
        double mouseX = mx[0] / scale;
        double mouseY = my[0] / scale;
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void drawBorder(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        ctx.fill(x0, y0, x1 + 1, y0 + 1, color);
        ctx.fill(x0, y1, x1 + 1, y1 + 1, color);
        ctx.fill(x0, y0, x0 + 1, y1 + 1, color);
        ctx.fill(x1, y0, x1 + 1, y1 + 1, color);
    }

    /** Заглушка для совместимости по углам (без шейдеров просто не рисуем). */
    private static void smoothCorners(DrawContext ctx, int x0, int y0, int x1, int y1) {
        // no-op
    }
}
