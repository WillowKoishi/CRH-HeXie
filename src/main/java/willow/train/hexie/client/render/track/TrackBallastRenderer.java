package willow.train.hexie.client.render.track;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blocks.track.standard.BallastData;
import willow.train.hexie.blocks.track.standard.TrackBallast;
import willow.train.hexie.init.track.TrackMaterialInit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import static willow.train.hexie.client.render.track.BallastMesh.*;

public final class TrackBallastRenderer {
    private static final Map<BezierConnection, List<Quad>> CURVES = new WeakHashMap<>();
    private static final Map<TrackShape, List<Quad>> STRAIGHTS = new EnumMap<>(TrackShape.class);

    public static boolean hasBallast(TrackBlockEntity be) {
        return TrackBallast.enabled(be.getBlockState()) || be.getConnections().values().stream()
                .anyMatch(c -> c.getMaterial() == TrackMaterialInit.STANDARD);
    }

    public static void render(TrackBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        if (be.getLevel() == null || !(be instanceof BallastData data)) return;
        VertexConsumer buffer = buffers.getBuffer(RenderType.cutoutMipped());
        if (TrackBallast.enabled(be.getBlockState())) {
            TrackShape shape = be.getBlockState().getValue(TrackBlock.SHAPE);
            draw(be, pose, buffer, STRAIGHTS.computeIfAbsent(shape, TrackBallastRenderer::straight), data.hexie$getBallast(null));
        }
        for (BezierConnection curve : be.getConnections().values()) {
            if (!curve.isPrimary() || curve.getMaterial() != TrackMaterialInit.STANDARD) continue;
            draw(be, pose, buffer, CURVES.computeIfAbsent(curve, TrackBallastRenderer::curve), data.hexie$getBallast(curve.getKey()));
        }
    }

    private static List<Quad> straight(TrackShape shape) {
        if (shape == TrackShape.NONE) return List.of();
        List<Quad> mesh = new ArrayList<>();
        for (Vec3 axis : shape.getAxes()) {
            Vec3 side = new Vec3(axis.z, 0, -axis.x).normalize();
            Vec3 center = new Vec3(.5, axis.y == 0 ? 0 : .5, .5);
            // A diagonal spans sqrt(2) blocks; a slope spans one block horizontally.
            List<Frame> frames = new ArrayList<>();
            int steps = Math.max(1, (int) Math.ceil(axis.length() * 4));
            for (int i = 0; i <= steps; i++) frames.add(new Frame(point(center.add(axis.scale(i / (double) steps - .5))),
                    point(side), new Point(0, 1, 0)));
            mesh.addAll(BallastMesh.sweep(frames));
        }
        return List.copyOf(mesh);
    }

    private static List<Quad> curve(BezierConnection curve) {
        int count = Math.max(4, (int) Math.ceil(curve.getLength() * 8));
        List<Frame> frames = new ArrayList<>();
        Vec3 origin = Vec3.atLowerCornerOf(curve.bePositions.getFirst());
        for (int i = 0; i <= count; i++) {
            double t = i / (double) count;
            Vec3 center = curve.getPosition(t).subtract(origin);
            Vec3 tangent = curve.getPosition(Math.min(1, t + .0001))
                    .subtract(curve.getPosition(Math.max(0, t - .0001))).normalize();
            Vec3 side = new Vec3(tangent.z, 0, -tangent.x).normalize();
            frames.add(new Frame(point(center), point(side), new Point(0, 1, 0)));
        }
        return BallastMesh.sweep(frames);
    }

    private static Point point(Vec3 v) { return new Point(v.x, v.y, v.z); }

    private static void draw(TrackBlockEntity be, PoseStack pose, VertexConsumer buffer, List<Quad> mesh, BlockState texture) {
        Minecraft mc = Minecraft.getInstance();
        var model = mc.getBlockRenderer().getBlockModel(texture);
        var top = model.getQuads(texture, Direction.UP, RandomSource.create(42));
        TextureAtlasSprite sprite = top.isEmpty() ? model.getParticleIcon() : top.getFirst().getSprite();
        int tint = top.isEmpty() || !top.getFirst().isTinted() ? -1 : top.getFirst().getTintIndex();
        int color = tint < 0 ? 0xffffff : mc.getBlockColors().getColor(texture, be.getLevel(), be.getBlockPos(), tint);
        for (Quad quad : mesh) {
            Point n = quad.normal();
            float shade = (float) (.6 + .4 * Math.max(0, n.y()));
            Point p = quad.a().position();
            int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().offset(
                    BlockPos.containing(p.x(), p.y() + .5, p.z())));
            vertex(buffer, pose, sprite, quad.a(), n, color, shade, light);
            vertex(buffer, pose, sprite, quad.b(), n, color, shade, light);
            vertex(buffer, pose, sprite, quad.c(), n, color, shade, light);
            vertex(buffer, pose, sprite, quad.d(), n, color, shade, light);
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack pose, TextureAtlasSprite sprite, Vertex v,
            Point n, int color, float shade, int light) {
        Point p = v.position();
        buffer.addVertex(pose.last(), (float) p.x(), (float) p.y(), (float) p.z())
                .setColor(((color >> 16) & 255) / 255f * shade, ((color >> 8) & 255) / 255f * shade,
                        (color & 255) / 255f * shade, 1)
                .setUv(sprite.getU((float) (.0001 + v.u() * .9998)), sprite.getV((float) (.0001 + v.v() * .9998)))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose.last(), (float) n.x(), (float) n.y(), (float) n.z());
    }
}
