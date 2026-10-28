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
    /**
     * What a part draws at a simpler level: none of its own cubes, and the groups anchored to it,
     * as triangles paired into quads.
     */
    public record PartDraw(int[] groups) {
    }

    /**
     * The model its levels were made from, its levels, and for each simpler level and group its
     * quads ({@link LodLevels#pair}) and triangles, and what each part draws. {@code usable[i]} is
     * false where level {@code i} cannot be drawn: a group anchored to a part the game never asks
     * to draw itself.
     */
    record Plan(LodModel model, LodLevels.Levels levels, List<double[]>[][] quads, int[][] triangles,
                PartDraw[][] draws, boolean[] usable) {
    }

    private final LodModel read;
    /** The kind of mob it was read for, and its textures in the order its layers are drawn: for the preview. */
    final net.minecraft.network.chat.Component name;
    final List<net.minecraft.resources.Identifier> layers;
    private final Map<ModelPart, Integer> index = new IdentityHashMap<>();
    /** Its baked textures, one per layer texture, as {@link LodRenderer} keeps them: a few, searched in order. */
    final List<Object> bakes = new java.util.ArrayList<>();
    private final boolean[] skips;
    /** Where each part stands in the model at rest, in blocks, and how its normals turn; made when first asked. */
    private org.joml.Matrix4f @Nullable [] rests;
    private org.joml.Matrix3f @Nullable [] restNormals;
    private volatile @Nullable Plan plan;
    private volatile boolean failed;

    LodSet(ModelReader.Read read, net.minecraft.network.chat.Component name,
           List<net.minecraft.resources.Identifier> layers) {
        this.read = read.model();
        this.name = name;
        this.layers = List.copyOf(layers);
        List<ModelPart> parts = read.parts();
        skips = new boolean[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            index.put(parts.get(i), i);
            skips[i] = parts.get(i).skipDraw;
        }
    }

    /**
     * Makes the levels from the cubes one of {@code textures} shows, each keeping {@code ratio}
     * of the triangles of the one before; away from the render thread.
     */
    @SuppressWarnings("unchecked")
    void generate(List<LodBake.Pixels> textures, double ratio) {
        try {
            List<LodModel.Cube> all = read.cubes();
            boolean[] keep = new boolean[all.size()];
            boolean any = false;
            for (int c = 0; c < all.size(); c++) {
                for (LodBake.Pixels texture : textures) {
                    if (shows(all.get(c), texture)) {
                        keep[c] = any = true;
                        break;
                    }
                }
            }
            if (!any) {
                failed = true;
                return;
            }
            LodModel model = read.keep(keep);
            LodLevels.Levels levels = LodLevels.generate(model, ratio);
            List<LodLevels.Group> groups = levels.groups();
            int partCount = model.parts().size();
            int count = levels.count();
            List<double[]>[][] quads = new List[count][];
            int[][] triangles = new int[count][];
            PartDraw[][] draws = new PartDraw[count][];
            boolean[] usable = new boolean[count];
            for (int level = 1; level < count; level++) {
                List<List<LodLevels.Triangle>> byGroup = new ArrayList<>();
                for (int g = 0; g < groups.size(); g++) byGroup.add(new ArrayList<>());
                for (LodLevels.Triangle triangle : levels.level(level)) byGroup.get(triangle.group()).add(triangle);
                quads[level] = new List[groups.size()];
                triangles[level] = new int[groups.size()];
                List<List<Integer>> anchored = new ArrayList<>();
                for (int p = 0; p < partCount; p++) anchored.add(new ArrayList<>());
                boolean ok = true;
                for (int g = 0; g < groups.size(); g++) {
                    quads[level][g] = LodLevels.pair(byGroup.get(g));
                    triangles[level][g] = byGroup.get(g).size();
                    int anchor = groups.get(g).anchor();
                    anchored.get(anchor).add(g);
                    if (skips[anchor] && !byGroup.get(g).isEmpty()) ok = false;
                }
                PartDraw[] byPart = new PartDraw[partCount];
                for (int p = 0; p < partCount; p++) {
                    byPart[p] = new PartDraw(anchored.get(p).stream().mapToInt(Integer::intValue).toArray());
                }
                draws[level] = byPart;
                usable[level] = ok;
            }
            plan = new Plan(model, levels, quads, triangles, draws, usable);
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

    /** The quads the game draws for its full model. */
    int fullQuads() {
        return read.quads();
    }

    /** Where part {@code part} stands in the model at rest, in blocks: a figure drawn standing. */
    org.joml.Matrix4f rest(int part) {
        if (rests == null) rests();
        return rests[part];
    }

    /** How part {@code part} turns its normals at rest. */
    org.joml.Matrix3f restNormal(int part) {
        if (restNormals == null) rests();
        return restNormals[part];
    }

    private void rests() {
        List<LodModel.Part> parts = read.parts();
        org.joml.Matrix4f[] places = new org.joml.Matrix4f[parts.size()];
        org.joml.Matrix3f[] turns = new org.joml.Matrix3f[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            // In model pixels; the game moves its parts by a sixteenth of that, and the faces are in blocks.
            places[i] = new org.joml.Matrix4f().scaling(1 / 16f).mul(new org.joml.Matrix4f(parts.get(i).rest())).scale(16f);
            turns[i] = places[i].normal(new org.joml.Matrix3f());
        }
        restNormals = turns;
        rests = places;
    }

    /** What the game's part {@code part} draws at {@code level}, null for all of its cubes as usual. */
    @Nullable PartDraw draw(ModelPart part, int level) {
        Plan p = plan;
        Integer at = index.get(part);
        if (p == null || at == null || level >= p.draws().length || p.draws()[level] == null) return null;
        return p.draws()[level][at];
    }
}
