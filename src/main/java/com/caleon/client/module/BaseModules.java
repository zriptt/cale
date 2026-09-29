package com.caleon.client.module;

import com.caleon.client.render.RenderUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Map;

public class BaseModules {
    public static SusChunkFinder susFinder;
    public static HoleEsp holeEsp;
    public static TunnelBaseFinder tunnelFinder;
    public static BlockEsp blockEsp;
    public static NetheriteFinder netheriteFinder;
    public static StorageEsp storageEsp;

    private static boolean near(BlockPos p, Vec3d cam, double range) {
        double dx = p.getX() + 0.5 - cam.x, dy = p.getY() + 0.5 - cam.y, dz = p.getZ() + 0.5 - cam.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    public static class HoleEsp extends Module {
        final Setting.Num minDepth = add(new Setting.Num("Min depth", 4, 2, 12));
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        public HoleEsp() { super("Hole ESP", Category.BASEFINDING); holeEsp = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Hole h : d.holes) {
                    if (h.len() < minDepth.value || !near(h.pos(), cam, range.value)) continue;
                    BlockPos p = h.pos();
                    RenderUtil.box(m, cam, p.getX() + 0.1, p.getY(), p.getZ() + 0.1, p.getX() + 0.9, p.getY() + h.len(), p.getZ() + 0.9, 0x5522DD55);
                }
        }
    }

    public static class LightFinder extends Module {
        final Setting.Num maxY = add(new Setting.Num("Max Y", 64, -64, 320));
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        public LightFinder() { super("Light Finder", Category.BASEFINDING); }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BlockPos p : d.lights) {
                    if (p.getY() > maxY.value || !near(p, cam, range.value)) continue;
                    RenderUtil.box(m, cam, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 0x66FFDD33);
                }
        }
    }

    public static class SuspiciousEsp extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        public SuspiciousEsp() { super("Suspicious ESP", Category.BASEFINDING); }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BlockPos p : d.sus) {
                    if (!near(p, cam, range.value)) continue;
                    RenderUtil.box(m, cam, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 0x66FF4444);
                }
        }
    }

    public static class SusChunkFinder extends Module {
        public final Setting.Num threshold = add(new Setting.Num("Threshold", 12, 3, 60));
        public final Setting.Bool chatAlert = add(new Setting.Bool("Chat alert", true));
        final Setting.Num range = add(new Setting.Num("Range", 160, 32, 256));
        public SusChunkFinder() { super("SUS Chunk Finder", Category.BASEFINDING); susFinder = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            double y = Math.floor(mc.player.getY());
            for (Map.Entry<Long, BaseScanner.Data> e : BaseScanner.DATA.entrySet()) {
                if (e.getValue().score < threshold.value) continue;
                net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(e.getKey());
                double cx = cp.getCenterX() - cam.x, cz = cp.getCenterZ() - cam.z;
                if (cx * cx + cz * cz > range.value * range.value) continue;
                RenderUtil.box(m, cam, cp.getStartX(), y, cp.getStartZ(), cp.getStartX() + 16, y + 0.1, cp.getStartZ() + 16, 0x55FF9922);
            }
        }
    }

    public static class StorageEsp extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        public StorageEsp() { super("Storage ESP", Category.BASEFINDING); storageEsp = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Colored c : d.storage) {
                    BlockPos p = c.pos();
                    if (!near(p, cam, range.value)) continue;
                    RenderUtil.box(m, cam, p.getX() + 0.06, p.getY(), p.getZ() + 0.06, p.getX() + 0.94, p.getY() + 0.9, p.getZ() + 0.94, c.color());
                }
        }
    }

    public static class BlockEsp extends Module {
        final Setting.Bool[] toggles = new Setting.Bool[BaseScanner.Ore.values().length];
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        public BlockEsp() {
            super("Block ESP", Category.BASEFINDING);
            for (BaseScanner.Ore o : BaseScanner.Ore.values())
                toggles[o.ordinal()] = add(new Setting.Bool(o.label, o == BaseScanner.Ore.DIAMOND || o == BaseScanner.Ore.DEBRIS || o == BaseScanner.Ore.SPAWNER));
            blockEsp = this;
        }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Find f : d.ores) {
                    if (!toggles[f.ore().ordinal()].value || !near(f.pos(), cam, range.value)) continue;
                    BlockPos p = f.pos();
                    RenderUtil.box(m, cam, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, f.ore().color);
                }
        }
    }

    public static class NetheriteFinder extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 192, 32, 256));
        public NetheriteFinder() { super("Netherite Finder", Category.BASEFINDING); netheriteFinder = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Find f : d.ores) {
                    if (f.ore() != BaseScanner.Ore.DEBRIS || !near(f.pos(), cam, range.value)) continue;
                    BlockPos p = f.pos();
                    RenderUtil.box(m, cam, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 0x99FF8833);
                }
        }
    }

    public static class TunnelBaseFinder extends Module {
        final Setting.Num minLen = add(new Setting.Num("Min length", 10, 6, 60));
        final Setting.Num range = add(new Setting.Num("Range", 160, 32, 256));
        public TunnelBaseFinder() { super("Tunnel Base Finder", Category.BASEFINDING); tunnelFinder = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Tunnel t : d.tunnels) {
                    BlockPos p = t.start();
                    if (t.len() < minLen.value || !near(p, cam, range.value)) continue;
                    if (t.alongX())
                        RenderUtil.box(m, cam, p.getX(), p.getY(), p.getZ() + 0.25, p.getX() + t.len(), p.getY() + 2, p.getZ() + 0.75, 0x664488FF);
                    else
                        RenderUtil.box(m, cam, p.getX() + 0.25, p.getY(), p.getZ(), p.getX() + 0.75, p.getY() + 2, p.getZ() + t.len(), 0x664488FF);
                }
        }
    }
}
