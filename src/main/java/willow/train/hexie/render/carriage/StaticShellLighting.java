package willow.train.hexie.render.carriage;

import java.util.Map;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

// split from willow/train/hexie/client/render/StaticShellSections.java, lines 281-295
public final class StaticShellLighting {
    /** Same continuous light field for both shells, independent of anchor and face direction. */
    public static int lightAt(net.minecraft.world.level.BlockAndTintGetter level, Map<BlockPos, Integer> cache,
                              double x, double y, double z) {
        x -= .5; y -= .5; z -= .5;
        int bx = (int)Math.floor(x), by = (int)Math.floor(y), bz = (int)Math.floor(z);
        double fx = x-bx, fy = y-by, fz = z-bz, block = 0, sky = 0;
        for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 2; dz++) {
            var at = new BlockPos(bx+dx, by+dy, bz+dz);
            int light = cache.computeIfAbsent(at, p -> LevelRenderer.getLightColor(level, p));
            double weight = (dx == 0 ? 1-fx : fx)*(dy == 0 ? 1-fy : fy)*(dz == 0 ? 1-fz : fz);
            block += (light & 65535)*weight;
            sky += (light >>> 16)*weight;
        }
        return (int)Math.round(block) | ((int)Math.round(sky) << 16);
    }
}
