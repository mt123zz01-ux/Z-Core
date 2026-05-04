# Fix compile errors for Minecraft 1.21.11 mappings

Fixed compile errors where old-style entity position calls no longer compile:

- `ClientPlayerEntity` position access updated to `new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())` or `mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))`.
- `ItemEntity` position access updated to `new Vec3d(item.getX(), item.getY(), item.getZ())`.

Affected files:

- `src/main/java/dev/zcore/modules/misc/SpawnerProtect.java`
- `src/main/java/dev/zcore/modules/render/SpawnerNotifier.java`

Remaining `getPos()` calls are for Camera, Chunk, and BlockEntity objects, not Entity/ClientPlayerEntity.
