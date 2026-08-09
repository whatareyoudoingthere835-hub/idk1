/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.utils.render.Render2DEngine;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.NotNull;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MainMenuButton {
    private final float posX, posY, width, height;
    private final String name;
    private final Runnable action;

    public MainMenuButton(float posX, float posY, @NotNull String name, Runnable action, boolean isExit) {
        this.name = name;
        this.posX = posX;
        this.posY = posY;

        this.action = action;

        this.width = isExit ? 222f : 107f;
        this.height = 38f;
    }

    public MainMenuButton(float posX, float posY, @NotNull String name, Runnable action) {
        this(posX, posY, name, action, false);
    }

    public void onRender(DrawContext context, float mouseX, float mouseY) {
        float halfOfWidth = mc.getWindow().getScaledWidth() / 2f;
        float halfOfHeight = mc.getWindow().getScaledHeight() / 2f;
        Render2DEngine.drawHudBase(context, halfOfWidth + posX, halfOfHeight + posY, width, height, 10);
        boolean hovered = Render2DEngine.isHovered(mouseX, mouseY, halfOfWidth + posX, halfOfHeight + posY, width, height);

        int textW = mc.textRenderer.getWidth(name);
        float textX = halfOfWidth + posX + width / 2f - textW / 2f;
        float textY = halfOfHeight + posY + height / 2f - 3f;
        int textColor = hovered ? -1 : Render2DEngine.applyOpacity(-1, 0.7f);

        context.drawTextWithShadow(mc.textRenderer, name, (int) textX, (int) textY, textColor);
    }

    public void onClick(int mouseX, int mouseY) {
        float halfOfWidth = mc.getWindow().getScaledWidth() / 2f;
        float halfOfHeight = mc.getWindow().getScaledHeight() / 2f;
        boolean hovered = Render2DEngine.isHovered(mouseX, mouseY, halfOfWidth + posX, halfOfHeight + posY, width, height);
        if (hovered) action.run();
    }
}
