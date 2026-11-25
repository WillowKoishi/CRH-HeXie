package willow.train.hexie.world.registry.blocks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.render.DoorModelLocations;
import willow.train.hexie.world.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.world.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.world.blocks.locos.CR200jFrontBlock;

public class CustomRenderedDoorBlockInit {
    public static final DeferredHolder<Block, CustomRenderedDoorBlock> MARSHALLED_CR200J_DOOR = BlockInit.register("marshalled_cr200j_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), new DoorModelLocations(
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom_lh"),
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top_lh")), new DoorModelLocations(
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom"),
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top")),
                    RenderShape.ENTITYBLOCK_ANIMATED, false
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> MARSHALLED_CR200J_CABIN_DOOR = BlockInit.register("marshalled_cr200j_cabin_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), new DoorModelLocations(
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom_lh"),
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top_lh_2")), new DoorModelLocations(
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom"),
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top_2")),
                    RenderShape.ENTITYBLOCK_ANIMATED, false
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> MARSHALLED_CR200J_DOOR_2 = BlockInit.register("marshalled_cr200j_door_2",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), new DoorModelLocations(
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom_lh_2"),
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top_lh")), new DoorModelLocations(
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom_2"),
                    ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top")),
                    RenderShape.ENTITYBLOCK_ANIMATED, true
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> PANEL_25Z_ORIGINAL_DOOR = BlockInit.register("original_25z_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),
                    new DoorModelLocations(ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage25z/door/original_25z_door_bottom_hinge"),
                            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage25z/door/original_25z_door_top_hinge")),
                    new DoorModelLocations(ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage25z/door/original_25z_door_bottom"),
                            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage25z/door/original_25z_door_top")),
                    RenderShape.ENTITYBLOCK_ANIMATED, false
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> PANEL_25K_ORIGINAL_SLIDING_DOOR = BlockInit.register("original_25k_sliding_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),
                    new DoorModelLocations(ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "door/sliding_door_25k_bottom"),
                            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "door/sliding_door_25k_upper")),
                    new DoorModelLocations(ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "door/sliding_door_25k_bottom_lh"),
                            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "door/sliding_door_25k_upper_lh")),
                    RenderShape.ENTITYBLOCK_ANIMATED, true
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> PANEL_25T_ORIGINAL_SLIDING_DOOR = BlockInit.register("original_25t_sliding_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),
                    new DoorModelLocations(ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "door/sliding_door_25t_bottom"),
                            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "door/sliding_door_25t_upper")),
                    new DoorModelLocations(ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "door/sliding_door_25t_bottom_lh"),
                            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "door/sliding_door_25t_upper_lh")),
                    RenderShape.ENTITYBLOCK_ANIMATED, true
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CR200jFrontBlock> CR200J_FRONT_BLOCK = BlockInit.register("cr200j_front",
            () -> new CR200jFrontBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> HexieBridges.itemFactory().createTooltipAppendableItem(object.get(), new Item.Properties(), "block.hexie.cr200j_front.tooltip"));

    public static final DeferredHolder<Block, OBJRenderableEndFaceBlock> END_FACE_MARSHALLED_CR200J = BlockInit.register("end_face_marshalled_cr200j",
            () -> new OBJRenderableEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),
                    new DoorModelLocations(ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/end_face/end_face_door_lh"),
                            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/end_face/end_face_door")),
                    false),
            object -> () -> HexieBridges.itemFactory().createToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OBJRenderableEndFaceBlock> END_FACE_MARSHALLED_CR200J_2 = BlockInit.register("end_face_marshalled_cr200j_2",
            () -> new OBJRenderableEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),
                    new DoorModelLocations(ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/end_face/end_face_door_lh"),
                            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "carriage/carriage_marshalled_cr200j/end_face/end_face_door")),
                    true),
            object -> () -> HexieBridges.itemFactory().createToolTipsItemHelper(object.get(), new Item.Properties()));

    public static void invoke(){}
}
