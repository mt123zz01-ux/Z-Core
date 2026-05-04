# Glazed Module Ports

Three modules were ported from Glazed into the Z-Core package/style for Minecraft 1.21.11.

## BedrockVoidEsp

- Category: `Z-Utility`
- Scans loaded bedrock layers for non-bedrock void groups.
- Renders boxes/tracers and can report new detections in chat.
- Threading was removed to avoid reading the world from a background thread.

## SpawnerNotifier

- Category: `Z-Utility`
- Detects `MobSpawnerBlockEntity` when chunks load.
- Supports chat/toast notifications and box/tracer rendering.
- Auto-disconnect behavior was not included.

## HideScoreboard

- Category: `Z-Utility`
- Hides the client-side sidebar scoreboard and restores it when disabled.
