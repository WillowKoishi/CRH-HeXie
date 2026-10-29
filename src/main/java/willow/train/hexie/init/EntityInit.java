package willow.train.hexie.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.Main;
import willow.train.hexie.entity.HexieSeatEntity;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import com.tterrag.registrate.util.entry.EntityEntry;

public class EntityInit {
    public static DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Main.MOD_ID);

    public static final EntityEntry<SnapshotCarriageEntity> ASSEMBLED_CARRIAGE =
            Main.registrate()
            .entity("assembled_carriage", SnapshotCarriageEntity::new, MobCategory.MISC)
            .properties(builder -> builder.sized(1, 1).fireImmune().setTrackingRange(15)
                    .setUpdateInterval(3).setShouldReceiveVelocityUpdates(false))
            .register();




    public static final DeferredHolder<EntityType<?>, EntityType<HexieSeatEntity>> YZ_SEAT_ENTITY =
            ENTITY_TYPES.register(
                    "yz_seat_entity",
                    () ->
                            EntityType.Builder.of(
                                            (EntityType.EntityFactory<HexieSeatEntity>)
                                                    HexieSeatEntity::new,
                                            MobCategory.MISC)
                                    .sized(0.2F, 0.2F)
                                    .build("yz_seat_entity"));
}
