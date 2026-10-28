package willow.train.hexie.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.Main;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.block.CustomRenderedDoorBlockInit;
import willow.train.hexie.blockentity.*;
import willow.train.hexie.blockentity.MegaphoneBlockEntity;

public class BlockEntitiesInit {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Main.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundStationSignEntity>> GROUND_STATION_SIGN =
            BLOCK_ENTITIES.register("ground_station_sign", () -> BlockEntityType.Builder.of(
                    GroundStationSignEntity::new, BlockInit.GROUND_STATION_SIGN.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MegaphoneBlockEntity>>
            MEGAPHONE_BLOCK_ENTITIES =
                    BLOCK_ENTITIES.register(
                            "megaphone_block_entity",
                            () ->
                                    BlockEntityType.Builder.of(
                                                    MegaphoneBlockEntity::new,
                                                    BlockInit.MEGAPHONE.get())
                                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarriageTypeSignEntity>> CARRIAGE_TYPE_SIGN =
            BLOCK_ENTITIES.register(
                    "carriage_type_sign",
                    () ->
                            BlockEntityType.Builder.of(
                                            CarriageTypeSignEntity::new,
                                            BlockInit.TRAIN_BOTTOM_PANEL_BLOCK.get())
                                    .build(null));

    /**
     * 这里注册方块实体的时候就已经连接了 Entity 和 Block CarriageNoSignEntity::new 是调用 Entity 的构造体，
     * Block.TRAIN_NO_BLOCK.get() 是调用对应的方块
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarriageNoSignEntity>> CARRIAGE_NO_SIGN =
            BLOCK_ENTITIES.register(
                    "carriage_no_sign",
                    () ->
                            BlockEntityType.Builder.of(
                                            CarriageNoSignEntity::new,
                                            BlockInit.TRAIN_NO_BLOCK.get())
                                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainSpeedSignEntity>> TRAIN_SPEED_SIGN =
            BLOCK_ENTITIES.register(
                    "carriage_speed_level_sign",
                    () ->
                            BlockEntityType.Builder.of(
                                            TrainSpeedSignEntity::new,
                                            BlockInit.TRAIN_SPEED_BLOCK.get())
                                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LacqueredBoardEntity>> LAQUERED_BOARD =
            BLOCK_ENTITIES.register(
                    "laquered_board",
                    () ->
                            BlockEntityType.Builder.of(
                                            LacqueredBoardEntity::new,
                                            BlockInit.LAQUERED_BOARD_BLOCK.get())
                                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFenceDoorEntity>>
            ELECTRIC_FENCE_DOOR =
                    BLOCK_ENTITIES.register(
                            "electric_fence_door",
                            () ->
                                    BlockEntityType.Builder.of(
                                                    ElectricFenceDoorEntity::new,
                                                    BlockInit.ELECTRIC_FENCE_DOOR_BLOCK.get())
                                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurnstileBlockEntity>> TURNS_A =
            BLOCK_ENTITIES.register(
                    "turns_a",
                    () ->
                            BlockEntityType.Builder.of(
                                            TurnstileBlockEntity::new, BlockInit.TURNS_A.get())
                                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurnstileCRBlockEntity>> TURNS_B =
            BLOCK_ENTITIES.register(
                    "turns_b",
                    () ->
                            BlockEntityType.Builder.of(
                                            TurnstileCRBlockEntity::new, BlockInit.TURNS_B.get())
                                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TicketVendorEntity>> TICKET_VENDOR =
            BLOCK_ENTITIES.register(
                    "ticket_vendor",
                    () ->
                            BlockEntityType.Builder.of(
                                            TicketVendorEntity::new,
                                            BlockInit.TICKET_VENDOR_BLOCK.get())
                                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OBJRenderableEndFaceEntity>>
            OBJ_RENDERABLE_END_FACE_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "obj_renderable_end_face_block_entity",
            () -> BlockEntityType.Builder.of(OBJRenderableEndFaceEntity::new,
                    CustomRenderedDoorBlockInit.END_FACE_MARSHALLED_CR200J.get(),
                    CustomRenderedDoorBlockInit.END_FACE_MARSHALLED_CR200J_2.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CR200jFrontBlockEntity>>
            CR200J_FRONT_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "cr200j_front_block_entity",
            () -> BlockEntityType.Builder.of(CR200jFrontBlockEntity::new, CustomRenderedDoorBlockInit.CR200J_FRONT_BLOCK.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CustomRenderedDoorEntity>>
            CUSTOM_RENDERED_DOOR_ENTITY = BLOCK_ENTITIES.register(
            "custom_rendered_door_entity",
            () -> BlockEntityType.Builder.of(CustomRenderedDoorEntity::new,
                            CustomRenderedDoorBlockInit.MARSHALLED_CR200J_DOOR.get(),
                            CustomRenderedDoorBlockInit.MARSHALLED_CR200J_DOOR_2.get(),
                            CustomRenderedDoorBlockInit.MARSHALLED_CR200J_CABIN_DOOR.get(),
                            CustomRenderedDoorBlockInit.PANEL_25K_ORIGINAL_SLIDING_DOOR.get(),
                            CustomRenderedDoorBlockInit.PANEL_25T_ORIGINAL_SLIDING_DOOR.get(),
                            CustomRenderedDoorBlockInit.PANEL_25Z_ORIGINAL_DOOR.get()
                    )
                    .build(null));

}
