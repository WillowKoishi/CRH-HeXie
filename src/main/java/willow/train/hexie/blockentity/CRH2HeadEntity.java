package willow.train.hexie.blockentity;
import net.minecraft.core.BlockPos;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import willow.train.hexie.menu.HeadNumberMenu;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;
import willow.train.hexie.blocks.emu.head.HeadBogey;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
public final class CRH2HeadEntity extends SmartBlockEntity implements MenuProvider,
        com.simibubi.create.api.contraption.transformable.TransformableBlockEntity {
    public static final int MAX_LENGTH = 32;
    private String number = "";
    private boolean bogeyEnabled;
    private CompoundTag bogeyData = new CompoundTag();
    private CompoundTag physicalBogeyData = new CompoundTag();
    private boolean carriageView;
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    public String number() { return number; }
    public boolean bogeyEnabled() { return bogeyEnabled; }
    /** The inner Create BogeyData compound; callers cannot mutate the stored value. */
    public CompoundTag bogeyData() { return HeadBogey.normalizedData(bogeyData, getBlockState().getValue(CRH2HeadBlock.FACING)); }
    public void markCarriageView() { carriageView = true; }
    public boolean isCarriageView() { return carriageView; }
    /** Optional backend settings travel with the one head item, including schematic printing. */
    public CompoundTag physicalBogeyData() { return physicalBogeyData.copy(); }
    public void updatePhysicalBogeyData(CompoundTag data) {
        if (physicalBogeyData.equals(data)) return;
        physicalBogeyData = data.copy();
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel && level.getBlockEntity(worldPosition) == this)
            sendData();
    }
    public void updateBogey(boolean enabled, CompoundTag data) {
        var next = HeadBogey.normalizedData(data, getBlockState().getValue(CRH2HeadBlock.FACING));
        if (enabled == bogeyEnabled && next.equals(bogeyData)) return;
        bogeyEnabled = enabled;
        bogeyData = next;
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel && level.getBlockEntity(worldPosition) == this) {
            sendData();
            if (getBlockState().getBlock() instanceof CRH2HeadBlock block) block.bogeyChanged(this);
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel && !carriageView
                && level.getBlockEntity(worldPosition) == this)
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }
    public void reorientBogey(net.minecraft.core.Direction before, net.minecraft.core.Direction after) {
        var data = HeadBogey.normalizedData(bogeyData, before);
        data.putInt(BogeyFloorData.LONGITUDINAL_KEY, BogeyFloorData.longitudinal(data)
                * before.getAxisDirection().getStep() * after.getAxisDirection().getStep());
        updateBogey(bogeyEnabled, data);
    }
    @Override public void transform(net.minecraft.world.level.block.entity.BlockEntity entity,
            com.simibubi.create.content.contraptions.StructureTransform transform) {
        if (!bogeyData.contains(BogeyFloorData.LONGITUDINAL_KEY, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                || !BogeyFloorData.validLongitudinal(bogeyData.getInt(BogeyFloorData.LONGITUDINAL_KEY))) {
            bogeyData = bogeyData();
            return;
        }
        var data = bogeyData();
        int value = BogeyFloorData.longitudinal(data);
        var axis = getBlockState().getValue(CRH2HeadBlock.FACING).getAxis();
        var positive = axis == net.minecraft.core.Direction.Axis.X
                ? new net.minecraft.world.phys.Vec3(1, 0, 0) : new net.minecraft.world.phys.Vec3(0, 0, 1);
        var source = transform.unapplyWithoutOffset(positive.add(.5, .5, .5)).subtract(.5, .5, .5);
        var sourcePositive = Math.abs(source.x) > Math.abs(source.z)
                ? new net.minecraft.world.phys.Vec3(1, 0, 0) : new net.minecraft.world.phys.Vec3(0, 0, 1);
        double sign = transform.applyWithoutOffsetUncentered(sourcePositive).dot(positive);
        if (Math.abs(sign) > .5) {
            data.putInt(BogeyFloorData.LONGITUDINAL_KEY, sign < 0 ? -value : value);
            bogeyData = data;
            setChanged();
        }
    }
    public static String clean(String text) {
        return text.codePoints().filter(c -> !Character.isISOControl(c)
                && Character.getType(c) != Character.FORMAT && c != 0xA7)
                .limit(MAX_LENGTH).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
    public void updateNumber(String text) { number = clean(text); setChanged(); sendData(); }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket); tag.putString("HeadNumber", number); writeBogey(tag);
    }
    @Override public void writeSafe(CompoundTag tag,HolderLookup.Provider registries) {
        super.writeSafe(tag,registries);tag.putString("HeadNumber",number);writeBogey(tag);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket); number = clean(tag.getString("HeadNumber"));
        bogeyEnabled = tag.getBoolean(HeadBogey.ENABLED);
        bogeyData = tag.getCompound(HeadBogey.DATA).copy();
        physicalBogeyData = tag.getCompound("PhysicalBogeyData").copy();
    }
    private void writeBogey(CompoundTag tag) {
        tag.putBoolean(HeadBogey.ENABLED, bogeyEnabled);
        tag.put(HeadBogey.DATA, bogeyData());
        if (!physicalBogeyData.isEmpty()) tag.put("PhysicalBogeyData", physicalBogeyData());
    }
    @Override public Component getDisplayName() { return Component.translatable("gui.hexie.head_number.title"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HeadNumberMenu(id, inventory, this);
    }
    public CRH2HeadEntity(BlockPos p,BlockState s) { super(BlockEntitiesInit.CRH2A_HEAD.get(),p,s); }
}
