package willow.train.hexie.client.render.track;

import net.minecraft.client.Minecraft;
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;
import willow.train.hexie.Main;
import willow.train.hexie.compat.iris.client.CurveBedPbr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A whole sampler texture for one painted face, shared by curves of any length. */
public final class CurveBedTexture extends AbstractTexture {
    private record Corner(float u, float v) {
        static Corner from(TextureAtlasSprite sprite, float[] atlas, int offset) {
            return new Corner((atlas[offset] - sprite.getU0()) / (sprite.getU1() - sprite.getU0()),
                    (atlas[offset + 1] - sprite.getV0()) / (sprite.getV1() - sprite.getV0()));
        }

        int distance(Corner other, TextureAtlasSprite sprite) {
            return Math.max(1, (int) Math.round(Math.hypot((u - other.u) * sprite.contents().width(),
                    (v - other.v) * sprite.contents().height())));
        }
    }

    private record Face(Corner a, Corner b, Corner c, Corner d, boolean repeating) {}
    private record Key(TextureAtlasSprite sprite, Face face) {}

    private static final Map<Key, ResourceLocation> TILES = new HashMap<>();
    private static final Set<CurveBedTexture> ANIMATED = new HashSet<>();
    private static long nextId;
    private static boolean pbrRegistered;
    private static final boolean SODIUM = ModList.get().isLoaded("sodium");
    private final AbstractTexture atlas;
    private final TextureAtlasSprite sprite;
    private final Face face;
    private int users;
    final int width, height, maxMip;
    final float[] sourceUv;
    final boolean repeating;

    private CurveBedTexture(AbstractTexture atlas, TextureAtlasSprite sprite, Face face) {
        this.atlas = atlas;
        this.sprite = sprite;
        this.face = face;
        repeating = face.repeating;
        width = Math.max(face.a.distance(face.b, sprite), face.d.distance(face.c, sprite));
        height = Math.max(face.a.distance(face.d, sprite), face.b.distance(face.c, sprite));
        maxMip = Math.min(sprite.contents().byMipLevel.length - 1,
                31 - Integer.numberOfLeadingZeros(Math.max(width, height)));
        sourceUv = new float[8];
        int i = 0;
        for (Corner point : new Corner[]{face.a, face.b, face.d, face.c}) {
            sourceUv[i++] = sprite.getU(point.u);
            sourceUv[i++] = sprite.getV(point.v);
        }
    }

    static ResourceLocation forFace(TextureAtlasSprite sprite, float[] uv, boolean repeating) {
        // BakedQuad corner order is (0,0), (0,1), (1,1), (1,0).
        var face = new Face(Corner.from(sprite, uv, 0), Corner.from(sprite, uv, 6),
                Corner.from(sprite, uv, 4), Corner.from(sprite, uv, 2), repeating);
        var key = new Key(sprite, face);
        var existing = TILES.get(key);
        var textures = Minecraft.getInstance().getTextureManager();
        if (existing != null) {
            ((CurveBedTexture) textures.getTexture(existing)).users++;
            return existing;
        }
        if (!pbrRegistered && ModList.get().isLoaded("iris")) {
            CurveBedPbr.register();
            pbrRegistered = true;
        }
        var id = Main.asResource("dynamic/curve_bed/" + nextId++);
        var tile = new CurveBedTexture(textures.getTexture(sprite.atlasLocation()), sprite, face);
        textures.register(id, tile);
        tile.users = 1;
        TILES.put(key, id);
        return id;
    }

    public TextureAtlasSprite sourceSprite() { return sprite; }

    /** Iris owns and closes its channel textures. Their sprite metadata can differ from the albedo. */
    public CurveBedTexture companion(AbstractTexture channelAtlas, TextureAtlasSprite channelSprite) {
        var tile = new CurveBedTexture(channelAtlas, channelSprite, face);
        tile.initialize();
        return tile;
    }

    @Override public void load(ResourceManager resources) {
        // Model reload invalidates all cached faces after the block atlas has been stitched.
        if (id == NOT_ASSIGNED) initialize();
    }

    private void initialize() {
        CurveBedTextureCopy.copy(atlas.getId(), this, true);
        if (sprite.contents().getUniqueFrames().distinct().limit(2).count() > 1) ANIMATED.add(this);
    }

    static void refreshAnimations() {
        for (var tile : ANIMATED) {
            // The animated sprite is sampled via the tile and may have no visible chunk quads.
            if (SODIUM) SpriteUtil.INSTANCE.markSpriteActive(tile.sprite);
            CurveBedTextureCopy.copy(tile.atlas.getId(), tile, false);
        }
    }

    @Override public void close() {
        ANIMATED.remove(this);
        releaseId();
    }

    static void release(ResourceLocation id) {
        var textures = Minecraft.getInstance().getTextureManager();
        if (textures.getTexture(id, null) instanceof CurveBedTexture tile && --tile.users == 0) {
            TILES.remove(new Key(tile.sprite, tile.face));
            textures.release(id);
        }
    }

    static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        TILES.values().forEach(textures::release);
        TILES.clear();
        CurveBedTextureCopy.clear();
    }
}
