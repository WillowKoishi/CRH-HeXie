package willow.train.hexie.compat.simurail;

import com.crystaelix.simurail.api.bogey.BogeySubtype;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyOptions;
import com.simibubi.create.compat.computercraft.AbstractComputerBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.blocks.base.CarriageViewBlockEntity;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;
import willow.train.hexie.blocks.emu.head.HeadBogey;
import willow.train.hexie.compat.HeXieSimurailCompat;

/** Native Simurail controller identity and physical attachment use distinct, persistent frames. */
public final class PhysicalHeadBogeyBlockEntity extends PhysicsBogeyBlockEntity implements CarriageViewBlockEntity {
    private static final String HEAD_DATA = "HexieHeadBogeyData";
    private static final String HEAD_ENABLED = "HexieHeadBogeyEnabled";
    private static final String MANUAL_ENABLED = "HexieManualEnabled";
    private CompoundTag headData;
    private CompoundTag copiedSettings = new CompoundTag();
    private boolean headEnabled;
    private boolean manualEnabled = true;
    private boolean syncingHead;
    private boolean headSettingsRead;
    private boolean carriageView;

    public PhysicalHeadBogeyBlockEntity(BlockPos pos, BlockState state) {
        super(SimurailHeadRegistration.PHYSICAL_HEAD_BOGEY_ENTITY.get(), pos, state);
        headData = HeadBogey.defaultData(getFacing());
        options.type = new BogeySubtype(HeXieSimurailCompat.DT206.type());
        options.enabled = false;
        updateAttachment(false);
    }
    public BlockPos headRoot() { return getBlockPos().relative(getFacing()); }
    public boolean headBogeyEnabled() { return headEnabled; }
    public Vector3dc attachmentCenter() { return new Vector3d(localCenter); }
    public Vec3 attachmentOffset() {
        return new Vec3(localCenter.x() - getBlockPos().getX() - .5, localCenter.y() - getBlockPos().getY() - .5,
                localCenter.z() - getBlockPos().getZ() - .5);
    }
    @Override public void markCarriageView() { carriageView = true; LOADED_BOGEYS.remove(this); }
    @Override public boolean isCarriageView() { return carriageView; }
    public AbstractComputerBehaviour computerBehaviour() { return computerBehaviour; }

    private CRH2HeadEntity head() {
        if (level == null || !level.hasChunkAt(headRoot())) return null;
        var state = level.getBlockState(headRoot());
        return state.getBlock() instanceof PhysicalHeadBlock && state.getValue(CRH2HeadBlock.FACING) == getFacing()
                && level.getBlockEntity(headRoot()) instanceof CRH2HeadEntity head ? head : null;
    }

    public void syncFromHead(CRH2HeadEntity head) {
        if (syncingHead || carriageView || level == null || level.isClientSide || !head.getBlockPos().equals(headRoot())) return;
        syncingHead = true;
        try {
            var settings = head.physicalBogeyData();
            boolean restored = !settings.isEmpty() && !settings.equals(copiedSettings);
            if (restored) restoreSettings(settings);
            headSettingsRead = true;
            var data = head.bogeyData();
            boolean heightChanged = BogeyFloorData.heightFromBogeyData(data) != BogeyFloorData.heightFromBogeyData(headData);
            boolean changed = restored || headEnabled != head.bogeyEnabled() || !data.equals(headData);
            if (changed) {
                headData = data; headEnabled = head.bogeyEnabled();
                var extra = options.type.extra().copy();
                for (var key : headData.getAllKeys())
                    if (!key.equals("BogeyStyle") && !key.equals("UpsideDown")) extra.put(key, headData.get(key).copy());
                var next = new PhysicsBogeyOptions().set(options);
                next.type = new BogeySubtype(options.type.type(), extra);
                next.enabled = manualEnabled && headEnabled;
                if (heightChanged) next.setAxleOffset((float) BogeyFloorData.offset(BogeyFloorData.heightFromBogeyData(headData)));
                applyOptions(next);
                updateAttachment(true);
                setChanged(); sendData();
            }
            pushSettings(head);
        } finally { syncingHead = false; }
    }

    private void applyOptions(PhysicsBogeyOptions next) {
        if (computerBehaviour == null) { options.set(next); bogeyData = null; }
        else {
            super.setOptions(next);
            // Rail availability belongs to the physical head even when a computer owns the controls.
            options.enabled = next.enabled;
            options.setAxleOffset(next.getAxleOffset());
        }
    }

    private void updateAttachment(boolean rebuild) {
        if (localCenter == null || headData == null) return;
        var mount = HeadBogey.mount(headRoot(), getFacing());
        var delta = BogeyFloorData.displacement(HeadBogey.state(getFacing()), BogeyFloorData.longitudinal(headData));
        var next = new Vector3d(mount.getX() + .5 + delta.x, mount.getY() + .5, mount.getZ() + .5 + delta.z);
        if (localCenter.equals(next, 1e-9)) return;
        // Simurail 0.1 initializes this protected final reference with JOMLConversion.atCenterOf, a Vector3d.
        ((Vector3d) localCenter).set(next);
        if (!rebuild || !initialized || carriageView || level == null) return;
        if (!level.isClientSide && Sable.HELPER.getContaining(this) instanceof ServerSubLevel subLevel) {
            removePivot(subLevel);
            if (localPivotOffset != null) localPivotOffset.zero();
            initialized = false;
            init();
        } else resetPivotPose();
    }

