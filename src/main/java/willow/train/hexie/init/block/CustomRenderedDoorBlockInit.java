package willow.train.hexie.init.block;

import net.minecraft.world.level.block.Block;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.locos.cr200j.CR200jFrontBlock;
import willow.train.hexie.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.item.ToolTipsItemHelper;
import willow.train.hexie.item.TooltipAppendableItem;

import static willow.train.hexie.init.block.BlockInit.register;

public class CustomRenderedDoorBlockInit {

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> MARSHALLED_CR200J_DOOR = register("marshalled_cr200j_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), Couple.create(
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom_lh"),
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top_lh")), Couple.create(
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom"),
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top")),
                    RenderShape.ENTITYBLOCK_ANIMATED, false
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> MARSHALLED_CR200J_CABIN_DOOR = register("marshalled_cr200j_cabin_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), Couple.create(
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom_lh"),
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top_lh_2")), Couple.create(
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom"),
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top_2")),
                    RenderShape.ENTITYBLOCK_ANIMATED, false
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> MARSHALLED_CR200J_DOOR_2 = register("marshalled_cr200j_door_2",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), Couple.create(
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom_lh_2"),
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top_lh")), Couple.create(
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_bottom_2"),
                    ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/door/door_top")),
                    RenderShape.ENTITYBLOCK_ANIMATED, true
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> PANEL_25Z_ORIGINAL_DOOR = register("original_25z_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),
                    Couple.create(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage25z/door/original_25z_door_bottom_hinge"),
                            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage25z/door/original_25z_door_top_hinge")),
                    Couple.create(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage25z/door/original_25z_door_bottom"),
                            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage25z/door/original_25z_door_top")),
                    RenderShape.ENTITYBLOCK_ANIMATED, false
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> PANEL_25K_ORIGINAL_SLIDING_DOOR = register("original_25k_sliding_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),
                    Couple.create(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "door/sliding_door_25k_bottom"),
                            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "door/sliding_door_25k_upper")),
                    Couple.create(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "door/sliding_door_25k_bottom_lh"),
                            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "door/sliding_door_25k_upper_lh")),
                    RenderShape.ENTITYBLOCK_ANIMATED, true
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CustomRenderedDoorBlock> PANEL_25T_ORIGINAL_SLIDING_DOOR = register("original_25t_sliding_door",
            () -> new CustomRenderedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),
                    Couple.create(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "door/sliding_door_25t_bottom"),
                            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "door/sliding_door_25t_upper")),
                    Couple.create(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "door/sliding_door_25t_bottom_lh"),
                            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "door/sliding_door_25t_upper_lh")),
                    RenderShape.ENTITYBLOCK_ANIMATED, true
            ),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CR200jFrontBlock> CR200J_FRONT_BLOCK = register("cr200j_front",
            () -> new CR200jFrontBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new TooltipAppendableItem(object.get(), new Item.Properties(), "block.hexie.cr200j_front.tooltip"));

    public static final DeferredHolder<Block, OBJRenderableEndFaceBlock> END_FACE_MARSHALLED_CR200J = register("end_face_marshalled_cr200j",
            () -> new OBJRenderableEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),
                    Couple.create(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/end_face/end_face_door_lh"),
                            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/end_face/end_face_door")),
                    false),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OBJRenderableEndFaceBlock> END_FACE_MARSHALLED_CR200J_2 = register("end_face_marshalled_cr200j_2",
            () -> new OBJRenderableEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),
                    Couple.create(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/end_face/end_face_door_lh"),
                            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "carriage/carriage_marshalled_cr200j/end_face/end_face_door")),
                    true),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static void invoke(){}
}
