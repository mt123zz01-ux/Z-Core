# Z-Core

Meteor Client addon for Minecraft 1.21.11.

## Project structure

```text
src/main/java/dev/zcore/
├── ZCore.java
├── ZCoreCat.java
└── modules/
    ├── combat/
    ├── misc/
    ├── movement/
    └── render/
```

## Requirements

- Java 21
- Fabric Loader 0.18.2+
- Minecraft 1.21.11
- Meteor Client 1.21.11
- Baritone compatible with Minecraft 1.21.11

## Build

```bat
gradlew.bat clean build --warning-mode all
```

Output jar:

```text
build/libs/z-core-1.0.0.jar
```

## Install

Copy these files into `.minecraft/mods/`:

1. `z-core-1.0.0.jar`
2. `meteor-client-1.21.11-*.jar`
3. `baritone*.jar` or a compatible Baritone jar for Minecraft 1.21.11

## Current modules

### Z-Combat

- `trigger-bot`
- `inv-totem`

### Z-Utility

- `AutoMine`
- `SpawnerProtect`
- `PlayerLeaveLog`
- `HideScoreboard`
- `AutoTPA`
- `FakePlayer`
- `TunnelBaseFinder`
- `BedrockVoidEsp`
- `SpawnerNotifier`

