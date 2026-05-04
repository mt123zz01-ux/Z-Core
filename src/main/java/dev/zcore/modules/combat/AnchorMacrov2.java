package dev.zcore.modules.combat;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Ported from Radium: charges and explodes respawn anchors while holding use. */
public final class AnchorMacrov2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> whileUse = sgGeneral.add(new BoolSetting.Builder()
        .name("while-use")
        .description("Allows the macro to run while using items.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> lootProtect = sgGeneral.add(new BoolSetting.Builder()
        .name("loot-protect")
        .description("Stops anchor actions near dead players or valuable dropped loot.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> clickSimulation = sgGeneral.add(new BoolSetting.Builder()
        .name("click-simulation")
        .description("Keeps the original simulated-click toggle. Block interaction is still sent directly.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> switchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("Delay before switching slots, in ticks.")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> switchChance = sgGeneral.add(new DoubleSetting.Builder()
        .name("switch-chance")
        .description("Chance to switch slots after the switch delay.")
        .defaultValue(100.0)
        .min(0.0)
        .max(100.0)
        .sliderMax(100.0)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Double> placeChance = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-chance")
        .description("Chance to charge an uncharged anchor.")
        .defaultValue(100.0)
        .min(0.0)
        .max(100.0)
        .sliderMax(100.0)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Integer> glowstoneDelay = sgGeneral.add(new IntSetting.Builder()
        .name("glowstone-delay")
        .description("Delay before using glowstone, in ticks.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> glowstoneChance = sgGeneral.add(new DoubleSetting.Builder()
        .name("glowstone-chance")
        .description("Chance to use glowstone after the glowstone delay.")
        .defaultValue(100.0)
        .min(0.0)
        .max(100.0)
        .sliderMax(100.0)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Integer> explodeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("explode-delay")
        .description("Delay before exploding a charged anchor, in ticks.")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> explodeChance = sgGeneral.add(new DoubleSetting.Builder()
        .name("explode-chance")
        .description("Chance to explode the anchor after the explode delay.")
        .defaultValue(100.0)
        .min(0.0)
        .max(100.0)
        .sliderMax(100.0)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Integer> explodeSlot = sgGeneral.add(new IntSetting.Builder()
        .name("explode-slot")
        .description("Hotbar slot used to explode charged anchors. 1-9.")
        .defaultValue(9)
        .min(1)
        .max(9)
        .sliderMin(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Boolean> onlyOwn = sgGeneral.add(new BoolSetting.Builder()
        .name("only-own")
        .description("Only explodes anchors placed while this module was active.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyCharge = sgGeneral.add(new BoolSetting.Builder()
        .name("only-charge")
        .description("Only charges anchors and does not explode them.")
        .defaultValue(false)
        .build()
    );

    private final Set<BlockPos> ownedAnchors = new HashSet<>();
    private final Random random = new Random();

    private int switchClock;
    private int glowstoneClock;
    private int explodeClock;

    public AnchorMacrov2() {
        super(ZCoreCat.COMBAT, "anchor-macro-v2", "Automatically charges and explodes respawn anchors.");
    }

    @Override
    public void onActivate() {
        switchClock = 0;
        glowstoneClock = 0;
        explodeClock = 0;
        ownedAnchors.clear();
    }

    @Override
    public void onDeactivate() {
        switchClock = 0;
        glowstoneClock = 0;
        explodeClock = 0;
        ownedAnchors.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;

        if (!whileUse.get() && (mc.player.isUsingItem() || isTool(mc.player.getOffHandStack()))) return;
        if (lootProtect.get() && (isDeadBodyNearby() || isValuableLootNearby())) return;
        if (!mc.options.useKey.isPressed()) return;

        HitResult target = mc.crosshairTarget;
        if (!(target instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) return;
        if (onlyOwn.get() && !ownedAnchors.contains(pos)) return;

        mc.options.useKey.setPressed(false);

        if (isAnchorNotCharged(pos)) {
            handleCharge(hit);
        }

        if (isAnchorCharged(pos)) {
            handleExplode(hit, pos);
        }
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.player == null || mc.world == null || event == null || event.result == null) return;

        BlockHitResult hit = event.result;
        if (hit.getType() != HitResult.Type.BLOCK) return;

        if (mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
            Direction side = hit.getSide();
            BlockPos pos = hit.getBlockPos();
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                ownedAnchors.add(pos.offset(side));
            } else {
                ownedAnchors.add(pos);
            }
        }

        BlockPos pos = hit.getBlockPos();
        if (isAnchorCharged(pos)) ownedAnchors.remove(pos);
    }

    private void handleCharge(BlockHitResult hit) {
        if (!roll(placeChance.get())) return;

        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (switchClock < switchDelay.get()) {
                switchClock++;
                return;
            }

            if (roll(switchChance.get())) {
                switchClock = 0;
                swapToItem(Items.GLOWSTONE);
            }
        }

        if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (glowstoneClock < glowstoneDelay.get()) {
                glowstoneClock++;
                return;
            }

            if (roll(glowstoneChance.get())) {
                glowstoneClock = 0;
                useBlock(hit);
            }
        }
    }

    private void handleExplode(BlockHitResult hit, BlockPos pos) {
        int slot = explodeSlot.get() - 1;

        if (mc.player.getInventory().getSelectedSlot() != slot) {
            if (switchClock < switchDelay.get()) {
                switchClock++;
                return;
            }

            if (roll(switchChance.get())) {
                switchClock = 0;
                InvUtils.swap(slot, false);
            }
        }

        if (mc.player.getInventory().getSelectedSlot() == slot) {
            if (explodeClock < explodeDelay.get()) {
                explodeClock++;
                return;
            }

            if (roll(explodeChance.get())) {
                explodeClock = 0;
                if (!onlyCharge.get()) {
                    if (clickSimulation.get()) mc.options.useKey.setPressed(true);
                    useBlock(hit);
                    if (clickSimulation.get()) mc.options.useKey.setPressed(false);
                    ownedAnchors.remove(pos);
                }
            }
        }
    }

    private boolean isAnchorCharged(BlockPos pos) {
        return mc.world != null
            && mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)
            && mc.world.getBlockState(pos).get(RespawnAnchorBlock.CHARGES) != 0;
    }

    private boolean isAnchorNotCharged(BlockPos pos) {
        return mc.world != null
            && mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)
            && mc.world.getBlockState(pos).get(RespawnAnchorBlock.CHARGES) == 0;
    }

    private boolean swapToItem(net.minecraft.item.Item item) {
        if (mc.player == null) return false;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                return InvUtils.swap(i, false);
            }
        }

        return false;
    }

    private void useBlock(BlockHitResult hit) {
        if (mc.player == null || mc.interactionManager == null) return;

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean roll(double percent) {
        if (percent <= 0.0) return false;
        if (percent >= 100.0) return true;
        return random.nextDouble() * 100.0 <= percent;
    }

    private boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return stack.isOf(Items.DIAMOND_PICKAXE) || stack.isOf(Items.NETHERITE_PICKAXE)
            || stack.isOf(Items.DIAMOND_AXE) || stack.isOf(Items.NETHERITE_AXE)
            || stack.isOf(Items.DIAMOND_SHOVEL) || stack.isOf(Items.NETHERITE_SHOVEL)
            || stack.isOf(Items.DIAMOND_HOE) || stack.isOf(Items.NETHERITE_HOE);
    }

    private boolean isDeadBodyNearby() {
        if (mc.world == null || mc.player == null) return false;

        return mc.world.getPlayers().stream()
            .filter(player -> player != mc.player)
            .filter(player -> player.squaredDistanceTo(mc.player) <= 36.0D)
            .anyMatch(LivingEntity::isDead);
    }

    private boolean isValuableArmor(ItemStack stack) {
        return stack.isOf(Items.NETHERITE_HELMET) || stack.isOf(Items.NETHERITE_CHESTPLATE)
            || stack.isOf(Items.NETHERITE_LEGGINGS) || stack.isOf(Items.NETHERITE_BOOTS)
            || stack.isOf(Items.DIAMOND_HELMET) || stack.isOf(Items.DIAMOND_CHESTPLATE)
            || stack.isOf(Items.DIAMOND_LEGGINGS) || stack.isOf(Items.DIAMOND_BOOTS);
    }

    private boolean isValuableLootNearby() {
        if (mc.world == null || mc.player == null) return false;

        Box area = new Box(
            mc.player.getX() - 10.0, mc.player.getY() - 5.0, mc.player.getZ() - 10.0,
            mc.player.getX() + 10.0, mc.player.getY() + 5.0, mc.player.getZ() + 10.0
        );

        int valuableArmorCount = 0;
        for (Entity entity : mc.world.getOtherEntities(null, area)) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) continue;

            if (isValuableArmor(stack)) {
                valuableArmorCount++;
            } else if (stack.getCount() > 32) {
                if (stack.isOf(Items.END_CRYSTAL) || stack.isOf(Items.OBSIDIAN)
                    || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.EXPERIENCE_BOTTLE)) {
                    return true;
                }
            }
        }

        return valuableArmorCount >= 2;
    }
}
