/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

/**
 * Порт модуля AntiAim (ThunderHack) под Meteor Client.
 *
 * Спуфит серверу (и клиенту) ваши ротации: pitch/yaw можно крутить,
 * дёргать, рандомить или фиксировать — пока игрок не атакует/не юзает
 * предмет (AllowInteract).
 *
 * В отличие от оригинала (ModuleManager.rotations.fixRotation):
 * - «фиксация» ротации для сервера делается через штатное событие
 *   SendMovementPacketsEvent.Pre — ровно перед отправкой пакета движения,
 *   т.е. сервер всегда видит именно заспуфенную ротацию.
 * - GCD-фикс сохранён как в оригинале, чтобы ротация не «дёргалась»
 *   на мелких углах.
 */
public class AntiAim extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> pitchMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("pitch-mode")
        .description("Как спуфить pitch.")
        .defaultValue(Mode.None)
        .build()
    );

    private final Setting<Mode> yawMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("yaw-mode")
        .description("Как спуфить yaw.")
        .defaultValue(Mode.None)
        .build()
    );

    private final Setting<Integer> speed = sgGeneral.add(new IntSetting.Builder()
        .name("speed")
        .description("Скорость смены ротации (тики между шагами).")
        .defaultValue(1)
        .min(1)
        .max(45)
        .sliderMax(45)
        .build()
    );

    private final Setting<Integer> yawDelta = sgGeneral.add(new IntSetting.Builder()
        .name("yaw-delta")
        .description("Шаг/амплитуда/значение для yaw.")
        .defaultValue(60)
        .min(-360)
        .max(360)
        .sliderMin(-360)
        .sliderMax(360)
        .build()
    );

    private final Setting<Integer> pitchDelta = sgGeneral.add(new IntSetting.Builder()
        .name("pitch-delta")
        .description("Шаг/амплитуда/значение для pitch.")
        .defaultValue(10)
        .min(-90)
        .max(90)
        .sliderMin(-90)
        .sliderMax(90)
        .build()
    );

    private final Setting<Integer> yawOffset = sgGeneral.add(new IntSetting.Builder()
        .name("yaw-offset")
        .description("Смещение yaw относительно базового направления.")
        .defaultValue(0)
        .min(-180)
        .max(180)
        .sliderMin(-180)
        .sliderMax(180)
        .build()
    );

    private final Setting<Boolean> bodySync = sgGeneral.add(new BoolSetting.Builder()
        .name("body-sync")
        .description("Синхронизировать yaw тела (bodyYaw) с заспуфенным yaw.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowInteract = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-interact")
        .description("Не спуфить ротацию, пока зажаты ЛКМ/ПКМ (атака/использование).")
        .defaultValue(true)
        .build()
    );

    private float rotationYaw, rotationPitch, pitchSinusStep, yawSinusStep;

    public AntiAim() {
        super(Categories.Player, "anti-aim", "Спуфит ваши ротации (порт AntiAim с ThunderHack).");
    }

    @Override
    public void onActivate() {
        rotationYaw = 0;
        rotationPitch = 0;
        pitchSinusStep = 0;
        yawSinusStep = 0;
    }

    // Расчёт целевой ротации — аналог PlayerUpdateEvent (ThunderHack)
    @EventHandler
    private void onCalc(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (pitchMode.get() == Mode.RandomAngle && mc.player.age % speed.get() == 0)
            rotationPitch = (float) (Math.random() * 180 - 90);

        if (yawMode.get() == Mode.RandomAngle && mc.player.age % speed.get() == 0)
            rotationYaw = (float) (Math.random() * 360);

        if (yawMode.get() == Mode.Spin && mc.player.age % speed.get() == 0) {
            rotationYaw += yawDelta.get();
            if (rotationYaw > 360) rotationYaw = 0;
            if (rotationYaw < 0) rotationYaw = 360;
        }

        if (pitchMode.get() == Mode.Spin && mc.player.age % speed.get() == 0) {
            rotationPitch += pitchDelta.get();
            if (rotationPitch > 90) rotationPitch = -90;
            if (rotationPitch < -90) rotationPitch = 90;
        }

        if (pitchMode.get() == Mode.Sinus) {
            pitchSinusStep += speed.get() / 10f;
            rotationPitch = (float) (mc.player.getPitch() + pitchDelta.get() * Math.sin(pitchSinusStep));
            rotationPitch = MathHelper.clamp(rotationPitch, -90, 90);
        }

        if (yawMode.get() == Mode.Sinus) {
            yawSinusStep += speed.get() / 10f;
            rotationYaw = (float) ((mc.player.getYaw() + yawDelta.get() * Math.sin(yawSinusStep)) + yawOffset.get());
        }

        if (pitchMode.get() == Mode.Fixed)
            rotationPitch = pitchDelta.get();

        if (yawMode.get() == Mode.Fixed)
            rotationYaw = yawDelta.get();

        if (pitchMode.get() == Mode.Static) {
            rotationPitch = mc.player.getPitch() + pitchDelta.get();
            rotationPitch = MathHelper.clamp(rotationPitch, -90, 90);
        }

        if (yawMode.get() == Mode.Static)
            rotationYaw = mc.player.getYaw() % 360 + yawDelta.get();

        // Jitter — резкие дёргания туда-сюда
        if (pitchMode.get() == Mode.Jitter) {
            if (mc.player.age % (speed.get() * 2) == 0) {
                rotationPitch = pitchDelta.get() / 2f;
            }

            if (mc.player.age % (speed.get() * 2) == speed.get()) {
                rotationPitch = pitchDelta.get() / -2f;
            }
        }

        if (yawMode.get() == Mode.Jitter) {
            if (mc.player.age % (speed.get() * 2) == 0) {
                rotationYaw = yawDelta.get() / 2f + (float) yawOffset.get() + mc.player.getYaw();
            }

            if (mc.player.age % (speed.get() * 2) == speed.get()) {
                rotationYaw = yawDelta.get() / -2f + (float) yawOffset.get() + mc.player.getYaw();
            }
        }
    }

    // Применение ротации с GCD-фиксом ровно перед отправкой пакета движения —
    // аналог EventSync (ThunderHack) / ModuleManager.rotations.fixRotation.
    @EventHandler
    private void onSync(SendMovementPacketsEvent.Pre event) {
        if (mc.player == null) return;

        if (allowInteract.get() && (mc.options.attackKey.isPressed() || mc.options.useKey.isPressed())) return;

        double gcdFix = Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0) * 1.2;

        if (yawMode.get() != Mode.None) {
            mc.player.setYaw((float) (rotationYaw - (rotationYaw - mc.player.getYaw()) % gcdFix));
            if (bodySync.get()) mc.player.bodyYaw = rotationYaw;
        }

        if (pitchMode.get() != Mode.None) {
            mc.player.setPitch((float) (rotationPitch - (rotationPitch - mc.player.getPitch()) % gcdFix));
        }
    }

    public enum Mode {
        None,
        RandomAngle,
        Spin,
        Sinus,
        Fixed,
        Static,
        Jitter
    }
}
