package dev.zcore.modules.misc;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Ported from Radium: auto-equips Elytra and exposes fake Elytra display data for mixins. */
public class FakeElytra extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Item> appearanceItem = sgGeneral.add(new ItemSetting.Builder()
        .name("swapped-item")
        .description("Chestplate item that should appear as an Elytra client-side.")
        .defaultValue(Items.DIAMOND_CHESTPLATE)
        .build()
    );

    private EquipStage equipStage = EquipStage.NONE;
    private int elytraSlotToEquip = -1;
    private int equipWaitTicks = 0;

    public FakeElytra() {
        super(ZCoreCat.UTILITY, "fake-elytra", "Shows the selected chestplate item as an Elytra and auto-equips real Elytra.");
    }

    @Override
    public void onActivate() {
        equipStage = EquipStage.NONE;
        elytraSlotToEquip = -1;
        equipWaitTicks = 0;
    }

    @Override
    public void onDeactivate() {
        equipStage = EquipStage.NONE;
        elytraSlotToEquip = -1;
        equipWaitTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        Item targetItem = appearanceItem.get();
        if (!isValidAppearanceItem(targetItem)) {
            equipStage = EquipStage.NONE;
            return;
        }

        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chestStack.isOf(Items.ELYTRA)) {
            equipStage = EquipStage.NONE;
            return;
        }

        switch (equipStage) {
            case NONE -> {
                int elytraSlot = findElytraSlot();
                if (elytraSlot == -1) return;

                elytraSlotToEquip = elytraSlot;
                equipStage = EquipStage.PICKUP_ELYTRA;
                equipWaitTicks = 0;
            }
            case PICKUP_ELYTRA -> {
                equipWaitTicks++;
                if (equipWaitTicks >= 1) {
                    mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        convertSlotIndex(elytraSlotToEquip),
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    equipStage = EquipStage.PLACE_ON_CHESTPLATE;
                    equipWaitTicks = 0;
                }
            }
            case PLACE_ON_CHESTPLATE -> {
                equipWaitTicks++;
                if (equipWaitTicks >= 1) {
                    mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        6,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    equipStage = EquipStage.NONE;
                    equipWaitTicks = 0;
                }
            }
        }
    }

    private int findElytraSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.ELYTRA)) return i;
        }

        return -1;
    }

    private int convertSlotIndex(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < 9) return 36 + slotIndex;
        return slotIndex;
    }

    public ItemStack getDisplayStack(ItemStack original) {
        if (original == null || original.isEmpty()) return null;

        Item target = appearanceItem.get();
        if (!isValidAppearanceItem(target)) return null;
        if (!original.isOf(target)) return null;

        ItemStack fake = new ItemStack(Items.ELYTRA, original.getCount());

        MutableText name = Text.literal(Items.ELYTRA.getName().getString())
            .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withItalic(false));
        fake.set(DataComponentTypes.CUSTOM_NAME, name);


        return fake;
    }

    private boolean isValidAppearanceItem(Item item) {
        if (item == null || item == Items.AIR || item == Items.ELYTRA) return false;

        return item == Items.LEATHER_CHESTPLATE
            || item == Items.CHAINMAIL_CHESTPLATE
            || item == Items.IRON_CHESTPLATE
            || item == Items.GOLDEN_CHESTPLATE
            || item == Items.DIAMOND_CHESTPLATE
            || item == Items.NETHERITE_CHESTPLATE;
    }

    private enum EquipStage {
        NONE,
        PICKUP_ELYTRA,
        PLACE_ON_CHESTPLATE
    }
}
