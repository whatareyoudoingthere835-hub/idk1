/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement.speed.modes;

import meteordevelopment.meteorclient.systems.modules.movement.speed.SpeedMode;
import meteordevelopment.meteorclient.systems.modules.movement.speed.SpeedModes;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

/**
 * Порт режима ElytraSpoof (ThunderHack Speed) под Meteor.
 *
 * Спуфит серверу полёт на элитре, отправляя START_FALL_FLYING через хотбарную
 * элитру каждые 100 мс, и добавляет горизонтальную скорость пока игрок в воздухе.
 */
public class ElytraSpoof extends SpeedMode {
    private long lastSpoof = 0L;
    private boolean wasFlyingSpoof = false;

    public ElytraSpoof() {
        super(SpeedModes.ElytraSpoof);
    }

    @Override
    public void onActivate() {
        wasFlyingSpoof = false;
        lastSpoof = 0L;
    }

    @Override
    public void onDeactivate() {
        stopSpoof();
    }

    @Override
    public void onTick() {
        // Не двигаемся — прекращаем спуф
        if (!PlayerUtils.isMoving()) {
            stopSpoof();
            return;
        }

        // Нужна элитра в хотбаре/руках
        FindItemResult elytra = InvUtils.findInHotbar(Items.ELYTRA);
        if (!elytra.found()) {
            stopSpoof();
            return;
        }

        // Автопрыжок для разгона
        if (settings.autoJump.get() && mc.player.isOnGround()) {
            mc.player.jump();
        }

        // Спуфим полёт на элитре каждые 100 мс
        if (System.currentTimeMillis() - lastSpoof > 100) {
            if (elytra.isHotbar() && !elytra.isOffhand()) {
                int currentSlot = mc.player.getInventory().getSelectedSlot();

                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(elytra.slot()));
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
            } else {
                // Элитра в руке/оффхенде — просто шлём пакет
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }

            wasFlyingSpoof = true;
            lastSpoof = System.currentTimeMillis();
        }

        // Применяем горизонтальную скорость пока в воздухе
        if (mc.player.fallDistance > 0 && !mc.player.isOnGround()) {
            applyForwardSpeed(settings.spoofSpeed.get());
        }
    }

    private void stopSpoof() {
        if (wasFlyingSpoof) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            wasFlyingSpoof = false;
        }
    }

    private void applyForwardSpeed(double speed) {
        float yaw = mc.player.getYaw();
        double motionX = -Math.sin(Math.toRadians(yaw)) * speed;
        double motionZ = Math.cos(Math.toRadians(yaw)) * speed;

        mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
    }
}
