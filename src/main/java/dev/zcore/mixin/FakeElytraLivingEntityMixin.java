package dev.zcore.mixin;

import dev.zcore.modules.misc.FakeElytra;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FakeElytraLivingEntityMixin {
    @Inject(
        method = "getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void zcore$swapChestplateForFakeElytra(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != EquipmentSlot.CHEST) return;

        FakeElytra module = zcore$getFakeElytra();
        if (module == null || !module.isActive()) return;

        ItemStack stack = cir.getReturnValue();
        if (stack == null || stack.isEmpty()) return;

        ItemStack fake = module.getDisplayStack(stack);
        if (fake != null) cir.setReturnValue(fake);
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
