package dev.zcore.modules.render;

import dev.zcore.ZCoreCat;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

@SuppressWarnings({"unchecked", "rawtypes"})
public class Finder extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> onlyOnBedrock = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("only-on-bedrock")).description("Nur Spawner auf Bedrock-Höhe (Y=0) erkennen."))
                    .defaultValue(true))
                .build()
        );
    private final Setting<Boolean> antiEspMode = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("anti-esp-mode")).description("Anti-ESP Fix: Verwendet mehrere Detection-Methoden."))
                    .defaultValue(true))
                .build()
        );
    private final Setting<Boolean> playerBasedDetection = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("player-based-detection")).description("Player-basierte Detection (umgeht Anti-ESP)."))
                    .defaultValue(true))
                .build()
        );
    private final Setting<Boolean> serverSideBypass = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("server-side-bypass")).description("Server-seitige Anti-ESP Umgehung.")).defaultValue(false))
                .build()
        );
    private final Setting<Boolean> strictValidation = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("strict-validation")).description("Strikte Validierung um false positives zu vermeiden."))
                    .defaultValue(false))
                .build()
        );
    private final Setting<Boolean> requirePlayerPresence = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("require-player-presence")).description("Benötigt Player in der Nähe für Detection."))
                    .defaultValue(false))
                .build()
        );
    private final Setting<Boolean> playerPacketDetection = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("player-packet-detection")).description("Player-Packet Detection für Bedrock/Deepslate."))
                    .defaultValue(true))
                .build()
        );
    private final Setting<Boolean> detectOnBedrock = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("detect-on-bedrock")).description("Player auf Bedrock (Y <= -64) detektieren."))
                    .defaultValue(true))
                .build()
        );
    private final Setting<Boolean> detectUnderDeepslate = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("detect-under-deepslate")).description("Player unter Deepslate (Y < 0) detektieren."))
                    .defaultValue(true))
                .build()
        );
    private final Setting<Boolean> packetAntiEspBypass = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("packet-anti-esp-bypass")).description("Verwendet Packet-Detection für Anti-ESP Bypass."))
                    .defaultValue(true))
                .build()
        );
    private final Setting<Boolean> showPendingChunks = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("show-pending-chunks")).description("Zeigt gelbe Chunks während des Scannens an."))
                    .defaultValue(true))
                .build()
        );
    private final Setting<Integer> scanDelay = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                            .name("scan-delay"))
                        .description("Verzögerung zwischen Scans in ms (Anti-ESP)."))
                    .defaultValue(100))
                .min(50)
                .max(1000)
                .sliderMax(1000)
                .build()
        );
    private final Setting<Integer> maxRetries = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("max-retries"))
                    .defaultValue(3))
                .min(1)
                .max(10)
                .sliderMax(10)
                .build()
        );
    private final Setting<Finder.RenderMode> renderMode = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                            .name("render-mode"))
                        .description("Kies tussen pillar of flat chunk rendering."))
                    .defaultValue(Finder.RenderMode.Pillar))
                .build()
        );
    private final Setting<Integer> flatRenderY = this.sgGeneral
        .add(
            ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                                .name("flat-render-y"))
                            .description("Y-level voor flat chunk render."))
                        .defaultValue(64))
                    .range(-64, 320)
                    .visible(() -> this.renderMode.get() == Finder.RenderMode.Flat))
                .build()
        );
    private final Set<ChunkPos> spawnerChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> pendingChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> playerChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<BlockPos> playerPositions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Integer> flaggedEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> entityChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> redstoneChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> activeBaseChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> greenBaseChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<BlockPos> redstoneActivity = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> particleActivity = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> soundActivity = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkPos> storageChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<ChunkPos, Long> lastActivityTime = new ConcurrentHashMap<>();

    public Finder() {
        super(ZCoreCat.UTILITY, "Finder", "Marks chunks with suspicious activity such as spawners, redstone, players, entities, and storage.");
    }

    public void onActivate() {
        this.spawnerChunks.clear();
        this.pendingChunks.clear();
        this.playerChunks.clear();
        this.playerPositions.clear();
        this.flaggedEntities.clear();
        this.entityChunks.clear();
        this.redstoneChunks.clear();
        this.activeBaseChunks.clear();
        this.greenBaseChunks.clear();
        this.redstoneActivity.clear();
        this.particleActivity.clear();
        this.soundActivity.clear();
        this.storageChunks.clear();
        this.lastActivityTime.clear();
    }

    @EventHandler(
        priority = 200
    )
    private void onTick(Post event) {
        if (this.mc.world != null && this.mc.player != null) {
            try {
                if (this.mc.world.getTime() % 20L == 0L) {
                    this.scanForHiddenSpawners();
                }
            } catch (Exception var3) {
            }
        }
    }

    private void scanForHiddenSpawners() {
        if (this.mc.world != null && this.mc.player != null) {
            try {
                ChunkPos playerChunk = this.mc.player.getChunkPos();
                int range = 5;

                for (int dx = -range; dx <= range; dx++) {
                    for (int dz = -range; dz <= range; dz++) {
                        ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                        if (this.mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                            WorldChunk chunk = this.mc.world.getChunk(chunkPos.x, chunkPos.z);
                            if (chunk != null) {
                                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                                    if (blockEntity instanceof MobSpawnerBlockEntity spawner) {
                                        int spawnerY = spawner.getPos().getY();
                                        if ((!(Boolean)this.onlyOnBedrock.get() || spawnerY == 0 || (Boolean)this.packetAntiEspBypass.get() && spawnerY <= 0)
                                            && (!(Boolean)this.strictValidation.get() || this.isValidSpawner(spawner))) {
                                            this.spawnerChunks.add(chunkPos);
                                            break;
                                        }
                                    }
                                }

                                for (PlayerEntity player : this.mc.world.getPlayers()) {
                                    if (player != null && player.getChunkPos().equals(chunkPos)) {
                                        double playerY = player.getY();
                                        boolean shouldDetect = false;
                                        if ((Boolean)this.detectOnBedrock.get() && playerY <= -64.0) {
                                            shouldDetect = true;
                                        } else if ((Boolean)this.detectUnderDeepslate.get() && playerY < 0.0) {
                                            shouldDetect = true;
                                        } else if ((Boolean)this.packetAntiEspBypass.get() && playerY <= 0.0) {
                                            shouldDetect = true;
                                        }

                                        if (shouldDetect) {
                                            this.playerChunks.add(chunkPos);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception var12) {
            }
        }
    }

    private void checkEntity(Entity entity) {
        if (entity != null && this.mc.world != null) {
            try {
                BlockPos pos = entity.getBlockPos();
                BlockPos belowPos = pos.down();
                if (this.mc.world.getBlockState(belowPos).isOf(Blocks.DEEPSLATE)
                    || this.mc.world.getBlockState(belowPos).isOf(Blocks.BEDROCK)) {
                    this.flagEntity(entity);
                }
            } catch (Exception var4) {
            }
        }
    }

    private void flagEntity(Entity entity) {
        if (entity != null) {
            try {
                if (!this.flaggedEntities.contains(entity.getId())) {
                    this.flaggedEntities.add(entity.getId());
                    ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());
                    this.entityChunks.add(chunkPos);
                }
            } catch (Exception var3) {
            }
        }
    }

    private void handleEntitySpawn(EntitySpawnS2CPacket packet) {
        try {
            if (this.mc.world != null) {
                for (Entity entity : this.mc.world.getEntities()) {
                    if (entity != null && !this.flaggedEntities.contains(entity.getId())) {
                        this.checkEntity(entity);
                    }
                }
            }
        } catch (Exception var4) {
        }
    }

    private void handleRedstonePacket(BlockUpdateS2CPacket packet) {
        try {
            BlockPos pos = packet.getPos();
            BlockState state = packet.getState();
            if (this.isRedstoneActive(state)) {
                this.redstoneActivity.add(pos);
                ChunkPos chunkPos = new ChunkPos(pos);
                this.redstoneChunks.add(chunkPos);
                this.addBaseChunk(pos);
            }
        } catch (Exception var5) {
        }
    }

    private void handleParticlePacket(ParticleS2CPacket packet) {
        try {
            double x = packet.getX();
            double z = packet.getZ();
            BlockPos pos = new BlockPos((int)x, (int)packet.getY(), (int)z);
            ChunkPos chunkPos = new ChunkPos(pos);
            if (this.isSuspiciousParticle(packet)) {
                this.particleActivity.add(chunkPos);
                this.addBaseChunk(pos);
            }
        } catch (Exception var8) {
        }
    }

    private void handleSoundPacket(PlaySoundS2CPacket packet) {
        try {
            double x = packet.getX();
            double z = packet.getZ();
            BlockPos pos = new BlockPos((int)x, (int)packet.getY(), (int)z);
            ChunkPos chunkPos = new ChunkPos(pos);
            if (this.isSuspiciousSound(packet)) {
                this.soundActivity.add(chunkPos);
                this.addBaseChunk(pos);
            }
        } catch (Exception var8) {
        }
    }

    private void updateActivityTime(ChunkPos chunkPos) {
        this.lastActivityTime.put(chunkPos, System.currentTimeMillis());
    }

    private void addBaseChunk(BlockPos pos) {
        try {
            BlockPos aboveBasePos = new BlockPos(pos.getX(), pos.getY() + 5, pos.getZ());
            ChunkPos aboveBaseChunk = new ChunkPos(aboveBasePos);
            this.greenBaseChunks.clear();
            this.greenBaseChunks.add(aboveBaseChunk);
            this.activeBaseChunks.clear();
            this.activeBaseChunks.add(aboveBaseChunk);
            this.updateActivityTime(aboveBaseChunk);
        } catch (Exception var4) {
            ChunkPos normalChunk = new ChunkPos(pos);
            this.greenBaseChunks.clear();
            this.greenBaseChunks.add(normalChunk);
            this.activeBaseChunks.clear();
            this.activeBaseChunks.add(normalChunk);
        }
    }

    private boolean isSuspiciousParticle(ParticleS2CPacket packet) {
        try {
            String particleType = packet.getParameters().getType().toString().toLowerCase();
            return !particleType.contains("portal")
                    && !particleType.contains("end")
                    && !particleType.contains("chest")
                    && !particleType.contains("ender")
                    && !particleType.contains("shulker")
                ? particleType.contains("redstone")
                    || particleType.contains("dust")
                    || particleType.contains("happy_villager")
                    || particleType.contains("composter")
                : false;
        } catch (Exception var3) {
            return false;
        }
    }

    private boolean isSuspiciousSound(PlaySoundS2CPacket packet) {
        try {
            String soundName = ((SoundEvent)packet.getSound().value()).toString().toLowerCase();
            return soundName.contains("hopper")
                || soundName.contains("piston")
                || soundName.contains("dispenser")
                || soundName.contains("dropper")
                || soundName.contains("furnace")
                || soundName.contains("redstone");
        } catch (Exception var3) {
            return false;
        }
    }

    private boolean isRedstoneActive(BlockState state) {
        try {
            return state.isOf(Blocks.REDSTONE_BLOCK)
                || state.isOf(Blocks.REDSTONE_TORCH)
                || state.isOf(Blocks.REPEATER)
                || state.isOf(Blocks.COMPARATOR)
                || state.isOf(Blocks.PISTON)
                || state.isOf(Blocks.STICKY_PISTON)
                || state.isOf(Blocks.HOPPER)
                || state.isOf(Blocks.DISPENSER)
                || state.isOf(Blocks.DROPPER)
                || state.isOf(Blocks.REDSTONE_WIRE);
        } catch (Exception var3) {
            return false;
        }
    }

    private boolean isUnderDeepslate(BlockPos pos) {
        try {
            return pos.getY() < 0;
        } catch (Exception var3) {
            return false;
        }
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        try {
            if ((Boolean)this.playerPacketDetection.get() && this.mc.world != null && this.mc.player != null) {
                this.scanForPlayersInWorld();
                this.cleanupOldPlayerChunks();
            }

            if (event.packet instanceof BlockEntityUpdateS2CPacket) {
                this.handleBlockEntityUpdate((BlockEntityUpdateS2CPacket)event.packet);
            }

            if (event.packet instanceof ChunkDataS2CPacket) {
                this.handleChunkDataPacket((ChunkDataS2CPacket)event.packet);
            }

            if ((Boolean)this.packetAntiEspBypass.get() && event.packet instanceof BlockUpdateS2CPacket) {
                this.handleRedstonePacket((BlockUpdateS2CPacket)event.packet);
            }

            if ((Boolean)this.packetAntiEspBypass.get() && event.packet instanceof ParticleS2CPacket) {
                this.handleParticlePacket((ParticleS2CPacket)event.packet);
            }

            if ((Boolean)this.packetAntiEspBypass.get() && event.packet instanceof PlaySoundS2CPacket) {
                this.handleSoundPacket((PlaySoundS2CPacket)event.packet);
            }

            if ((Boolean)this.packetAntiEspBypass.get() && event.packet instanceof EntitySpawnS2CPacket s) {
                this.handleEntitySpawn(s);
                this.applyEntitySpawnAntiEsp(s);
            }

            if (event.packet instanceof EntityStatusS2CPacket) {
                this.handleEntityStatus((EntityStatusS2CPacket)event.packet);
            }

            if (event.packet instanceof EntitiesDestroyS2CPacket) {
                this.handleEntityDestroy((EntitiesDestroyS2CPacket)event.packet);
            }

            if (event.packet instanceof ScoreboardObjectiveUpdateS2CPacket) {
                this.handleScoreboardUpdate((ScoreboardObjectiveUpdateS2CPacket)event.packet);
            }

            if ((Boolean)this.antiEspMode.get() && this.mc.world != null) {
                this.scanForSpawnersDirect();
                this.cleanupOldSpawnerChunks();
            }
        } catch (Exception var4) {
        }
    }

    private void handleBlockEntityUpdate(BlockEntityUpdateS2CPacket packet) {
        try {
            BlockPos pos = packet.getPos();
            ChunkPos chunkPos = new ChunkPos(pos);
            if (this.mc.world != null && this.mc.world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner) {
                if ((Boolean)this.onlyOnBedrock.get() && spawner.getPos().getY() != 0) {
                    return;
                }

                if ((Boolean)this.strictValidation.get() && !this.isValidSpawner(spawner)) {
                    return;
                }

                this.spawnerChunks.add(chunkPos);
            }
        } catch (Exception var6) {
        }
    }

    private void handleEntityStatus(EntityStatusS2CPacket packet) {
        try {
            System.out.println("EntityStatus: " + packet.toString());
        } catch (Exception var3) {
            System.out.println("EntityStatus packet received");
        }
    }

    private void handleScoreboardUpdate(ScoreboardObjectiveUpdateS2CPacket packet) {
        try {
            System.out.println("Scoreboard Update: " + packet.toString());
        } catch (Exception var3) {
            System.out.println("Scoreboard Update received");
        }
    }

    private void handleEntityDestroy(EntitiesDestroyS2CPacket packet) {
        try {
            System.out.println("EntityDestroy: IDs=" + packet.getEntityIds().toString());
        } catch (Exception var3) {
        }
    }

    private void handleChunkDataPacket(ChunkDataS2CPacket packet) {
        try {
            System.out.println("ChunkData packet received");
            if (this.mc.world != null) {
                ChunkPos playerChunk = this.mc.player.getChunkPos();
                int range = 3;

                for (int dx = -range; dx <= range; dx++) {
                    for (int dz = -range; dz <= range; dz++) {
                        ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                        if (this.mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                            WorldChunk chunk = this.mc.world.getChunk(chunkPos.x, chunkPos.z);
                            if (chunk != null) {
                                this.scanChunkForSpawners(chunk, chunkPos);
                            }
                        }
                    }
                }
            }
        } catch (Exception var8) {
        }
    }

    private void cleanupOldPlayerChunks() {
        if (this.mc.world != null && this.mc.player != null) {
            try {
                this.playerChunks.removeIf(chunkPos -> {
                    for (PlayerEntity player : this.mc.world.getPlayers()) {
                        if (player != null && player.getChunkPos().equals(chunkPos)) {
                            double y = player.getY();
                            if ((Boolean)this.detectOnBedrock.get() && y <= -64.0 || (Boolean)this.detectUnderDeepslate.get() && y < 0.0) {
                                return false;
                            }
                        }
                    }

                    return true;
                });
                this.playerPositions.removeIf(pos -> {
                    for (PlayerEntity player : this.mc.world.getPlayers()) {
                        if (player != null) {
                            BlockPos playerPos = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ());
                            if (pos.equals(playerPos)) {
                                double y = player.getY();
                                return (!(Boolean)this.detectOnBedrock.get() || !(y <= -64.0)) && (!(Boolean)this.detectUnderDeepslate.get() || !(y < 0.0));
                            }
                        }
                    }

                    return true;
                });
            } catch (Exception var2) {
                this.playerChunks.clear();
                this.playerPositions.clear();
            }
        }
    }

    private void cleanupOldSpawnerChunks() {
        if (this.mc.player != null) {
            try {
                ChunkPos playerChunk = this.mc.player.getChunkPos();
                int maxDistance = 2;
                this.spawnerChunks.removeIf(chunkPos -> {
                    int distance = Math.max(Math.abs(chunkPos.x - playerChunk.x), Math.abs(chunkPos.z - playerChunk.z));
                    return distance > maxDistance;
                });
            } catch (Exception var3) {
            }
        }
    }

    private void scanForSpawnersDirect() {
        if (this.mc.world != null && this.mc.player != null) {
            try {
                ChunkPos playerChunkPos = this.mc.player.getChunkPos();
                int range = 3;

                for (int dx = -range; dx <= range; dx++) {
                    for (int dz = -range; dz <= range; dz++) {
                        ChunkPos chunkPos = new ChunkPos(playerChunkPos.x + dx, playerChunkPos.z + dz);
                        if (this.mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                            WorldChunk chunk = this.mc.world.getChunk(chunkPos.x, chunkPos.z);
                            if (chunk != null) {
                                this.scanChunkForSpawners(chunk, chunkPos);
                            }
                        }
                    }
                }
            } catch (Exception var7) {
            }
        }
    }

    private void scanChunkForSpawners(WorldChunk chunk, ChunkPos chunkPos) {
        if (chunk != null) {
            try {
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof MobSpawnerBlockEntity spawner) {
                        int spawnerY = spawner.getPos().getY();
                        if ((!(Boolean)this.onlyOnBedrock.get() || spawnerY == 0 || (Boolean)this.packetAntiEspBypass.get() && spawnerY <= 0)
                            && (!(Boolean)this.strictValidation.get() || this.isValidSpawner(spawner))) {
                            this.spawnerChunks.removeIf(existingChunkPos -> {
                                try {
                                    WorldChunk existingChunk = this.mc.world.getChunk(existingChunkPos.x, existingChunkPos.z);
                                    boolean hasSpawner = false;

                                    for (BlockEntity be : existingChunk.getBlockEntities().values()) {
                                        if (be instanceof MobSpawnerBlockEntity) {
                                            hasSpawner = true;
                                            break;
                                        }
                                    }

                                    return !hasSpawner;
                                } catch (Exception var6x) {
                                    return true;
                                }
                            });
                            this.spawnerChunks.add(chunkPos);
                            return;
                        }
                    }
                }

                if ((Boolean)this.packetAntiEspBypass.get() && this.hasSpawnerIndicators(chunk)) {
                    this.spawnerChunks.add(chunkPos);
                }

                this.scanChunkForStorage(chunk, chunkPos);
            } catch (Exception var8) {
                if ((Boolean)this.packetAntiEspBypass.get()) {
                    try {
                        if (this.hasSpawnerIndicators(chunk)) {
                            this.spawnerChunks.add(chunkPos);
                        }
                    } catch (Exception var7) {
                    }
                }
            }
        }
    }

    private void scanForPlayersInWorld() {
        if (this.mc.world != null && this.mc.player != null) {
            try {
                for (PlayerEntity player : this.mc.world.getPlayers()) {
                    if (player != null) {
                        double x = player.getX();
                        double y = player.getY();
                        double z = player.getZ();
                        boolean shouldDetect = false;
                        if ((Boolean)this.detectOnBedrock.get() && y <= -64.0) {
                            shouldDetect = true;
                        } else if ((Boolean)this.detectUnderDeepslate.get() && y < 0.0) {
                            shouldDetect = true;
                        } else if ((Boolean)this.packetAntiEspBypass.get() && y <= 0.0) {
                            shouldDetect = true;
                        }

                        if (shouldDetect) {
                            BlockPos pos = new BlockPos((int)x, (int)y, (int)z);
                            this.playerPositions.add(pos);
                            ChunkPos chunkPos = new ChunkPos(pos);
                            this.playerChunks
                                .removeIf(
                                    existingChunk -> {
                                        for (BlockEntity blockEntity : this.mc
                                            .world
                                            .getChunk(existingChunk.x, existingChunk.z)
                                            .getBlockEntities()
                                            .values()) {
                                            if (blockEntity instanceof MobSpawnerBlockEntity) {
                                                return false;
                                            }
                                        }

                                        return true;
                                    }
                                );
                            this.playerChunks.add(chunkPos);
                            if ((Boolean)this.packetAntiEspBypass.get()) {
                                new Thread(() -> {
                                    try {
                                        Thread.sleep((long)((Integer)this.scanDelay.get()).intValue());
                                        this.validatePlayerPosition(pos, chunkPos);
                                    } catch (InterruptedException var4) {
                                    }
                                }).start();
                            }
                        }
                    }
                }
            } catch (Exception var12) {
            }
        }
    }

    private void validatePlayerPosition(BlockPos pos, ChunkPos chunkPos) {
        if (this.mc.world != null) {
            try {
                if (this.mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)
                    || this.mc.world.getBlockState(pos.down()).isOf(Blocks.BEDROCK)
                    || this.mc.world.getBlockState(pos).isOf(Blocks.DEEPSLATE)
                    || this.mc.world.getBlockState(pos.down()).isOf(Blocks.DEEPSLATE)) {
                    return;
                }

                this.playerPositions.remove(pos);
                this.playerChunks.remove(chunkPos);
            } catch (Exception var4) {
                this.playerPositions.remove(pos);
                this.playerChunks.remove(chunkPos);
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        ChunkPos chunkPos = event.chunk().getPos();
        this.pendingChunks.add(chunkPos);
        if ((Boolean)this.antiEspMode.get()) {
            for (int i = 0; i < this.maxRetries.get(); i++) {
                int retry = i;
                new Thread(() -> {
                    try {
                        Thread.sleep((long)((Integer)this.scanDelay.get() * (retry + 1)));
                        if ((Boolean)this.playerBasedDetection.get()) {
                            this.scanChunkPlayerBased(event.chunk(), retry);
                        } else {
                            this.scanChunkStandard(event.chunk(), retry);
                        }

                        if ((Boolean)this.serverSideBypass.get() && retry == (Integer)this.maxRetries.get() - 1) {
                            this.scanChunkServerBypass(event.chunk());
                        }
                    } catch (InterruptedException var4x) {
                    }
                }).start();
            }
        } else {
            new Thread(() -> this.scanChunkStandard(event.chunk(), 0)).start();
        }
    }

    private void scanChunkStandard(WorldChunk chunk, int retry) {
        if (chunk != null) {
            ChunkPos pos = chunk.getPos();

            try {
                if ((Boolean)this.requirePlayerPresence.get() && !this.isPlayerNearChunk(pos)) {
                    return;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof MobSpawnerBlockEntity spawner
                        && (!(Boolean)this.onlyOnBedrock.get() || spawner.getPos().getY() == 0)
                        && (!(Boolean)this.strictValidation.get() || this.isValidSpawner(spawner))) {
                        this.spawnerChunks.add(pos);
                        this.pendingChunks.remove(pos);
                        return;
                    }
                }
            } catch (Exception var7) {
            }

            if (retry == (Integer)this.maxRetries.get() - 1) {
                this.spawnerChunks.remove(pos);
                this.pendingChunks.remove(pos);
            }
        }
    }

    private void scanChunkPlayerBased(WorldChunk chunk, int retry) {
        if (chunk != null) {
            ChunkPos pos = chunk.getPos();

            try {
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof MobSpawnerBlockEntity spawner
                        && (!(Boolean)this.onlyOnBedrock.get() || spawner.getPos().getY() == 0)
                        && (!(Boolean)this.strictValidation.get() || this.isValidSpawner(spawner))) {
                        this.spawnerChunks.add(pos);
                        this.pendingChunks.remove(pos);
                        return;
                    }
                }
            } catch (Exception var7) {
            }

            if (retry == (Integer)this.maxRetries.get() - 1) {
                this.pendingChunks.remove(pos);
            }
        }
    }

    private void scanChunkServerBypass(WorldChunk chunk) {
        if (chunk != null) {
            ChunkPos pos = chunk.getPos();

            try {
                int suspiciousBlocks = 0;
                int totalBlocks = 0;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    totalBlocks++;
                    if (blockEntity != null) {
                        if (blockEntity.getCachedState().isOpaque() && blockEntity.getCachedState().hasBlockEntity()) {
                            suspiciousBlocks++;
                        }

                        if (blockEntity instanceof MobSpawnerBlockEntity
                            && (!(Boolean)this.onlyOnBedrock.get() || blockEntity.getPos().getY() == 0)) {
                            this.spawnerChunks.add(pos);
                            this.pendingChunks.remove(pos);
                            return;
                        }
                    }
                }

                if (totalBlocks > 0 && (double)suspiciousBlocks >= (double)totalBlocks * 0.3) {
                    this.spawnerChunks.add(pos);
                    this.pendingChunks.remove(pos);
                    return;
                }
            } catch (Exception var7) {
            }

            this.pendingChunks.remove(pos);
        }
    }

    private boolean hasSpawnerIndicators(WorldChunk chunk) {
        try {
            int spawnerLikeCount = 0;
            int totalEntities = 0;

            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                totalEntities++;
                if (blockEntity != null) {
                    if (!(blockEntity instanceof EnderChestBlockEntity) && !blockEntity.getCachedState().isOf(Blocks.SHULKER_BOX)) {
                        if ((
                                blockEntity.getCachedState().isOf(Blocks.CHEST)
                                    || blockEntity.getCachedState().isOf(Blocks.TRAPPED_CHEST)
                                    || blockEntity.getCachedState().isOf(Blocks.BARREL)
                            )
                            && blockEntity.getPos().getY() < 0) {
                            this.storageChunks.add(chunk.getPos());
                        }
                    } else if (blockEntity.getPos().getY() < 0) {
                        this.greenBaseChunks.add(chunk.getPos());
                        this.storageChunks.add(chunk.getPos());
                    }

                    if (blockEntity.getCachedState().isOpaque() && blockEntity.getCachedState().hasBlockEntity()) {
                        spawnerLikeCount++;
                    }

                    if (blockEntity instanceof MobSpawnerBlockEntity && blockEntity.getPos().getY() < 0) {
                        return true;
                    }
                }
            }

            int threshold = 1;
            return spawnerLikeCount >= threshold && totalEntities > 0;
        } catch (Exception var6) {
            return chunk.getBlockEntities().size() > 0;
        }
    }

    private boolean isPlayerNearChunk(ChunkPos pos) {
        if (this.mc.player == null) {
            return false;
        } else {
            double distance = Math.sqrt(
                Math.pow(this.mc.player.getX() - (double)(pos.x * 16 + 8), 2.0)
                    + Math.pow(this.mc.player.getZ() - (double)(pos.z * 16 + 8), 2.0)
            );
            return distance < 96.0;
        }
    }

    private boolean isValidSpawner(MobSpawnerBlockEntity spawner) {
        try {
            if (spawner == null) {
                return false;
            } else {
                BlockPos pos = spawner.getPos();
                if (pos != null && this.mc.world != null) {
                    return !this.mc.world.getBlockState(pos).isOf(Blocks.SPAWNER) ? false : spawner.getLogic() != null;
                } else {
                    return false;
                }
            }
        } catch (Exception var3) {
            return false;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.world != null) {
            for (ChunkPos chunkPos : this.spawnerChunks) {
                this.renderChunkBox(event, chunkPos, new SettingColor(0, 255, 0, 75));
            }

            for (ChunkPos chunkPos : this.greenBaseChunks) {
                this.renderChunkBox(event, chunkPos, new SettingColor(0, 255, 100, 100));
            }

            for (ChunkPos chunkPos : this.playerChunks) {
                this.renderChunkBox(event, chunkPos, new SettingColor(0, 100, 255, 75));
            }

            for (ChunkPos chunkPos : this.entityChunks) {
                this.renderChunkBox(event, chunkPos, new SettingColor(255, 165, 0, 100));
            }

            for (ChunkPos chunkPos : this.redstoneChunks) {
                this.renderChunkBox(event, chunkPos, new SettingColor(255, 0, 0, 75));
            }

            for (ChunkPos chunkPos : this.activeBaseChunks) {
                this.renderChunkBox(event, chunkPos, new SettingColor(128, 0, 255, 100));
            }

            for (ChunkPos chunkPos : this.storageChunks) {
                this.renderChunkBox(event, chunkPos, new SettingColor(255, 215, 0, 100));
            }

            if ((Boolean)this.antiEspMode.get() && (Boolean)this.showPendingChunks.get()) {
                for (ChunkPos c : this.pendingChunks) {
                    this.renderChunkBox(event, c, new SettingColor(255, 255, 0, 50));
                }
            }
        }
    }

    private void renderChunkBox(Render3DEvent event, ChunkPos chunkPos, SettingColor color) {
        double x1 = (double)chunkPos.getStartX();
        double z1 = (double)chunkPos.getStartZ();
        double x2 = (double)(chunkPos.getEndX() + 1);
        double z2 = (double)(chunkPos.getEndZ() + 1);
        double y1;
        double y2;
        if (this.renderMode.get() == Finder.RenderMode.Flat) {
            y1 = (double)((Integer)this.flatRenderY.get()).intValue();
            y2 = y1 + 1.0;
        } else {
            y1 = (double)this.mc.world.getBottomY();
            y2 = 320.0;
        }

        event.renderer.box(x1, y1, z1, x2, y2, z2, color, color, ShapeMode.Both, 0);
    }

    public void findNearbySpawners(PlayerEntity player, int radius) {
        if (player != null && this.mc.world != null) {
            World world = this.mc.world;
            BlockPos playerPos = player.getBlockPos();

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = playerPos.add(dx, dy, dz);
                        BlockState state = world.getBlockState(pos);
                        if (state.isOf(Blocks.SPAWNER)) {
                            if (world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner) {
                                player.sendMessage(Text.of("Spawner gefunden bei: " + pos), false);
                            } else {
                                player.sendMessage(Text.of("Spawner gefunden bei: " + pos), false);
                            }
                        }
                    }
                }
            }
        }
    }

    public int getSpawnerChunkCount() {
        return this.spawnerChunks.size();
    }

    public int getPlayerChunkCount() {
        return this.playerChunks.size();
    }

    public int getEntityChunkCount() {
        return this.entityChunks.size();
    }

    public int getRedstoneChunkCount() {
        return this.redstoneChunks.size();
    }

    public int getActiveBaseChunkCount() {
        return this.activeBaseChunks.size();
    }

    public boolean isSpawnerChunk(ChunkPos pos) {
        return this.spawnerChunks.contains(pos);
    }

    public boolean isPlayerChunk(ChunkPos pos) {
        return this.playerChunks.contains(pos);
    }

    public boolean isEntityChunk(ChunkPos pos) {
        return this.entityChunks.contains(pos);
    }

    public boolean isRedstoneChunk(ChunkPos pos) {
        return this.redstoneChunks.contains(pos);
    }

    public boolean isActiveBaseChunk(ChunkPos pos) {
        return this.activeBaseChunks.contains(pos);
    }

    public void clearCache() {
        this.spawnerChunks.clear();
        this.pendingChunks.clear();
        this.playerChunks.clear();
        this.playerPositions.clear();
        this.flaggedEntities.clear();
        this.entityChunks.clear();
        this.redstoneChunks.clear();
        this.activeBaseChunks.clear();
        this.greenBaseChunks.clear();
        this.redstoneActivity.clear();
        this.particleActivity.clear();
        this.soundActivity.clear();
        this.storageChunks.clear();
        this.lastActivityTime.clear();
    }

    private void scanChunkForStorage(WorldChunk chunk, ChunkPos chunkPos) {
        if (chunk != null) {
            try {
                int storageCount = 0;
                int playerMadeCount = 0;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockState state = be.getCachedState();
                    if (be instanceof EnderChestBlockEntity || state.isOf(Blocks.SHULKER_BOX)) {
                        playerMadeCount++;
                        storageCount++;
                        if (be.getPos().getY() < 0) {
                            this.greenBaseChunks.add(chunkPos);
                        }
                    } else if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL)) {
                        storageCount++;
                    } else if (state.isOf(Blocks.HOPPER) || state.isOf(Blocks.DROPPER)) {
                        playerMadeCount++;
                        if (be.getPos().getY() < 0) {
                            this.greenBaseChunks.add(chunkPos);
                        }
                    }
                }

                if (playerMadeCount >= 1 || storageCount >= 5) {
                    this.storageChunks.add(chunkPos);
                    if (playerMadeCount >= 1 && storageCount >= 2) {
                        this.activeBaseChunks.add(chunkPos);
                    }
                }
            } catch (Exception var8) {
            }
        }
    }

    private int extractEntityId(EntitySpawnS2CPacket packet) {
        try {
            for (Method m : packet.getClass().getMethods()) {
                if (m.getReturnType() == int.class && m.getParameterCount() == 0) {
                    String n = m.getName().toLowerCase();
                    if (n.equals("getid") || n.equals("getentityid") || n.equals("id") || n.equals("entityid")) {
                        return (Integer)m.invoke(packet);
                    }
                }
            }
        } catch (Exception var7) {
        }

        return -1;
    }

    private void applyEntitySpawnAntiEsp(EntitySpawnS2CPacket spawnPacket) {
        double x = spawnPacket.getX();
        double y = spawnPacket.getY();
        double z = spawnPacket.getZ();
        int entityId = this.extractEntityId(spawnPacket);
        if (entityId != -1) {
            BlockPos pos = new BlockPos((int)x, (int)y, (int)z);
            BlockState stateBelow = this.getBlockState(pos.down());
            if (stateBelow != null && (stateBelow.isOf(Blocks.DEEPSLATE) || stateBelow.isOf(Blocks.BEDROCK))) {
                this.flagEntityIdAt(entityId, pos);
            }
        }
    }

    private void flagEntityIdAt(int entityId, BlockPos pos) {
        try {
            this.flaggedEntities.add(entityId);
            ChunkPos chunkPos = new ChunkPos(pos);
            this.entityChunks.add(chunkPos);
        } catch (Exception var4) {
        }
    }

    private BlockState getBlockState(BlockPos pos) {
        return this.mc.world != null ? this.mc.world.getBlockState(pos) : null;
    }

    private static enum RenderMode {
        Pillar,
        Flat;
    }
}