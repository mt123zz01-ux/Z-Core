package dev.zcore.modules.misc;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/**
 * TunnelBaseFinder - compact port based on the Radium idea.
 * Scans block entities in newly loaded chunks to detect underground tunnel/base signs.
 */
public class TunnelBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThresholds = settings.createGroup("Thresholds");
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Only counts block entities with Y less than or equal to this value.")
        .defaultValue(0)
        .min(-64)
        .max(320)
        .sliderMin(-64)
        .sliderMax(80)
        .build()
    );

    private final Setting<Boolean> spawnerCritical = sgGeneral.add(new BoolSetting.Builder()
        .name("spawner-critical")
        .description("Reports immediately when a spawner is detected below max-y.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> chestThreshold = sgThresholds.add(new IntSetting.Builder()
        .name("chest-threshold")
        .description("Chest count in a chunk required to suspect a base.")
        .defaultValue(12)
        .min(1)
        .max(80)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> shulkerThreshold = sgThresholds.add(new IntSetting.Builder()
        .name("shulker-threshold")
        .description("Shulker count in a chunk required to suspect a base.")
        .defaultValue(8)
        .min(1)
        .max(80)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> hopperThreshold = sgThresholds.add(new IntSetting.Builder()
        .name("hopper-threshold")
        .description("Hopper count in a chunk required to suspect a farm/storage.")
        .defaultValue(8)
        .min(1)
        .max(80)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> dispenserThreshold = sgThresholds.add(new IntSetting.Builder()
        .name("dispenser-threshold")
        .description("Dispenser/dropper count in a chunk required to suspect a base/farm.")
        .defaultValue(8)
        .min(1)
        .max(80)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> pistonThreshold = sgThresholds.add(new IntSetting.Builder()
        .name("piston-threshold")
        .description("Moving piston count in a chunk required to suspect a machine/base.")
        .defaultValue(6)
        .min(1)
        .max(80)
        .sliderMax(50)
        .build()
    );

    private final Setting<Boolean> disconnectOnFind = sgSafety.add(new BoolSetting.Builder()
        .name("disconnect-on-find")
        .description("Automatically disconnects when a base is detected. Disabled by default to avoid accidental disconnects.")
        .defaultValue(false)
        .build()
    );

    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private final Set<ChunkPos> alertedChunks = new HashSet<>();
    private int basesFound;

    public TunnelBaseFinder() {
        super(ZCoreCat.UTILITY, "tunnel-base-finder", "Scans underground chunks to detect tunnel/base signs.");
    }

    @Override
    public void onActivate() {
        scannedChunks.clear();
        alertedChunks.clear();
        basesFound = 0;
    }

    @Override
    public void onDeactivate() {
        scannedChunks.clear();
        alertedChunks.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.world == null || event == null || event.chunk() == null) return;

        ChunkPos chunkPos = event.chunk().getPos();
        if (!scannedChunks.add(chunkPos)) return;

        ScanResult result = scanChunk(event.chunk().getBlockEntities().values());
        String reason = getReason(result);
        if (reason == null || !alertedChunks.add(chunkPos)) return;

        basesFound++;
        int centerX = chunkPos.x * 16 + 8;
        int centerZ = chunkPos.z * 16 + 8;
        String message = String.format(
            "[Z-Core] TunnelBaseFinder: possible %s at chunk center X: %d Z: %d | chests=%d shulkers=%d hoppers=%d dispensers=%d pistons=%d spawners=%d",
            reason, centerX, centerZ, result.chests, result.shulkers, result.hoppers, result.dispensers, result.pistons, result.spawners
        );

        warning(message);
        if (disconnectOnFind.get() && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(message));
        }
    }

    private ScanResult scanChunk(Iterable<BlockEntity> blockEntities) {
        ScanResult result = new ScanResult();

        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity == null) continue;
            BlockPos pos = blockEntity.getPos();
            if (pos.getY() > maxY.get()) continue;

            if (blockEntity instanceof ChestBlockEntity) result.chests++;
            else if (blockEntity instanceof ShulkerBoxBlockEntity) result.shulkers++;
            else if (blockEntity instanceof HopperBlockEntity) result.hoppers++;
            else if (blockEntity instanceof DispenserBlockEntity) result.dispensers++;
            else if (blockEntity instanceof PistonBlockEntity) result.pistons++;
            else if (blockEntity instanceof MobSpawnerBlockEntity) result.spawners++;
        }

        return result;
    }

    private String getReason(ScanResult result) {
        if (spawnerCritical.get() && result.spawners > 0) return "SPAWNER";
        if (result.chests >= chestThreshold.get()) return "BASE/CHESTS";
        if (result.shulkers >= shulkerThreshold.get()) return "BASE/SHULKERS";
        if (result.hoppers >= hopperThreshold.get()) return "FARM/HOPPERS";
        if (result.dispensers >= dispenserThreshold.get()) return "BASE/DISPENSERS";
        if (result.pistons >= pistonThreshold.get()) return "MACHINE/PISTONS";
        return null;
    }

    @Override
    public String getInfoString() {
        return String.valueOf(basesFound);
    }

    private static class ScanResult {
        int chests;
        int shulkers;
        int hoppers;
        int dispensers;
        int pistons;
        int spawners;
    }
}
