# Radium Modules Port - 2026-05-04

Target environment preserved from Z-Core:

- Minecraft: 1.21.11
- Meteor Client: 1.21.11
- Java: 21
- Package root: `dev.zcore`

Added modules:

- `dev.zcore.modules.combat.MaceSwap`
- `dev.zcore.modules.combat.AnchorMacrov2`
- `dev.zcore.modules.combat.CrystalMacro`
- `dev.zcore.modules.misc.FakeElytra`
- `dev.zcore.modules.misc.SpawnerDropper`

Registered in `dev.zcore.ZCore`:

- `Modules.get().add(new MaceSwap());`
- `Modules.get().add(new AnchorMacrov2());`
- `Modules.get().add(new CrystalMacro());`
- `Modules.get().add(new FakeElytra());`
- `Modules.get().add(new SpawnerDropper());`

FakeElytra support mixins added:

- `FakeElytraItemRendererMixin`
- `FakeElytraItemStackMixin`
- `FakeElytraPlayerInventoryMixin`

Validation performed in sandbox:

- Confirmed all five requested classes exist and extend Meteor `Module`.
- Confirmed all five are imported and registered in `ZCore.java`.
- Confirmed Z-Core target remains 1.21.11 in `gradle.properties`.
- Confirmed no old error symbols remain: `prevYaw`, `prevPitch`, `prevHeadYaw`, `prevBodyYaw`, `getCamera().getPos()`.
- Attempted Gradle compile, but sandbox cannot download Gradle 9.2.1 because external network access is unavailable.

Build locally with:

```bat
gradlew.bat clean build
```

or on Linux/macOS:

```bash
./gradlew clean build
```
