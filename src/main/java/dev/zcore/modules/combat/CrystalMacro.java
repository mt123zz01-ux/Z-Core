package dev.zcore.modules.combat;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Random;

/** Ported from Radium: hold use/right-click to place and break crystals. */
public class CrystalMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay after placing a crystal, in ticks.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> minCps = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-cps")
        .description("Minimum break CPS.")
        .defaultValue(8.0)
        .min(1.0)
        .max(20.0)
        .sliderMax(20.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Double> maxCps = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-cps")
        .description("Maximum break CPS.")
        .defaultValue(12.0)
        .min(1.0)
        .max(20.0)
        .sliderMax(20.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Boolean> placeObsidian = sgGeneral.add(new BoolSetting.Builder()
        .name("place-obsidian")
        .description("Places obsidian first, then switches back to crystals.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> obsidianSwitchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("obsidian-switch-delay")
        .description("Delay after switching to obsidian, in ticks.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .visible(placeObsidian::get)
        .build()
    );

    private final Setting<Double> obsidianPlaceCooldown = sgGeneral.add(new DoubleSetting.Builder()
        .name("obsidian-place-cooldown")
        .description("Cooldown after an obsidian placement cycle, in seconds.")
        .defaultValue(1.0)
        .min(0.0)
        .max(10.0)
        .sliderMax(10.0)
        .decimalPlaces(1)
        .visible(placeObsidian::get)
        .build()
    );

    private final Random random = new Random();
    private int placeDelayCounter;
    private int breakDelayCounter;
    private int obsidianSwitchDelayCounter;
    private int obsidianPlaceCooldownCounter;
    private boolean placingObsidian;
    private BlockHitResult pendingObsidianPlacement;
    private int nextBreakDelay;
    private boolean wasUseKeyPressed;

    public CrystalMacro() {
        super(ZCoreCat.COMBAT, "crystal-macro", "Hold use to place and break end crystals.");
    }

    @Override
    public void onActivate() {
        resetState();
        calculateNextBreakDelay();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;

        updateCounters();
        if (mc.player.isUsingItem()) return;

        boolean usePressed = mc.options.useKey.isPressed();
        if (!usePressed) {
            wasUseKeyPressed = false;
            obsidianPlaceCooldownCounter = 0;
            return;
        }

        if (!wasUseKeyPressed) {
            wasUseKeyPressed = true;
            obsidianPlaceCooldownCounter = 0;
        }

        if (placingObsidian && obsidianSwitchDelayCounter <= 0 && pendingObsidianPlacement != null) {
            finishObsidianPlacement();
            return;
        }

        if (!mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return;
        handleInteraction();
    }

    private void resetState() {
        placeDelayCounter = 0;
        breakDelayCounter = 0;
        obsidianSwitchDelayCounter = 0;
        obsidianPlaceCooldownCounter = 0;
        placingObsidian = false;
        pendingObsidianPlacement = null;
        wasUseKeyPressed = false;
    }

    private void updateCounters() {
        if (placeDelayCounter > 0) placeDelayCounter--;
        if (breakDelayCounter > 0) breakDelayCounter--;
        if (obsidianSwitchDelayCounter > 0) obsidianSwitchDelayCounter--;
        if (obsidianPlaceCooldownCounter > 0) obsidianPlaceCooldownCounter--;
    }

    private void calculateNextBreakDelay() {
        double min = minCps.get();
        double max = maxCps.get();
        if (min > max) {
            double temp = min;
            min = max;
            max = temp;
        }

        double cps = min + (max - min) * random.nextDouble();
        nextBreakDelay = Math.max(1, (int) (20.0 / cps));
    }

    private void handleInteraction() {
        HitResult target = mc.crosshairTarget;
        if (target instanceof BlockHitResult blockHit) {
            handleBlockInteraction(blockHit);
        } else if (target instanceof EntityHitResult entityHit) {
            handleEntityInteraction(entityHit);
        }
    }

    private void handleBlockInteraction(BlockHitResult blockHit) {
        if (blockHit.getType() != HitResult.Type.BLOCK) return;
        if (placeDelayCounter > 0 || placingObsidian) return;

        BlockPos blockPos = blockHit.getBlockPos();

        if (placeObsidian.get()
            && obsidianPlaceCooldownCounter <= 0
            && !isBlockAt(blockPos, Blocks.OBSIDIAN)
            && !isBlockAt(blockPos, Blocks.BEDROCK)) {
            startObsidianPlacement(blockHit);
            return;
        }

        if ((isBlockAt(blockPos, Blocks.OBSIDIAN) || isBlockAt(blockPos, Blocks.BEDROCK))
            && isValidCrystalPlacement(blockPos)) {
            BlockPos crystalPos = blockPos.up();
            if (hasLootNearby(crystalPos)) return;

            interactWithBlock(blockHit, true);
            placeDelayCounter = placeDelay.get();
        }
    }

    private boolean startObsidianPlacement(BlockHitResult blockHit) {
        int obsidianSlot = findHotbarSlot(Items.OBSIDIAN);
        if (obsidianSlot == -1) return false;

        InvUtils.swap(obsidianSlot, false);
        placingObsidian = true;
        pendingObsidianPlacement = blockHit;
        obsidianSwitchDelayCounter = obsidianSwitchDelay.get();
        return true;
    }

    private void finishObsidianPlacement() {
        if (pendingObsidianPlacement == null) {
            placingObsidian = false;
            return;
        }

        interactWithBlock(pendingObsidianPlacement, true);

        int crystalSlot = findHotbarSlot(Items.END_CRYSTAL);
        if (crystalSlot != -1) InvUtils.swap(crystalSlot, false);

        BlockPos blockPos = pendingObsidianPlacement.getBlockPos();
        if (isValidCrystalPlacement(blockPos)) {
            interactWithBlock(pendingObsidianPlacement, true);
            placeDelayCounter = placeDelay.get();
        }

        obsidianPlaceCooldownCounter = (int) (obsidianPlaceCooldown.get() * 20.0);
        placingObsidian = false;
        pendingObsidianPlacement = null;
    }

    private void handleEntityInteraction(EntityHitResult entityHit) {
        if (breakDelayCounter > 0 || placingObsidian) return;

        Entity entity = entityHit.getEntity();
        if (entity == null || entity.isRemoved() || !entity.isAlive()) return;
        if (!(entity instanceof EndCrystalEntity) && !(entity instanceof SlimeEntity)) return;
        if (hasLootNearby(entity.getBlockPos())) return;

        try {
            mc.interactionManager.attackEntity(mc.player, entity);
            mc.player.swingHand(Hand.MAIN_HAND);
            breakDelayCounter = nextBreakDelay;
            calculateNextBreakDelay();
        } catch (Exception ignored) {
        }
    }

    private boolean isValidCrystalPlacement(BlockPos blockPos) {
        if (mc.world == null) return false;

        BlockPos up = blockPos.up();
        if (!mc.world.isAir(up)) return false;

        return mc.world.getOtherEntities(null, new Box(
            up.getX(), up.getY(), up.getZ(),
            up.getX() + 1.0, up.getY() + 2.0, up.getZ() + 1.0
        )).isEmpty();
    }

    private boolean hasLootNearby(BlockPos pos) {
        if (mc.world == null || pos == null) return false;

        double radius = 10.0;
        Box box = new Box(
            pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
            pos.getX() + radius, pos.getY() + radius, pos.getZ() + radius
        );

        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) continue;

            if (isArmorStack(stack)) return true;
            if (isSwordStack(stack)) return true;
            if (stack.isOf(Items.ELYTRA)) return true;
        }

        return false;
    }

    private boolean isArmorStack(ItemStack stack) {
        return stack.isOf(Items.LEATHER_HELMET) || stack.isOf(Items.LEATHER_CHESTPLATE)
            || stack.isOf(Items.LEATHER_LEGGINGS) || stack.isOf(Items.LEATHER_BOOTS)
            || stack.isOf(Items.CHAINMAIL_HELMET) || stack.isOf(Items.CHAINMAIL_CHESTPLATE)
            || stack.isOf(Items.CHAINMAIL_LEGGINGS) || stack.isOf(Items.CHAINMAIL_BOOTS)
            || stack.isOf(Items.IRON_HELMET) || stack.isOf(Items.IRON_CHESTPLATE)
            || stack.isOf(Items.IRON_LEGGINGS) || stack.isOf(Items.IRON_BOOTS)
            || stack.isOf(Items.GOLDEN_HELMET) || stack.isOf(Items.GOLDEN_CHESTPLATE)
            || stack.isOf(Items.GOLDEN_LEGGINGS) || stack.isOf(Items.GOLDEN_BOOTS)
            || stack.isOf(Items.DIAMOND_HELMET) || stack.isOf(Items.DIAMOND_CHESTPLATE)
            || stack.isOf(Items.DIAMOND_LEGGINGS) || stack.isOf(Items.DIAMOND_BOOTS)
            || stack.isOf(Items.NETHERITE_HELMET) || stack.isOf(Items.NETHERITE_CHESTPLATE)
            || stack.isOf(Items.NETHERITE_LEGGINGS) || stack.isOf(Items.NETHERITE_BOOTS);
    }

    private boolean isSwordStack(ItemStack stack) {
        return stack.isOf(Items.WOODEN_SWORD) || stack.isOf(Items.STONE_SWORD)
            || stack.isOf(Items.IRON_SWORD) || stack.isOf(Items.GOLDEN_SWORD)
            || stack.isOf(Items.DIAMOND_SWORD) || stack.isOf(Items.NETHERITE_SWORD)
            || stack.isOf(Items.MACE);
    }

    private boolean isBlockAt(BlockPos pos, Block block) {
        return mc.world != null && mc.world.getBlockState(pos).isOf(block);
    }

    private int findHotbarSlot(net.minecraft.item.Item item) {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }

        return -1;
    }

    private void interactWithBlock(BlockHitResult blockHit, boolean swingHand) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit).isAccepted() && swingHand) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
