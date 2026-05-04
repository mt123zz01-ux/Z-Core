package dev.zcore.mixin;

import dev.zcore.modules.misc.FakeElytra;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public abstract class FakeElytraItemStackMixin {
    @Unique
    private boolean zcore$handlingFakeElytra;

    @Inject(method = "getTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void zcore$swapFakeElytraTooltip(Item.TooltipContext tooltipContext,
                                             PlayerEntity player,
                                             TooltipType type,
                                             CallbackInfoReturnable<List<Text>> cir) {
        if (zcore$handlingFakeElytra) return;

        FakeElytra module = zcore$getFakeElytra();
        if (module == null || !module.isActive()) return;

        ItemStack self = (ItemStack) (Object) this;
        ItemStack fake = module.getDisplayStack(self);
        if (fake == null) return;

        zcore$handlingFakeElytra = true;
        try {
            Object tooltip = ItemStack.class
                .getMethod("getTooltip", Item.TooltipContext.class, PlayerEntity.class, TooltipType.class)
                .invoke(fake, tooltipContext, player, type);
            if (tooltip instanceof List<?> list) {
                ArrayList<Text> copy = new ArrayList<>();
                for (Object value : list) {
                    if (value instanceof Text text) copy.add(text);
                }
                cir.setReturnValue(copy);
            }
        } catch (ReflectiveOperationException ignored) {
        } finally {
            zcore$handlingFakeElytra = false;
        }
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true, require = 0)
    private void zcore$swapFakeElytraName(CallbackInfoReturnable<Text> cir) {
        if (zcore$handlingFakeElytra) return;

        FakeElytra module = zcore$getFakeElytra();
        if (module == null || !module.isActive()) return;

        ItemStack self = (ItemStack) (Object) this;
        ItemStack fake = module.getDisplayStack(self);
        if (fake == null) return;

        zcore$handlingFakeElytra = true;
        try {
            cir.setReturnValue(fake.getName());
        } finally {
            zcore$handlingFakeElytra = false;
        }
    }

    @Unique
    private FakeElytra zcore$getFakeElytra() {
        try {
            return Modules.get().get(FakeElytra.class);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
