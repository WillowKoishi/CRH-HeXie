package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock;
import willow.train.hexie.compat.create.SnapshotCouplerAnimation;
import willow.train.hexie.init.client.PartialModelInit;

/** A single authored electrical cable joins compatible, actually coupled end faces. */
public final class CRH2EndFaceRenderer implements CouplerModelParts {
    private final PartialCouplerRenderer coupler;

    public CRH2EndFaceRenderer(PartialCouplerRenderer coupler) { this.coupler = coupler; }
    @Override public void arm(PoseStack pose, Sink sink) { coupler.arm(pose, sink); }
    @Override public void base(PoseStack pose, Sink sink) { coupler.base(pose, sink); }
    @Override public AABB attachedBounds(BlockState state) { return coupler.attachedBounds(state); }

    public static boolean rendersCable(CRH2EndFaceBlock.Type own, SnapshotCouplerAnimation.Parameters parameters) {
        if (parameters == null || !parameters.coupled() || parameters.partnerPose() == null) return false;
        var partner = parameters.partnerPose();
        // Both cars receive the same pair. Draw its whole cable from exactly one stable owner.
        if (parameters.endpoint().compareTo(partner.endpoint()) >= 0 || partner.state() == null
                || !partner.state().block().equals("hexie:crh2a_endface")) return false;
        String value = partner.state().properties().getOrDefault("variant", "original");
        for (var type : CRH2EndFaceBlock.Type.values())
            if (type.getSerializedName().equals(value)) return own.blackCableCompatible(type);
        return false;
    }

    @Override public void attached(BlockState state, BlockEntity display, SnapshotCouplerAnimation.Parameters parameters,
                                   float partialTick, boolean inWorld, PoseStack pose, Sink sink) {
        coupler.attached(state, display, parameters, partialTick, inWorld, pose, sink);
        var type = CRH2EndFaceBlock.type(state);
        if (inWorld || !rendersCable(type, parameters)) return;
        var shape = type.isHorizontal() ? EndFaceCableGeometry.HORIZONTAL : EndFaceCableGeometry.TILTED;
        var start = shape.start();
        var partner = parameters.partnerPose();
        var rotation = partner.rotation();
        var origin = partner.origin();
        Vector3f endPosition = new Vector3f(start.position()).rotate(rotation)
                .add((float) origin.x(), (float) origin.y(), (float) origin.z());
        // The authored far ring already faces a reversed mate. Undo that neutral half-turn
        // before applying the actual mate pose, so square cables do not gain an artificial roll.
        Vector3f endUp = new Vector3f(shape.end().up()).rotateY(-(float) Math.PI).rotate(rotation);
        var end = new CableDeformation.Endpoint(endPosition,
                new Vector3f(start.tangent()).rotate(rotation).negate(), endUp);
        Vector3f sag = shape.sagDirection();
        // Both references describe the same authored bend, even though the mate faces backwards.
        Vector3f sharedSag = new Vector3f(sag).add(new Vector3f(sag).rotateY(-(float) Math.PI).rotate(rotation));
        if (sharedSag.lengthSquared() > 1e-8f) sag = sharedSag.normalize();
        var deformation = CableDeformation.solve(shape, start, end, sag);
        sink.cable(type.isHorizontal() ? PartialModelInit.CRH2_END_CABLE_HORIZONTAL : PartialModelInit.CRH2_END_CABLE_TILTED,
                pose, deformation);
    }
}
