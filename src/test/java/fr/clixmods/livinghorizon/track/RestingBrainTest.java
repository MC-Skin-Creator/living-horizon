package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server's simulation of the players who logged off, and the message that carries it. */
class RestingBrainTest {
    private static final String WORLD = "minecraft:overworld";

    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    /** A stone floor under y = 63 over 41 by 41 blocks around (0, 0), nothing else. */
    private static final class Flat implements Footing.Source {
        final boolean loaded;

        Flat(boolean loaded) {
            this.loaded = loaded;
        }

        @Override
        public net.minecraft.world.level.block.state.BlockState state(net.minecraft.core.BlockPos at) {
            return at.getY() <= 63 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public net.minecraft.world.level.material.FluidState fluid(net.minecraft.core.BlockPos at) {
            return Blocks.AIR.defaultBlockState().getFluidState();
        }

        @Override
        public boolean loaded(net.minecraft.core.BlockPos at) {
            return loaded;
        }

        @Override
        public net.minecraft.world.phys.shapes.VoxelShape collision(net.minecraft.world.level.block.state.BlockState state,
                                                                    net.minecraft.core.BlockPos at) {
            return state.isAir() ? net.minecraft.world.phys.shapes.Shapes.empty() : net.minecraft.world.phys.shapes.Shapes.block();
        }

        @Override
        public double fluidHeight(net.minecraft.world.level.material.FluidState fluid, net.minecraft.core.BlockPos at) {
            return 0;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int top() {
            return 256;
        }
    }

    private static RestingBrain brain(boolean loaded, int max) {
        Flat flat = new Flat(loaded);
        return new RestingBrain(new RestingBrain.World() {
            @Override
            public Footing.Source blocks(String dimension) {
                return flat;
            }

            @Override
            public boolean night(String dimension) {
                return false;
            }
        }, 42L, max);
    }

    private static PositionsMessage.Player gone(String name, double x) {
        return new PositionsMessage.Player(name, x, 64, 0, 0f, WORLD, false, false, null);
    }

    private static final List<RestingBrain.Eye> EYE = List.of(new RestingBrain.Eye(WORLD, 0, 0));

    private static String run(RestingBrain brain, int steps) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < steps; i++) {
            Map<String, PositionsMessage.Actor> now = brain.step(4, List.of(gone("Alex", 0.5)), EYE);
            PositionsMessage.Actor a = now.get("Alex");
            out.append(a.activity()).append(':').append(Math.round(a.x() * 100)).append(',').append(Math.round(a.z() * 100)).append(';');
        }
        return out.toString();
    }

    @Test
    void theSameSeedLivesTheSameLife() {
        assertEquals(run(brain(true, 8), 600), run(brain(true, 8), 600));
    }

    @Test
    void anActorWalksOnceTheGroundIsRead() {
        RestingBrain brain = brain(true, 8);
        boolean walked = false, stayed = true;
        for (int i = 0; i < 1500 && !walked; i++) {
            PositionsMessage.Actor a = brain.step(4, List.of(gone("Alex", 0.5)), EYE).get("Alex");
            walked = a.activity() == RestingBrain.WALK;
            stayed &= a.y() == 64;
        }
        assertTrue(walked);
        assertTrue(stayed);
    }

    @Test
    void whereTheBlocksAreNotLoadedItStaysPut() {
        RestingBrain brain = brain(false, 8);
        for (int i = 0; i < 400; i++) {
            PositionsMessage.Actor a = brain.step(4, List.of(gone("Alex", 0.5)), EYE).get("Alex");
            assertEquals(0.5, a.x(), 1e-9);
            assertEquals(RestingBrain.IDLE, a.activity());
        }
    }

    @Test
    void onlyTheNearestUpToTheLimitAreSimulatedAndNoOneOnline() {
        RestingBrain brain = brain(true, 2);
        PositionsMessage.Player online = new PositionsMessage.Player("On", 1, 64, 0, 0f, WORLD, true, false, null);
        Map<String, PositionsMessage.Actor> now = brain.step(4,
                List.of(gone("Far", 100), gone("Near", 2), gone("Mid", 20), gone("Out", 500), online), EYE);
        assertEquals(java.util.Set.of("Near", "Mid"), now.keySet());
    }

    @Test
    void theMessageRoundTripsAndOlderKindsStillRead() {
        PositionsMessage.Actor bed = new PositionsMessage.Actor("Alex", 1.5, 64.6875, -2.5, 90f, WORLD, RestingBrain.BED, 1, 64, -3);
        PositionsMessage.Actor walk = new PositionsMessage.Actor("Bob", 3, 64, 4, 10f, WORLD, RestingBrain.WALK, 0, 0, 0);
        PositionsMessage.Decoded read = PositionsMessage.read(PositionsMessage.resting(1234L, List.of(bed, walk)));
        assertNotNull(read);
        assertEquals(PositionsMessage.RESTING, read.kind());
        assertEquals(1234L, read.resting().tick());
        assertEquals(List.of(bed, walk), read.resting().actors());
        // Empty is still a word: the server runs the simulation.
        assertTrue(PositionsMessage.read(PositionsMessage.resting(1L, List.of())).resting().actors().isEmpty());
        // A player list of this version still reads.
        assertNotNull(PositionsMessage.read(PositionsMessage.players(List.of(gone("Alex", 0)))).players());
    }

    @Test
    void anOlderClientLeavesTheKindUnreadAndKeepsItsLife() {
        // Kind 9 as a later version might add: not read, and not taken for an unreadable format.
        byte[] message = PositionsMessage.resting(1L, List.of());
        message[8] = 9;
        assertNull(PositionsMessage.read(message));
        SharedPositions shared = new SharedPositions();
        shared.receive(message);
        assertNull(shared.unreadable());
        assertNull(shared.resting("Alex"));
    }

    @Test
    void theClientFollowsTheServerOnlyWhileItKeepsSpeaking() {
        SharedPositions shared = new SharedPositions();
        shared.receive(PositionsMessage.resting(1L, List.of(
                new PositionsMessage.Actor("Alex", 1, 64, 1, 0f, WORLD, RestingBrain.SIT, 0, 0, 0))));
        assertNotNull(shared.resting("alex"));
        for (int i = 0; i < 100; i++) shared.update();
        assertNull(shared.resting("Alex"));
        shared.receive(PositionsMessage.resting(2L, List.of()));
        assertNull(shared.resting("Alex"));
        assertFalse(new ByteArrayOutputStream().size() > 0);
    }
}
