package fr.clixmods.livinghorizon.ui;

import com.mojang.blaze3d.platform.NativeImage;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.lod.LodBake;
import fr.clixmods.livinghorizon.render.lod.LodPreview;
import fr.clixmods.livinghorizon.render.lod.LodRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if <1.21.2
//import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21.2 && <1.21.6
//import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the levels of detail look like: one model at a time, its full model and each of its
 * simpler ones side by side, turning, each with its quads and the distance it begins at with
 * the settings. The models are those the mod has made levels for, the mobs drawn far away
 * since the game started. Drawn on the CPU ({@link LodPreview}), so the same on every version.
 */
public final class LodScreen extends Screen {
    private static final String KEY = "livinghorizon.lods.";
    private static final int SKY = 0xFF9DB8E8, TEXT = 0xFFFFFFFF, DIM = 0xFFA0A0A0;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "lod_preview");
    /** Radians a second the models turn. */
    private static final double TURN = 0.6;

    /** The model shown, kept while the game runs. */
    private static int shown;

    private final @Nullable Screen parent;
    private List<LodRenderer.Preview> previews = List.of();
    private LodRenderer.@Nullable Preview previewOf;
    private @Nullable LodPreview preview;
    private @Nullable DynamicTexture texture;
    private int textureSize, textureLevels;
    private int[] pixels = new int[0];
    private long listedAt;

    public LodScreen(@Nullable Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("<"), b -> step(-1)).bounds(width / 2 - 130, 22, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> step(1)).bounds(width / 2 + 110, 22, 20, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 75, height - 26, 150, 20).build());
    }

    private void step(int by) {
        if (previews.isEmpty()) return;
        shown = Math.floorMod(shown + by, previews.size());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** Escape pressed over Sodium's screen: closed when it is released, see {@link SodiumReturn}. */
    private boolean closeOnRelease;

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && SodiumReturn.below(parent)) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        if (closeOnRelease && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            onClose();
            return true;
        }
        return super.keyReleased(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && SodiumReturn.below(parent)) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (closeOnRelease && keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    *///?}

    @Override
    public void removed() {
        if (texture != null) minecraft.getTextureManager().release(TEXTURE);
        texture = null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Before 1.20.2 a screen draws its own background.
        //? if <1.20.2
        /*renderBackground(graphics);*/
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, TEXT);

        // Made again once a second: the models behind are made while the world goes on.
        long now = System.currentTimeMillis();
        if (now - listedAt > 1000) {
            previews = LodRenderer.previews();
            listedAt = now;
        }
        if (previews.isEmpty()) {
            int y = height / 2 - 10;
            for (var line : font.split(Component.translatable(KEY + "none"), Math.min(320, width - 40))) {
                graphics.drawCenteredString(font, line, width / 2, y, DIM);
                y += font.lineHeight + 2;
            }
            return;
        }
        shown = Math.floorMod(shown, previews.size());
        LodRenderer.Preview chosen = previews.get(shown);
        if (preview == null || previewOf == null || !previewOf.same(chosen)) open(chosen);
        graphics.drawCenteredString(font, Component.translatable(KEY + "model", chosen.name(), shown + 1, previews.size()),
                width / 2, 28, TEXT);
        if (preview == null) return;

        FarConfig config = FarConfig.get();
        int levels = preview.levels();
        int gap = 8, top = 52;
        int size = Math.max(32, Math.min((width - 40 - gap * (levels - 1)) / levels, height - top - 80));
        draw(levels, size, now);
        int left = (width - levels * size - (levels - 1) * gap) / 2;
        double from = config.lodDistance;
        for (int level = 0; level < levels; level++) {
            int x = left + level * (size + gap);
            graphics.fill(x, top, x + size, top + size, SKY);
            blit(graphics, x, top, size, level * textureSize, textureSize, textureSize * levels);
            int y = top + size + 4;
            Component name = level == 0 ? Component.translatable(KEY + "full") : Component.translatable(KEY + "level", level);
            graphics.drawCenteredString(font, name, x + size / 2, y, TEXT);
            graphics.drawCenteredString(font, Component.translatable(KEY + "quads", preview.triangles(level)), x + size / 2,
                    y + font.lineHeight + 2, DIM);
            Component when;
            if (level == 0) {
                when = Component.translatable(KEY + "near", (int) Math.round(from));
            } else if (!config.mobLods || level > config.lodLevels) {
                when = Component.translatable(KEY + "unused");
            } else {
                when = Component.translatable(KEY + "from", (int) Math.round(from));
                from *= config.lodSpacing / 100.0;
            }
            graphics.drawCenteredString(font, when, x + size / 2, y + 2 * (font.lineHeight + 2), DIM);
        }
    }

    /** Reads the textures of a model and makes its pictures' geometry. */
    private void open(LodRenderer.Preview chosen) {
        previewOf = chosen;
        List<LodBake.Pixels> textures = new ArrayList<>();
        for (Identifier id : chosen.textures()) {
            LodBake.Pixels read = LodRenderer.pixels(id);
            if (read != null) textures.add(read);
        }
        preview = textures.isEmpty() ? null : new LodPreview(chosen, textures);
    }

    /** Draws every level, turned to where the clock says, into the texture; made again when the size changes. */
    private void draw(int levels, int size, long now) {
        // At most 160 pixels a side, scaled up on screen: quick enough to draw every frame.
        int resolution = Math.max(48, Math.min(160, size));
        if (texture == null || textureSize != resolution || textureLevels != levels) {
            if (texture != null) minecraft.getTextureManager().release(TEXTURE);
            NativeImage image = new NativeImage(resolution * levels, resolution, false);
            //? if >=1.21.5 {
            texture = new DynamicTexture(() -> "living horizon level of detail preview", image);
            //?} else
            /*texture = new DynamicTexture(image);*/
            minecraft.getTextureManager().register(TEXTURE, texture);
            textureSize = resolution;
            textureLevels = levels;
        }
        NativeImage image = texture.getPixels();
        if (image == null || preview == null) return;
        double yaw = now / 1000.0 * TURN;
        for (int level = 0; level < levels; level++) {
            pixels = preview.draw(level, yaw, Math.toRadians(20), resolution, pixels);
            for (int y = 0; y < resolution; y++) {
                for (int x = 0; x < resolution; x++) {
                    int pixel = pixels[y * resolution + x];
                    //? if >=1.21.2 {
                    image.setPixel(level * resolution + x, y, pixel);
                    //?} else
                    /*image.setPixelRGBA(level * resolution + x, y, (pixel & 0xFF00FF00) | ((pixel >> 16) & 0xFF) | ((pixel & 0xFF) << 16));*/
                }
            }
        }
        texture.upload();
    }

    /** One level's picture, a square of the texture from {@code u}, drawn {@code size} pixels square. */
    private static void blit(GuiGraphics graphics, int x, int y, int size, int u, int tile, int wide) {
        //? if >=1.21.6 {
        float u0 = (float) u / wide, u1 = (float) (u + tile) / wide;
        graphics.blit(TEXTURE, x, y, x + size, y + size, u0, u1, 0f, 1f);
        //?} else {
        /*//? if >=1.21.2 {
        graphics.blit(RenderTypes::guiTextured, TEXTURE, x, y, u, 0, size, size, tile, tile, wide, tile);
        //?} else {
        /^RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(TEXTURE, x, y, size, size, u, 0, tile, tile, wide, tile);
        RenderSystem.disableBlend();
        ^///?}
        *///?}
    }
}
