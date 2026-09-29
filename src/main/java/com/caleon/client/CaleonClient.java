package com.caleon.client;

import com.caleon.client.gui.ClickGuiScreen;
import com.caleon.client.module.Module;
import com.caleon.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CaleonClient implements ClientModInitializer {
    private static KeyBinding openGui;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.caleonclient.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.caleonclient"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openGui.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
            ModuleManager.tick();
        });

        WorldRenderEvents.LAST.register(ctx -> {
            var ms = ctx.matrixStack();
            if (ms == null) return;
            ModuleManager.render(ms.peek().getPositionMatrix(), ctx.camera().getPos());
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            var hud = ModuleManager.hud;
            if (hud == null || !hud.enabled) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            if (hud.watermark.value) ctx.drawTextWithShadow(mc.textRenderer, "Caleon Client", 4, 4, 0xFF6EA8FF);
            if (hud.arrayList.value) {
                List<Module> on = new ArrayList<>();
                for (Module m : ModuleManager.MODULES) if (m.enabled && m != hud) on.add(m);
                on.sort(Comparator.comparingInt((Module m) -> -mc.textRenderer.getWidth(m.name)));
                int y = 4, w = ctx.getScaledWindowWidth();
                for (Module m : on) {
                    ctx.drawTextWithShadow(mc.textRenderer, m.name, w - mc.textRenderer.getWidth(m.name) - 4, y, 0xFFFFFFFF);
                    y += 10;
                }
            }
        });
    }
}
