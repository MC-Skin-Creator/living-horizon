//? if >=26.4 {
/*package fr.clixmods.livinghorizon.render.impostor;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.TextureHandle;
import org.jspecify.annotations.Nullable;

/^*
 * A page of impostors from 26.4, where the game's {@code AbstractTexture} is gone: a texture,
 * its view and its sampler, which the game's texture manager hands out through an external
 * provider ({@link ImpostorAtlas}). It keeps the old class's names, which the sources read
 * there in its place (a replacement in {@code stonecutter.gradle.kts}).
 ^/
public class ImpostorTexture implements TextureHandle, AutoCloseable {
    protected @Nullable GpuTexture texture;
    protected @Nullable GpuTextureView textureView;
    protected @Nullable GpuSampler sampler;

    public GpuTexture getTexture() {
        if (texture == null) throw new IllegalStateException("Texture does not exist, can't get it before something initializes it");
        return texture;
    }

    public GpuTextureView getTextureView() {
        if (textureView == null) throw new IllegalStateException("Texture view does not exist, can't get it before something initializes it");
        return textureView;
    }

    public GpuSampler getSampler() {
        if (sampler == null) throw new IllegalStateException("Sampler does not exist, can't get it before something initializes it");
        return sampler;
    }

    @Override
    public GpuTextureView textureView() {
        return getTextureView();
    }

    @Override
    public GpuSampler sampler() {
        return getSampler();
    }

    @Override
    public void close() {
        if (textureView != null) textureView.close();
        if (texture != null) texture.close();
        textureView = null;
        texture = null;
    }
}
*///?}
