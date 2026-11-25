package willow.train.hexie.bridge.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.core.api.bogey.BogeyGeometry;
import willow.train.hexie.core.api.bogey.BogeyModel;
import willow.train.hexie.core.api.bogey.BogeyRegistry;

/** Bogey registration, model lookup and selection data. The registry facade uses this same backend. */
public interface BogeyRegistryBridge extends BogeyRegistry.Backend {
    BogeyEntityInitOperations bogeyEntityInit();

    interface BogeyEntityInitOperations {
        BlockEntityType<? extends BlockEntity> hxStandardBogey();
        BlockEntityType<? extends BlockEntity> df11gBogey();
        BlockEntityType<? extends BlockEntity> ss7dBogey();
        BlockEntityType<? extends BlockEntity> crhBogey();
    }

    BogeyModelCatalogOperations bogeyModelCatalog();

    interface BogeyModelCatalogOperations {
        BogeyModelCatalogView production();
    }

    BogeyStyleMigrationOperations bogeyStyleMigration();

    interface BogeyStyleMigrationOperations {
        void normalizeData(CompoundTag data, String styleKey, ResourceLocation size);
    }

    BuiltinBogeyModelOperations builtinBogeyModel();

    interface BuiltinBogeyModelOperations {
        ResourceLocation id(BuiltinBogeyModel value);
        ResourceLocation style(BuiltinBogeyModel value);
        ResourceLocation size(BuiltinBogeyModel value);
        BuiltinBogeyProfile profile(BuiltinBogeyModel value);
        BuiltinBogeyModel find(ResourceLocation style, ResourceLocation size);
    }

    BuiltinBogeyProfileOperations builtinBogeyProfile();

    interface BuiltinBogeyProfileOperations {
        BogeyGeometry geometry(BuiltinBogeyProfile value);
    }

    interface BogeyModelCatalogView {
        void register(ResourceLocation block, BogeyModel model);
    }

    enum BuiltinBogeyModel {
        CW2, PK209P, PK209P_NO_MOTOR, PK209P_BACKWARD, SW220K, SW220K_BACKWARD, SW160, DF11G, DF11G_BACKWARD, SS7D_FRONT, QJ, QJ_BACKWARD, DT206, SKTB200, CRH1A_MOTOR, CRH1A_TRAILER
    }

    enum BuiltinBogeyProfile {
        CW2, PK209P, SW220K, SW160, DF11G, SS7D_FRONT, QJ, DT206, SKTB200, CRH1A_MOTOR, CRH1A_TRAILER
    }
}