    @Override public void init() {
        if (carriageView) return;
        var owner = head();
        if (owner != null && !syncingHead && level != null && !level.isClientSide) syncFromHead(owner);
        super.init();
    }
    @Override public void tick() {
        if (carriageView) return;
        super.tick();
        if (!level.isClientSide && !syncingHead) {
            var owner = head();
            if (owner != null) pushSettings(owner);
        }
    }
    @Override public void setOptions(PhysicsBogeyOptions requested) {
        var next = new PhysicsBogeyOptions().set(requested);
        var owner = ensureHeadSettings();
        if (computerBehaviour == null || !computerBehaviour.hasAttachedComputer()) manualEnabled = next.enabled;
        next.enabled = manualEnabled && headEnabled;
        applyOptions(next);
        if (owner != null) pushSettings(owner);
    }
    @Override public void setCustomName(Component name) {
        var owner = ensureHeadSettings();
        super.setCustomName(name);
        if (owner != null) pushSettings(owner);
    }
    @Override public void setChanged() {
        // Simurail's ComputerCraft peripheral mutates getOptions() directly before marking the entity dirty.
        if (headSettingsRead && !syncingHead && headData != null && options != null && options.enabled != (manualEnabled && headEnabled)) {
            manualEnabled = options.enabled;
            options.enabled = manualEnabled && headEnabled;
        }
        super.setChanged();
        if (headSettingsRead && !syncingHead && !carriageView && level != null && !level.isClientSide) {
            var owner = head();
            if (owner != null) pushSettings(owner);
        }
    }
    @Override public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag data, Player player,
                                                Direction side, boolean simulate) {
        if (!simulate) ensureHeadSettings();
        boolean requestedEnable = data.contains("options")
                ? new PhysicsBogeyOptions().read(data.getCompound("options")).enabled : manualEnabled;
        boolean accepted = super.readFromClipboard(registries, data, player, side, simulate);
        if (accepted && !simulate) {
            manualEnabled = requestedEnable;
            options.enabled = manualEnabled && headEnabled;
            var owner = head();
            if (owner != null) pushSettings(owner);
        }
        return accepted;
    }
    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        updateAttachment(true);
    }
    @Override public void setLevel(Level level) {
        super.setLevel(level);
        if (carriageView) LOADED_BOGEYS.remove(this);
    }

    /** Item/blueprint settings; no auxiliary body IDs, absolute positions or dynamic constraint poses. */
    public CompoundTag safeSettings() {
        var data = new CompoundTag();
        data.put("options", options.write()); data.putBoolean(MANUAL_ENABLED, manualEnabled);
        if (customName != null && level != null) data.putString("custom_name", Component.Serializer.toJson(customName, level.registryAccess()));
        return data;
    }
    private void restoreSettings(CompoundTag data) {
        if (data.contains("options")) options.read(data.getCompound("options"));
        manualEnabled = data.contains(MANUAL_ENABLED) ? data.getBoolean(MANUAL_ENABLED) : options.enabled;
        if (level != null) customName = data.contains("custom_name")
                ? Component.Serializer.fromJson(data.getString("custom_name"), level.registryAccess()) : null;
        bogeyData = null; copiedSettings = data.copy();
    }
    private void pushSettings(CRH2HeadEntity owner) {
        if (!headSettingsRead || carriageView || level == null || level.isClientSide) return;
        var data = safeSettings();
        copiedSettings = data.copy();
        if (!data.equals(owner.physicalBogeyData())) owner.updatePhysicalBogeyData(data);
    }
    private CRH2HeadEntity ensureHeadSettings() {
        var owner = head();
        if (owner != null && !headSettingsRead && !syncingHead) syncFromHead(owner);
        return owner;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket); writeHeadData(tag);
    }
    @Override public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries); writeHeadData(tag);
    }
    private void writeHeadData(CompoundTag tag) {
        tag.put(HEAD_DATA, headData.copy()); tag.putBoolean(HEAD_ENABLED, headEnabled); tag.putBoolean(MANUAL_ENABLED, manualEnabled);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        headSettingsRead = false;
        copiedSettings = new CompoundTag();
        super.read(tag, registries, clientPacket);
        headData = tag.contains(HEAD_DATA) ? HeadBogey.normalizedData(tag.getCompound(HEAD_DATA), getFacing()) : HeadBogey.defaultData(getFacing());
        headEnabled = tag.getBoolean(HEAD_ENABLED);
        manualEnabled = tag.contains(MANUAL_ENABLED) ? tag.getBoolean(MANUAL_ENABLED) : options.enabled;
        updateAttachment(initialized && !clientPacket);
    }
}
