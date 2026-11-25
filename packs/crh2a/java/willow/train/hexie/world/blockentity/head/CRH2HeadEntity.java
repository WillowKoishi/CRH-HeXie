package willow.train.hexie.world.blockentity.head;

import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.core.api.interaction.HexieTransformableBlockEntity;
import willow.train.hexie.world.blockentity.base.HexieBlockEntity;
import willow.train.hexie.world.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.world.blocks.emu.CRH2HeadBlock;
import willow.train.hexie.world.blocks.emu.HeadBogey;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public final class CRH2HeadEntity extends HexieBlockEntity implements MenuProvider,
        HexieTransformableBlockEntity, BlockEntityViews.CRH2HeadEntityView {
    public static final int MAX_LENGTH = 32;
    private String number = "";
    private boolean bogeyEnabled;
    private CompoundTag bogeyData = new CompoundTag();
    private CompoundTag physicalBogeyData = new CompoundTag();
    private boolean carriageView;
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
        if (level instanceof ServerLevel && level.getBlockEntity(worldPosition) == this)
            sendData();
    }
    public void updateBogey(boolean enabled, CompoundTag data) {
        var next = HeadBogey.normalizedData(data, getBlockState().getValue(CRH2HeadBlock.FACING));
        if (enabled == bogeyEnabled && next.equals(bogeyData)) return;
        bogeyEnabled = enabled;
        bogeyData = next;
        setChanged();
        if (level instanceof ServerLevel && level.getBlockEntity(worldPosition) == this) {
            sendData();
            if (getBlockState().getBlock() instanceof CRH2HeadBlock block) block.bogeyChanged(this);
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && !carriageView
                && level.getBlockEntity(worldPosition) == this)
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }
    public void reorientBogey(Direction before, Direction after) {
        var data = HeadBogey.normalizedData(bogeyData, before);
        data.putInt(BogeyFloorData.LONGITUDINAL_KEY, BogeyFloorData.longitudinal(data)
                * before.getAxisDirection().getStep() * after.getAxisDirection().getStep());
        updateBogey(bogeyEnabled, data);
    }
    @Override public void transformLongitudinal(UnaryOperator<Vec3> unapplyWithoutOffset,
            UnaryOperator<Vec3> applyWithoutOffsetUncentered) {
        if (!bogeyData.contains(BogeyFloorData.LONGITUDINAL_KEY, Tag.TAG_ANY_NUMERIC)
                || !BogeyFloorData.validLongitudinal(bogeyData.getInt(BogeyFloorData.LONGITUDINAL_KEY))) {
            bogeyData = bogeyData();
            return;
        }
        var data = bogeyData();
        int value = BogeyFloorData.longitudinal(data);
        var axis = getBlockState().getValue(CRH2HeadBlock.FACING).getAxis();
        var positive = axis == Direction.Axis.X
                ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        var source = unapplyWithoutOffset.apply(positive.add(.5, .5, .5)).subtract(.5, .5, .5);
        var sourcePositive = Math.abs(source.x) > Math.abs(source.z)
                ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        double sign = applyWithoutOffsetUncentered.apply(sourcePositive).dot(positive);
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
        return HexieBridges.menu().headNumberMenu().create(id, inventory, this);
    }
    public CRH2HeadEntity(BlockPos p,BlockState s) { super(BlockEntitiesInit.CRH2A_HEAD.get(),p,s); }
}
