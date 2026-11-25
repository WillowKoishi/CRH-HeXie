package willow.train.hexie.interaction.surface;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.ControlSurface;

@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class ClientGeometricPicker {
    private ClientGeometricPicker() {}

    record Selection(BlockControlContext target, BlockHitResult hit, double distanceSquared, boolean wins) {
        SurfaceInputTarget.Pick controlPick() {
            var control = BlockControlHit.controlOf(hit);
            if (control == null) return null;
            double distance = Math.sqrt(distanceSquared);
            var point = hit.getLocation();
            return new SurfaceInputTarget.Pick(new SurfaceInputTarget(null, target, point),
                    new ControlSurface.Pick(control, distance, point), distance);
        }

        boolean outsideOwner() {
            var local = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
            return local.x < 0 || local.x > 1 || local.y < 0 || local.y > 1 || local.z < 0 || local.z > 1;
        }
    }

    /** All three positions are in rendered world space. A distance tie preserves vanilla selection. */
    public static boolean winsAgainstVanilla(Vec3 eye, Vec3 geometricPoint, HitResult vanilla) {
        return vanilla == null || vanilla.getType() == HitResult.Type.MISS
                || eye.distanceToSqr(geometricPoint) < eye.distanceToSqr(vanilla.getLocation());
    }

    static Selection select(SurfaceInputSources.Query query, Vec3 from, Vec3 to) {
        var vanilla = query.vanilla();
        var obstructions = query.obstructions();
        double reachSquared = from.distanceToSqr(to);
        Selection nearest = null;
        for (var target : query.candidates()) {
            var hit = query.selected(target);
            if (hit == null) hit = intersect(target, from, to);
            if (hit == null) continue;
            var worldPoint = SurfacePointerQuery.renderWorld(target, hit.getLocation());
            double distanceSquared = from.distanceToSqr(worldPoint);
            if (!(distanceSquared <= reachSquared) || nearest != null && nearest.distanceSquared <= distanceSquared) continue;
            boolean wins = winsAgainstVanilla(from, worldPoint, vanilla);
            // Allow existing input on the vanilla-selected face or on the selected carrier itself.
            boolean sameFace = target.carrier() == null && vanilla instanceof BlockHitResult block
                    && hit.getBlockPos().equals(block.getBlockPos()) && worldPoint.equals(block.getLocation());
            boolean sameCarrier = vanilla instanceof EntityHitResult entity && entity.getEntity() == target.carrier();
            if (!wins && !sameFace && !sameCarrier) continue;
            double distance = Math.sqrt(distanceSquared);
            if (obstructions.stream().anyMatch(block -> block.hides(target, from, distance))) continue;
            // A door half or panel member can receive native use on behalf of the declaring host.
            nearest = new Selection(target, hit, distanceSquared, wins);
        }
        return nearest;
    }

    private static BlockHitResult intersect(BlockControlContext target, Vec3 from, Vec3 to) {
        var state = target.state();
        var host = BlockControlHost.find(state);
        if (host != null) {
            if (!host.allowsWorldControl()) return null;
            var ray = SurfacePointerQuery.localRay(target, from, to);
            var picked = host.pickControl(target, ray.from(), ray.to());
            return picked == null ? null : picked.blockHit();
        }
        // A data-defined coupler can be attached to a third-party block with an ordinary voxel outline.
        var coupler = HexieBridges.partBlock().couplerInteraction();
        if (target.carrier() != null || !coupler.hasGeometry(state)) return null;
        var ray = SurfacePointerQuery.localRay(target, from, to);
        var entity = target.blocks().getBlockEntity(target.position()) instanceof CouplerBlockEntityView be ? be : null;
        float progress = entity == null ? coupler.coverProgress(state) : entity.coverProgress(target.partialTick());
        var hit = coupler.clipWorld(state, target.position(), progress, entity, target.partialTick(), ray.from(), ray.to());
        return hit == null ? null : new BlockHitResult(hit.point(), hit.face(), target.position(), false);
    }

    private static Selection worldSelection() {
        if (!SurfaceInputClient.gameplay() || SurfaceInputClient.dragging() || SurfaceInputClient.capturesInput()
                || SurfaceInputSources.blocked()) return null;
        var selected = SurfacePointerQuery.selection();
        return selected != null && selected.target.carrier() == null ? selected : null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        var selected = worldSelection();
        if (selected == null || !selected.wins && !selected.outsideOwner()) return;
        var host = BlockControlHost.find(selected.target.state());
        if (host != null && !host.nativeInteraction()) return;
        var hit = selected.hit;
        event.setCanceled(true);
        event.setSwingHand(true);
        HexieBridges.sync().sendExtendedBlockInteract(hit.getBlockPos(), hit.getDirection(),
                hit.getLocation(), event.getHand(), hit.isInside());
    }

    @SubscribeEvent
    public static void highlight(RenderHighlightEvent.Block event) {
        var selected = worldSelection();
        if (selected != null && selected.wins) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void outline(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        var selected = worldSelection();
        if (selected == null || !selected.wins) return;
        var target = selected.target;
        var shape = outlineShape(selected);
        var origin = Vec3.atLowerCornerOf(selected.hit.getBlockPos());
        var frame = SurfaceInputSources.frame(target);
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        var pose = event.getPoseStack().last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            Vec3 a = frame.toWorld(origin.add(x1, y1, z1)).subtract(camera);
            Vec3 b = frame.toWorld(origin.add(x2, y2, z2)).subtract(camera);
            Vec3 normal = b.subtract(a).normalize();
            vertex(lines, pose, a, normal);
            vertex(lines, pose, b, normal);
        });
        buffers.endBatch(RenderType.lines());
    }

    private static VoxelShape outlineShape(Selection selected) {
        var target = selected.target;
        var pos = selected.hit.getBlockPos();
        var outline = target.blocks().getBlockState(pos).getShape(target.blocks(), pos);
        var local = selected.hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        if (!outline.isEmpty() && outline.bounds().inflate(1e-6).contains(local)) return outline;
        var state = target.state();
        var host = BlockControlHost.find(state);
        var bounds = host != null ? host.controlBounds(state, target.blocks().getBlockEntity(target.position()))
                : HexieBridges.worldBlock().couplerAdapterGeometry().bounds(state,
                        target.blocks().getBlockEntity(pos) instanceof CouplerBlockEntityView be ? be : null);
        return bounds == null ? outline : Shapes.create(bounds.move(target.position().subtract(pos)));
    }

    private static void vertex(VertexConsumer lines, PoseStack.Pose pose, Vec3 point, Vec3 normal) {
        lines.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(0, 0, 0, 102)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
