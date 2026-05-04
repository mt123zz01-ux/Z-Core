package dev.zcore.modules.render;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SpawnerNotifier - reports in chat and renders spawners when a chunk containing a spawner is loaded by the client.
 * Core Glazed port that keeps chat/render features to stay lightweight.
 */
public class SpawnerNotifier extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> showCoordinates = sgGeneral.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Shows spawner coordinates in the notification.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Shows distance from you to the spawner/spawner chunk.")
        .defaultValue(true)
        .build()
    );

    private final Setting<NotifyMode> notificationMode = sgNotifications.add(new EnumSetting.Builder<NotifyMode>()
        .name("notification-mode")
        .description("Notification type when a spawner is detected.")
        .defaultValue(NotifyMode.Chat)
        .build()
    );

    private final Setting<Boolean> showEsp = sgRender.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Renders a box at detected spawners.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> espColor = sgRender.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("ESP color for spawners.")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Box render mode.")
        .defaultValue(ShapeMode.Both)
        .visible(showEsp::get)
        .build()
    );

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draws tracers to spawners.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Tracer color for spawners.")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> onlyRenderNew = sgRender.add(new BoolSetting.Builder()
        .name("only-render-new")
        .description("Only renders spawners newly detected in this session.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> renderDistance = sgRender.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Maximum render distance. 0 = unlimited.")
        .defaultValue(0.0)
        .min(0.0)
        .max(500.0)
        .sliderMax(200.0)
        .build()
    );

    private final Set<ChunkPos> processedChunks = new HashSet<>();
    private final Set<BlockPos> foundSpawners = new HashSet<>();
    private final Set<BlockPos> newFoundSpawners = new HashSet<>();
    private int totalSpawnersFound = 0;

    public SpawnerNotifier() {
        super(ZCoreCat.UTILITY, "spawner-notifier", "Reports in chat and renders ESP when a mob spawner is detected in a loaded chunk.");
    }

    @Override
    public void onActivate() {
        processedChunks.clear();
        foundSpawners.clear();
        newFoundSpawners.clear();
        totalSpawnersFound = 0;
    }

    @Override
    public void onDeactivate() {
        processedChunks.clear();
        foundSpawners.clear();
        newFoundSpawners.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.world == null || event == null || event.chunk() == null) return;

        ChunkPos chunkPos = event.chunk().getPos();
        if (processedChunks.contains(chunkPos)) return;
        processedChunks.add(chunkPos);

        List<BlockPos> chunkSpawners = new ArrayList<>();
        for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
            if (blockEntity instanceof MobSpawnerBlockEntity) {
                BlockPos pos = blockEntity.getPos();
                chunkSpawners.add(pos);
                if (foundSpawners.add(pos)) {
                    newFoundSpawners.add(pos);
                    totalSpawnersFound++;
                }
            }
        }

        if (!chunkSpawners.isEmpty()) {
            notifySpawnersFound(chunkPos, chunkSpawners);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || (!showEsp.get() && !showTracers.get())) return;

        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);
        Color sideColor = espColor.get();
        Color lineColor = espColor.get();
        Color tracer = tracerColor.get();
        double maxDistance = renderDistance.get();

        for (BlockPos pos : foundSpawners) {
            if (onlyRenderNew.get() && !newFoundSpawners.contains(pos)) continue;

            Vec3d center = Vec3d.ofCenter(pos);
            if (maxDistance > 0 && playerPos.distanceTo(center) > maxDistance) continue;

            if (showEsp.get()) {
                event.renderer.box(pos, sideColor, lineColor, shapeMode.get(), 0);
            }

            if (showTracers.get()) {
                Vec3d start = getTracerStart(playerPos);
                event.renderer.line(start.x, start.y, start.z, center.x, center.y, center.z, tracer);
            }
        }
    }

    private Vec3d getTracerStart(Vec3d playerPos) {
        if (mc.player == null) return playerPos;
        if (mc.options.getPerspective().isFirstPerson()) {
            Vec3d look = mc.player.getRotationVector();
            return new Vec3d(
                playerPos.x + look.x * 0.5,
                playerPos.y + mc.player.getEyeHeight(mc.player.getPose()) + look.y * 0.5,
                playerPos.z + look.z * 0.5
            );
        }
        return new Vec3d(playerPos.x, playerPos.y + mc.player.getEyeHeight(mc.player.getPose()), playerPos.z);
    }

    private void notifySpawnersFound(ChunkPos chunkPos, List<BlockPos> spawners) {
        String message = buildMessage(chunkPos, spawners);
        switch (notificationMode.get()) {
            case Chat -> info(message);
            case Toast -> showToast(message);
            case Both -> {
                info(message);
                showToast(message);
            }
        }
    }

    private void showToast(String message) {
        try {
            mc.getToastManager().add(new MeteorToast.Builder(title).text(message).icon(Items.SPAWNER).build());
        } catch (Exception ignored) {
            info(message);
        }
    }

    private String buildMessage(ChunkPos chunkPos, List<BlockPos> spawners) {
        StringBuilder message = new StringBuilder("[Z-Core] ");
        message.append(spawners.size() == 1 ? "Spawner found" : "Spawners found x" + spawners.size());

        if (showCoordinates.get()) {
            if (spawners.size() == 1) {
                BlockPos pos = spawners.get(0);
                message.append(String.format(" at X: %d Y: %d Z: %d", pos.getX(), pos.getY(), pos.getZ()));
            } else {
                int centerX = chunkPos.x * 16 + 8;
                int centerZ = chunkPos.z * 16 + 8;
                message.append(String.format(" trong chunk X: %d Z: %d", centerX, centerZ));
            }
        }

        if (showDistance.get() && mc.player != null) {
            Vec3d target = spawners.size() == 1
                ? Vec3d.ofCenter(spawners.get(0))
                : new Vec3d(chunkPos.x * 16 + 8, mc.player.getY(), chunkPos.z * 16 + 8);
            Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            message.append(String.format(" (%.1fm)", playerPos.distanceTo(target)));
        }

        return message.toString();
    }

    @Override
    public String getInfoString() {
        return String.valueOf(totalSpawnersFound);
    }

    public enum NotifyMode {
        Chat,
        Toast,
        Both
    }
}
