package dev.zcore.modules.misc;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.stream.StreamSupport;

/** Ported from Radium: automates Donut spawner item dropping GUI slots. */
public class SpawnerDropper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between GUI actions, in ticks.")
        .defaultValue(10)
        .min(1)
        .max(40)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> boneOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("bone-only")
        .description("Uses the Radium bone-only drop flow.")
        .defaultValue(false)
        .build()
    );

    private State currentState = State.IDLE;
    private BlockPos spawnerPos;
    private int waitTicks;

    public SpawnerDropper() {
        super(ZCoreCat.UTILITY, "spawner-dropper", "Automates dropping items from nearby spawner GUIs.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        currentState = State.FINDING_SPAWNER;
        spawnerPos = null;
        waitTicks = 0;
    }

    @Override
    public void onDeactivate() {
        currentState = State.IDLE;
        spawnerPos = null;
        waitTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            toggle();
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (currentState) {
            case FINDING_SPAWNER -> findSpawner();
            case OPENING_SPAWNER -> openSpawner();
            case WAITING_FOR_GUI -> waitForGui();
            case CLICKING_SLOT_46 -> clickSlot46();
            case WAITING_DELAY -> waitDelay();
            case CLICKING_SLOT_50 -> clickSlot50();
            case CHECKING_SLOT_50 -> checkSlot50();
            case CHECKING_SLOTS_FOR_ARROWS -> checkSlotsForArrows();
            case CLICKING_DROP_ALL -> clickDropAll();
            case CLICKING_NEXT_PAGE -> clickNextPage();
            case RE_CHECKING_SLOTS -> reCheckSlotsForArrows();
            case IDLE -> {
            }
        }
    }

    private void findSpawner() {
        spawnerPos = StreamSupport.stream(
                BlockPos.iterate(mc.player.getBlockPos().add(-8, -8, -8), mc.player.getBlockPos().add(8, 8, 8)).spliterator(),
                false
            )
            .filter(pos -> mc.world.getBlockState(pos).isOf(Blocks.SPAWNER))
            .min(Comparator.comparingDouble(this::squaredDistanceToPlayer))
            .map(BlockPos::toImmutable)
            .orElse(null);

        if (spawnerPos != null) {
            currentState = State.OPENING_SPAWNER;
        } else {
            toggle();
        }
    }

    private double squaredDistanceToPlayer(BlockPos pos) {
        if (mc.player == null) return Double.MAX_VALUE;
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = mc.player.getX() - center.x;
        double dy = mc.player.getY() - center.y;
        double dz = mc.player.getZ() - center.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void openSpawner() {
        if (spawnerPos == null) {
            currentState = State.FINDING_SPAWNER;
            return;
        }

        mc.interactionManager.interactBlock(
            mc.player,
            Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(spawnerPos), Direction.UP, spawnerPos, false)
        );

        currentState = State.WAITING_FOR_GUI;
        waitTicks = 600;
    }

    private void waitForGui() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            if (boneOnly.get()) {
                currentState = State.CHECKING_SLOTS_FOR_ARROWS;
            } else {
                currentState = State.CLICKING_SLOT_46;
            }
            waitTicks = 2;
        } else if (waitTicks <= 1) {
            toggle();
        }
    }

    private void clickSlot46() {
        if (!isContainerOpen()) {
            toggle();
            return;
        }

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 46, 0, SlotActionType.PICKUP, mc.player);
        currentState = State.WAITING_DELAY;
    }

    private void waitDelay() {
        waitTicks = delay.get();
        currentState = State.CLICKING_SLOT_50;
    }

    private void clickSlot50() {
        if (!isContainerOpen()) {
            toggle();
            return;
        }

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 50, 0, SlotActionType.PICKUP, mc.player);
        currentState = State.CHECKING_SLOT_50;
        waitTicks = 2;
    }

    private void checkSlot50() {
        if (!isContainerOpen()) {
            toggle();
            return;
        }

        if (!slotIsArrow(50)) {
            mc.player.closeHandledScreen();
            toggle();
        } else {
            currentState = State.CLICKING_SLOT_46;
        }
    }

    private void checkSlotsForArrows() {
        if (!isContainerOpen()) {
            toggle();
            return;
        }

        for (int slot = 0; slot <= 44; slot++) {
            if (slotIsArrow(slot)) {
                mc.player.closeHandledScreen();
                toggle();
                return;
            }
        }

        currentState = State.CLICKING_DROP_ALL;
        waitTicks = 2;
    }

    private void clickDropAll() {
        if (!isContainerOpen()) {
            toggle();
            return;
        }

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 50, 0, SlotActionType.PICKUP, mc.player);
        currentState = State.CLICKING_NEXT_PAGE;
        waitTicks = 2;
    }

    private void clickNextPage() {
        if (!isContainerOpen()) {
            toggle();
            return;
        }

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 53, 0, SlotActionType.PICKUP, mc.player);
        currentState = State.RE_CHECKING_SLOTS;
        waitTicks = 2;
    }

    private void reCheckSlotsForArrows() {
        if (!isContainerOpen()) {
            toggle();
            return;
        }

        for (int slot = 0; slot <= 44; slot++) {
            if (slotIsArrow(slot)) {
                mc.player.closeHandledScreen();
                toggle();
                return;
            }
        }

        currentState = State.CLICKING_DROP_ALL;
        waitTicks = 2;
    }

    private boolean isContainerOpen() {
        return mc.currentScreen instanceof GenericContainerScreen && mc.player != null && mc.player.currentScreenHandler != null;
    }

    private boolean slotIsArrow(int slot) {
        try {
            return mc.player.currentScreenHandler.getSlot(slot).getStack().isOf(Items.ARROW);
        } catch (Exception ignored) {
            return false;
        }
    }

    private enum State {
        IDLE,
        FINDING_SPAWNER,
        OPENING_SPAWNER,
        WAITING_FOR_GUI,
        CLICKING_SLOT_46,
        WAITING_DELAY,
        CLICKING_SLOT_50,
        CHECKING_SLOT_50,
        CHECKING_SLOTS_FOR_ARROWS,
        CLICKING_DROP_ALL,
        CLICKING_NEXT_PAGE,
        RE_CHECKING_SLOTS
    }
}
