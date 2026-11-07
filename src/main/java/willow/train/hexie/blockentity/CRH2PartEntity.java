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
import willow.train.hexie.blocks.parts.PanelText;

/** The panel root owns its lower-body markings; proxy cells resolve all editing to that root. */
public final class CRH2PartEntity extends SmartBlockEntity implements MenuProvider, willow.train.hexie.blocks.parts.VehicleMarkings,
        willow.train.hexie.api.display.PassengerDisplayHost {
    private willow.train.hexie.api.display.PassengerDisplaySource passengerSource =
            () -> willow.train.hexie.api.display.PassengerDisplayState.EMPTY;
    @Override public void passengerDisplaySource(willow.train.hexie.api.display.PassengerDisplaySource source) { passengerSource = source; }
    @Override public willow.train.hexie.api.display.PassengerDisplayState passengerDisplay() { return passengerSource.read(); }
    private String carriageNumber = "";
    @Override public String carriageNumber() { return carriageNumber; }
    @Override public void setCarriageNumber(String number) {
        if (!willow.train.hexie.api.display.PassengerCarriageNumber.valid(number) || carriageNumber.equals(number)) return;
        carriageNumber = number;
        setChanged();
        sendData();
    }
    private final String[] fields = {"", "", ""};
    private boolean descriptionLeft;
    private int markingSlot;
    public int markingSlot() { return markingSlot; }
    public void selectMarkingSlot(int slot) {
        int selected=Math.clamp(slot,0,1);
        if(markingSlot==selected)return;
        markingSlot=selected; setChanged(); sendData();
    }
    private final PanelText[] labels = {PanelText.EMPTY, PanelText.EMPTY};
    public PanelText panelText(int slot) { return labels[slot]; }
    public void setPanelText(int slot, PanelText text) {
        labels[slot] = text;
        setChanged();
        sendData();
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
    public boolean hasMarkings() {
        return java.util.Arrays.stream(fields).anyMatch(value -> !value.isBlank())
                || java.util.Arrays.stream(harmony).anyMatch(value -> value != 0)
                || java.util.Arrays.stream(labels).anyMatch(value -> !value.text().isEmpty());
    }
    /** Remove user-applied content while keeping the panel and its passenger data source. */
    public void clearMarkings() {
        java.util.Arrays.fill(fields, "");
        java.util.Arrays.fill(harmony, 0);
        java.util.Arrays.fill(labels, PanelText.EMPTY);
        descriptionLeft = false;
        markingSlot = 0;
        setChanged();
        sendData();
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeAppearance(tag);
    }
    @Override public void writeSafe(CompoundTag tag,HolderLookup.Provider registries) {
        super.writeSafe(tag,registries);writeAppearance(tag);
    }
    private void writeAppearance(CompoundTag tag) {
        willow.train.hexie.api.display.PassengerCarriageNumber.write(tag, carriageNumber);
        for (int i=0;i<fields.length;i++) tag.putString("CarriageMarking"+i, fields[i]);
        tag.putBoolean("DescriptionLeft", descriptionLeft);
        tag.putInt("CarriageMarkingSlot", markingSlot);
        tag.putIntArray("HarmonyLogos",harmony);
        for (int i = 0; i < labels.length; i++)
            if (!labels[i].text().isEmpty()) tag.put("PanelText" + i, labels[i].save());
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        carriageNumber = willow.train.hexie.api.display.PassengerCarriageNumber.read(tag);
        for (int i=0;i<fields.length;i++) fields[i]=GroundStationSignEntity.clean(tag.getString("CarriageMarking"+i));
        descriptionLeft=tag.getBoolean("DescriptionLeft");
        markingSlot=Math.clamp(tag.getInt("CarriageMarkingSlot"),0,1);
        for (int i = 0; i < labels.length; i++)
            labels[i] = tag.contains("PanelText" + i) ? PanelText.load(tag.getCompound("PanelText" + i)) : PanelText.EMPTY;
        var logos=tag.getIntArray("HarmonyLogos");
        for(int i=0;i<2;i++) harmony[i]=i<logos.length && logos[i]>=1 && logos[i]<=2 ? logos[i] : 0;
    }
    @Override public Component getDisplayName() { return Component.translatable("gui.hexie.carriage_marking.title"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CarriageMarkingMenu(id, inventory, this);
    }
}
