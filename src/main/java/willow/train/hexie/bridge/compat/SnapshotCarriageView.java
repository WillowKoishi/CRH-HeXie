package willow.train.hexie.bridge.compat;

import com.simibubi.create.content.trains.entity.Carriage;
import net.minecraft.core.BlockPos;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import willow.train.hexie.core.api.cab.CabDisplayState;

public interface SnapshotCarriageView {
    CabDisplayState cabDisplayState(BlockPos position);
    CreateCarriageBridge.SnapshotBogeyAnimationView bogeyAnimation();
    CreateCarriageBridge.SnapshotCouplerAnimationView couplerAnimation();
    Carriage getCarriage();
    void syncCarriage();
    void setBlock(BlockPos localPos, StructureTemplate.StructureBlockInfo info);
    CreateCarriageBridge.AssembledVehicleMotionView motion();
    Quaternionf bodyRotation(float partialTicks);
    Vec3 renderPosition(float partialTicks);
    boolean startControlling(BlockPos controls, Player player);
    void releaseControls(Player player, BlockPos controls);
    default CarriageContraptionEntity asEntity() { return (CarriageContraptionEntity) this; }
}
