/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Порт модуля RedstoneCrasher (ThunderHack) под Meteor Client.
 *
 * Ищет все позиции вокруг игрока, куда можно поставить редстоун, и
 * массово расставляет редстоун-пыль (до BlocksPerTick за тик).
 *
 * В отличие от оригинала ThunderHack:
 * - Ротация делается через штатную систему Meteor (Rotations/BlockUtils.place),
 *   поэтому не нужен ручной спуфинг yaw/pitch и пакеты PlayerMove/HandSwing.
 * - Silent-переключение предмета делается автоматически через InvUtils.swap.
 */
public class RedstoneCrasher extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Радиус поиска позиций для установки редстоуна.")
        .defaultValue(4)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Сколько блоков редстоуна ставится за один тик.")
        .defaultValue(2)
        .min(1)
        .max(10)
        .sliderMax(10)
        .build()
    );

    private final Setting<Switch> autoSwitch = sgGeneral.add(new EnumSetting.Builder<Switch>()
        .name("auto-switch")
        .description("Как подбирать редстоун в руку.")
        .defaultValue(Switch.Silent)
        .build()
    );

    // --- Рендер ---

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Рисовать подсветку позиций установки.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Как отрисовывать кубы.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Цвет сторон.")
        .defaultValue(new SettingColor(0, 255, 0, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Цвет линий.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final List<BlockPos> targets = new ArrayList<>();
    private int placedThisTick = 0;

    private enum Switch {
        Normal,
        Silent,
        Inventory,
        None
    }

    public RedstoneCrasher() {
        super(Categories.World, "redstone-crasher", "Массово расставляет редстоун вокруг игрока (порт RedstoneCrasher с ThunderHack).");
    }

    @Override
    public void onActivate() {
        targets.clear();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        placedThisTick = 0;

        // Проверяем наличие редстоуна
        FindItemResult redstone = getRedstone();
        if (!redstone.found()) {
            targets.clear();
            return;
        }

        // Обновляем очередь целей
        if (targets.isEmpty()) {
            targets.addAll(getTargets());
            if (targets.isEmpty()) return;
        }

        // Ставим блоки до лимита за тик
        while (!targets.isEmpty() && placedThisTick < blocksPerTick.get()) {
            BlockPos pos = targets.remove(0);
            if (BlockUtils.place(pos, redstone, true, 50, true, true)) {
                placedThisTick++;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : targets) {
            event.renderer.box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    /**
     * Находит позиции вокруг игрока, куда можно поставить блок
     * (позиция заменяема, а блок под ней — твёрдый).
     */
    private List<BlockPos> getTargets() {
        List<BlockPos> positions = new ArrayList<>();
        int r = (int) Math.ceil(range.get());
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) continue;

                    if (mc.world.getBlockState(pos).isReplaceable()
                        && mc.world.getBlockState(pos.down()).isOpaqueFullCube()) {
                        positions.add(pos);
                    }
                }
            }
        }

        positions.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.toCenterPos())));
        return positions;
    }

    /**
     * Ищет редстоун в руках / хотбаре / инвентаре в зависимости от режима {@link #autoSwitch}.
     * Возвращает FindItemResult со слотом 0-8 (хотбар) или OFFHAND.
     */
    private FindItemResult getRedstone() {
        // Сначала руки
        if (mc.player.getOffHandStack().getItem() == Items.REDSTONE) {
            return new FindItemResult(SlotUtils.OFFHAND, mc.player.getOffHandStack().getCount());
        }

        if (mc.player.getMainHandStack().getItem() == Items.REDSTONE) {
            return new FindItemResult(mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandStack().getCount());
        }

        switch (autoSwitch.get()) {
            case Normal, Silent -> {
                return InvUtils.findInHotbar(i -> i.getItem() == Items.REDSTONE);
            }

            case Inventory -> {
                FindItemResult inv = InvUtils.find(i -> i.getItem() == Items.REDSTONE);
                if (!inv.found()) return inv;

                // Если предмет не в хотбаре — перемещаем в выбранный слот
                if (!inv.isHotbar() && inv.isMain()) {
                    InvUtils.move().from(inv.slot()).toHotbar(mc.player.getInventory().getSelectedSlot());
                    return new FindItemResult(mc.player.getInventory().getSelectedSlot(), inv.count());
                }

                return inv;
            }

            case None -> {
                return new FindItemResult(-1, 0);
            }
        }

        return new FindItemResult(-1, 0);
    }
}
