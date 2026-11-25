package willow.train.hexie.world.blockentity.panel;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.core.api.display.PassengerCarriageNumber;
import willow.train.hexie.core.api.display.PassengerDisplayHost;
import willow.train.hexie.core.api.display.PassengerDisplaySource;
import willow.train.hexie.core.api.display.PassengerDisplayState;
import willow.train.hexie.world.blockentity.base.HexieBlockEntity;
import willow.train.hexie.world.blockentity.sign.GroundStationSignEntity;
import willow.train.hexie.world.blocks.parts.PanelText;
import willow.train.hexie.world.blocks.parts.VehicleMarkings;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

/** The panel root owns its lower-body markings; proxy cells resolve all editing to that root. */
public final class CRH2PartEntity extends HexieBlockEntity implements MenuProvider, VehicleMarkings,
        PassengerDisplayHost, BlockEntityViews.CRH2PartEntityView {
    private PassengerDisplaySource passengerSource =
            () -> PassengerDisplayState.EMPTY;
    @Override public void passengerDisplaySource(PassengerDisplaySource source) { passengerSource = source; }
    @Override public PassengerDisplayState passengerDisplay() { return passengerSource.read(); }
    private String carriageNumber = "";
    @Override public String carriageNumber() { return carriageNumber; }
    @Override public void setCarriageNumber(String number) {
        if (!PassengerCarriageNumber.valid(number) || carriageNumber.equals(number)) return;
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
    private final PartBlockBridge.PanelTextView[] labels = {PanelText.EMPTY, PanelText.EMPTY};
    @Override public PartBlockBridge.PanelTextView panelText(int slot) { return labels[slot]; }
    @Override public void setPanelText(int slot, PartBlockBridge.PanelTextView text) {
        labels[slot] = text;
        setChanged();
        sendData();
    }
    private final int[] harmony={0,0};
    public int harmony(int slot) { return harmony[slot]; }
    @Override public void setHarmony(int slot, int direction) {
        if (slot < 0 || slot >= harmony.length || direction < 0 || direction > 2 || harmony[slot] == direction) return;
        harmony[slot] = direction;
        setChanged();
        sendData();
    }
    public void attachHarmony(int slot) { setHarmony(slot, 1); }
    public void toggleHarmony(int slot) { if(harmony[slot]!=0) setHarmony(slot, 3-harmony[slot]); }
    public CRH2PartEntity(BlockPos pos, BlockState state) { super(BlockEntitiesInit.CRH2A_PART.get(), pos, state); }
    public String[] fields() { return fields.clone(); }
    public boolean descriptionLeft() { return descriptionLeft; }
    public void update(String[] values, boolean left) {
        if (values.length != fields.length) return;
        for (int i=0; i<fields.length; i++) fields[i]=GroundStationSignEntity.clean(values[i]);
        descriptionLeft=left; setChanged(); sendData();
    }
    public void toggleDescription() { descriptionLeft=!descriptionLeft; setChanged(); sendData(); }
    public boolean hasMarkings() {
        return Arrays.stream(fields).anyMatch(value -> !value.isBlank())
                || Arrays.stream(harmony).anyMatch(value -> value != 0)
                || Arrays.stream(labels).anyMatch(value -> !value.text().isEmpty());
    }
    /** Remove user-applied content while keeping the panel and its passenger data source. */
    public void clearMarkings() {
        Arrays.fill(fields, "");
        Arrays.fill(harmony, 0);
        Arrays.fill(labels, PanelText.EMPTY);
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
        PassengerCarriageNumber.write(tag, carriageNumber);
        for (int i=0;i<fields.length;i++) tag.putString("CarriageMarking"+i, fields[i]);
        tag.putBoolean("DescriptionLeft", descriptionLeft);
        tag.putInt("CarriageMarkingSlot", markingSlot);
        tag.putIntArray("HarmonyLogos",harmony);
        for (int i = 0; i < labels.length; i++)
            if (!labels[i].text().isEmpty()) tag.put("PanelText" + i, labels[i].save());
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        carriageNumber = PassengerCarriageNumber.read(tag);
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
        return HexieBridges.menu().carriageMarkingMenu().create(id, inventory, this);
    }
}
