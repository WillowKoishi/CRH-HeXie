package willow.train.hexie.bridge.sticker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.core.sticker.StickerEntry;

/** Thread-safe client chunk-meshing cutter contract used by vanilla SectionCompiler & occlusion mixins. */
public interface StickerMeshCutterBridge {
    /** Updates the lock-free client mirror for chunk meshing and triggers chunk section rebuilds. */
    void onChunkSynced(int chunkX, int chunkZ, List<StickerEntry> entries);

    /** Applies a live local preview/edit override (e.g. during Free-Camera gizmo drag) and rebuilds sections. */
    void setLiveOverride(StickerEntry editing);

    /** Fast O(1) check whether this BlockPos is cut by at least one WINDOW sticker (safe on worker threads). */
    boolean hasWindowCutAt(BlockPos pos);

    /** Fast O(1) check whether this BlockPos OR a multi-block model rooted here may overlap a WINDOW cut. */
    boolean hasCandidateCutAt(BlockPos pos);

    /** Returns true if a neighbor block face looking into a cut cavity should NOT be culled. */
    boolean shouldExposeFaceIntoCut(BlockPos pos, Direction side, BlockPos neighborPos);

    /**
     * Replaces vanilla block tessellation with real coplanar single-layer / zero-thickness cutting +
     * parametric frame & translucent glass emission directly into the chunk section buffers.
     * Returns true if this block was cut and rendered, false to fall back to vanilla rendering.
     */
    boolean tesselateCutBlock(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos,
                              BlockAndTintGetter region, PoseStack pose, VertexConsumer layerConsumer,
                              Function<RenderType, VertexConsumer> layerSupplier, RandomSource random,
                              ModelData modelData, RenderType renderType);
}
