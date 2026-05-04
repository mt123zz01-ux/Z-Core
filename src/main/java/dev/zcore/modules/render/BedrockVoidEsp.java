package dev.zcore.modules.render;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BedrockVoidEsp - scans loaded bedrock layers for holes/voids and renders ESP.
 * Safe Glazed port: keeps scan/render/chat features and removes threading to avoid reading the world from another thread.
 */
public class BedrockVoidEsp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minVoidSize = sgGeneral.add(new IntSetting.Builder()
        .name("min-void-size")
        .description("Minimum number of non-bedrock blocks required to count as a bedrock void.")
        .defaultValue(2)
        .min(1)
        .max(50)
        .sliderMin(1)
        .sliderMax(50)
        .onChanged(value -> rescanLoadedChunks())
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Reports in chat when a new bedrock void is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxMessagesPerMinute = sgGeneral.add(new IntSetting.Builder()
        .name("max-messages-per-minute")
        .description("Message limit per minute. 0 = unlimited.")
        .defaultValue(10)
        .min(0)
        .max(60)
        .sliderMin(0)
        .sliderMax(60)
        .visible(chatFeedback::get)
        .build()
    );

    private final Setting<Boolean> showEsp = sgRender.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Renders a box at the bedrock void.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> espColor = sgRender.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("ESP color for bedrock voids.")
        .defaultValue(new SettingColor(240, 85, 80, 128))
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
        .description("Draws tracers to bedrock voids.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Tracer color.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(showTracers::get)
        .build()
    );

    private static final List<Integer> OVERWORLD_Y_LEVELS = List.of(-64, -63, -62, -61, -60);
    private static final List<Integer> NETHER_FLOOR_Y_LEVELS = List.of(0, 1, 2, 3, 4);
    private static final List<Integer> NETHER_ROOF_Y_LEVELS = List.of(123, 124, 125, 126, 127);

    private final Set<BlockPos> voidBlocks = ConcurrentHashMap.newKeySet();
    private long lastMinute = -1;
    private int messagesThisMinute = 0;
    private String lastDimension = "";

    public BedrockVoidEsp() {
        super(ZCoreCat.UTILITY, "bedrock-void-esp", "Finds and renders voids in loaded bedrock layers.");
    }

    @Override
    public void onActivate() {
        voidBlocks.clear();
        lastMinute = -1;
        messagesThisMinute = 0;
        lastDimension = getDimensionId();
        rescanLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        voidBlocks.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;
        handleDimensionChange();
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || event == null || event.pos == null) return;
        List<Integer> levels = getYLevelsForCurrentDimension();
        if (!levels.contains(event.pos.getY())) return;

        ChunkPos chunkPos = new ChunkPos(event.pos);
        scanChunk(mc.world.getChunk(chunkPos.x, chunkPos.z));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (showEsp.get()) {
            Color color = espColor.get();
            for (BlockPos pos : voidBlocks) {
                event.renderer.box(pos, color, color, shapeMode.get(), 0);
            }
        }

        if (showTracers.get()) {
            Color color = tracerColor.get();
            Vec3d camera = new Vec3d(event.offsetX, event.offsetY, event.offsetZ);
            for (BlockPos pos : voidBlocks) {
                Vec3d center = Vec3d.ofCenter(pos);
                event.renderer.line(camera.x, camera.y, camera.z, center.x, center.y, center.z, color);
            }
        }
    }

    private void rescanLoadedChunks() {
        if (!isActive() || mc.world == null) return;
        voidBlocks.clear();
        for (Chunk chunk : Utils.chunks()) {
            scanChunk(chunk);
        }
    }

    private void scanChunk(Chunk chunk) {
        if (mc.world == null || !(chunk instanceof WorldChunk worldChunk)) return;

        ChunkPos chunkPos = worldChunk.getPos();
        voidBlocks.removeIf(pos -> new ChunkPos(pos).equals(chunkPos));

        List<Integer> levels = getYLevelsForCurrentDimension();
        if (levels.isEmpty()) return;

        findVoidsInChunk(worldChunk, levels);
    }

    private void findVoidsInChunk(WorldChunk chunk, List<Integer> levels) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        Set<BlockPos> processed = new HashSet<>();

        for (int y : levels) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    BlockPos pos = new BlockPos(startX + dx, y, startZ + dz);
                    if (processed.contains(pos) || isBedrock(pos)) continue;

                    List<BlockPos> group = floodFill(pos, levels, processed);
                    if (group.size() >= minVoidSize.get() && isEnclosedByBedrock(group)) {
                        boolean hasNew = voidBlocks.addAll(group);
                        if (hasNew && !group.isEmpty()) {
                            BlockPos first = group.get(0);
                            sendVoidMessage(String.format("[Z-Core] Bedrock void: %d blocks at X: %d Y: %d Z: %d", group.size(), first.getX(), first.getY(), first.getZ()));
                        }
                    }
                }
            }
        }
    }

    private List<BlockPos> floodFill(BlockPos start, List<Integer> levels, Set<BlockPos> processed) {
        List<BlockPos> group = new ArrayList<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.offer(start);

        while (!queue.isEmpty() && group.size() < 200) {
            BlockPos current = queue.poll();
            if (processed.contains(current)) continue;
            if (!levels.contains(current.getY())) continue;
            if (isBedrock(current)) continue;

            processed.add(current);
            group.add(current);

            for (Direction direction : Direction.values()) {
                queue.offer(current.offset(direction));
            }
        }

        return group;
    }

    private boolean isEnclosedByBedrock(List<BlockPos> group) {
        Set<BlockPos> groupSet = new HashSet<>(group);
        for (BlockPos pos : group) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.offset(direction);
                if (groupSet.contains(neighbor)) continue;
                if (!isBedrock(neighbor)) return false;
            }
        }
        return true;
    }

    private boolean isBedrock(BlockPos pos) {
        if (mc.world == null) return true;
        BlockState state = mc.world.getBlockState(pos);
        return state.getBlock() == Blocks.BEDROCK;
    }

    private List<Integer> getYLevelsForCurrentDimension() {
        return switch (getDimensionId()) {
            case "minecraft:overworld" -> OVERWORLD_Y_LEVELS;
            case "minecraft:the_nether" -> {
                List<Integer> levels = new ArrayList<>(NETHER_FLOOR_Y_LEVELS);
                levels.addAll(NETHER_ROOF_Y_LEVELS);
                yield levels;
            }
            default -> Collections.emptyList();
        };
    }

    private String getDimensionId() {
        if (mc.world == null) return "";
        return mc.world.getRegistryKey().getValue().toString();
    }

    private void handleDimensionChange() {
        String current = getDimensionId();
        if (!current.equals(lastDimension)) {
            lastDimension = current;
            voidBlocks.clear();
        }
    }

    private void sendVoidMessage(String message) {
        if (!chatFeedback.get()) return;

        long minute = System.currentTimeMillis() / 60000L;
        if (minute != lastMinute) {
            lastMinute = minute;
            messagesThisMinute = 0;
        }

        int max = maxMessagesPerMinute.get();
        if (max == 0 || messagesThisMinute < max) {
            info(message);
            messagesThisMinute++;
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(voidBlocks.size());
    }
}
