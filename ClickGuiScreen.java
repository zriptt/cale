package com.caleon.client.gui;

import com.caleon.client.module.Category;
import com.caleon.client.module.Module;
import com.caleon.client.module.ModuleManager;
import com.caleon.client.module.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private static final int W = 100, ROW = 12, HEAD = 14, GAP = 10;
    private static final int ACCENT = 0xFF6EA8FF;

    private record Hit(int x, int y, Module m, Setting s) {}

    public ClickGuiScreen() { super(Text.literal("Caleon Client")); }

    @Override public boolean shouldPause() { return false; }

    private List<Hit> hits() {
        List<Hit> out = new ArrayList<>();
        int i = 0;
        for (Category c : Category.values()) {
            int x = 10 + i++ * (W + GAP), y = 10 + HEAD;
            for (Module m : ModuleManager.MODULES) {
                if (m.category != c) continue;
                out.add(new Hit(x, y, m, null)); y += ROW;
                if (m.expanded) for (Setting s : m.settings) { out.add(new Hit(x, y, m, s)); y += ROW; }
            }
        }
        return out;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
        int i = 0;
        for (Category c : Category.values()) {
            int x = 10 + i++ * (W + GAP);
            ctx.fill(x, 10, x + W, 10 + HEAD, 0xFF14161C);
            ctx.drawTextWithShadow(textRenderer, c.label, x + 4, 13, ACCENT);
        }
        for (Hit h : hits()) {
            boolean over = mouseX >= h.x && mouseX < h.x + W && mouseY >= h.y && mouseY < h.y + ROW;
            ctx.fill(h.x, h.y, h.x + W, h.y + ROW, over ? 0xFF262A35 : 0xFF1B1E27);
            if (h.s == null) {
                if (h.m.enabled) ctx.fill(h.x, h.y, h.x + 2, h.y + ROW, ACCENT);
                ctx.drawTextWithShadow(textRenderer, h.m.name, h.x + 5, h.y + 2, h.m.enabled ? ACCENT : 0xFFCCCCCC);
                if (!h.m.settings.isEmpty())
                    ctx.drawTextWithShadow(textRenderer, h.m.expanded ? "-" : "+", h.x + W - 9, h.y + 2, 0xFF888888);
            } else if (h.s instanceof Setting.Bool b) {
                ctx.fill(h.x, h.y, h.x + W, h.y + ROW, 0xFF11131A);
                ctx.drawTextWithShadow(textRenderer, b.name, h.x + 8, h.y + 2, 0xFFAAAAAA);
                ctx.drawTextWithShadow(textRenderer, b.value ? "ON" : "OFF", h.x + W - 22, h.y + 2, b.value ? ACCENT : 0xFF777777);
            } else if (h.s instanceof Setting.Num n) {
                ctx.fill(h.x, h.y, h.x + W, h.y + ROW, 0xFF11131A);
                int fillW = (int) (W * (n.value - n.min) / (n.max - n.min));
                ctx.fill(h.x, h.y, h.x + fillW, h.y + ROW, 0x556EA8FF);
                ctx.drawTextWithShadow(textRenderer, n.name, h.x + 8, h.y + 2, 0xFFAAAAAA);
                String v = String.format("%.1f", n.value);
                ctx.drawTextWithShadow(textRenderer, v, h.x + W - 4 - textRenderer.getWidth(v), h.y + 2, 0xFFFFFFFF);
            }
        }
    }

    private void handle(double mx, double my, int button) {
        for (Hit h : hits()) {
            if (mx < h.x || mx >= h.x + W || my < h.y || my >= h.y + ROW) continue;
            if (h.s == null) {
                if (button == 0) h.m.toggle();
                else if (button == 1 && !h.m.settings.isEmpty()) h.m.expanded = !h.m.expanded;
            } else if (h.s instanceof Setting.Bool b) {
                if (button == 0) b.value = !b.value;
            } else if (h.s instanceof Setting.Num n) {
                double t = Math.max(0, Math.min(1, (mx - h.x) / W));
                n.value = Math.round((n.min + t * (n.max - n.min)) * 10.0) / 10.0;
            }
            return;
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) { handle(mx, my, button); return true; }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0) {
            for (Hit h : hits()) if (h.s instanceof Setting.Num && my >= h.y && my < h.y + ROW && mx >= h.x - 30 && mx < h.x + W + 30) {
                handle(mx, my, 0); break;
            }
        }
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
