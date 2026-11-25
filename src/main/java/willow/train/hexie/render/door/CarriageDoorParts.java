package willow.train.hexie.render.door;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;
import willow.train.hexie.render.init.CRH2DoorModelSelection;

// split from willow/train/hexie/client/render/blockentity/CustomRenderedDoorRenderer.java, lines 51-109
final class CarriageDoorParts {
    @FunctionalInterface
    interface PartRenderer {
        void draw(PartialModel model, RenderType layer, PoseStack transform);
    }

    static boolean isShellDoor(BlockState state) {
        return state.getBlock() instanceof CRH2DoorBlockView
                || state.getBlock() instanceof CRH1DoorBlockView;
    }

    /** Compute each authored part's local pose for both buffered and instanced rendering. */
    static void drawParts(CustomRenderedDoorEntityView entity, float progress, PoseStack pose, PartRenderer renderer) {
        BlockState state = entity.asBlockEntity().getBlockState();
        if (state.getBlock() instanceof CRH1DoorBlockView) {
            CRH1DoorRenderer.drawParts(entity, progress, pose, renderer);
            return;
        }
        var facing = state.getValue(DoorBlock.FACING);
        boolean left = entity.isLeftSide(state);
        pose.translate(.5, 0, .5);
        // CRH2 sliding and hinged doors
        if (state.getBlock() instanceof CRH2DoorBlockView door) {
            boolean mirrored = !left;
            pose.mulPose(Axis.YP.rotationDegrees(switch (facing) {
                case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
            }));
            pose.translate(-.5, 0, -.5);
            if (door.isSlideDoor()) {
                renderer.draw(CRH2DoorModelSelection.crh2PassengerDoorFrame(door.doorType(), entity.hasPassengerFrame(), mirrored),
                        RenderType.cutout(), pose);
                pose.translate(door.slideOffset(mirrored, entity.hasReverseSlide(), progress), 0, 0);
            } else {
                double x = HexieBridges.carriageBlock().crh2DoorBlock().cabHingeX(mirrored), z = HexieBridges.carriageBlock().crh2DoorBlock().cabHingeZ();
                pose.translate(x, 0, z);
                pose.mulPose(Axis.YP.rotationDegrees(HexieBridges.carriageBlock().crh2DoorBlock().cabAngleDegrees(mirrored, progress)));
                pose.translate(-x, 0, -z);
            }
            renderer.draw(CRH2DoorModelSelection.crh2DoorLayer(door.doorType(), mirrored, false), RenderType.cutout(), pose);
            renderer.draw(CRH2DoorModelSelection.crh2DoorLayer(door.doorType(), mirrored, true), RenderType.translucent(), pose);
            return;
        }

        // Legacy two-part doors
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot() - 180));
        pose.translate(left ? .5 : -.5, 0, -.5);
        if (entity.isSlideDoor()) {
            float first = .4f;
            if (progress > first) {
                double offset = .2 + (progress - first) / (1 - first) * .7;
                pose.translate(left ? offset : -offset, 0, -.2);
            } else {
                pose.translate(progress * (left ? .2 : -.2), 0, progress / first * -.2);
            }
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(progress * (left ? 90 : -90)));
        }
        var models = HexieBridges.Client.blockEntityViewsClient().customRenderedDoorEntity().getDoorModelLocations(entity, state);
        renderer.draw(DoorPartialModelCache.get(models.first()), RenderType.cutoutMipped(), pose);
        pose.translate(0, 1, 0);
        renderer.draw(DoorPartialModelCache.get(models.second()), RenderType.cutoutMipped(), pose);
    }
}
