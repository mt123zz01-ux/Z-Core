package dev.zcore.modules.misc;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;

/**
 * HideScoreboard - hides the client-side sidebar scoreboard and restores it when disabled.
 */
public class HideScoreboard extends Module {
    private ScoreboardObjective savedObjective;

    public HideScoreboard() {
        super(ZCoreCat.UTILITY, "hide-scoreboard", "Hides the client-side sidebar scoreboard.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective current = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        if (current != null) {
            if (savedObjective == null) savedObjective = current;
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.world == null || savedObjective == null) return;
        mc.world.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, savedObjective);
        savedObjective = null;
    }
}
