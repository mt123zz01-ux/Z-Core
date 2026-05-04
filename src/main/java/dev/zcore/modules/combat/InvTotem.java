package dev.zcore.modules.combat;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;

/** InvTotem - automatically moves a Totem of Undying to the offhand when the offhand has no totem. */
public class InvTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyWhenInventoryClosed = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-inventory-closed")
        .description("Only swaps when no other GUI is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between checks/swaps, in ticks.")
        .defaultValue(2)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private int timer;

    public InvTotem() {
        super(ZCoreCat.COMBAT, "inv-totem", "Automatically moves a totem from inventory to the offhand when the offhand is empty or has no totem.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (onlyWhenInventoryClosed.get() && mc.currentScreen != null) return;

        if (timer > 0) {
            timer--;
            return;
        }
        timer = delay.get();

        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (!totem.found() || totem.isOffhand()) return;

        InvUtils.move().from(totem.slot()).toOffhand();
    }
}
