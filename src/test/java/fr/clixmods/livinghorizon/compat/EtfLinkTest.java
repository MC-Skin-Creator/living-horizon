package fr.clixmods.livinghorizon.compat;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The texture a mob wears, as an impostor key counts it: the same whether its eyes are open or
 * closed this frame, and whether ETF patched it for an emissive layer. A blink counted in gave
 * the same mob a new key, and a new sheet, every time it was asked during one.
 */
class EtfLinkTest {
    private static final Identifier WORN = id("minecraft", "optifine/random/entity/cow/temperate_cow2.png");

    @Test
    void blinksAreTheTextureWorn() {
        assertEquals(WORN, EtfLink.steady(id("minecraft", "optifine/random/entity/cow/temperate_cow2_blink.png")));
        assertEquals(WORN, EtfLink.steady(id("minecraft", "optifine/random/entity/cow/temperate_cow2_blink2.png")));
    }

    @Test
    void patchedCopiesAreTheTextureWorn() {
        assertEquals(WORN, EtfLink.steady(id("etf_patched_minecraft", "optifine/random/entity/cow/temperate_cow2.png")));
        assertEquals(WORN, EtfLink.steady(id("etf_patched_minecraft", "optifine/random/entity/cow/temperate_cow2_blink.png")));
    }

    @Test
    void aTextureWornIsKept() {
        assertSame(WORN, EtfLink.steady(WORN));
        Identifier blinking = id("minecraft", "textures/entity/blinking.png");
        assertSame(blinking, EtfLink.steady(blinking));
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
