package com.caleon.client.module;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/** Scans loaded chunks a little each tick and caches what the basefinding modules need. */
public class BaseScanner {
    public record Hole(BlockPos pos, int len) {}
    public record Colored(BlockPos pos, int color) {}
    public record Find(BlockPos pos, Ore ore) {}
    public record Tunnel(BlockPos start, int len, boolean alongX) {}

    public enum Ore {
        DIAMOND("Diamond", 0x6633DDFF), DEBRIS("Ancient Debris", 0x77AA5522), SPAWNER("Spawner", 0x77CC33FF),
        EMERALD("Emerald", 0x6633EE55), GOLD("Gold", 0x66FFCC22), IRON("Iron", 0x66DDBBAA),
        REDSTONE("Redstone", 0x66FF2222), LAPIS("Lapis", 0x663355FF);
        public final String label; public final int color;
        Ore(String label, int color) { this.label = label; this.color = color; }
    }

    public static class Data {
        public final List<BlockPos> lights = new ArrayList<>();
        public final List<BlockPos> sus = new ArrayList<>();
        public final List<Hole> holes = new ArrayList<>();
        public final List<Colored> storage = new ArrayList<>();
        public final List<Find> ores = new ArrayList<>();
        public final List<Tunnel> tunnels = new ArrayList<>();
        public int score;
    }

    public static final Map<Long, Data> DATA = new HashMap<>();
    private static final Map<Long, Integer> LAST_SCAN = new HashMap<>();
    private static final Set<Long> REPORTED = new HashSet<>();
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static ClientWorld lastWorld;
    private static int ticks, lastMask = -1;
    private static final int CAP = 1500;

    static final Set<Block> LIGHTS = Set.of(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
            Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT,
            Blocks.REDSTONE_LAMP, Blocks.JACK_O_LANTERN, Blocks.END_ROD, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
            Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT,
            Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH);

    static final Set<Block> SUS = Set.of(Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.HOPPER, Blocks.DROPPER,
            Blocks.DISPENSER, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.TNT, Blocks.GLASS,
            Blocks.GLASS_PANE, Blocks.IRON_DOOR, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.BREWING_STAND,
            Blocks.BEACON, Blocks.LECTERN, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.COBBLESTONE, Blocks.STONE_BRICKS);

    static final Map<Block, Integer> STORAGE = new HashMap<>();
    static final Map<Block, Ore> ORES = new HashMap<>();
    static {
        STORAGE.put(Blocks.CHEST, 0x77FFAA33);
        STORAGE.put(Blocks.TRAPPED_CHEST, 0x77FF3333);
        STORAGE.put(Blocks.ENDER_CHEST, 0x77AA33FF);
        STORAGE.put(Blocks.BARREL, 0x77BB8855);
        STORAGE.put(Blocks.HOPPER, 0x77999999);
        ORES.put(Blocks.DIAMOND_ORE, Ore.DIAMOND); ORES.put(Blocks.DEEPSLATE_DIAMOND_ORE, Ore.DIAMOND);
        ORES.put(Blocks.ANCIENT_DEBRIS, Ore.DEBRIS); ORES.put(Blocks.SPAWNER, Ore.SPAWNER);
        ORES.put(Blocks.EMERALD_ORE, Ore.EMERALD); ORES.put(Blocks.DEEPSLATE_EMERALD_ORE, Ore.EMERALD);
        ORES.put(Blocks.GOLD_ORE, Ore.GOLD); ORES.put(Blocks.DEEPSLATE_GOLD_ORE, Ore.GOLD);
        ORES.put(Blocks.NETHER_GOLD_ORE, Ore.GOLD);
        ORES.put(Blocks.IRON_ORE, Ore.IRON); ORES.put(Blocks.DEEPSLATE_IRON_ORE, Ore.IRON);
        ORES.put(Blocks.REDSTONE_ORE, Ore.REDSTONE); ORES.put(Blocks.DEEPSLATE_REDSTONE_ORE, Ore.REDSTONE);
        ORES.put(Blocks.LAPIS_ORE, Ore.LAPIS); ORES.put(Blocks.DEEPSLATE_LAPIS_ORE, Ore.LAPIS);
    }

