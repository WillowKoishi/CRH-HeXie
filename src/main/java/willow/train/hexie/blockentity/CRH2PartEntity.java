package willow.train.hexie.blockentity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.menu.CarriageMarkingMenu;
import java.util.List;

/** The panel root owns its lower-body markings; proxy cells resolve all editing to that root. */
public final class CRH2PartEntity extends SmartBlockEntity implements MenuProvider {
    private final String[] fields = {"", "", ""};
    private boolean descriptionLeft;
    public CRH2PartEntity(BlockPos pos, BlockState state) { super(BlockEntitiesInit.CRH2A_PART.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    public String[] fields() { return fields.clone(); }
    public boolean descriptionLeft() { return descriptionLeft; }
    public void update(String[] values, boolean left) {
        if (values.length != fields.length) return;
        for (int i=0; i<fields.length; i++) fields[i]=GroundStationSignEntity.clean(values[i]);
        descriptionLeft=left; setChanged(); sendData();
    }
    public void toggleDescription() { descriptionLeft=!descriptionLeft; setChanged(); sendData(); }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        for (int i=0;i<fields.length;i++) tag.putString("CarriageMarking"+i, fields[i]);
        tag.putBoolean("DescriptionLeft", descriptionLeft);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        for (int i=0;i<fields.length;i++) fields[i]=GroundStationSignEntity.clean(tag.getString("CarriageMarking"+i));
        descriptionLeft=tag.getBoolean("DescriptionLeft");
    }
    @Override public Component getDisplayName() { return Component.translatable("gui.hexie.carriage_marking.title"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CarriageMarkingMenu(id, inventory, this);
    }
}
