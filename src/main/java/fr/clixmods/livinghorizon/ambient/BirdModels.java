package fr.clixmods.livinghorizon.ambient;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The 3D birds: each species built of a few boxes, like the game's own mobs, in pixels.
 *
 * <p>A model faces +Z, its back up (+Y), its left wing along +X; the origin is the middle of
 * the body. Perched, the whole bird is lifted by {@link Model#lift} so that it stands on its
 * feet (or floats, for the duck) at the origin. Wing boxes are given for the +X wing only:
 * the -X wing is their mirror. A wing turns on the body at {@link Model#hingeX}, and its
 * tip turns a little further at {@link Model#tipX}, so that the beat bends the wing.
 */
public final class BirdModels {
    /** What a box moves with, and in which pose it shows. */
    public enum Part {
        /** The body, head and tail: always there. */
        BODY,
        /** Only flying: a neck held out straight, say. */
        FLY,
        /** Only perched: folded wings, legs, a neck held up. */
        PERCH,
        /** The inner half of the +X wing, flying. */
        WING,
        /** The tip of the +X wing, flying. */
        TIP
    }

    /** A box from one corner to the other, in pixels, coloured 0xRRGGBB. */
    public record Cube(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, Part part) {
    }

    /**
     * One species. {@code span} is the distance from wing tip to wing tip, flying, in
     * pixels: the size the bird's own wingspan is drawn at.
     */
    public record Model(float span, float hingeX, float tipX, float lift, List<Cube> cubes) {
        public List<Cube> part(Part part) {
            return cubes.stream().filter(cube -> cube.part == part).toList();
        }
    }

    private static final Map<Ambience.Species, Model> MODELS = new EnumMap<>(Ambience.Species.class);

    private BirdModels() {
    }

    public static Model of(Ambience.Species species) {
        return MODELS.get(species);
    }

    /** Collects the boxes of one model. */
    private static final class Builder {
        private final List<Cube> cubes = new ArrayList<>();

        Builder box(Part part, int rgb, float x0, float y0, float z0, float x1, float y1, float z1) {
            cubes.add(new Cube(x0, y0, z0, x1, y1, z1, rgb, part));
            return this;
        }

        /** A box on the +X side and its mirror on the -X side: eyes, legs, folded wings. */
        Builder pair(Part part, int rgb, float x0, float y0, float z0, float x1, float y1, float z1) {
            box(part, rgb, x0, y0, z0, x1, y1, z1);
            return box(part, rgb, -x1, y0, z0, -x0, y1, z1);
        }

        /** Two thin legs from the belly down to the ground, with toes pointing forward. */
        Builder legs(int rgb, float apart, float belly, float lift, float z) {
            pair(Part.PERCH, rgb, apart - 0.25f, -lift, z - 0.25f, apart + 0.25f, belly, z + 0.25f);
            return pair(Part.PERCH, rgb, apart - 0.35f, -lift, z - 0.5f, apart + 0.35f, -lift + 0.25f, z + 1f);
        }

        Model build(float span, float hingeX, float tipX, float lift) {
            return new Model(span, hingeX, tipX, lift, List.copyOf(cubes));
        }
    }

    static {
        // A starling: the small dark birds of the flocks.
        MODELS.put(Ambience.Species.GENERIC, new Builder()
                .box(Part.BODY, 0x2B2D36, -1.5f, -1.25f, -3f, 1.5f, 1.25f, 3f)
                .box(Part.BODY, 0x3A3C46, -1.25f, -1.5f, -2f, 1.25f, -1f, 2.5f)
                .box(Part.BODY, 0x262730, -1.25f, -0.5f, 2.5f, 1.25f, 1.75f, 5f)
                .box(Part.BODY, 0xD8B03C, -0.35f, 0.1f, 5f, 0.35f, 0.7f, 6.75f)
                .box(Part.BODY, 0x23242C, -1.1f, -0.4f, -6f, 1.1f, 0.3f, -3f)
                .box(Part.WING, 0x30323C, 1.5f, -0.3f, -1.5f, 5f, 0.3f, 2f)
                .box(Part.TIP, 0x1E1F26, 5f, -0.25f, -1.75f, 8f, 0.25f, 1.25f)
                .pair(Part.PERCH, 0x30323C, 1.5f, -0.75f, -4f, 1.75f, 1f, 2f)
                .legs(0x8A6A4A, 0.65f, -1.5f, 3f, 0.5f)
                .build(16, 1.5f, 5f, 3f));

        // A Canada goose: brown, pale breast, black neck and head, a white chin strap.
        MODELS.put(Ambience.Species.GOOSE, new Builder()
                .box(Part.BODY, 0x7D6F5E, -2.5f, -2f, -5f, 2.5f, 2f, 5f)
                .box(Part.BODY, 0xCFC4B2, -2.25f, -2.4f, -3f, 2.25f, -1.6f, 4.5f)
                .box(Part.BODY, 0xF0F0EA, -2f, -1.2f, -5.4f, 2f, 1f, -4.9f)
                .box(Part.BODY, 0x1E1E1E, -1.75f, -0.8f, -7.5f, 1.75f, 0.6f, -5f)
                // Flying: the neck straight out ahead.
                .box(Part.FLY, 0x151515, -0.9f, -0.5f, 5f, 0.9f, 1f, 9.5f)
                .box(Part.FLY, 0x151515, -1.1f, -0.4f, 9.5f, 1.1f, 1.6f, 12f)
                .pair(Part.FLY, 0xF2F2EE, 1.1f, -0.3f, 9.8f, 1.2f, 1f, 11.2f)
                .box(Part.FLY, 0xF2F2EE, -1f, -0.5f, 9.8f, 1f, -0.4f, 11.2f)
                .box(Part.FLY, 0x101010, -0.5f, 0f, 12f, 0.5f, 0.8f, 13.8f)
                // Standing: the neck held up.
                .box(Part.PERCH, 0x151515, -0.9f, 1f, 3.5f, 0.9f, 7f, 5.3f)
                .box(Part.PERCH, 0x151515, -1.1f, 6f, 3.5f, 1.1f, 8f, 6.5f)
                .pair(Part.PERCH, 0xF2F2EE, 1.1f, 6.1f, 4.2f, 1.2f, 7.4f, 5.8f)
                .box(Part.PERCH, 0x101010, -0.5f, 6.4f, 6.5f, 0.5f, 7.2f, 8.2f)
                .pair(Part.PERCH, 0x6E604F, 2.5f, -1.2f, -6f, 2.8f, 1.6f, 3f)
                .legs(0x2A2A2A, 1.1f, -2.4f, 4.5f, 0.5f)
                .box(Part.WING, 0x6E604F, 2.5f, -0.4f, -2.5f, 8f, 0.4f, 3f)
                .box(Part.TIP, 0x2E2A26, 8f, -0.3f, -2.5f, 12f, 0.3f, 2f)
                .build(24, 2.5f, 8f, 4.5f));

        // A buzzard: broad brown wings with spread finger tips, a pale barred belly, a hooked beak.
        MODELS.put(Ambience.Species.RAPTOR, new Builder()
                .box(Part.BODY, 0x6B4A2C, -2.25f, -1.75f, -4f, 2.25f, 1.75f, 4f)
                .box(Part.BODY, 0xD8C39A, -2f, -2.1f, -3f, 2f, -1.4f, 3.5f)
                .box(Part.BODY, 0x8A6038, -2.05f, -2.2f, 0f, 2.05f, -1.9f, 1f)
                .box(Part.BODY, 0x5E4026, -1.75f, -0.75f, 4f, 1.75f, 2f, 7f)
                .box(Part.BODY, 0xE3B640, -0.55f, 0.4f, 7f, 0.55f, 1.15f, 7.5f)
                .box(Part.BODY, 0x2A2A2A, -0.5f, 0.1f, 7.5f, 0.5f, 1f, 8.4f)
                .box(Part.BODY, 0x2A2A2A, -0.4f, -0.3f, 8f, 0.4f, 0.1f, 8.4f)
                .pair(Part.BODY, 0x1A1208, 1.75f, 1f, 5.6f, 1.85f, 1.5f, 6.1f)
                .box(Part.BODY, 0x7A5634, -1.75f, -0.3f, -8.5f, 1.75f, 0.3f, -4f)
                .box(Part.BODY, 0x4A3020, -1.8f, -0.35f, -6f, 1.8f, 0.35f, -5.6f)
                .box(Part.BODY, 0x4A3020, -1.8f, -0.35f, -7.6f, 1.8f, 0.35f, -7.2f)
                .box(Part.WING, 0x6B4A2C, 2.25f, -0.35f, -3f, 8.5f, 0.35f, 3.5f)
                .box(Part.WING, 0x8A6A48, 2.25f, -0.4f, 2.5f, 8.5f, 0.4f, 3.5f)
                .box(Part.TIP, 0x3A2A1C, 8.5f, -0.3f, 1f, 13f, 0.3f, 2.6f)
                .box(Part.TIP, 0x3A2A1C, 8.5f, -0.3f, -0.9f, 12.5f, 0.3f, 0.7f)
                .box(Part.TIP, 0x3A2A1C, 8.5f, -0.3f, -2.8f, 11.5f, 0.3f, -1.2f)
                .pair(Part.PERCH, 0x5E4026, 2.25f, -1.25f, -6f, 2.5f, 1.5f, 3f)
                .legs(0xD8B040, 1f, -2.1f, 4f, 0.5f)
                .build(26, 2.25f, 8.5f, 4f));

        // A herring gull: white, grey wings with black tips and a white spot, a yellow beak with a red spot.
        MODELS.put(Ambience.Species.GULL, new Builder()
                .box(Part.BODY, 0xF2F2F0, -1.75f, -1.5f, -4f, 1.75f, 1.5f, 3.5f)
                .box(Part.BODY, 0xF7F7F5, -1.4f, -0.4f, 3.5f, 1.4f, 2f, 6.25f)
                .box(Part.BODY, 0xE8C33A, -0.4f, 0.2f, 6.25f, 0.4f, 0.9f, 8.25f)
                .box(Part.BODY, 0xD23A2A, -0.42f, 0.15f, 7.3f, 0.42f, 0.5f, 7.8f)
                .pair(Part.BODY, 0x1A1A1A, 1.4f, 1f, 5f, 1.5f, 1.4f, 5.4f)
                .box(Part.BODY, 0xF4F4F2, -1.4f, -0.3f, -6.5f, 1.4f, 0.4f, -4f)
                .box(Part.WING, 0xA8B0BA, 1.75f, -0.3f, -1.5f, 7f, 0.3f, 2.25f)
                .box(Part.TIP, 0xA8B0BA, 7f, -0.25f, -1.25f, 9f, 0.25f, 1.75f)
                .box(Part.TIP, 0x1B1B1D, 9f, -0.25f, -1.25f, 11f, 0.25f, 1.75f)
                .box(Part.TIP, 0xF5F5F5, 9.9f, -0.3f, 0f, 10.5f, 0.3f, 1f)
                .pair(Part.PERCH, 0xA8B0BA, 1.75f, -0.5f, -5f, 2f, 1.25f, 2f)
                .pair(Part.PERCH, 0x1B1B1D, 1.75f, -0.4f, -6.5f, 1.95f, 0.9f, -5f)
                .legs(0xE0A888, 0.8f, -1.5f, 3.5f, 0.5f)
                .build(22, 1.75f, 7f, 3.5f));

        // A town pigeon: grey, a green sheen on the neck, two black bars on the wings.
        MODELS.put(Ambience.Species.PIGEON, new Builder()
                .box(Part.BODY, 0x8F95A0, -1.75f, -1.5f, -3f, 1.75f, 1.5f, 3f)
                .box(Part.BODY, 0x9A8FA0, -1.6f, -1.75f, 0.5f, 1.6f, 0.75f, 3.4f)
                .box(Part.BODY, 0x4F8A6E, -1.3f, 0f, 2.5f, 1.3f, 1.6f, 4f)
                .box(Part.BODY, 0x858B97, -1.1f, 0.5f, 3.5f, 1.1f, 2.5f, 5.5f)
                .box(Part.BODY, 0x2A2A2A, -0.3f, 1.1f, 5.5f, 0.3f, 1.6f, 6.6f)
                .box(Part.BODY, 0xE8E8E8, -0.32f, 1.4f, 5.5f, 0.32f, 1.7f, 5.9f)
                .pair(Part.BODY, 0xD0602A, 1.1f, 1.6f, 4.4f, 1.2f, 2f, 4.8f)
                .box(Part.BODY, 0x8A909B, -1.25f, -0.5f, -6f, 1.25f, 0.3f, -3f)
                .box(Part.BODY, 0x2F3138, -1.3f, -0.55f, -6f, 1.3f, 0.35f, -5.3f)
                .box(Part.WING, 0xA0A6B0, 1.75f, -0.3f, -1.5f, 5.25f, 0.3f, 2f)
                .box(Part.WING, 0x2F3138, 1.75f, -0.35f, -1f, 5.25f, 0.35f, -0.6f)
                .box(Part.WING, 0x2F3138, 1.75f, -0.35f, 0f, 5.25f, 0.35f, 0.4f)
                .box(Part.TIP, 0x3A3D45, 5.25f, -0.25f, -1.75f, 8f, 0.25f, 1.25f)
                .pair(Part.PERCH, 0xA0A6B0, 1.75f, -0.75f, -4.5f, 2f, 1f, 2f)
                .pair(Part.PERCH, 0x2F3138, 1.75f, -0.5f, -1.5f, 2.05f, 0.75f, -1.1f)
                .pair(Part.PERCH, 0x2F3138, 1.75f, -0.5f, -0.5f, 2.05f, 0.75f, -0.1f)
                .legs(0xC04A4A, 0.7f, -1.75f, 3f, 0.5f)
                .build(16, 1.75f, 5.25f, 3f));

        // A robin: olive brown, an orange breast and face, a pale belly.
        MODELS.put(Ambience.Species.ROBIN, new Builder()
                .box(Part.BODY, 0x7A6248, -1.5f, -1.4f, -2.25f, 1.5f, 1.4f, 2.25f)
                .box(Part.BODY, 0xE0702A, -1.55f, -1f, 0.5f, 1.55f, 1.45f, 2.35f)
                .box(Part.BODY, 0xECE4D6, -1.3f, -1.5f, -1.5f, 1.3f, -1.2f, 1.5f)
                .box(Part.BODY, 0x7A6248, -1.25f, 0.25f, 1.75f, 1.25f, 2.6f, 4f)
                .box(Part.BODY, 0xE0702A, -1.3f, 0.25f, 3f, 1.3f, 1.6f, 4.05f)
                .pair(Part.BODY, 0x101010, 1.25f, 1.7f, 3f, 1.35f, 2.1f, 3.4f)
                .box(Part.BODY, 0x2A2420, -0.25f, 1.2f, 4f, 0.25f, 1.6f, 4.9f)
                .box(Part.BODY, 0x6A5440, -0.9f, -0.1f, -4.75f, 0.9f, 0.6f, -2.25f)
                .box(Part.WING, 0x6F5840, 1.5f, -0.25f, -1.25f, 4f, 0.25f, 1.5f)
                .box(Part.TIP, 0x5A4632, 4f, -0.2f, -1.25f, 6f, 0.2f, 1f)
                .pair(Part.PERCH, 0x6F5840, 1.5f, -0.6f, -3f, 1.75f, 1.2f, 1f)
                .legs(0x9A7A5A, 0.6f, -1.5f, 3f, 0.5f)
                .build(12, 1.5f, 4f, 3f));

        // A blue tit: blue cap, wings and tail, white cheeks, a dark eye stripe, yellow belly.
        MODELS.put(Ambience.Species.TIT, new Builder()
                .box(Part.BODY, 0x8AA04A, -1.4f, -1.3f, -2f, 1.4f, 1.3f, 2f)
                .box(Part.BODY, 0xF0D040, -1.45f, -1.4f, -1.4f, 1.45f, 0.4f, 2.05f)
                .box(Part.BODY, 0xF4F4F0, -1.25f, 0.2f, 1.6f, 1.25f, 2.5f, 3.75f)
                .box(Part.BODY, 0x3F7FD0, -1.3f, 1.7f, 1.55f, 1.3f, 2.55f, 3.6f)
                .pair(Part.BODY, 0x1A2440, 1.25f, 1.2f, 1.6f, 1.32f, 1.55f, 3.75f)
                .box(Part.BODY, 0x1A2440, -0.8f, 0.2f, 3.75f, 0.8f, 0.9f, 3.82f)
                .box(Part.BODY, 0x2A2A2A, -0.2f, 1f, 3.75f, 0.2f, 1.35f, 4.4f)
                .box(Part.BODY, 0x3F7FD0, -0.8f, -0.1f, -4.25f, 0.8f, 0.5f, -2f)
                .box(Part.WING, 0x3F7FD0, 1.4f, -0.25f, -1.25f, 3.75f, 0.25f, 1.4f)
                .box(Part.WING, 0xF4F4F0, 1.4f, -0.3f, 0.5f, 3.75f, 0.3f, 0.85f)
                .box(Part.TIP, 0x2E5A9A, 3.75f, -0.2f, -1.25f, 5.5f, 0.2f, 1f)
                .pair(Part.PERCH, 0x3F7FD0, 1.4f, -0.5f, -2.75f, 1.65f, 1.1f, 1f)
                .pair(Part.PERCH, 0xF4F4F0, 1.4f, 0.3f, -0.5f, 1.7f, 0.6f, 1f)
                .legs(0x5A6A80, 0.6f, -1.4f, 2.5f, 0.5f)
                .build(11, 1.4f, 3.75f, 2.5f));

        // A mallard drake: green head, white collar, chestnut breast, grey body, a blue patch on the wings.
        MODELS.put(Ambience.Species.DUCK, new Builder()
                .box(Part.BODY, 0xB9B4AB, -2f, -1.5f, -4f, 2f, 1.5f, 3.5f)
                .box(Part.BODY, 0x6B3A24, -2.05f, -1.4f, 2f, 2.05f, 1.2f, 3.75f)
                .box(Part.BODY, 0x1C1C1C, -1.8f, -1.2f, -5f, 1.8f, 1f, -4f)
                .box(Part.BODY, 0xE8E8E4, -1.5f, -0.3f, -5.75f, 1.5f, 0.3f, -5f)
                .box(Part.BODY, 0x1C1C1C, -0.3f, 1f, -4.8f, 0.3f, 1.6f, -4.2f)
                // Flying: the neck stretched out ahead.
                .box(Part.FLY, 0x1F6B3A, -0.8f, -0.1f, 3.5f, 0.8f, 1.1f, 6f)
                .box(Part.FLY, 0xF0F0F0, -0.85f, -0.15f, 4f, 0.85f, 1.15f, 4.4f)
                .box(Part.FLY, 0x1F6B3A, -1f, -0.2f, 6f, 1f, 1.6f, 8.25f)
                .pair(Part.FLY, 0x101010, 1f, 0.8f, 7.2f, 1.1f, 1.2f, 7.6f)
                .box(Part.FLY, 0xE8C23A, -0.6f, 0.2f, 8.25f, 0.6f, 0.8f, 10f)
                // Swimming: the head held up.
                .box(Part.PERCH, 0x1F6B3A, -0.9f, 0.5f, 2.75f, 0.9f, 2.25f, 4.25f)
                .box(Part.PERCH, 0xF0F0F0, -0.95f, 0.6f, 2.7f, 0.95f, 1f, 4.3f)
                .box(Part.PERCH, 0x1F6B3A, -1.1f, 1.75f, 2.75f, 1.1f, 3.75f, 5f)
                .pair(Part.PERCH, 0x101010, 1.1f, 2.9f, 4f, 1.2f, 3.3f, 4.4f)
                .box(Part.PERCH, 0xE8C23A, -0.6f, 2.1f, 5f, 0.6f, 2.7f, 7f)
                .pair(Part.PERCH, 0x8C8578, 2f, -0.8f, -4f, 2.2f, 1f, 1.5f)
                .pair(Part.PERCH, 0x3A4EC8, 2f, -0.5f, -2.5f, 2.25f, 0.3f, -1.5f)
                .box(Part.WING, 0x8C8578, 2f, -0.3f, -2f, 6f, 0.3f, 2.25f)
                .box(Part.WING, 0x3A4EC8, 2f, -0.35f, -2f, 6f, 0.35f, -1.2f)
                .box(Part.TIP, 0x6E6A62, 6f, -0.25f, -1.75f, 9f, 0.25f, 1.75f)
                // Afloat: the body half under the water line.
                .build(18, 2f, 6f, 0.75f));
    }
}
