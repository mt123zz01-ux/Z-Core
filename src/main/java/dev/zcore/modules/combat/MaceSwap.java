package dev.zcore.modules.combat;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Ported from Radium: switches to mace when attacking an entity. */
public class MaceSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Switches back to the previous hotbar slot after the attack.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> switchBackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("switch-back-delay")
        .description("Delay before switching back, in ticks.")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> ignoreEndCrystals = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-end-crystals")
        .description("Does not switch when attacking end crystals.")
        .defaultValue(false)
        .build()
    );

    private int previousSlot = -1;
    private int delayCounter = 0;

    public MaceSwap() {
        super(ZCoreCat.COMBAT, "mace-swap", "Switches to a mace when attacking an entity.");
    }

    @Override
    public void onActivate() {
        previousSlot = -1;
        delayCounter = 0;
    }

    @Override
    public void onDeactivate() {
        previousSlot = -1;
        delayCounter = 0;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null || event == null || event.entity == null) return;
        if (ignoreEndCrystals.get() && event.entity instanceof EndCrystalEntity) return;

        int maceSlot = findMaceInHotbar();
        if (maceSlot == -1) return;

        if (switchBack.get()) previousSlot = mc.player.getInventory().getSelectedSlot();

        InvUtils.swap(maceSlot, false);

        if (switchBack.get() && previousSlot != -1) {
            delayCounter = switchBackDelay.get();
            if (delayCounter <= 0) switchToPreviousSlot();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            if (delayCounter == 0) switchToPreviousSlot();
        }
    }

    private void switchToPreviousSlot() {
        if (previousSlot >= 0 && previousSlot <= 8) InvUtils.swap(previousSlot, false);
        previousSlot = -1;
    }

    private int findMaceInHotbar() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.MACE)) return i;
        }

        return -1;
    }
}
