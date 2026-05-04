package dev.zcore.modules.misc;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SpawnerProtect - Automatically protects spawners when an unknown player is detected.
 *
 * Flow: IDLE → MINING → PICKING_ITEMS → DEPOSITING → DISCONNECTING
 *
 * Requires baritone-api-fabric.jar or a compatible Baritone jar in libs/.
 */
public class SpawnerProtect extends Module {

    // ── State machine ────────────────────────────────────────────
    private enum Step { IDLE, MINING, PICKING_ITEMS, DEPOSITING, DISCONNECTING }

    // ── Setting groups ───────────────────────────────────────────
    private final SettingGroup sgRange  = settings.createGroup("Range");
    private final SettingGroup sgDelay  = settings.createGroup("Delays");
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    // ── Settings ─────────────────────────────────────────────────
    private final Setting<Integer> detectionRange = sgRange.add(new IntSetting.Builder()
        .name("detection-range")
        .description("Detect players within this distance, in blocks.")
        .min(10).max(256).defaultValue(10).sliderMin(10).sliderMax(256)
        .build()
    );

    private final Setting<Integer> spawnerScanRange = sgRange.add(new IntSetting.Builder()
        .name("spawner-scan-range")
        .description("Radius used to scan for nearby spawners.")
        .min(2).max(20).defaultValue(10)
        .build()
    );

    private final Setting<Integer> spawnerMineRange = sgRange.add(new IntSetting.Builder()
        .name("spawner-mine-range")
        .description("Maximum distance for mining a spawner without Baritone movement.")
        .min(1).max(3).defaultValue(1)
        .build()
    );

    private final Setting<Integer> enderChestDelay = sgDelay.add(new IntSetting.Builder()
        .name("ender-chest-delay")
        .description("Delay between storing items into an ender chest, in ticks.")
        .min(2).max(20).defaultValue(10)
        .build()
    );

    private final Setting<Integer> maxMiningTicks = sgDelay.add(new IntSetting.Builder()
        .name("max-mining-ticks")
        .description("Maximum ticks allowed to mine one spawner before treating it as failed.")
        .min(20).max(400).defaultValue(160).sliderMin(20).sliderMax(400)
        .build()
    );

    private final Setting<Integer> pickupWaitTicks = sgDelay.add(new IntSetting.Builder()
        .name("pickup-wait-ticks")
        .description("Ticks to wait for a spawner item to enter inventory before storing.")
        .min(10).max(200).defaultValue(60).sliderMin(10).sliderMax(200)
        .build()
    );

    private final Setting<Boolean> ignoreMeteorFriends = sgSafety.add(new BoolSetting.Builder()
        .name("ignore-meteor-friends")
        .description("Does not trigger or disconnect when the nearby player is a Meteor Friend.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> whitelist = sgSafety.add(new StringListSetting.Builder()
        .name("whitelist")
        .description("Player names to ignore in addition to Meteor Friends.")
        .defaultValue()
        .build()
    );

    // ── Internal state ───────────────────────────────────────────
    private Step             step               = Step.IDLE;
    private List<BlockPos>   targetSpawners     = new ArrayList<>();
    private int              currentSpawnerIndex = 0;
    private List<ItemEntity> targetItems        = new ArrayList<>();
    private int              currentItemIndex   = 0;
    private BlockPos         targetEnderChest   = null;
    private int              enderChestCooldown = 0;
    private boolean          isSneaking         = false;
    private BlockPos         currentMiningTarget = null;
    private int              currentMiningTicks = 0;
    private int              pickupWaitCounter = 0;

    // ── Constructor ──────────────────────────────────────────────
    public SpawnerProtect() {
        super(
            ZCoreCat.UTILITY,
            "spawner-protect",
            "Automatically protects nearby spawners when an unknown player appears."
        );
    }

    // ── Lifecycle ────────────────────────────────────────────────
    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        stopBaritone();
        reset();
    }

    // ── Helpers ──────────────────────────────────────────────────
    private void reset() {
        step = Step.IDLE;
        targetSpawners.clear();
        targetItems.clear();
        currentSpawnerIndex = 0;
        currentItemIndex    = 0;
        targetEnderChest    = null;
        enderChestCooldown  = 0;
        currentMiningTarget = null;
        currentMiningTicks  = 0;
        pickupWaitCounter   = 0;
        releaseSneak();
    }

    private void stopBaritone() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    private void holdSneak() {
        if (!isSneaking && mc.options.sneakKey != null) {
            mc.options.sneakKey.setPressed(true);
            isSneaking = true;
        }
    }

