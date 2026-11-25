package willow.train.hexie.world.registry.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

@EventBusSubscriber(modid = HexieConstants.MOD_ID)
public final class EntityInit {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, HexieConstants.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<Entity>> YZ_SEAT_ENTITY =
            ENTITY_TYPES.register("yz_seat_entity", () -> EntityType.Builder
                    .<Entity>of(HexieBridges.playerCarriage().seatEntityFactory(), MobCategory.MISC)
                    .sized(0.2F, 0.2F).build("yz_seat_entity"));

    // Older entry points only attach ENTITY_TYPES. Declare the optional type before the registry event.
    @SubscribeEvent private static void onConstruct(FMLConstructModEvent event) {
        registerCreateEntities();
    }

    public static void registerCreateEntities() {
        if (ModList.get().isLoaded("create")) CreateEntities.register();
    }

    public static EntityType<?> assembledCarriage() {
        if (!ModList.get().isLoaded("create")) return null;
        registerCreateEntities();
        return CreateEntities.ASSEMBLED_CARRIAGE.get();
    }

    private static final class CreateEntities {
        static final DeferredHolder<EntityType<?>, EntityType<Entity>> ASSEMBLED_CARRIAGE =
                ENTITY_TYPES.register("create_assembled_carriage", () -> EntityType.Builder
                        .<Entity>of(HexieBridges.createCarriage()::createSnapshotCarriageEntity, MobCategory.MISC)
                        .sized(1, 1).fireImmune().setTrackingRange(15).setUpdateInterval(3)
                        .setShouldReceiveVelocityUpdates(false).build("hexie:create_assembled_carriage"));

        static void register() {} // The holder initializes once, before registry events.
    }
}
