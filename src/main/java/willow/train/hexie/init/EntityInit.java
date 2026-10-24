package willow.train.hexie.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.Main;
import willow.train.hexie.entity.HexieSeatEntity;

public class EntityInit {
    public static DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Main.MOD_ID);




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
