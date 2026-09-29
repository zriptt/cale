package com.caleon.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Draws translucent filled boxes that show through walls. */
public class RenderUtil {
    private static BufferBuilder buf;

    public static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        buf = null;
    }

    public static void end() {
        if (buf != null) {
            BuiltBuffer built = buf.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
            buf = null;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void box(Matrix4f m, Vec3d cam, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (buf == null) buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float ax = (float) (x1 - cam.x), ay = (float) (y1 - cam.y), az = (float) (z1 - cam.z);
        float bx = (float) (x2 - cam.x), by = (float) (y2 - cam.y), bz = (float) (z2 - cam.z);
        // bottom, top
        quad(m, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz, argb);
        quad(m, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, argb);
        // north, south
        quad(m, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, argb);
        quad(m, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz, argb);
        // west, east
        quad(m, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az, argb);
        quad(m, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, argb);
    }

    private static void quad(Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int argb) {
        buf.vertex(m, x1, y1, z1).color(argb);
        buf.vertex(m, x2, y2, z2).color(argb);
        buf.vertex(m, x3, y3, z3).color(argb);
        buf.vertex(m, x4, y4, z4).color(argb);
    }
}
