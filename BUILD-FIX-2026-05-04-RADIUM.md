# Build fix for Radium module port on Meteor/Minecraft 1.21.11

Fixed compile errors reported from the previous Radium module port:

- `FakeElytra.java`
  - Removed direct enchantment registry lookup using `Registry.getEntry(RegistryKey<Enchantment>)` because Yarn 1.21.11 no longer supports that call shape.
  - Replaced `PlayerInventory#getArmorStack(int)` usage with `LivingEntity#getEquippedStack(EquipmentSlot.CHEST)`.
  - Changed the default swapped item to `DIAMOND_CHESTPLATE`, which matches the module validation.

- `FakeElytra` mixins
  - Removed the old item renderer mixin that referenced missing `BakedModel` APIs.
  - Replaced the old `PlayerInventory#getArmorStack` mixin with a `LivingEntity#getEquippedStack` mixin.
  - Kept the `ItemStack` tooltip/name mixin.

- `MaceSwap.java`
  - Replaced private `PlayerInventory#selectedSlot` field access with `PlayerInventory#getSelectedSlot()`.

- `AnchorMacrov2.java`
  - Replaced private `selectedSlot` field access with `getSelectedSlot()`.
  - Removed the missing `ArmorItem` class dependency and replaced it with explicit item checks.

- `CrystalMacro.java`
  - Removed missing `ArmorItem` and `SwordItem` class dependencies and replaced them with explicit item checks.

- `SpawnerDropper.java`
  - Replaced removed `ClientPlayerEntity#getPos()` usage with direct `getX()/getY()/getZ()` distance calculation.

Static verification performed:

- No references remain to: `BakedModel`, `FakeElytraItemRendererMixin`, `FakeElytraPlayerInventoryMixin`, `getArmorStack`, direct `selectedSlot`, `ArmorItem`, `SwordItem`, `mc.player.getPos()`, or `getEntry(Enchantments...)`.
- All five requested modules are still registered in `ZCore.java`.
- `z-core.mixins.json` now references only mixins that match the 1.21.11 API shape used by this source.
