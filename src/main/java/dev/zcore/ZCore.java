package dev.zcore;

import dev.zcore.modules.combat.TriggerBot;
import dev.zcore.modules.combat.InvTotem;
import dev.zcore.modules.misc.PlayerLeaveLog;
import dev.zcore.modules.misc.HideScoreboard;
import dev.zcore.modules.misc.AutoTPA;
import dev.zcore.modules.misc.FakePlayer;
import dev.zcore.modules.misc.TunnelBaseFinder;
import dev.zcore.modules.misc.SpawnerProtect;
import dev.zcore.modules.movement.AutoMine;
import dev.zcore.modules.render.BedrockVoidEsp;
import dev.zcore.modules.render.SpawnerNotifier;
import dev.zcore.modules.render.Finder;
import dev.zcore.modules.combat.MaceSwap;
import dev.zcore.modules.combat.AnchorMacrov2;
import dev.zcore.modules.combat.CrystalMacro;
import dev.zcore.modules.misc.FakeElytra;
import dev.zcore.modules.misc.SpawnerDropper;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZCore extends MeteorAddon {

    public static final Logger LOG = LoggerFactory.getLogger("Z-Core");

    @Override
    public void onInitialize() {
        LOG.info("Z-Core addon initializing...");

        // Register combat modules.
        Modules.get().add(new TriggerBot());
        Modules.get().add(new InvTotem());
        Modules.get().add(new CrystalMacro());
        Modules.get().add(new AnchorMacrov2());
        Modules.get().add(new MaceSwap());

        // Register utility modules.
        Modules.get().add(new AutoMine());
        Modules.get().add(new SpawnerProtect());
        Modules.get().add(new PlayerLeaveLog());
        Modules.get().add(new HideScoreboard());
        Modules.get().add(new AutoTPA());
        Modules.get().add(new FakePlayer());
        Modules.get().add(new SpawnerDropper());
        Modules.get().add(new FakeElytra());
        Modules.get().add(new TunnelBaseFinder());
        Modules.get().add(new BedrockVoidEsp());
        Modules.get().add(new SpawnerNotifier());
        Modules.get().add(new Finder());

        LOG.info("Z-Core loaded successfully!");
    }


    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(ZCoreCat.COMBAT);
        Modules.registerCategory(ZCoreCat.UTILITY);
    }

    @Override
    public String getPackage() {
        return "dev.zcore";
    }
}
