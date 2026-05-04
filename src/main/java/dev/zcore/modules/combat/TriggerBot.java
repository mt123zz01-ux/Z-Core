package dev.zcore.modules.combat;

import dev.zcore.ZCoreCat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * TriggerBot - Automatically attacks the entity targeted by your crosshair.
 *
 * Ported from the foure.dev source to the Meteor Client addon API.
 */
public class TriggerBot extends Module {

    private enum Mode {
        Mace,
        Weapons,
        Mace_And_Weapons,
        All_Items
    }

    // ── Settings ────────────────────────────────────────────────
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelay = settings.createGroup("Delay");
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgCrit = settings.createGroup("Criticals");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Item type allowed for automatic attacks.")
        .defaultValue(Mode.Weapons)
        .build()
    );

    private final Setting<Boolean> workInScreen = sgGeneral.add(new BoolSetting.Builder()
        .name("work-in-screen")
        .description("Allows the module to work while a GUI or screen is open.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> whileUse = sgGeneral.add(new BoolSetting.Builder()
        .name("while-use")
        .description("Allows attacking while holding right-click or using an item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onLeftClick = sgGeneral.add(new BoolSetting.Builder()
        .name("on-left-click")
        .description("Only attacks automatically while the attack key is held.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("Swings your hand when attacking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clickSimulation = sgGeneral.add(new BoolSetting.Builder()
        .name("click-simulation")
        .description("Simulates pressing the attack key after attacking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> swordDelayMin = sgDelay.add(new DoubleSetting.Builder()
        .name("sword-delay-min")
        .description("Minimum delay for sword/all-items mode, in milliseconds.")
        .defaultValue(540)
        .min(1)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Double> swordDelayMax = sgDelay.add(new DoubleSetting.Builder()
        .name("sword-delay-max")
        .description("Maximum delay for sword/all-items mode, in milliseconds.")
        .defaultValue(550)
        .min(1)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Double> axeDelayMin = sgDelay.add(new DoubleSetting.Builder()
        .name("axe-delay-min")
        .description("Minimum delay for axe mode, in milliseconds.")
        .defaultValue(780)
        .min(1)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Double> axeDelayMax = sgDelay.add(new DoubleSetting.Builder()
        .name("axe-delay-max")
        .description("Maximum delay for axe mode, in milliseconds.")
        .defaultValue(800)
        .min(1)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Boolean> checkShield = sgTarget.add(new BoolSetting.Builder()
        .name("check-shield")
        .description("Does not attack players who are using a shield.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> strayBypass = sgTarget.add(new BoolSetting.Builder()
        .name("bypass-mode")
        .description("Allows attacking hostile mobs in addition to players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allEntities = sgTarget.add(new BoolSetting.Builder()
        .name("all-entities")
        .description("Allows attacking any living entity targeted by your crosshair.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sticky = sgTarget.add(new BoolSetting.Builder()
        .name("same-player")
        .description("Only continues attacking the previous target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Does not attack Meteor Friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyCritSword = sgCrit.add(new BoolSetting.Builder()
        .name("only-crit-sword")
        .description("Sword/all-items only attacks when a critical hit is possible.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyCritAxe = sgCrit.add(new BoolSetting.Builder()
        .name("only-crit-axe")
        .description("Axe only attacks when a critical hit is possible.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> whileAscending = sgCrit.add(new BoolSetting.Builder()
        .name("while-ascending")
        .description("Allows attacking while the player is flying or jumping upward.")
        .defaultValue(false)
        .build()
    );

    // ── Internal state ───────────────────────────────────────────
    private long lastAttackTime = 0;
    private int currentSwordDelay = 540;
    private int currentAxeDelay = 780;
    private Entity lastTarget = null;

    public TriggerBot() {
        super(
            ZCoreCat.COMBAT,
            "trigger-bot",
            "Automatically attacks the entity targeted by your crosshair."
        );
    }

    @Override
    public void onActivate() {
        currentSwordDelay = getRandomDelay(swordDelayMin, swordDelayMax);
        currentAxeDelay = getRandomDelay(axeDelayMin, axeDelayMax);
        lastAttackTime = System.currentTimeMillis();
        lastTarget = null;
    }

    @Override
    public void onDeactivate() {
        lastTarget = null;
        if (mc.options != null && mc.options.attackKey != null) {
            mc.options.attackKey.setPressed(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!canRun()) return;

        Item item = mc.player.getMainHandStack().getItem();
        if (!isItemAllowed(item)) return;

        if (isMaceItem(item)) {
            handleMace();
        } else if (isSwordItem(item)) {
            handleSword();
        } else if (isAxeItem(item)) {
            handleAxe();
        } else {
            handleAllItems();
        }
    }

    private boolean canRun() {
        if (!workInScreen.get() && mc.currentScreen != null) return false;

        long handle = mc.getWindow().getHandle();
        if (onLeftClick.get() && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) return false;
        if (!whileUse.get() && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) return false;

        return whileAscending.get() || mc.player.isOnGround() || !(mc.player.getVelocity().y > 0.0);
    }

    private boolean isItemAllowed(Item item) {
        return switch (mode.get()) {
            case Mace -> isMaceItem(item);
            case Weapons -> isSwordItem(item) || isAxeItem(item);
            case Mace_And_Weapons -> isMaceItem(item) || isSwordItem(item) || isAxeItem(item);
            case All_Items -> true;
        };
    }

    private boolean isMaceItem(Item item) {
        return item == Items.MACE;
    }

    private boolean isSwordItem(Item item) {
        return item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD || item == Items.IRON_SWORD ||
            item == Items.GOLDEN_SWORD || item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
    }

    private boolean isAxeItem(Item item) {
        return item == Items.NETHERITE_AXE || item == Items.DIAMOND_AXE || item == Items.IRON_AXE ||
            item == Items.GOLDEN_AXE || item == Items.STONE_AXE || item == Items.WOODEN_AXE;
    }

    private Entity getTarget() {
        HitResult result = mc.crosshairTarget;
        if (!(result instanceof EntityHitResult hit)) return null;

        Entity entity = hit.getEntity();
        if (sticky.get() && lastTarget != null && entity != lastTarget) return null;
        return entity;
    }

    private boolean isValidTarget(Entity entity, boolean critCheck) {
        if (entity == null || !entity.isAlive()) return false;

        boolean typeValid = entity instanceof PlayerEntity ||
            (strayBypass.get() && entity instanceof HostileEntity) ||
            allEntities.get();
        if (!typeValid) return false;

        if (entity instanceof PlayerEntity player) {
            if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
            if (checkShield.get() && player.isBlocking()) return false;
        }

        return !critCheck || canCrit();
    }

    private boolean canCrit() {
        return !mc.player.isOnGround()
            && mc.player.getVelocity().y < 0.0
            && !mc.player.isUsingItem()
            && !mc.player.isSubmergedInWater();
    }

    private void handleMace() {
        Entity entity = getTarget();
        if (isValidTarget(entity, onlyCritSword.get())) {
            doAttack(entity);
        }
    }

    private void handleSword() {
        Entity entity = getTarget();
        if (!isValidTarget(entity, onlyCritSword.get())) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAttackTime >= currentSwordDelay) {
            doAttack(entity);
            currentSwordDelay = getRandomDelay(swordDelayMin, swordDelayMax);
            lastAttackTime = currentTime;
        }
    }

    private void handleAxe() {
        Entity entity = getTarget();
        if (!isValidTarget(entity, onlyCritAxe.get())) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAttackTime >= currentAxeDelay) {
            doAttack(entity);
            currentAxeDelay = getRandomDelay(axeDelayMin, axeDelayMax);
            lastAttackTime = currentTime;
        }
    }

    private void handleAllItems() {
        Entity entity = getTarget();
        if (!isValidTarget(entity, onlyCritSword.get())) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAttackTime >= currentSwordDelay) {
            doAttack(entity);
            currentSwordDelay = getRandomDelay(swordDelayMin, swordDelayMax);
            lastAttackTime = currentTime;
        }
    }

    private void doAttack(Entity entity) {
        if (entity == null) return;

        mc.interactionManager.attackEntity(mc.player, entity);
        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);

        if (clickSimulation.get() && mc.options.attackKey != null) {
            mc.options.attackKey.setPressed(true);
            mc.options.attackKey.setPressed(false);
        }

        lastTarget = entity;
    }

    private int getRandomDelay(Setting<Double> min, Setting<Double> max) {
        int minVal = Math.max(1, min.get().intValue());
        int maxVal = Math.max(1, max.get().intValue());
        if (minVal >= maxVal) return minVal;
        return minVal + (int) (Math.random() * (maxVal - minVal + 1));
    }
}
