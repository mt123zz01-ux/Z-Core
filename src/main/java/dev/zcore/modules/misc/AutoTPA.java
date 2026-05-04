package dev.zcore.modules.misc;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.concurrent.ThreadLocalRandom;

/** AutoTPA - sends /tpa or /tpahere with a random delay. */
public class AutoTPA extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> playerName = sgGeneral.add(new StringSetting.Builder()
        .name("player")
        .description("Player name to send the TPA command to.")
        .defaultValue("DrDonutt")
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Command type to send.")
        .defaultValue(Mode.TPAHERE)
        .build()
    );

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Minimum delay between commands, in seconds.")
        .defaultValue(10)
        .min(1)
        .max(300)
        .sliderMin(1)
        .sliderMax(120)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Maximum delay between commands, in seconds.")
        .defaultValue(30)
        .min(1)
        .max(300)
        .sliderMin(1)
        .sliderMax(120)
        .build()
    );

    private int delayTicks;

    public AutoTPA() {
        super(ZCoreCat.UTILITY, "auto-tpa", "Automatically sends /tpa or /tpahere to the selected player.");
    }

    @Override
    public void onActivate() {
        delayTicks = 0;
    }

    @Override
    public void onDeactivate() {
        delayTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        String name = playerName.get();
        if (name == null || name.trim().isEmpty()) {
            warning("AutoTPA: player name is empty.");
            scheduleNextDelay();
            return;
        }

        String command = (mode.get() == Mode.TPA ? "tpa " : "tpahere ") + name.trim();
        mc.getNetworkHandler().sendChatCommand(command);
        scheduleNextDelay();
    }

    private void scheduleNextDelay() {
        int min = Math.max(1, minDelay.get());
        int max = Math.max(min, maxDelay.get());
        delayTicks = ThreadLocalRandom.current().nextInt(min, max + 1) * 20;
    }

    public enum Mode {
        TPA,
        TPAHERE
    }
}
