package fr.clixmods.livinghorizon.compat;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Voxy cannot run in the tests: its sections are stood in for by classes of the same shape. */
class VoxySourceTest {
    private static final int VOLUME = 32 * 32 * 32;

    /** Voxy's section: an array for every section. */
    public static final class VoxySection {
        private final long[] data = new long[VOLUME];

        public long[] _unsafeGetRawDataArray() {
            return data;
        }
    }

    /** The NHblock714 fork's: one voxel and no array, until a block in it changes. */
    public static final class ForkSection {
        private long[] data;
        private final long uniformValue;

        ForkSection(long uniformValue) {
            this.uniformValue = uniformValue;
        }

        public long[] _rawOrNull() {
            return data;
        }

        public long getUniformValue() {
            return uniformValue;
        }

        void set(int index, long id) {
            if (data == null) {
                data = new long[VOLUME];
                Arrays.fill(data, uniformValue);
            }
            data[index] = id;
        }
    }

    private static int index(int x, int y, int z) {
        return (y & 31) << 10 | (z & 31) << 5 | (x & 31);
    }

    @Test
    void readsVoxysSections() throws Throwable {
        VoxySource.Voxels voxels = VoxySource.Voxels.of(MethodHandles.publicLookup(), VoxySection.class);
        VoxySection section = new VoxySection();
        section.data[index(3, 7, 9)] = 42;
        assertEquals(42, voxels.at(section, index(3, 7, 9)));
        assertEquals(0, voxels.at(section, index(9, 7, 3)));
    }

    @Test
    void readsTheForksSectionsOfOneVoxel() throws Throwable {
        VoxySource.Voxels voxels = VoxySource.Voxels.of(MethodHandles.publicLookup(), ForkSection.class);
        ForkSection stone = new ForkSection(17);
        assertEquals(17, voxels.at(stone, index(0, 0, 0)));
        assertEquals(17, voxels.at(stone, index(31, 31, 31)));
        // A block placed in it: the section now has its array, read from then on.
        stone.set(index(4, 5, 6), 42);
        assertEquals(42, voxels.at(stone, index(4, 5, 6)));
        assertEquals(17, voxels.at(stone, index(6, 5, 4)));
    }

    @Test
    void refusesASectionItDoesNotKnow() {
        assertThrows(NoSuchMethodException.class, () -> VoxySource.Voxels.of(MethodHandles.publicLookup(), Object.class));
    }
}
