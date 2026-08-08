/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.gui.screens.MainMenuScreen;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.player.ChangelogPanel;
import meteordevelopment.meteorclient.utils.player.TitleScreenCredits;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    public TitleScreenMixin(Text title) {
        super(title);
    }

    /**
     * Подменяем ванильный TitleScreen на кастомный MainMenuScreen при заходе в игру.
     * Флаг {@link MainMenuScreen#confirm} позволяет временно вернуться к ванильному
     * меню (кнопка "Back to default menu" в кастомном меню).
     */
    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        if (!MainMenuScreen.confirm) {
            mc.setScreen(MainMenuScreen.getInstance());
        }
    }

    /**
     * На ванильном TitleScreen (доступен через "Back to default menu")
     * остаются панель ченжлога и титры аддонов.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChangelogPanel.render(context, this.width, this.height);
        if (Config.get().titleScreenCredits.get()) TitleScreenCredits.render(context);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && ChangelogPanel.onClicked(click, doubled)) {
            cir.setReturnValue(true);
            return;
        }

        if (Config.get().titleScreenCredits.get() && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (TitleScreenCredits.onClicked(click.x(), click.y())) cir.setReturnValue(true);
        }
    }
}
