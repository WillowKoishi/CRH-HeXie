package willow.train.hexie.client.render.track;

import com.simibubi.create.content.trains.track.TrackBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import willow.train.hexie.blocks.track.standard.TrackParticleGeometry;
import willow.train.hexie.api.railway.RailwayDefinition;
import willow.train.hexie.compat.create.track.CreateRailwayBackend;

/** Uses Create's full-width destruction effects and connected-track overlays. */
public final class StandardTrackRenderProperties extends TrackBlock.RenderProperties {
    @Override
    public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine particles) {
        if (level instanceof ClientLevel client && target instanceof BlockHitResult hit
                && state.getRenderShape() != RenderShape.INVISIBLE
                && state.shouldSpawnTerrainParticles()) {
            BlockPos pos = hit.getBlockPos();
            double verticalOffset = state.getBlock() instanceof TrackBlock track
                    && CreateRailwayBackend.supports(track.getMaterial())
                    ? CreateRailwayBackend.surfaceHeight(track.getMaterial()) - RailwayDefinition.STANDARD_SURFACE_HEIGHT : 0;
            TrackParticleGeometry.hit(state.getShape(client, pos), hit, verticalOffset,
                    (x, y, z, vx, vy, vz) -> particles.add(
                            new TerrainParticle(client, x, y, z, vx, vy, vz, state, pos)
                                    .updateSprite(state, pos).setPower(0.2F).scale(0.6F)));
        }
        return true;
    }
}
