package willow.train.hexie.blocks.coupler;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Axis;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Exposed Scharfenberg coupler, compatible only with the same registered head. */
public final class ScharfenbergCouplerBlock extends AbstractCouplerBlock {
    public static final String MODEL = "hexie:scharfenberg";
    static { willow.train.hexie.vehicle.runtime.CouplingRules.directOnly(MODEL); }
    public static final MapCodec<ScharfenbergCouplerBlock> CODEC = simpleCodec(ScharfenbergCouplerBlock::new);
    private final CouplerGeometry geometry = CouplerGeometry.load("/data/hexie/scharfenberg_coupler_geometry.json");
    public ScharfenbergCouplerBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public String model() { return MODEL; }
    @Override public boolean allowsAdapters() { return false; }
    @Override public CouplerGeometry geometry() { return geometry; }
    @Override public Axis axis() { return new Axis(geometry.modelAxis().x() - .5, geometry.modelAxis().z() - .5); }
    @Override public Point start() { return complete(); }
    @Override public Point complete() {
        Vec3 point = geometry.contact();
        return new Point(point.x - .5, point.y - .5, point.z - .5);
    }
    @Override protected VoxelShape couplerShape(BlockState state) { return geometry.shape(facing(state), false); }
}
