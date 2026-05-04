# Z-Core 1.21.11 - Radium module port

Changes:

- Removed `AutoJump` completely.
- Updated `AutoMine` so it holds Forward + Attack and actively calls block breaking progress on the block under crosshair.
- Added `AutoTPA` from Radium idea, rewritten for Meteor settings/events.
- Added `InvTotem` from Radium idea, rewritten with Meteor `InvUtils`.
- Added `FakePlayer` from Radium idea, rewritten as client-side fake player spawner.
- Added `TunnelBaseFinder` from Radium idea, rewritten as chunk/block-entity scanner for possible underground bases.

Build command:

```bat
gradlew.bat clean build --warning-mode all
```
