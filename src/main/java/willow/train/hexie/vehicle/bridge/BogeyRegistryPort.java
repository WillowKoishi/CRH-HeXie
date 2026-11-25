package willow.train.hexie.vehicle.bridge;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge;
import willow.train.hexie.core.api.bogey.BogeyDefinition;
import willow.train.hexie.core.api.bogey.BogeyHandle;
import willow.train.hexie.vehicle.assembly.BogeyModelCatalog;
import willow.train.hexie.vehicle.bogey.core.BogeyStyleMigration;
import willow.train.hexie.vehicle.bogey.registry.BogeyCatalog;
import willow.train.hexie.vehicle.bogey.registry.BogeyEntityInit;

final class BogeyRegistryPort implements BogeyRegistryBridge {
    private final BogeyCatalog catalog = new BogeyCatalog();

    private final BogeyEntityInitOperations bogeyEntityInit = new BogeyEntityInitOperations() {
        @Override
        public BlockEntityType<? extends BlockEntity> hxStandardBogey() {
            return BogeyEntityInit.HX_STANDARD_BOGEY.get();
        }

        @Override
        public BlockEntityType<? extends BlockEntity> df11gBogey() {
            return BogeyEntityInit.DF11G_BOGEY.get();
        }

        @Override
        public BlockEntityType<? extends BlockEntity> ss7dBogey() {
            return BogeyEntityInit.SS7D_BOGEY.get();
        }

        @Override
        public BlockEntityType<? extends BlockEntity> crhBogey() {
            return BogeyEntityInit.CRH_BOGEY.get();
        }
    };

    private final BuiltinBogeyModelOperations builtinBogeyModel = new BuiltinBogeyModelOperations() {
        @Override public ResourceLocation id(BuiltinBogeyModel value) {
            return willow.train.hexie.vehicle.bogey.core.BuiltinBogeyModel.valueOf(value.name()).id();
        }

        @Override public ResourceLocation style(BuiltinBogeyModel value) {
            return willow.train.hexie.vehicle.bogey.core.BuiltinBogeyModel.valueOf(value.name()).style();
        }

        @Override public ResourceLocation size(BuiltinBogeyModel value) {
            return willow.train.hexie.vehicle.bogey.core.BuiltinBogeyModel.valueOf(value.name()).size();
        }

        @Override
        public BuiltinBogeyProfile profile(BuiltinBogeyModel value) {
            return BuiltinBogeyProfile.valueOf(willow.train.hexie.vehicle.bogey.core.BuiltinBogeyModel.valueOf(value.name()).profile().name());
        }

        @Override
        public BuiltinBogeyModel find(ResourceLocation style, ResourceLocation size) {
            var model = willow.train.hexie.vehicle.bogey.core.BuiltinBogeyModel.find(style, size);
            return model == null ? null : BuiltinBogeyModel.valueOf(model.name());
        }
    };

    @Override
    public BogeyEntityInitOperations bogeyEntityInit() {
        return bogeyEntityInit;
    }

    @Override
    public BogeyModelCatalogOperations bogeyModelCatalog() {
        return BogeyModelCatalog::production;
    }

    @Override
    public BogeyStyleMigrationOperations bogeyStyleMigration() {
        return BogeyStyleMigration::normalizeData;
    }

    @Override
    public BuiltinBogeyModelOperations builtinBogeyModel() {
        return builtinBogeyModel;
    }

    @Override
    public BuiltinBogeyProfileOperations builtinBogeyProfile() {
        return value -> willow.train.hexie.vehicle.bogey.core.BuiltinBogeyProfile.valueOf(value.name()).geometry();
    }

    @Override
    public BogeyHandle register(IEventBus bus, ResourceLocation id, BogeyDefinition definition) {
        return catalog.register(bus, id, definition);
    }

    @Override
    public List<BogeyHandle> entries() {
        return catalog.entries();
    }
}
