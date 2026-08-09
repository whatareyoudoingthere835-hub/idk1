/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.awt.Color;

public final class Render2DEngine {
    private Render2DEngine() {}

    public static boolean isHovered(double mouseX, double mouseY, double x, double y, double w, double h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public static int applyOpacity(int color, float opacity) {
        if (opacity < 0.0f) opacity = 0.0f;
        if (opacity > 1.0f) opacity = 1.0f;
        int a = (color >> 24) & 0xFF;
        if (a == 0 && color != 0) a = 255;
        int newA = (int) (a * opacity);
        return (color & 0x00FFFFFF) | (newA << 24);
    }

    public static int applyOpacity(Color color, float opacity) {
        if (color == null) return 0xFFFFFFFF;
        if (opacity < 0.0f) opacity = 0.0f;
        if (opacity > 1.0f) opacity = 1.0f;
        int a = (int) (color.getAlpha() * opacity);
        return (color.getRGB() & 0x00FFFFFF) | (a << 24);
    }

    public static void drawRoundedRect(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iw = (int) Math.ceil(w);
        int ih = (int) Math.ceil(h);
        int ir = Math.min((int) Math.round(radius), Math.min(iw / 2, ih / 2));

        if (ir <= 0) {
            context.fill(ix, iy, ix + iw, iy + ih, color);
            return;
        }

        // Верхние скруглённые строки
        for (int dy = 0; dy < ir; dy++) {
            int distY = ir - dy;
            int dx = ir - (int) Math.round(Math.sqrt(Math.max(0, ir * ir - distY * distY)));
            context.fill(ix + dx, iy + dy, ix + iw - dx, iy + dy + 1, color);
        }

        // Центральная часть
        if (ih > ir * 2) {
            context.fill(ix, iy + ir, ix + iw, iy + ih - ir, color);
        }

        // Нижние скруглённые строки
        for (int dy = 0; dy < ir; dy++) {
            int distY = dy + 1;
            int dx = ir - (int) Math.round(Math.sqrt(Math.max(0, ir * ir - (ir - distY) * (ir - distY))));
            context.fill(ix + dx, iy + ih - ir + dy, ix + iw - dx, iy + ih - ir + dy + 1, color);
        }
    }

    public static void drawRoundedBorder(DrawContext context, float x, float y, float w, float h, float radius, int borderColor, int bgColor) {
        drawRoundedRect(context, x, y, w, h, radius, borderColor);
        drawRoundedRect(context, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), bgColor);
    }

    public static void drawHudBase(DrawContext context, float x, float y, float w, float h, float radius) {
        // Мягкая внешняя тень
        drawRoundedRect(context, x - 2, y - 1, w + 4, h + 3, radius + 2, 0x33000000);
        // Внешняя граница
        drawRoundedRect(context, x, y, w, h, radius, 0x80354670);
        // Внутренний тёмный фон
        drawRoundedRect(context, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), 0xCC0C101F);
    }

    public static void drawHudBase(Matrix3x2fStack matrices, float x, float y, float w, float h, float radius) {
        // Overload for compatibility
    }

    public static void drawBloom(DrawContext context, float centerX, float centerY, int color, float radius) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(centerX, centerY);
        drawBloom(context, color, radius);
        context.getMatrices().popMatrix();
    }

    public static void drawBloom(DrawContext context, int color, float radius) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a == 0 && color != 0) a = 255;

        int rad = (int) Math.ceil(radius);
        for (int i = rad; i >= 1; i--) {
            float factor = 1.0f - (float) i / rad;
            int currentAlpha = (int) (a * factor * factor * 0.45f);
            if (currentAlpha <= 0) continue;
            int ringColor = (currentAlpha << 24) | (r << 16) | (g << 8) | b;
            drawRoundedRect(context, -i, -i, i * 2, i * 2, i, ringColor);
        }
        // Яркий центр
        int centerColor = (a << 24) | (r << 16) | (g << 8) | b;
        drawRoundedRect(context, -2, -2, 4, 4, 2, centerColor);
    }

    public static void drawBloom(Matrix3x2fStack matrices, int color, float radius) {
        // Overload for compatibility
    }
}
