package willow.train.hexie.render.coupler;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationParametersView;

/** CRH1 gangways share the same contact-plane solver in snapshots and physical plots. */
public final class CRH1EndFaceRenderer implements CouplerModelParts {
    private final PartialCouplerRenderer parts;
    public CRH1EndFaceRenderer(PartialCouplerRenderer parts) { this.parts = parts; }
    @Override public void arm(PoseStack pose, Sink sink) { parts.arm(pose, sink); }
    @Override public void base(PoseStack pose, Sink sink) { parts.base(pose, sink); }
    @Override public AABB attachedBounds(BlockState state) { return parts.attachedBounds(state); }

    @Override public void attached(BlockState state, BlockEntity display, SnapshotCouplerAnimationParametersView parameters,
                                   float partialTick, boolean inWorld, PoseStack pose, Sink sink) {
        if (inWorld && display != null && ModList.get().isLoaded("simurail") && ModList.get().isLoaded("sable")) {
            var physical = HexieBridges.Client.simurailClient().crh1EndFacePhysicalRender().parameters(display, partialTick);
            if (physical != null) {
                parts.attached(state, display, physical, partialTick, false, pose, sink);
                return;
            }
        }
        parts.attached(state, display, parameters, partialTick, inWorld, pose, sink);
    }
}
