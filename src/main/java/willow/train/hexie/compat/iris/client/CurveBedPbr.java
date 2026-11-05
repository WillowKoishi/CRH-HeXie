package willow.train.hexie.compat.iris.client;

import net.irisshaders.iris.pbr.loader.PBRTextureLoader.PBRTextureConsumer;
import net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry;
import net.irisshaders.iris.pbr.texture.PBRAtlasTexture;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import net.irisshaders.iris.pbr.texture.SpriteContentsExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.server.packs.resources.ResourceManager;
import willow.train.hexie.client.render.track.CurveBedTexture;

public final class CurveBedPbr {
    private CurveBedPbr() {}

    public static void register() {
        PBRTextureLoaderRegistry.INSTANCE.register(CurveBedTexture.class, CurveBedPbr::load);
    }

    private static void load(CurveBedTexture tile, ResourceManager resources, PBRTextureConsumer consumer) {
        var sprite = tile.sourceSprite();
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation());
        var manager = PBRTextureManager.INSTANCE;
        var channels = manager.getOrLoadHolder(atlas.getId());
        var source = ((SpriteContentsExtension) sprite.contents()).getPBRHolder();
        // Iris reuses one consumer for nested loads. Explicitly supply both channels so the
        // source atlas can never become owned (and subsequently closed) by this tile's holder.
        var defaults = manager.getHolder(0);
        var normal = defaults.normalTexture();
        var specular = defaults.specularTexture();
        if (source != null) {
            normal = channel(tile, channels.normalTexture(), source.getNormalSprite(), normal);
            try {
                specular = channel(tile, channels.specularTexture(), source.getSpecularSprite(), specular);
            } catch (RuntimeException | Error failure) {
                if (normal != defaults.normalTexture()) normal.close();
                throw failure;
            }
        }
        consumer.acceptNormalTexture(normal);
        consumer.acceptSpecularTexture(specular);
    }

    private static AbstractTexture channel(CurveBedTexture tile, AbstractTexture source,
                                           TextureAtlasSprite sprite, AbstractTexture fallback) {
        return source instanceof PBRAtlasTexture && sprite != null ? tile.companion(source, sprite) : fallback;
    }
}
