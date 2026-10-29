package fr.clixmods.livinghorizon.track;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Airships and other moving builds of Sable, remembered: their blocks, their pose, when they are forgotten. */
class FarStructuresTest {
    private static BlockState stairs;

    @BeforeAll
    static void boot() {
        GameBoot.start();
        stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
    }

    /** A small ship: a deck of planks, a mast of logs, stairs at the bow. */
    private static BlockState ship(int x, int y, int z) {
        if (y == 0) return Blocks.OAK_PLANKS.defaultBlockState();
        if (x == 1 && z == 1) return Blocks.OAK_LOG.defaultBlockState();
        if (x == 2 && y == 1 && z == 3) return stairs;
        return Blocks.AIR.defaultBlockState();
    }

    @Test
    void blocksReadBackAsSavedOnAnyVersion() {
        StructureBlocks blocks = StructureBlocks.read(3, 3, 4, FarStructuresTest::ship);
        assertNotNull(blocks);
        assertEquals(12 + 2 + 1, blocks.solid());

        StructureBlocks loaded = StructureBlocks.load(3, 3, 4, blocks.savedPalette(), blocks.savedRuns());
        assertNotNull(loaded);
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 3; x++) assertEquals(ship(x, y, z), loaded.state(x, y, z), x + " " + y + " " + z);
            }
        }
        assertEquals(blocks.hash(), loaded.hash());
        assertEquals(Blocks.AIR.defaultBlockState(), loaded.state(-1, 0, 0), "outside the box is air");
    }

    @Test
    void aBlockNoModHasAnyMoreIsAir() {
        StructureBlocks blocks = StructureBlocks.read(3, 3, 4, FarStructuresTest::ship);
        assertNotNull(blocks);
        List<JsonElement> palette = new ArrayList<>(blocks.savedPalette());
        int log = -1;
        for (int i = 0; i < palette.size(); i++) {
            if (palette.get(i).toString().contains("oak_log")) log = i;
        }
        palette.set(log, JsonParser.parseString("{\"Name\":\"gone:mast\"}"));
        StructureBlocks loaded = StructureBlocks.load(3, 3, 4, palette, blocks.savedRuns());
        assertNotNull(loaded);
        assertTrue(loaded.state(1, 1, 1).isAir());
        assertEquals(12 + 1, loaded.solid());
    }

    @Test
    void aSaveThatDoesNotMatchItsSizeIsDropped() {
        StructureBlocks blocks = StructureBlocks.read(3, 3, 4, FarStructuresTest::ship);
        assertNotNull(blocks);
        assertNull(StructureBlocks.load(3, 3, 5, blocks.savedPalette(), blocks.savedRuns()));
        assertNull(StructureBlocks.load(3, 3, 4, blocks.savedPalette(), new int[]{0, 36, 9}));
        assertNull(StructureBlocks.load(3, 3, 4, blocks.savedPalette(), new int[]{99, 36}));
    }

    @Test
    void aBuildTooLargeIsNotKept() {
        assertFalse(StructureBlocks.fits(129, 128, 128));
        assertTrue(StructureBlocks.fits(128, 128, 128));
        assertNull(StructureBlocks.read(200, 200, 200, (x, y, z) -> Blocks.STONE.defaultBlockState()));
    }

    @Test
    void itsPlotIsPlacedAsSableDrawsIt() {
        FarStructures.Remembered ship = new FarStructures.Remembered(UUID.randomUUID());
        // Plot 20 million blocks out, turned a quarter round the vertical axis, at 100, 80, -40.
        ship.x = 100;
        ship.y = 80;
        ship.z = -40;
        double half = Math.sqrt(0.5);
        ship.qy = half;
        ship.qw = half;
        ship.rx = 20_000_000.5;
        ship.ry = 64;
        ship.rz = 20_000_000.5;
        Vector3d pivot = ship.world(20_000_000.5, 64, 20_000_000.5);
        assertEquals(100, pivot.x, 1e-6);
        assertEquals(80, pivot.y, 1e-6);
        assertEquals(-40, pivot.z, 1e-6);
        // One block towards +x in the plot is one block towards -z in the world, turned that way.
        Vector3d bow = ship.world(20_000_001.5, 64, 20_000_000.5);
        assertEquals(100, bow.x, 1e-6);
        assertEquals(-41, bow.z, 1e-6);
        ship.sizeX = 2;
        ship.sizeY = 2;
        ship.sizeZ = 2;
        assertEquals(Math.sqrt(3), ship.radius(), 1e-9);
    }

    @Test
    void goneCloseByIsTakenApartGoneFarIsLeftBehind() {
        assertTrue(FarStructures.gone(20, 10), "gone beside the player: taken apart");
        assertFalse(FarStructures.gone(200, 10), "gone far away: out of range, kept");
    }

    @Test
    void aCopyWhereSableWouldSendItIsNotThere() {
        // Chunks are sent 192 blocks out: a copy whose whole box is well inside is missing.
        assertTrue(FarStructures.missing(100, 20, 192));
        assertFalse(FarStructures.missing(150, 20, 192), "its edge is near where chunks stop: not judged");
        assertFalse(FarStructures.missing(400, 20, 192));
    }

    @Test
    void theFileKeepsEveryBuildWithItsBlocks() throws Exception {
        FarStructures.Remembered ship = new FarStructures.Remembered(UUID.randomUUID());
        ship.dimension = "minecraft:overworld";
        ship.x = 12.5;
        ship.qw = 1;
        StructureBlocks blocks = StructureBlocks.read(3, 3, 4, FarStructuresTest::ship);
        assertNotNull(blocks);
        ship.sizeX = 3;
        ship.sizeY = 3;
        ship.sizeZ = 4;
        ship.minX = 20_000_000;
        ship.palette = blocks.savedPalette();
        ship.runs = blocks.savedRuns();
        FarStructures.Remembered empty = new FarStructures.Remembered(UUID.randomUUID());
        empty.dimension = "minecraft:overworld";

        StringWriter writer = new StringWriter();
        FarStructures.write(List.of(ship, empty), writer);
        JsonElement root = JsonParser.parseString(writer.toString());
        assertEquals(FarStructures.VERSION, root.getAsJsonObject().get("version").getAsInt());
        List<FarStructures.Remembered> read = FarStructures.read(root.getAsJsonObject().get("structures"));

        assertEquals(1, read.size(), "a build with no blocks is left out");
        FarStructures.Remembered back = read.get(0);
        assertEquals(ship.id(), back.id());
        assertEquals(12.5, back.position().x);
        assertEquals(20_000_000, back.minX);
        assertNotNull(back.blocks());
        assertEquals(stairs, back.blocks().state(2, 1, 3));
    }
}
