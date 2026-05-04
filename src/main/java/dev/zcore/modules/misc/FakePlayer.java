package dev.zcore.modules.misc;

import com.mojang.authlib.GameProfile;
import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** FakePlayer - spawns client-side fake players for ESP/radar/combat visual testing. */
public class FakePlayer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maxFakePlayers = sgGeneral.add(new IntSetting.Builder()
        .name("max-players")
        .description("Maximum number of fake players spawned per activation.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> spawnDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("spawn-distance")
        .description("Spawn distance around you for fake players.")
        .defaultValue(3.0)
        .min(1.0)
        .max(10.0)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<String> playerName = sgGeneral.add(new StringSetting.Builder()
        .name("player-name")
        .description("Fake player name.")
        .defaultValue("Steve")
        .build()
    );

    private final Setting<Boolean> copyRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("copy-rotation")
        .description("Copies your yaw/pitch to the fake player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> copyPose = sgGeneral.add(new BoolSetting.Builder()
        .name("copy-pose")
        .description("Copies your current pose.")
        .defaultValue(true)
        .build()
    );

    private final List<OtherClientPlayerEntity> fakePlayers = new ArrayList<>();
    private int spawnModeIndex;
    private int nextEntityId = -100000;

    public FakePlayer() {
        super(ZCoreCat.UTILITY, "fake-player", "Spawns client-side fake players for ESP/radar testing.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        clearFakePlayers();
        spawnModeIndex = 0;
        nextEntityId = -100000;
        for (int i = 0; i < maxFakePlayers.get(); i++) spawnFakePlayer(i + 1);
    }

    @Override
    public void onDeactivate() {
        clearFakePlayers();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;
        fakePlayers.removeIf(fakePlayer -> mc.world.getEntityById(fakePlayer.getId()) == null);
    }

    private void spawnFakePlayer(int index) {
        if (mc.player == null || mc.world == null) return;

        Vec3d spawn = getSpawnPosition();
        String baseName = playerName.get();
        if (baseName == null || baseName.trim().isEmpty()) baseName = "Steve";
        String name = maxFakePlayers.get() == 1 ? baseName.trim() : baseName.trim() + index;

        OtherClientPlayerEntity fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), name));
        fake.setId(nextEntityId--);
        fake.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, mc.player.getYaw(), mc.player.getPitch());
        fake.setVelocity(0, 0, 0);
        fake.setHealth(20.0f);

        if (copyRotation.get()) {
            fake.setYaw(mc.player.getYaw());
            fake.setPitch(mc.player.getPitch());
            fake.headYaw = mc.player.headYaw;
            fake.bodyYaw = mc.player.bodyYaw;
        }

        if (copyPose.get()) fake.setPose(mc.player.getPose());

        mc.world.addEntity(fake);
        fakePlayers.add(fake);
    }

    private Vec3d getSpawnPosition() {
        double dist = spawnDistance.get();
        double yaw = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        Vec3d base = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        return switch (spawnModeIndex++ % 5) {
            case 0 -> base.add(-sin * dist, 0, cos * dist);     // Front
            case 1 -> base.add(sin * dist, 0, -cos * dist);     // Behind
            case 2 -> base.add(cos * dist, 0, sin * dist);      // Left
            case 3 -> base.add(-cos * dist, 0, -sin * dist);    // Right
            default -> base.add(0, dist, 0);                    // Above
        };
    }

    private void clearFakePlayers() {
        if (mc.world == null) {
            fakePlayers.clear();
            return;
        }

        for (OtherClientPlayerEntity fakePlayer : new ArrayList<>(fakePlayers)) {
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakePlayers.clear();
    }
}
