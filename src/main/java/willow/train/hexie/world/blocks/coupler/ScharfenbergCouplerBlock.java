package willow.train.hexie.world.blocks.coupler;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.world.collision.CouplerCollision;
import willow.train.hexie.world.collision.CouplerGeometry;

/** Exposed Scharfenberg coupler, compatible only with the same registered head. */
public final class ScharfenbergCouplerBlock extends AbstractCouplerBlock implements CouplerBlockBridge.ScharfenbergCouplerBlockView {
    public static final String MODEL = "hexie:scharfenberg";
    static { HexieBridges.coupling().couplingRules().directOnly(MODEL); }
    public static final MapCodec<ScharfenbergCouplerBlock> CODEC = simpleCodec(ScharfenbergCouplerBlock::new);
    private final CouplerGeometry geometry = CouplerGeometry.load("/data/hexie/scharfenberg_coupler_geometry.json");
    public ScharfenbergCouplerBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public String model() { return MODEL; }
    @Override public boolean allowsAdapters() { return false; }
    @Override public CouplerGeometry geometry() { return geometry; }
    @Override public CouplingBridge.CouplingEndpointAxisView axis() { return HexieBridges.coupling().createCouplingEndpointAxis(geometry.modelAxis().x() - .5, geometry.modelAxis().z() - .5); }
    @Override public Point3 start() { return complete(); }
    @Override public Point3 complete() {
        Vec3 point = geometry.contact();
        return new Point3(point.x - .5, point.y - .5, point.z - .5);
    }
    @Override public VoxelShape couplerShape(BlockState state) {
        return CouplerCollision.ForScharfenbergCouplerBlock.couplerShape(this, state);
    }
}
