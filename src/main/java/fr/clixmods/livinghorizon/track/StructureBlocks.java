package fr.clixmods.livinghorizon.track;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks of a moving build ({@link FarStructures}), from the corner of its box: a palette and,
 * for each block, its place in it. Never changed once made; a new copy replaces it.
 *
 * <p>Saved as the palette, each state as the game writes one ({@code {"Name": …, "Properties":
 * …}}, which reads back on any version and with other mods' blocks), and runs of the same index.
 * A block a mod no longer has reads back as air.
 */
public final class StructureBlocks {
    /** The most blocks a build may span, air included: 128 by 128 by 128. A larger one is not kept. */
    public static final int MAX_VOLUME = 128 * 128 * 128;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Reads the block at a place of the box, from its corner. */
    @FunctionalInterface
    public interface Reader {
        BlockState state(int x, int y, int z);
    }

    private final int sizeX, sizeY, sizeZ;
    /** The states, air first. */
    private final BlockState[] palette;
    /** Each block's index in {@link #palette}, x fastest, then z, then y. */
    private final char[] blocks;
    private final int solid;
    private final long hash;

    private StructureBlocks(int sizeX, int sizeY, int sizeZ, BlockState[] palette, char[] blocks) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.blocks = blocks;
        int count = 0;
        long h = 0xcbf29ce484222325L;
        for (char index : blocks) {
            if (index != 0) count++;
            h = (h ^ Block.getId(palette[index])) * 0x100000001b3L;
        }
        this.solid = count;
        this.hash = h;
    }

    /** Whether a box of this size may be kept. */
    public static boolean fits(int sizeX, int sizeY, int sizeZ) {
        return sizeX > 0 && sizeY > 0 && sizeZ > 0 && (long) sizeX * sizeY * sizeZ <= MAX_VOLUME;
    }

    /** The blocks of a box of this size, read one by one; null when it is too large or has too many kinds of block. */
    public static @Nullable StructureBlocks read(int sizeX, int sizeY, int sizeZ, Reader reader) {
        if (!fits(sizeX, sizeY, sizeZ)) return null;
        Map<BlockState, Integer> indices = new IdentityHashMap<>();
        List<BlockState> palette = new ArrayList<>();
        indices.put(AIR, 0);
        palette.add(AIR);
        char[] blocks = new char[sizeX * sizeY * sizeZ];
        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, i++) {
                    BlockState state = reader.state(x, y, z);
                    if (state == null || state.isAir()) continue;
                    Integer index = indices.get(state);
                    if (index == null) {
                        if (palette.size() > Character.MAX_VALUE) return null;
                        index = palette.size();
                        indices.put(state, index);
                        palette.add(state);
                    }
                    blocks[i] = (char) index.intValue();
                }
            }
        }
        return new StructureBlocks(sizeX, sizeY, sizeZ, palette.toArray(BlockState[]::new), blocks);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /** How many blocks are not air. */
    public int solid() {
        return solid;
    }

    /** The same for the same blocks in the same places, in this game: tells a copy that changed. */
    public long hash() {
        return hash;
    }

    /** The block there, from the corner; air outside the box. */
    public BlockState state(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return AIR;
        return palette[blocks[(y * sizeZ + z) * sizeX + x]];
    }

    // --- File --------------------------------------------------------------------------

    /** The palette as saved: each state as the game writes one. */
    public List<JsonElement> savedPalette() {
        List<JsonElement> saved = new ArrayList<>(palette.length);
        for (BlockState state : palette) {
            saved.add(BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).result()
                    .orElseGet(() -> BlockState.CODEC.encodeStart(JsonOps.INSTANCE, AIR).result().orElseThrow()));
        }
        return saved;
    }

    /** The blocks as saved: pairs of an index in the palette and how many times it comes in a row. */
    public int[] savedRuns() {
        List<Integer> runs = new ArrayList<>();
        int i = 0;
        while (i < blocks.length) {
            char index = blocks[i];
            int start = i;
            while (i < blocks.length && blocks[i] == index) i++;
            runs.add((int) index);
            runs.add(i - start);
        }
        int[] saved = new int[runs.size()];
        for (int k = 0; k < saved.length; k++) saved[k] = runs.get(k);
        return saved;
    }

    /** The blocks of a saved build: null when the save does not match its size. */
    public static @Nullable StructureBlocks load(int sizeX, int sizeY, int sizeZ, @Nullable List<JsonElement> savedPalette,
                                                 int @Nullable [] runs) {
        if (!fits(sizeX, sizeY, sizeZ) || savedPalette == null || savedPalette.isEmpty() || runs == null
                || runs.length % 2 != 0 || savedPalette.size() > Character.MAX_VALUE + 1) {
            return null;
        }
        BlockState[] palette = new BlockState[savedPalette.size()];
        for (int k = 0; k < palette.length; k++) {
            JsonElement saved = savedPalette.get(k);
            palette[k] = saved == null ? AIR : BlockState.CODEC.parse(JsonOps.INSTANCE, saved).result().orElse(AIR);
        }
        // Air comes first however it was saved: index 0 is what counts as nothing.
        palette[0] = AIR;
        char[] blocks = new char[sizeX * sizeY * sizeZ];
        int at = 0;
        for (int k = 0; k < runs.length; k += 2) {
            int index = runs[k], count = runs[k + 1];
            if (index < 0 || index >= palette.length || count < 0 || at + count > blocks.length) return null;
            // A block a mod no longer has is air, whatever index it kept.
            char stored = palette[index].isAir() ? 0 : (char) index;
            Arrays.fill(blocks, at, at + count, stored);
            at += count;
        }
        if (at != blocks.length) return null;
        return new StructureBlocks(sizeX, sizeY, sizeZ, palette, blocks);
    }
}
