package dev.zcore.modules.misc;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PlayerLeaveLog - Lightweight radar that tracks players in range and reports when they disappear.
 *
 * Note: the client can only know that a player disappeared from the entity list or scan range.
 * This can mean logout, teleport, dimension change, or leaving the tracking range.
 */
public class PlayerLeaveLog extends Module {

    // ── Setting groups ───────────────────────────────────────────
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ── Settings ─────────────────────────────────────────────────
    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Radar radius for tracking nearby players.")
        .defaultValue(256)
        .min(8)
        .max(512)
        .sliderMin(8)
        .sliderMax(512)
        .build()
    );

    private final Setting<Integer> confirmDelay = sgGeneral.add(new IntSetting.Builder()
        .name("confirm-delay")
        .description("Seconds to wait before reporting that a player disappeared or logged out.")
        .defaultValue(3)
        .min(0)
        .max(30)
        .sliderMin(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> showCoords = sgGeneral.add(new BoolSetting.Builder()
        .name("show-coords")
        .description("Shows the player's last known coordinates in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("include-friends")
        .description("Also reports players who are in Meteor Friends.")
        .defaultValue(true)
        .build()
    );

    // ── Internal state ───────────────────────────────────────────
    private final Map<UUID, TrackedPlayer> trackedPlayers = new HashMap<>();
    private final Map<UUID, Long> missingSince = new HashMap<>();

    public PlayerLeaveLog() {
        super(
            ZCoreCat.UTILITY,
            "player-leave-log",
            "Reports in chat when a player in radar disappears or logs out."
        );
    }

    @Override
    public void onActivate() {
        trackedPlayers.clear();
        missingSince.clear();
    }

    @Override
    public void onDeactivate() {
        trackedPlayers.clear();
        missingSince.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        double maxDistanceSq = (double) range.get() * range.get();
        Set<UUID> currentlySeen = new HashSet<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player == mc.player) continue;
            if (!includeFriends.get() && Friends.get().isFriend(player)) continue;
            if (mc.player.squaredDistanceTo(player) > maxDistanceSq) continue;

            UUID uuid = player.getUuid();
            String name = player.getName().getString();
            BlockPos pos = player.getBlockPos();

            currentlySeen.add(uuid);
            trackedPlayers.put(uuid, new TrackedPlayer(name, pos.getX(), pos.getY(), pos.getZ()));
            missingSince.remove(uuid);
        }

        Iterator<Map.Entry<UUID, TrackedPlayer>> iterator = trackedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedPlayer> entry = iterator.next();
            UUID uuid = entry.getKey();
            TrackedPlayer tracked = entry.getValue();

            if (currentlySeen.contains(uuid)) continue;

            long firstMissing = missingSince.computeIfAbsent(uuid, ignored -> now);
            if (now - firstMissing < confirmDelay.get() * 1000L) continue;

            sendLeaveMessage(tracked);
            missingSince.remove(uuid);
            iterator.remove();
        }
    }

    private void sendLeaveMessage(TrackedPlayer player) {
        if (showCoords.get()) {
            info("[Z-Core] %s disappeared/logged out at X: %d Y: %d Z: %d", player.name, player.x, player.y, player.z);
        } else {
            info("[Z-Core] %s disappeared/logged out.", player.name);
        }
    }

    private static class TrackedPlayer {
        private final String name;
        private final int x;
        private final int y;
        private final int z;

        private TrackedPlayer(String name, int x, int y, int z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
