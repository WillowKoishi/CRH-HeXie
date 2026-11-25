package willow.train.hexie.world.blocks.emu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.bridge.world.PartBlockBridge;

/** Visible emblem bounds: left texture 500 x 123, right texture 500 x 131. */
public final class HarmonyMarking {
    public static final double EMBLEM_HEIGHT = 6.5 / 16d;
    private HarmonyMarking() {}
    public static int slot(PartBlockBridge.VehicleMarkingSurfaceView block, BlockState state, BlockPos root, Vec3 hit) {
        return block.markingSlot(state,root,hit);
    }
    public record Layout(double left, double top, double width, double height) implements CarriageBlockBridge.HarmonyMarkingLayoutView {}
    public static HarmonyMarking.Layout layout(double cellLeft, double panelTop, boolean emblemRight) {
        double scale=EMBLEM_HEIGHT/(emblemRight ? 77 : 73);
        double left=emblemRight ? cellLeft+1-500*scale : cellLeft;
        // Full-image UVs must use each texture's actual height to preserve square pixels.
        double textureHeight=emblemRight ? 131 : 123;
        return new HarmonyMarking.Layout(left,panelTop+(emblemRight ? 22 : 20)*scale,500*scale,textureHeight*scale);
    }
    // Authored exterior: z=3/16 at y=1, z=2.6/16 at y=2.
    public static double surfaceDepth(double y) { return 3/16d-Math.max(0,y-1)*.025; }
}
