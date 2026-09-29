package com.caleon.client.module;

import com.caleon.client.mixin.MinecraftClientAccessor;
import com.caleon.client.render.RenderUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    public static final List<Module> MODULES = new ArrayList<>();
    public static Hud hud;

    public static void init() {
        MODULES.add(new AimAssist());
        MODULES.add(new TriggerBot());
        MODULES.add(new AutoTotem());
        MODULES.add(new Sprint());
        MODULES.add(new AutoWalk());
        MODULES.add(new AutoTool());
        MODULES.add(new FastPlace());
        MODULES.add(new Fullbright());
        MODULES.add(new PlayerEsp());
        MODULES.add(new MobEsp());
        MODULES.add(new BaseModules.HoleEsp());
        MODULES.add(new BaseModules.LightFinder());
        MODULES.add(new BaseModules.SuspiciousEsp());
        MODULES.add(new BaseModules.SusChunkFinder());
        MODULES.add(new BaseModules.StorageEsp());
        MODULES.add(new BaseModules.BlockEsp());
        MODULES.add(new BaseModules.NetheriteFinder());
        MODULES.add(new BaseModules.TunnelBaseFinder());
        hud = new Hud();
        hud.enabled = true;
        MODULES.add(hud);
    }

    public static void tick() {
        if (mcNotReady()) return;
        for (Module m : new ArrayList<>(MODULES)) if (m.enabled) m.onTick();
        BaseScanner.tick();
    }

    public static void render(org.joml.Matrix4f matrix, net.minecraft.util.math.Vec3d cam) {
        if (mcNotReady()) return;
        boolean any = false;
        for (Module m : MODULES) if (m.enabled && m.category == Category.BASEFINDING) any = true;
        if (!any) return;
        RenderUtil.begin();
        for (Module m : MODULES) if (m.enabled) m.onRender(matrix, cam);
        RenderUtil.end();
    }

    private static boolean mcNotReady() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        return mc.player == null || mc.world == null;
    }

    // ---------------- COMBAT ----------------
    static class AimAssist extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 4.5, 3, 8));
        final Setting.Num speed = add(new Setting.Num("Speed", 5, 1, 20));
        AimAssist() { super("Aim Assist", Category.COMBAT); }
        @Override public void onTick() {
            if (mc.currentScreen != null) return;
            PlayerEntity best = null; double bestD = range.value;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || !p.isAlive()) continue;
                double d = mc.player.distanceTo(p);
                if (d < bestD) { bestD = d; best = p; }
            }
            if (best == null) return;
            Vec3d d = best.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
            float yaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
            float s = (float) speed.value;
            float dy = MathHelper.clamp(MathHelper.wrapDegrees(yaw - mc.player.getYaw()), -s, s);
            float dp = MathHelper.clamp(pitch - mc.player.getPitch(), -s, s);
            mc.player.setYaw(mc.player.getYaw() + dy);
            mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + dp, -90f, 90f));
        }
    }

    static class TriggerBot extends Module {
        final Setting.Bool playersOnly = add(new Setting.Bool("Players only", true));
        TriggerBot() { super("Trigger Bot", Category.COMBAT); }
        @Override public void onTick() {
            if (mc.currentScreen != null) return;
            if (mc.crosshairTarget instanceof EntityHitResult r && r.getEntity() instanceof LivingEntity le
                    && le.isAlive() && mc.player.getAttackCooldownProgress(0f) >= 0.95f) {
                if (playersOnly.value && !(le instanceof PlayerEntity)) return;
                mc.interactionManager.attackEntity(mc.player, le);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    static class AutoTotem extends Module {
        AutoTotem() { super("Auto Totem", Category.COMBAT); }
        @Override public void onTick() {
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    int slotId = i < 9 ? i + 36 : i;
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slotId, 40, SlotActionType.SWAP, mc.player);
                    return;
                }
            }
        }
    }

    // ---------------- MISC ----------------
    static class Sprint extends Module {
        Sprint() { super("Sprint", Category.MISC); }
        @Override public void onTick() {
            if (mc.options.forwardKey.isPressed()) mc.options.sprintKey.setPressed(true);
        }
    }

    static class AutoWalk extends Module {
        AutoWalk() { super("Auto Walk", Category.MISC); }
        @Override public void onTick() { mc.options.forwardKey.setPressed(true); }
        @Override public void onDisable() { mc.options.forwardKey.setPressed(false); }
    }

    static class AutoTool extends Module {
        AutoTool() { super("Auto Tool", Category.MISC); }
        @Override public void onTick() {
            if (!mc.options.attackKey.isPressed()) return;
            if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
            var state = mc.world.getBlockState(hit.getBlockPos());
            if (state.isAir()) return;
            int best = mc.player.getInventory().selectedSlot;
            float bestSpeed = mc.player.getInventory().getStack(best).getMiningSpeedMultiplier(state);
            for (int i = 0; i < 9; i++) {
                ItemStack st = mc.player.getInventory().getStack(i);
                float sp = st.getMiningSpeedMultiplier(state);
                if (sp > bestSpeed) { bestSpeed = sp; best = i; }
            }
            mc.player.getInventory().selectedSlot = best;
        }
    }

    static class FastPlace extends Module {
        FastPlace() { super("Fast Place", Category.MISC); }
        @Override public void onTick() { ((MinecraftClientAccessor) mc).caleon$setItemUseCooldown(0); }
    }

    // ---------------- RENDER ----------------
    static class Fullbright extends Module {
        Fullbright() { super("Fullbright", Category.RENDER); }
        @Override public void onTick() {
            ClientPlayerEntity p = mc.player;
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 220)
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false, false));
        }
        @Override public void onDisable() {
            if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    static class PlayerEsp extends Module {
        PlayerEsp() { super("Player ESP", Category.RENDER); }
        @Override public void onTick() {
            for (Entity e : mc.world.getEntities())
                if (e instanceof PlayerEntity && e != mc.player) e.setGlowing(true);
        }
        @Override public void onDisable() {
            if (mc.world == null) return;
            for (Entity e : mc.world.getEntities())
                if (e instanceof PlayerEntity && e != mc.player) e.setGlowing(false);
        }
    }

    static class MobEsp extends Module {
        MobEsp() { super("Mob ESP", Category.RENDER); }
        @Override public void onTick() {
            for (Entity e : mc.world.getEntities()) if (e instanceof HostileEntity) e.setGlowing(true);
        }
        @Override public void onDisable() {
            if (mc.world == null) return;
            for (Entity e : mc.world.getEntities()) if (e instanceof HostileEntity) e.setGlowing(false);
        }
    }

    public static class Hud extends Module {
        public final Setting.Bool watermark = add(new Setting.Bool("Watermark", true));
        public final Setting.Bool arrayList = add(new Setting.Bool("Module list", true));
        Hud() { super("HUD", Category.RENDER); }
    }
}
