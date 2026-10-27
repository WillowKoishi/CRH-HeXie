package willow.train.hexie.client.carriage.browser;

import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

/** Native material batches plus live block entities in an isolated preview world. */
public final class CarriagePreview implements AutoCloseable {
    public static ShaderInstance shader;
    private final CarriageAssets.Model model;
    private final VirtualRenderWorld world;
    private final CarriageRenderLayer shell;
    private final CarriageRenderLayer interior;
    private final CarriageRenderLayer rails;

    public CarriagePreview(CarriageAssets.Model model) {
        this.model = model;
        Minecraft mc = Minecraft.getInstance();
        world = new VirtualRenderWorld(mc.level, 0, Math.max(16, model.height()), BlockPos.ZERO, () -> {});
        world.setExternalLight(LightTexture.FULL_BRIGHT);
        for (var cell : model.cells()) world.setBlock(cell.pos(), cell.state(), 0);
        for (var cell : model.cells()) if (cell.state().getBlock() instanceof EntityBlock factory) {
            BlockEntity entity = factory.newBlockEntity(cell.pos(), cell.state());
            if (entity != null) {
                entity.setLevel(world);
                if (cell.data() != null) entity.loadWithComponents(cell.data().copy(), world.registryAccess());
                world.setBlockEntity(entity);
            }
        }
        interior = bake(false);
        CarriageRenderLayer exterior;
        try { exterior = bake(true); }
        catch (RuntimeException e) { if (interior != null) interior.close(); throw e; }
        shell = exterior;
        try { rails = bakeRails(); }
        catch (RuntimeException e) {
            if (shell != null) shell.close();
            if (interior != null) interior.close();
            throw e;
        }
    }

    private CarriageRenderLayer bakeRails() {
        return CarriageRenderLayer.capture(buffers -> {
            PoseStack pose = new PoseStack();
            for (int z = -64; z < 64; z++) {
                pose.pushPose();
                pose.translate(-.5, 0, z);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(model.track(), pose, buffers,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        });
    }

    public void renderRails(Matrix4f trackOrigin, int copies) {
        CarriageRenderLayer.composite(1, () -> {
            for (int i = -copies; i <= copies; i++)
                rails.drawNative(new Matrix4f(trackOrigin).translate(0, 0, i * 128));
        });
    }

    private CarriageRenderLayer bake(boolean exterior) {
        Minecraft mc = Minecraft.getInstance();
        return CarriageRenderLayer.capture(buffers -> {
            PoseStack pose = new PoseStack();
            for (var cell : model.cells()) {
                if (cell.shell() != exterior) continue;
                pose.pushPose();
                pose.translate(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ());
                // Animated entity blocks are rendered with their real block entity below.
                if (cell.state().getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
                    var baked = mc.getBlockRenderer().getBlockModel(cell.state());
                    var entity = world.getBlockEntity(cell.pos());
                    var data = baked.getModelData(world, cell.pos(), cell.state(), entity == null
                            ? net.neoforged.neoforge.client.model.data.ModelData.EMPTY : entity.getModelData());
                    var random = net.minecraft.util.RandomSource.create(cell.state().getSeed(cell.pos()));
                    for (RenderType type : baked.getRenderTypes(cell.state(), random, data)) {
                        mc.getBlockRenderer().renderBatched(cell.state(), cell.pos(), world, pose,
                                buffers.getBuffer(type), false, random, data, type);
                    }
                }
                if (!cell.state().getFluidState().isEmpty()) {
                    VertexConsumer fluid = buffers.getBuffer(ItemBlockRenderTypes.getRenderLayer(cell.state().getFluidState()));
                    mc.getBlockRenderer().renderLiquid(cell.pos(), world,
                            new FluidOffset(fluid, cell.pos()), cell.state(), cell.state().getFluidState());
                }
                pose.popPose();
            }
        });
    }

    // LiquidBlockRenderer emits chunk-local coordinates; the preview can span several chunks.
    private record FluidOffset(VertexConsumer delegate, BlockPos pos) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x + (pos.getX() & ~15), y + (pos.getY() & ~15), z + (pos.getZ() & ~15));
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r,g,b,a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u,v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u,v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u,v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x,y,z); return this; }
    }

    private void drawEntities(boolean exterior, Matrix4f transform) {
        // Keep text, custom textures, Create fallback renderers and animations on their native paths.
        try (CarriageRenderLayer dynamic = CarriageRenderLayer.capture(buffers -> {
            PoseStack pose = new PoseStack();
            for (var cell : model.cells()) {
                if (cell.shell() != exterior) continue;
                BlockEntity entity = world.getBlockEntity(cell.pos());
                if (entity == null && cell.state().getRenderShape() != net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED) continue;
                pose.pushPose();
                pose.translate(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ());
                if (entity != null) renderEntity(entity, pose, buffers);
                else Minecraft.getInstance().getBlockRenderer().renderSingleBlock(cell.state(), pose, buffers,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        })) { dynamic.drawNative(transform); }
    }

    private static <T extends BlockEntity> void renderEntity(T entity, PoseStack pose, MultiBufferSource buffers) {
        BlockEntityRenderer<T> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
        if (renderer != null) renderer.render(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    public void render(Matrix4f transform, float opacity, float shellVisibility) {
        CarriageRenderLayer.composite(opacity, () -> {
            interior.drawNative(transform);
            drawEntities(false, transform);
        });
        if (shellVisibility > .001f) {
            Matrix4f lifted = new Matrix4f(transform).translate(0, (1 - shellVisibility) * 4, 0);
            CarriageRenderLayer.composite(opacity * shellVisibility, () -> {
                shell.drawNative(lifted);
                drawEntities(true, lifted);
            });
        }
    }

    public record Hit(CarriageAssets.Cell cell, net.minecraft.world.phys.shapes.VoxelShape outline, double distance) {}

    public Hit pick(Matrix4f transform, double mouseX, double mouseY, boolean showShell) {
        Matrix4f inverse = new Matrix4f(transform).invert();
        Vector3f a = inverse.transformPosition(new Vector3f((float) mouseX, (float) mouseY, 2000));
        Vector3f b = inverse.transformPosition(new Vector3f((float) mouseX, (float) mouseY, -2000));
        Vec3 start = new Vec3(a.x, a.y, a.z), end = new Vec3(b.x, b.y, b.z);
        Hit best = null;
        for (var cell : model.cells()) {
            if (cell.shell() && !showShell) continue;
            var shape = cell.state().getShape(world, cell.pos());
            if (shape.isEmpty()) shape = net.minecraft.world.phys.shapes.Shapes.block();
            // Pick individual boxes accurately, but highlight the whole block's union outline.
            var outline = shape.optimize().move(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ());
            for (AABB local : shape.toAabbs()) {
                AABB box = local.move(cell.pos());
                var hit = box.clip(start, end);
                if (hit.isEmpty()) continue;
                double distance = hit.get().distanceToSqr(start);
                if (best == null || distance < best.distance()) best = new Hit(cell, outline, distance);
            }
        }
        return best;
    }

    @Override public void close() {
        if (shell != null) shell.close();
        if (interior != null) interior.close();
        if (rails != null) rails.close();
        world.clear();
        CarriageRenderLayer.releaseTarget();
    }
}
