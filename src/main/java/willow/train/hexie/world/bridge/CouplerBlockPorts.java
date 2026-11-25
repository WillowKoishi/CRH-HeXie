package willow.train.hexie.world.bridge;

import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.world.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.world.blocks.coupler.CouplerAdapterData;
import willow.train.hexie.world.blocks.coupler.CouplerBlock;
import willow.train.hexie.world.blocks.coupler.RescueCouplerState;

final class CouplerBlockPorts implements CouplerBlockBridge {
    private static final AbstractCouplerBlockOperations ABSTRACT_COUPLER_BLOCK = AbstractCouplerBlock::toggleCover;
    @Override public AbstractCouplerBlockOperations abstractCouplerBlock() { return ABSTRACT_COUPLER_BLOCK; }

    private static final CouplerAdapterDataOperations COUPLER_ADAPTER_DATA = new CouplerAdapterDataOperations() {
        @Override public CouplingBridge.CouplerAdapterView read(CompoundTag root) {
            return CouplerAdapterData.read(root);
        }
        @Override public CouplingBridge.CouplerAdapterView decode(CompoundTag tag) {
            return CouplerAdapterData.decode(tag);
        }
        @Override public CompoundTag encode(CouplingBridge.CouplerAdapterView adapter) {
            return CouplerAdapterData.encode(adapter);
        }
        @Override public void write(CompoundTag root, CouplingBridge.CouplerAdapterView adapter) {
            CouplerAdapterData.write(root, adapter);
        }
    };
    @Override public CouplerAdapterDataOperations couplerAdapterData() { return COUPLER_ADAPTER_DATA; }

    private static final CouplerBlockOperations COUPLER_BLOCK = CouplerBlock::of;
    @Override public CouplerBlockOperations couplerBlock() { return COUPLER_BLOCK; }

    private static final RescueCouplerStateOperations RESCUE_COUPLER_STATE = new RescueCouplerStateOperations() {
        @Override public boolean exposed(CouplingBridge.CouplerAdapterView adapter, String baseModel, int layerIndex) {
            return RescueCouplerState.exposed(adapter, baseModel, layerIndex);
        }
        @Override public boolean open(CompoundTag data) { return RescueCouplerState.open(data); }
        @Override public CompoundTag copyState(CompoundTag data) { return RescueCouplerState.copyState(data); }
        @Override public void copyState(CompoundTag from, CompoundTag to) { RescueCouplerState.copyState(from, to); }

        @Override public boolean installed(CouplingBridge.CouplerAdapterView adapter) {
            return RescueCouplerState.installed(adapter);
        }
        @Override public boolean exposed(CouplingBridge.CouplerAdapterView adapter, String baseModel) {
            return RescueCouplerState.exposed(adapter, baseModel);
        }
        @Override public float progress(CompoundTag data, long now, float partialTick) {
            return RescueCouplerState.progress(data, now, partialTick);
        }
        @Override public CompoundTag withOpen(CompoundTag data, boolean open, long now) {
            return RescueCouplerState.withOpen(data, open, now);
        }
        @Override public CompoundTag sync(CompoundTag data, CouplingBridge.CouplerAdapterView adapter, String baseModel, boolean coupled, long now) {
            return RescueCouplerState.sync(data, adapter, baseModel, coupled, now);
        }
    };
    @Override public RescueCouplerStateOperations rescueCouplerState() { return RESCUE_COUPLER_STATE; }
}
