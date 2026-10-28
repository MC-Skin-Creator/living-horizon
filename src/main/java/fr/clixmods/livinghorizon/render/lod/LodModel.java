package fr.clixmods.livinghorizon.render.lod;

import org.joml.Matrix4d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A model as the level of detail generator reads it: its parts, each posed at rest under its
 * parent, and its cubes, each with the quads the game draws for it. Plain data, in the model's
 * pixels (a block is 16), asking the game nothing: {@link ModelReader} fills it from a
 * {@code ModelPart} tree, the tests from anything.
 */
public final class LodModel {
    /**
     * One part of the model.
     *
     * @param parent   the index of its parent, -1 for the root
     * @param rest     from the part's space to the root's, at rest
     * @param animated whether the game moves it on its own (a leg, an arm, the head): a merge
     *                 that would carry its cubes up to a parent freezes it
     */
    public record Part(String name, int parent, int depth, Matrix4d rest, Matrix4d restInverse, boolean animated) {
    }

    /**
     * One cube of a part.
     *
     * @param box   its box in its part's space: min x, y, z, then max x, y, z
     * @param quads its faces, 20 floats each: four corners of x, y, z, u, v, the texture
     *              coordinates from 0 to 1
     */
    public record Cube(int part, double[] box, float[][] quads) {
    }

    private final List<Part> parts = new ArrayList<>();
    private final List<Cube> cubes = new ArrayList<>();
    private int quads;

    /**
     * Adds a part, posed under {@code parent} as the game poses it: moved, turned round z,
     * then y, then x, then scaled. Returns its index.
     */
    public int part(String name, int parent, double x, double y, double z, double xRot, double yRot, double zRot,
                    double xScale, double yScale, double zScale) {
        Matrix4d local = new Matrix4d().translation(x, y, z).rotateZYX(zRot, yRot, xRot).scale(xScale, yScale, zScale);
        Matrix4d rest = parent < 0 ? local : new Matrix4d(parts.get(parent).rest).mul(local);
        int depth = parent < 0 ? 0 : parts.get(parent).depth + 1;
        parts.add(new Part(name, parent, depth, rest, new Matrix4d(rest).invert(), animated(name)));
        return parts.size() - 1;
    }

    /** Adds a cube to a part. */
    public void cube(int part, double[] box, float[][] quads) {
        cubes.add(new Cube(part, box.clone(), quads));
        this.quads += quads.length;
    }

    /** The same parts with only the cubes {@code keep} says. */
    public LodModel keep(boolean[] keep) {
        LodModel out = new LodModel();
        out.parts.addAll(parts);
        for (int i = 0; i < cubes.size(); i++) {
            if (keep[i]) out.cube(cubes.get(i).part(), cubes.get(i).box(), cubes.get(i).quads());
        }
        return out;
    }

    public List<Part> parts() {
        return Collections.unmodifiableList(parts);
    }

    public List<Cube> cubes() {
        return Collections.unmodifiableList(cubes);
    }

    /** Quads of the whole model, as the game draws it. */
    public int quads() {
        return quads;
    }

    /**
     * Whether the game animates a part by this name on its own: legs and arms on a side, the
     * head. Read from the name, as most models (the game's and other mods') name them.
     */
    static boolean animated(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        boolean right = n.contains("right"), left = n.contains("left");
        if (n.contains("leg") || n.contains("haunch")) return n.contains("hind") || n.contains("front") || right || left;
        if (n.contains("arm")) return right || left;
        return n.equals("head") || n.equals("head_parts") || n.equals("real_head");
    }
}
