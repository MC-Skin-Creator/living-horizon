package fr.clixmods.livinghorizon.render.lod;

import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The levels of detail of one model, as the game drew its parts when it was read (a part
 * hidden then has no cube in it) with the textures its figure was drawn with (a cube none of
 * them shows is left out: a villager's hat rim, painted by some professions only), and what
 * each of the game's parts draws at each level. Read on the render thread, its levels made
 * away from it: {@link #plan} is null until then.
 */
public final class LodSet {
    /** What a part draws at a level: some of its own cubes, and merged boxes anchored to it. */
    public record PartDraw(int[] kept, int[] merged, int keptQuads) {
    }

    /**
     * The model its levels were made from, its levels, and for each level and part what it
     * draws. {@code usable[i]} is false where level {@code i} cannot be drawn: a box anchored
     * to a part the game never asks to draw itself.
     */
    record Plan(LodModel model, List<LodLevels.Level> levels, PartDraw[][] draws, boolean[] usable) {
    }

    private final LodModel read;
    private final Map<ModelPart, Integer> index = new IdentityHashMap<>();
    private final int[] cubeIndex;
    private final boolean[] skips;
    private volatile @Nullable Plan plan;
    private volatile boolean failed;

    LodSet(ModelReader.Read read) {
        this.read = read.model();
        List<ModelPart> parts = read.parts();
        skips = new boolean[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            index.put(parts.get(i), i);
            skips[i] = parts.get(i).skipDraw;
        }
        cubeIndex = read.cubeIndex();
    }

    /** Makes the levels from the cubes one of {@code textures} shows; away from the render thread. */
    void generate(List<LodBake.Pixels> textures) {
        try {
            List<LodModel.Cube> all = read.cubes();
            boolean[] keep = new boolean[all.size()];
            List<Integer> kept = new ArrayList<>();
            for (int c = 0; c < all.size(); c++) {
                for (LodBake.Pixels texture : textures) {
                    if (shows(all.get(c), texture)) {
                        keep[c] = true;
                        kept.add(cubeIndex[c]);
                        break;
                    }
                }
            }
            LodModel model = read.keep(keep);
            int[] index = kept.stream().mapToInt(Integer::intValue).toArray();
            List<LodLevels.Level> levels = LodLevels.generate(model);
            int partCount = model.parts().size();
            PartDraw[][] draws = new PartDraw[levels.size()][];
            boolean[] usable = new boolean[levels.size()];
            for (int level = 1; level < levels.size(); level++) {
                List<List<Integer>> keptCubes = new ArrayList<>(), merged = new ArrayList<>();
                int[] keptQuads = new int[partCount];
                for (int p = 0; p < partCount; p++) {
                    keptCubes.add(new ArrayList<>());
                    merged.add(new ArrayList<>());
                }
                boolean ok = true;
                List<LodLevels.Group> groups = levels.get(level).groups();
                for (int g = 0; g < groups.size(); g++) {
                    LodLevels.Group group = groups.get(g);
                    if (group.merged()) {
                        merged.get(group.anchor()).add(g);
                        if (skips[group.anchor()]) ok = false;
                    } else {
                        int source = group.sources()[0];
                        int part = model.cubes().get(source).part();
                        keptCubes.get(part).add(index[source]);
                        keptQuads[part] += group.quads();
                    }
                }
                PartDraw[] byPart = new PartDraw[partCount];
                for (int p = 0; p < partCount; p++) {
                    byPart[p] = new PartDraw(keptCubes.get(p).stream().mapToInt(Integer::intValue).sorted().toArray(),
                            merged.get(p).stream().mapToInt(Integer::intValue).toArray(), keptQuads[p]);
                }
                draws[level] = byPart;
                usable[level] = ok;
            }
            plan = new Plan(model, levels, draws, usable);
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    /** Whether any face of the cube shows an opaque texel of the texture. */
    private static boolean shows(LodModel.Cube cube, LodBake.Pixels texture) {
        for (float[] quad : cube.quads()) {
            float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                u0 = Math.min(u0, quad[k * 5 + 3]);
                u1 = Math.max(u1, quad[k * 5 + 3]);
                v0 = Math.min(v0, quad[k * 5 + 4]);
                v1 = Math.max(v1, quad[k * 5 + 4]);
            }
            int x0 = clamp((int) Math.floor(u0 * texture.width()), texture.width());
            int x1 = clamp((int) Math.ceil(u1 * texture.width()) - 1, texture.width());
            int y0 = clamp((int) Math.floor(v0 * texture.height()), texture.height());
            int y1 = clamp((int) Math.ceil(v1 * texture.height()) - 1, texture.height());
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if ((texture.argb(x, y) >>> 24) >= 128) return true;
                }
            }
        }
        return false;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    @Nullable Plan plan() {
        return plan;
    }

    void fail() {
        failed = true;
    }

    boolean failed() {
        return failed;
    }

    /** Whether level {@code level} can be drawn. */
    boolean ready(int level) {
        Plan p = plan;
        return p != null && level < p.usable().length && p.usable()[level];
    }

    /** What the game's part {@code part} draws at {@code level}, null for all of its cubes as usual. */
    @Nullable PartDraw draw(ModelPart part, int level) {
        Plan p = plan;
        Integer at = index.get(part);
        if (p == null || at == null || level >= p.draws().length || p.draws()[level] == null) return null;
        return p.draws()[level][at];
    }
}
