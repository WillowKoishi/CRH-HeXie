package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import willow.train.hexie.api.display.PassengerDisplayHost;
import willow.train.hexie.api.display.PassengerDisplayState;
import willow.train.hexie.blockentity.PassengerPanelEntity;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.signs.PanelDisplayMount;
import willow.train.hexie.client.render.display.PassengerPanelModels;
import willow.train.hexie.client.sign.NativePanelText;

/** Timetable lettering shared by ordinary panel entities and CRH2 carriage-panel renderers. */
public final class PassengerPanelRenderer implements BlockEntityRenderer<PassengerPanelEntity> {
    private static final float WIDTH = 9.34f / 16, HEIGHT = 2.59f / 16;
    private static final float NUMBER_WIDTH = 1.94f / 16, NUMBER_HEIGHT = 1.89f / 16;
    public static final int TRAIN_COLOR = 0xB5C734, ROUTE_COLOR = 0xFF3020;

    public PassengerPanelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(PassengerPanelEntity entity, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        draw(entity, pose, buffers, partialTick);
    }

    /** The supplied pose is block-local; layout already includes facing, handedness and shell slope. */
    public static void draw(BlockEntity entity, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        if (!(entity instanceof PassengerDisplayHost host) || entity.getLevel() == null) return;
        var state = entity.getBlockState();
        if (!state.hasProperty(PanelDisplayMount.PROPERTY)
                || state.getValue(PanelDisplayMount.PROPERTY) == PanelDisplayMount.NONE) return;
        var root = PartStructures.resolve(entity.getLevel(), entity.getBlockPos(), state);
        if (root == null || !root.pos().equals(entity.getBlockPos())) return;
        var layout = PassengerPanelModels.layout(state);
        if (layout == null) return;
        // A moving contraption can supply a virtual level with a stationary clock.
        var clock = Minecraft.getInstance().level;
        double seconds = ((clock == null ? entity.getLevel() : clock).getGameTime() + partialTick) / 20d;
        drawMounted(layout, host.passengerDisplay(), host.carriageNumber(), pose, buffers, seconds);
    }

    /** Sign and number have independent recessed planes, including number-only installations. */
    public static void drawMounted(PassengerPanelModels.Layout layout, PassengerDisplayState data, String number,
                                    PoseStack pose, MultiBufferSource buffers, double seconds) {
        if (layout.signOrigin() != null) {
            pose.pushPose();
            try {
                pose.mulPose(transform(layout, layout.signOrigin()));
                drawContents(data, pose, buffers, seconds);
            } finally { pose.popPose(); }
        }
        if (layout.numberOrigin() != null) {
            pose.pushPose();
            try {
                pose.mulPose(transform(layout, layout.numberOrigin()));
                drawNumber(number, pose, buffers);
            } finally { pose.popPose(); }
        }
    }

    private static Matrix4f transform(PassengerPanelModels.Layout layout, net.minecraft.world.phys.Vec3 center) {
        var right = layout.right();
        var up = layout.up();
        var normal = layout.normal();
        var origin = center.add(normal.scale(.001));
        return new Matrix4f(
                (float) right.x, (float) right.y, (float) right.z, 0,
                (float) up.x, (float) up.y, (float) up.z, 0,
                (float) normal.x, (float) normal.y, (float) normal.z, 0,
                (float) origin.x, (float) origin.y, (float) origin.z, 1);
    }

    public static void drawNumber(String number, PoseStack pose, MultiBufferSource buffers) {
        if (number == null || number.isEmpty() || number.length() > 2
                || number.chars().anyMatch(c -> c < '0' || c > '9')) return;
        NativePanelText.drawFitted(number, pose, buffers, 0, NUMBER_WIDTH * .84f, NUMBER_HEIGHT * .86f, ROUTE_COLOR);
    }

    /** Both rows share the real screen plane; missing fields leave their assigned row blank. */
    public static void drawContents(PassengerDisplayState data, PoseStack pose,
                                    MultiBufferSource buffers, double seconds) {
        if (data == null) return;
        NativePanelText.drawLine(data.trainNumber(), pose, buffers, HEIGHT * (.5f - .34f),
                WIDTH * .94f, HEIGHT * .33f, TRAIN_COLOR, seconds);
        if (!data.origin().isBlank() && !data.destination().isBlank()) {
            int limit = (NativePanelText.MAX_CODE_POINTS - 1) / 2;
            String route = limited(data.origin(), limit) + "—" + limited(data.destination(), limit);
            NativePanelText.drawLine(route, pose, buffers, HEIGHT * (.5f - .75f),
                    WIDTH * .94f, HEIGHT * .37f, ROUTE_COLOR, seconds);
        }
    }

    private static String limited(String text, int codePoints) {
        return text.codePointCount(0, text.length()) <= codePoints ? text
                : text.substring(0, text.offsetByCodePoints(0, codePoints - 1)) + "…";
    }

    @Override public boolean shouldRenderOffScreen(PassengerPanelEntity entity) { return true; }

    @Override public AABB getRenderBoundingBox(PassengerPanelEntity entity) {
        AABB bounds = new AABB(entity.getBlockPos());
        var level = entity.getLevel();
        if (level != null) {
            var root = PartStructures.resolve(level, entity.getBlockPos(), entity.getBlockState());
            if (root != null)
                for (var pos : root.structure().partCells(level, root.pos(), root.state()).keySet())
                    bounds = bounds.minmax(new AABB(pos));
        }
        // The shell and recessed lettering belong to the entire panel, not just its root cell.
        return bounds.inflate(1 / 16d);
    }
}
