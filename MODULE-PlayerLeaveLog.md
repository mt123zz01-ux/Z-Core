# PlayerLeaveLog

Module for Z-Core / Meteor 1.21.11.

## Features

- Tracks nearby players within the default `256` block radius.
- Stores the player name and last known coordinates.
- When a player disappears from the radar/entity list, waits `3` seconds before reporting to reduce false positives from short lag spikes.
- Reports the event in chat only.

## Files

```text
src/main/java/dev/zcore/modules/misc/PlayerLeaveLog.java
src/main/java/dev/zcore/ZCore.java
```

## Default settings

```text
range = 256
confirm-delay = 3
show-coords = true
include-friends = true
```

## Example message

```text
[Z-Core] Steve disappeared/logged out at X: 120 Y: 64 Z: -340
```

## Note

The Minecraft client only knows about players sent by the server. This event can mean logout, teleport, dimension change, or leaving the radar/tracking range.
