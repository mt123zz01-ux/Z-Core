# Z-Core 1.21.11 Camera Fix

Fixed compile error in `BedrockVoidEsp.java`:

```java
camera position access through the old getter
```

Replaced with Meteor `Render3DEvent` camera offsets:

```java
new Vec3d(event.offsetX, event.offsetY, event.offsetZ)
```

Also rechecked source for old player/item/camera `getPos()` calls. Remaining `getPos()` calls are on chunk/block-entity APIs and are not the failing client entity/camera methods.
