package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.WorldBlockBridge.MovingWipers;
import willow.train.hexie.world.blockentity.base.HexieBlockEntity;
import willow.train.hexie.world.blocks.base.CarriageViewBlockEntity;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public final class SS7DHeadEntity extends HexieBlockEntity implements CarriageViewBlockEntity, BlockEntityViews.SS7DHeadEntityView {
    public static final String NUMBER_TAG = "SS7DNumber";
    public static final String BUREAU_TAG = "SS7DBureau";
    public static final String DEPOT_TAG = "SS7DDepot";
    public static final String DEPOT_VISIBLE_TAG = "SS7DDepotVisible";
    public static final String SECOND_END_TAG = "SS7DSecondEnd";
    public static final int MAX_UNIT_LENGTH = 12;
    public static final Vec3 RAIN_PROBE = new Vec3(.5, 3.25, -.73);
    private SS7DWipers wipers = new SS7DWipers();
    private String digits = "0001";
    private String bureau = "";
    private String depot = "";
    private boolean depotVisible = true;
    private boolean secondEnd;
    private boolean carriageView;

    public SS7DHeadEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesInit.SS7D_HEAD.get(), pos, state);
    }

    @Override public void markCarriageView() { carriageView = true; }
    public boolean isCarriageView() { return carriageView; }
    public String digits() { return digits; }
    public String bureau() { return bureau; }
    public String depot() { return depot; }
    public boolean depotVisible() { return depotVisible; }
    public boolean secondEnd() { return secondEnd; }
    public String endLabel() { return secondEnd ? "II" : "I"; }
    public String depotLabel() {
        return (bureau.isBlank() ? "X" : bureau) + "局" + (depot.isBlank() ? "X" : depot) + "段";
    }
    public boolean hasSidePlates() { return hasSidePlates(digits); }
    public static boolean hasSidePlates(String value) {
        return !validDigits(value) || Integer.parseInt(value) <= 2;
    }
    public static boolean validDigits(String value) {
        return value != null && value.length() == 4 && value.chars().allMatch(c -> c >= '0' && c <= '9');
    }
    public static boolean validUnit(String value) {
        return value != null && value.length() <= MAX_UNIT_LENGTH
                && value.codePoints().noneMatch(c -> Character.isISOControl(c)
                        || Character.getType(c) == Character.FORMAT || c == 0xA7);
    }
    public void updateDigits(String value) {
        if (validDigits(value)) digits = value;
    }
    public void updateSecondEnd(boolean value) { secondEnd = value; }
    public void updateDepot(String bureau, String depot, boolean visible) {
        if (!validUnit(bureau) || !validUnit(depot)) return;
        this.bureau = bureau.strip();
        this.depot = depot.strip();
        this.depotVisible = visible;
    }
    public float wiperAngle(int side, float partialTick) { return wipers.angle(side, partialTick); }
    public static Vec3 rainProbe(BlockState state) {
        return SS7DHeadGeometry.rotate(RAIN_PROBE, state.getValue(SS7DHeadBlock.FACING));
    }

    public static MovingWipers createMovingWipers() {
        var moving = new SS7DWipers();
        return (world, windshield, entity) -> {
            moving.tick(world, windshield);
            if (entity instanceof SS7DHeadEntity head) head.wipers = moving;
        };
    }

    @Override public void tick() {
        // Schematic preview levels implement ServerLevelAccessor even on the client.
        if (level != null && level.isClientSide && !carriageView && !(level instanceof ServerLevelAccessor)) {
            var probe = rainProbe(getBlockState());
            wipers.tick(level, BlockPos.containing(probe.add(Vec3.atLowerCornerOf(worldPosition))));
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        String value = tag.getString(NUMBER_TAG);
        digits = validDigits(value) ? value : "0001";
        String loadedBureau = tag.getString(BUREAU_TAG);
        String loadedDepot = tag.getString(DEPOT_TAG);
        bureau = validUnit(loadedBureau) ? loadedBureau.strip() : "";
        depot = validUnit(loadedDepot) ? loadedDepot.strip() : "";
        depotVisible = !tag.contains(DEPOT_VISIBLE_TAG, Tag.TAG_BYTE) || tag.getBoolean(DEPOT_VISIBLE_TAG);
        // Existing heads have no end marker field and therefore default to end I.
        secondEnd = tag.getBoolean(SECOND_END_TAG);
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString(NUMBER_TAG, digits);
        writeDepot(tag);
    }
    @Override public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putString(NUMBER_TAG, digits);
        writeDepot(tag);
    }
    private void writeDepot(CompoundTag tag) {
        tag.putString(BUREAU_TAG, bureau);
        tag.putString(DEPOT_TAG, depot);
        tag.putBoolean(DEPOT_VISIBLE_TAG, depotVisible);
        tag.putBoolean(SECOND_END_TAG, secondEnd);
    }
}
