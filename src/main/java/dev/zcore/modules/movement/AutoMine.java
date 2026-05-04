package dev.zcore.modules.movement;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * AutoMine - holds W and left-click.
 *
 * In addition to setting the attack key, this module calls updateBlockBreakingProgress
 * to make sure the targeted block is mined continuously on 1.21.11.
 */
public class AutoMine extends Module {
    private boolean holdingForward;
    private boolean holdingAttack;

    public AutoMine() {
        super(ZCoreCat.UTILITY, "auto-mine", "Automatically walks forward and mines the block in front, like holding W and left-click.");
    }

    @Override
    public void onActivate() {
        holdingForward = false;
        holdingAttack = false;
        info("AutoMine enabled: holding W and left-click.");
    }

    @Override
    public void onDeactivate() {
        releaseKeys();
        info("AutoMine disabled.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            releaseKeys();
            return;
        }

        holdForward();
        holdAttack();
        mineTargetBlock();
    }

    private void holdForward() {
        if (mc.options.forwardKey == null) return;
        mc.options.forwardKey.setPressed(true);
        holdingForward = true;
    }

    private void holdAttack() {
        if (mc.options.attackKey == null) return;
        mc.options.attackKey.setPressed(true);
        holdingAttack = true;
    }

    private void mineTargetBlock() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        Direction side = hit.getSide();
        if (mc.world.isAir(pos)) return;

        boolean breaking = mc.interactionManager.updateBlockBreakingProgress(pos, side);
        if (breaking) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void releaseKeys() {
        if (holdingForward && mc.options.forwardKey != null) mc.options.forwardKey.setPressed(false);
        if (holdingAttack && mc.options.attackKey != null) mc.options.attackKey.setPressed(false);
        holdingForward = false;
        holdingAttack = false;
    }
}
