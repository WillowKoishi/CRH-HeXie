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
    private int markingSlot;
    public int markingSlot() { return markingSlot; }
    public void selectMarkingSlot(int slot) {
        int selected=Math.clamp(slot,0,1);
        if(markingSlot==selected)return;
        markingSlot=selected; setChanged(); sendData();
    }
    private final int[] harmony={0,0};
    public int harmony(int slot) { return harmony[slot]; }
    public void attachHarmony(int slot) { harmony[slot]=1; setChanged(); sendData(); }
    public void toggleHarmony(int slot) { if(harmony[slot]!=0) { harmony[slot]=3-harmony[slot]; setChanged(); sendData(); } }
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
        tag.putInt("CarriageMarkingSlot", markingSlot);
        tag.putIntArray("HarmonyLogos",harmony);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        for (int i=0;i<fields.length;i++) fields[i]=GroundStationSignEntity.clean(tag.getString("CarriageMarking"+i));
        descriptionLeft=tag.getBoolean("DescriptionLeft");
        markingSlot=Math.clamp(tag.getInt("CarriageMarkingSlot"),0,1);
        var logos=tag.getIntArray("HarmonyLogos");
        for(int i=0;i<2;i++) harmony[i]=i<logos.length && logos[i]>=1 && logos[i]<=2 ? logos[i] : 0;
    }
    @Override public Component getDisplayName() { return Component.translatable("gui.hexie.carriage_marking.title"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CarriageMarkingMenu(id, inventory, this);
    }
}