    private static boolean on(Module m) { return m != null && m.enabled; }

    static boolean isActive() {
        for (Module m : ModuleManager.MODULES)
            if (m.enabled && m.category == Category.BASEFINDING) return true;
        return false;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            DATA.clear(); LAST_SCAN.clear(); REPORTED.clear(); QUEUE.clear();
        }
        if (!isActive()) return;
        int mask = (wantHoles() ? 1 : 0) | (wantTunnels() ? 2 : 0) | (wantOres() ? 4 : 0) | (wantStorage() ? 8 : 0);
        if (mask != lastMask) { lastMask = mask; LAST_SCAN.clear(); QUEUE.clear(); }
        ticks++;
        if (QUEUE.isEmpty()) {
            if (ticks % 20 != 0) return;
            refill(mc);
        }
        Long k = QUEUE.poll();
        if (k == null) return;
        ChunkPos cp = new ChunkPos(k);
        WorldChunk ch = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z);
        if (ch == null) return;
        scan(mc, ch, cp, k);
    }

    private static boolean wantHoles() { return on(BaseModules.holeEsp) || on(BaseModules.susFinder); }
    private static boolean wantTunnels() { return on(BaseModules.tunnelFinder); }
    private static boolean wantOres() { return on(BaseModules.blockEsp) || on(BaseModules.netheriteFinder); }
    private static boolean wantStorage() { return on(BaseModules.storageEsp); }

    private static void refill(MinecraftClient mc) {
        int r = Math.min(10, mc.options.getViewDistance().getValue());
        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
        List<long[]> list = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                long key = ChunkPos.toLong(pcx + dx, pcz + dz);
                Integer last = LAST_SCAN.get(key);
                if (last != null && ticks - last < 1200) continue;
                list.add(new long[]{key, (long) dx * dx + (long) dz * dz});
            }
        list.sort(Comparator.comparingLong(a -> a[1]));
        for (long[] a : list) QUEUE.add(a[0]);
    }

    private static boolean solid(ClientWorld w, BlockPos.Mutable mp, int x, int y, int z) {
        mp.set(x, y, z);
        BlockState s = w.getBlockState(mp);
        return s.isSolidBlock(w, mp);
    }

    private static boolean air(ClientWorld w, BlockPos.Mutable mp, int x, int y, int z) {
        mp.set(x, y, z);
        return w.getBlockState(mp).isAir();
    }

    private static void scan(MinecraftClient mc, WorldChunk ch, ChunkPos cp, long key) {
        ClientWorld w = mc.world;
        boolean holes = wantHoles(), tunnels = wantTunnels(), ores = wantOres(), storage = wantStorage();
        Data d = new Data();
        BlockPos.Mutable mp = new BlockPos.Mutable();
        Set<Long> cells = new HashSet<>(), txCells = new HashSet<>(), tzCells = new HashSet<>();
        int[] oreCounts = new int[Ore.values().length];
        ChunkSection[] secs = ch.getSectionArray();
        for (int si = 0; si < secs.length; si++) {
            ChunkSection sec = secs[si];
            if (sec == null || sec.isEmpty()) continue;
            int y0 = ch.getBottomY() + si * 16;
            for (int ly = 0; ly < 16; ly++)
                for (int lz = 0; lz < 16; lz++)
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState st = sec.getBlockState(lx, ly, lz);
                        int wx = cp.getStartX() + lx, wy = y0 + ly, wz = cp.getStartZ() + lz;
                        if (st.isAir()) {
                            if (holes && cells.size() < 20000
                                    && solid(w, mp, wx + 1, wy, wz) && solid(w, mp, wx - 1, wy, wz)
                                    && solid(w, mp, wx, wy, wz + 1) && solid(w, mp, wx, wy, wz - 1))
                                cells.add(new BlockPos(wx, wy, wz).asLong());
                            if (tunnels && txCells.size() + tzCells.size() < 20000
                                    && air(w, mp, wx, wy + 1, wz)
                                    && solid(w, mp, wx, wy - 1, wz) && solid(w, mp, wx, wy + 2, wz)) {
                                if (solid(w, mp, wx, wy, wz + 1) && solid(w, mp, wx, wy, wz - 1)
                                        && solid(w, mp, wx, wy + 1, wz + 1) && solid(w, mp, wx, wy + 1, wz - 1))
                                    txCells.add(new BlockPos(wx, wy, wz).asLong());
                                else if (solid(w, mp, wx + 1, wy, wz) && solid(w, mp, wx - 1, wy, wz)
                                        && solid(w, mp, wx + 1, wy + 1, wz) && solid(w, mp, wx - 1, wy + 1, wz))
                                    tzCells.add(new BlockPos(wx, wy, wz).asLong());
                            }
                            continue;
                        }
                        Block b = st.getBlock();
                        if (ores) {
                            Ore o = ORES.get(b);
                            if (o != null) {
                                if (oreCounts[o.ordinal()]++ < 500) d.ores.add(new Find(new BlockPos(wx, wy, wz), o));
                                continue;
                            }
                        }
                        if (storage) {
                            Integer col = STORAGE.get(b);
                            if (col == null && b instanceof ShulkerBoxBlock) col = 0x77FF77CC;
                            if (col != null && d.storage.size() < CAP) d.storage.add(new Colored(new BlockPos(wx, wy, wz), col));
                        }
                        if (LIGHTS.contains(b)) {
                            if (st.getLuminance() > 0 && d.lights.size() < CAP) d.lights.add(new BlockPos(wx, wy, wz));
                        } else if ((SUS.contains(b) || st.isIn(BlockTags.BEDS) || st.isIn(BlockTags.SHULKER_BOXES)
                                || st.isIn(BlockTags.WOOL)) && d.sus.size() < CAP) {
                            d.sus.add(new BlockPos(wx, wy, wz));
                        }
                    }
        }
        for (long c : cells) {
            BlockPos p = BlockPos.fromLong(c);
            if (cells.contains(p.down().asLong())) continue;
            int len = 1;
            BlockPos q = p.up();
            while (cells.contains(q.asLong())) { len++; q = q.up(); }
            if (len >= 2 && d.holes.size() < CAP) d.holes.add(new Hole(p, len));
        }
        for (long c : txCells) {
            BlockPos p = BlockPos.fromLong(c);
            if (txCells.contains(p.west().asLong())) continue;
            int len = 1;
            BlockPos q = p.east();
            while (txCells.contains(q.asLong())) { len++; q = q.east(); }
            if (len >= 6 && d.tunnels.size() < 500) d.tunnels.add(new Tunnel(p, len, true));
        }
        for (long c : tzCells) {
            BlockPos p = BlockPos.fromLong(c);
            if (tzCells.contains(p.north().asLong())) continue;
            int len = 1;
            BlockPos q = p.south();
            while (tzCells.contains(q.asLong())) { len++; q = q.south(); }
            if (len >= 6 && d.tunnels.size() < 500) d.tunnels.add(new Tunnel(p, len, false));
        }
        int deep = 0;
        for (Hole h : d.holes) if (h.len() >= 4) deep++;
        d.score = d.sus.size() + d.lights.size() * 3 + deep * 5;
        DATA.put(key, d);
        LAST_SCAN.put(key, ticks);

        BaseModules.SusChunkFinder f = BaseModules.susFinder;
        if (f != null && f.enabled && f.chatAlert.value && d.score >= f.threshold.value && REPORTED.add(key)) {
            mc.player.sendMessage(Text.literal("§9[Caleon] §fSUS chunk at X " + cp.getCenterX() + " Z " + cp.getCenterZ()
                    + " (score " + d.score + ")"), false);
        }
    }
}