    private void releaseSneak() {
        if (isSneaking && mc.options.sneakKey != null) {
            mc.options.sneakKey.setPressed(false);
            isSneaking = false;
        }
    }

    /** Emergency disconnect; only called once. */
    private void emergencyDisconnect(String reason) {
        if (step == Step.DISCONNECTING) return;
        step = Step.DISCONNECTING;
        info("SpawnerProtect: " + reason + " → disconnecting!");
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal("SpawnerProtect: " + reason));
        }
    }

    /** Scans all spawners in range and sorts them by nearest distance. */
    private List<BlockPos> scanSpawners() {
        List<BlockPos> list   = new ArrayList<>();
        BlockPos       center = mc.player.getBlockPos();
        int            r      = spawnerScanRange.get();

        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER)
                        list.add(pos);
                }

        list.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(Vec3d.ofCenter(p))));
        return list;
    }

    /** Finds the nearest ender chest within 16 blocks. */
    private BlockPos findNearestEnderChest() {
        BlockPos nearest = null;
        double   minDist = Double.MAX_VALUE;
        BlockPos center  = mc.player.getBlockPos();

        for (int x = -16; x <= 16; x++)
            for (int y = -5; y <= 5; y++)
                for (int z = -16; z <= 16; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
                        double d = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
                        if (d < minDist) { minDist = d; nearest = pos; }
                    }
                }
        return nearest;
    }

    /** Checks whether the player still has a spawner in inventory. */
    private boolean hasSpawnerInInventory() {
        for (int i = 0; i < mc.player.getInventory().size(); i++)
            if (mc.player.getInventory().getStack(i).getItem() == Items.SPAWNER) return true;
        return false;
    }

    /** Ignores self, Meteor Friends, and user-defined whitelist entries. */
    private boolean shouldIgnorePlayer(PlayerEntity player) {
        if (player == mc.player) return true;

        if (ignoreMeteorFriends.get() && Friends.get().isFriend(player)) return true;

        String playerName = player.getName().getString();
        for (String name : whitelist.get()) {
            if (name != null && playerName.equalsIgnoreCase(name.trim())) return true;
        }

        return false;
    }

    /** Rescans dropped spawner items near the player. */
    private void refreshTargetItems() {
        targetItems.clear();
        mc.world.getEntitiesByClass(
            ItemEntity.class,
            new Box(mc.player.getBlockPos()).expand(spawnerScanRange.get()),
            e -> !e.isRemoved() && e.getStack().getItem() == Items.SPAWNER
        ).forEach(targetItems::add);
        targetItems.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)));
        currentItemIndex = 0;
    }

    // ── Tick event ───────────────────────────────────────────────
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Detect player -> trigger MINING if IDLE, disconnect if already processing
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (shouldIgnorePlayer(p)) continue;
            if (mc.player.distanceTo(p) <= detectionRange.get()) {
                if (step == Step.IDLE) {
                    info("SpawnerProtect: Detected " + p.getName().getString() + " -> starting spawner mining!");
                    targetSpawners = scanSpawners();
                    currentSpawnerIndex = 0;
                    if (targetSpawners.isEmpty()) {
                        emergencyDisconnect("No nearby spawners found");
                    } else {
                        step = Step.MINING;
                    }
                } else if (step != Step.DISCONNECTING) {
                    emergencyDisconnect("Detected " + p.getName().getString() + " while already processing");
                }
                return;
            }
        }
        if (step == Step.DISCONNECTING) return;

        // ── State machine ─────────────────────────────────────────
        switch (step) {

            case IDLE -> {
                // Normally does nothing; player detection above will trigger
                // (current flow: only mine spawners when an unknown player is detected)
            }

            case MINING -> {
                if (currentSpawnerIndex >= targetSpawners.size()) {
                    // All spawners done -> collect dropped items
                    stopBaritone();
                    releaseSneak();
                    refreshTargetItems();
                    pickupWaitCounter = 0;
                    step = Step.PICKING_ITEMS;
                    break;
                }

                BlockPos spawner = targetSpawners.get(currentSpawnerIndex);

                // Only move to the next spawner after the current block is actually broken.
                if (mc.world.getBlockState(spawner).getBlock() != Blocks.SPAWNER) {
                    currentSpawnerIndex++;
                    currentMiningTarget = null;
                    currentMiningTicks = 0;
                    break;
                }

                double distSq = mc.player.squaredDistanceTo(
                    spawner.getX() + 0.5, spawner.getY() + 0.5, spawner.getZ() + 0.5);
                int mineR = spawnerMineRange.get();

                if (distSq > (double) mineR * mineR) {
                    // Too far -> use Baritone to move there. Do not advance the index until the block is broken.
                    releaseSneak();
                    BaritoneAPI.getProvider().getPrimaryBaritone()
                        .getCustomGoalProcess().setGoalAndPath(new GoalBlock(spawner));
                    currentMiningTarget = null;
                    currentMiningTicks = 0;
                } else {
                    // Close enough -> call breakBlock every tick until the block actually disappears.
                    stopBaritone();
                    holdSneak();

                    if (!spawner.equals(currentMiningTarget)) {
                        currentMiningTarget = spawner;
                        currentMiningTicks = 0;
                    }

                    BlockUtils.breakBlock(spawner, true);
                    currentMiningTicks++;

                    if (currentMiningTicks > maxMiningTicks.get()) {
                        emergencyDisconnect("Spawner mining took too long; tool may be missing or the server may be blocking it");
                    }
                }
            }

            case PICKING_ITEMS -> {
                if (hasSpawnerInInventory()) {
                    // Only switch to storing after confirming the spawner is in inventory.
                    stopBaritone();
                    releaseSneak();
                    targetEnderChest = findNearestEnderChest();
                    if (targetEnderChest == null) {
                        error("No nearby ender chest found!");
                        emergencyDisconnect("No ender chest");
                    } else {
                        step = Step.DEPOSITING;
                    }
                    break;
                }

                if (currentItemIndex >= targetItems.size()) {
                    // If no spawner is in inventory yet, rescan dropped items and wait a short time.
                    refreshTargetItems();
                    pickupWaitCounter++;

                    if (targetItems.isEmpty() && pickupWaitCounter > pickupWaitTicks.get()) {
                        emergencyDisconnect("Could not confirm a spawner in inventory");
                    }
                    break;
                }

                ItemEntity item = targetItems.get(currentItemIndex);
                if (item.isRemoved()) { currentItemIndex++; break; }

                Vec3d pos = new Vec3d(item.getX(), item.getY(), item.getZ());
                if (mc.player.squaredDistanceTo(pos) > 2.5 * 2.5) {
                    releaseSneak();
                    BaritoneAPI.getProvider().getPrimaryBaritone()
                        .getCustomGoalProcess().setGoalAndPath(
                            new GoalBlock(BlockPos.ofFloored(pos)));
                } else {
                    currentItemIndex++;   // item should be picked up when standing nearby; still wait for hasSpawnerInInventory().
                }
            }

            case DEPOSITING -> {
                if (targetEnderChest == null) {
                    emergencyDisconnect("Lost ender chest position");
                    break;
                }

                double distSq = mc.player.squaredDistanceTo(
                    targetEnderChest.getX() + 0.5,
                    targetEnderChest.getY() + 0.5,
                    targetEnderChest.getZ() + 0.5);

                if (distSq > 3.5 * 3.5) {
                    stopBaritone();
                    BaritoneAPI.getProvider().getPrimaryBaritone()
                        .getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetEnderChest));
                } else {
                    stopBaritone();
                    BlockHitResult hitResult = new BlockHitResult(
                        Vec3d.ofCenter(targetEnderChest),
                        Direction.UP,
                        targetEnderChest,
                        false
                    );
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

                    // If the ender chest UI is open -> store the spawner
                    if (mc.currentScreen instanceof GenericContainerScreen screen) {
                        if (enderChestCooldown <= 0) {
                            FindItemResult result = InvUtils.find(Items.SPAWNER);
                            if (result.found()) {
                                mc.interactionManager.clickSlot(
                                    screen.getScreenHandler().syncId,
                                    result.slot(),
                                    0,
                                    net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,
                                    mc.player
                                );
                                enderChestCooldown = enderChestDelay.get();
                            }
                        } else {
                            enderChestCooldown--;
                        }
                    }

                    // No spawner remains in inventory -> done
                    if (!hasSpawnerInInventory()) {
                        emergencyDisconnect("Spawner protection completed");
                    }
                }
            }

            case DISCONNECTING -> {
                if (mc.getNetworkHandler() != null
                        && mc.getNetworkHandler().getConnection().isOpen()) {
                    mc.getNetworkHandler().getConnection()
                        .disconnect(Text.literal("SpawnerProtect: Completed."));
                }
                reset();
            }
        }
    }
}
