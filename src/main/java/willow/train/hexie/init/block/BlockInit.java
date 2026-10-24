package willow.train.hexie.init.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import com.google.common.base.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.FluorescentLightBlock;
import willow.train.hexie.blocks.MegaphoneBlock;
import willow.train.hexie.blocks.locos.hxd3d.HXD3DCabDoorBlock;
import willow.train.hexie.blocks.locos.hxd3d.HXD3DPanelBlocks;
import willow.train.hexie.blocks.locos.PantographBlock;
import willow.train.hexie.blocks.locos.SS8PantographBlock;
import willow.train.hexie.blocks.locos.cr200j.CR200jPanelBlocks;
import willow.train.hexie.blocks.locos.df11g.*;
import willow.train.hexie.blocks.seats.RZSeatBlock;
import willow.train.hexie.blocks.seats.SeatYZBlock;
import willow.train.hexie.blocks.signal.SeatedSignalBlock;
import willow.train.hexie.blocks.signal.SignalPoleLightBlock;
import willow.train.hexie.blocks.signs.LacqueredBoardBlock;
import willow.train.hexie.blocks.signs.TrainNoBlock;
import willow.train.hexie.blocks.signs.TrainPanelBlock;
import willow.train.hexie.blocks.signs.TrainSpeedSignBlock;
import willow.train.hexie.blocks.slidingdoor.ElectricFenceDoor;
import willow.train.hexie.blocks.slidingdoor.ElectricFenceWall;
import willow.train.hexie.blocks.structure.*;
import willow.train.hexie.blocks.ticketvendor.TicketVendorBlock;
import willow.train.hexie.blocks.carriage.*;
import willow.train.hexie.blocks.turnstile.TurnstileBlock;
import willow.train.hexie.blocks.turnstile.TurnstileCRBlock;
import willow.train.hexie.init.item.AllItemsInit;
import willow.train.hexie.item.ElectricFenceWallItem;
import willow.train.hexie.item.ToolTipsItemHelper;
import willow.train.hexie.item.editable_panels.TrainNoItem;
import willow.train.hexie.item.editable_panels.TrainPanelItem;

import java.io.File;
import java.io.FileWriter;
import java.util.function.Function;

public class BlockInit {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeXie");

    //注册机
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK,
            Main.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = AllItemsInit.ITEMS;


    /**
     * logo
     */
    public static final DeferredHolder<Block, Block> CR_LOGO = register("cr_logo",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.POLISHED_DEEPSLATE)),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    /**
     * 这些是座位
     */
    public static final DeferredHolder<Block, RZSeatBlock> SEAT_RZ = register("seat_rz",
            () -> new RZSeatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE)),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, SeatYZBlock> SEAT_YZ = register("seat_yz",
            () -> new SeatYZBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE)),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    /*
    public static final DeferredHolder<Block, SeatYZBlock> SEAT_YZ_22 = register("yz_seat22",
            () -> new SeatYZBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    */

    /**
     * 车厢里面的其他东西
     */

    public static final DeferredHolder<Block, CeilingShelfBlock> CEILINSHELF = register("ceilin_shelf",
            () -> new CeilingShelfBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops().lightLevel(state -> 12)),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, YZTableBlock> YZ_TABLE = register("yz_table",
            () -> new YZTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    /**
     * 这里是售票机和闸机
     */

    public static final DeferredHolder<Block, TicketVendorBlock> TICKET_VENDOR_BLOCK = register("ticket_vendor",
            () -> new TicketVendorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TurnstileBlock> TURNS_A = register("turns_a_block",
            () -> new TurnstileBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TurnstileCRBlock> TURNS_B = register("turns_b_block",
            () -> new TurnstileCRBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    /**
     * 这里是 Sign 方块
     */

    public static final DeferredHolder<Block, TrainPanelBlock> TRAIN_BOTTOM_PANEL_BLOCK = register("train_bottom_panel_block",
            () -> new TrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new TrainPanelItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainNoBlock> TRAIN_NO_BLOCK = register("train_no_block",
            () -> new TrainNoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new TrainNoItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainSpeedSignBlock> TRAIN_SPEED_BLOCK = register("train_speed_block",
            () -> new TrainSpeedSignBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new TrainNoItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, LacqueredBoardBlock> LAQUERED_BOARD_BLOCK = register("laquered_board_block",
            () -> new LacqueredBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new TrainNoItem(object.get(), new Item.Properties()));

    /**
     * 各种屏蔽门
     */

    public static final DeferredHolder<Block, ElectricFenceDoor> ELECTRIC_FENCE_DOOR_BLOCK = register("electric_fence_door_block",
            () -> new ElectricFenceDoor(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new Item(new Item.Properties()));  // 

    public static final DeferredHolder<Block, ElectricFenceWall> ELECTRIC_FENCE_WALL_BLOCK = register("electric_fence_wall_block",
            () -> new ElectricFenceWall(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new ElectricFenceWallItem(object.get(), new Item.Properties()));  // 


    /**
     * 暂未分类
     */
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25 = register("original_color_window_25",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainUnOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25_SEALED = register("original_color_window_25_sealed",
            () -> new TrainUnOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //25B
    public static final DeferredHolder<Block, TrainDoorBlock> PANEL_25B_ORIGINAL_DOOR = register("original_25b_door",
            () -> new TrainDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CarPortBlockBGKZ.Train25BGEndFaceBlock> END_FACE_25B = register("end_face_25b_original",
            () -> new CarPortBlockBGKZ.Train25BGEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    //panel_25b_original_bottom
    //猪

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25B_ORIGINAL_BOTTOM = register("panel_25b_original_bottom",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //panel_25b_original_mid
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25B_ORIGINAL_MID = register("panel_25b_original_mid",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //panel_25b_original_window
    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_25B_ORIGINAL_WINDOW = register("panel_25b_original_window",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25B_WIDE = register("original_color_window_25b_wide",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25B_SMALL = register("original_color_window_25b_small",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25B_TOILET = register("original_color_window_25b_toilet",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainSealedWindowBlock> ORIGINAL_COLOR_WINDOW_25B_WIDE_SEALED = register("original_color_window_25b_wide_sealed",
            () -> new TrainSealedWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25B = register("original_color_window_25b",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //panel_25b_original_top
    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25B_ORIGINAL_TOP = register("panel_25b_original_top",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25B_ORIGINAL_TOP_B = register("panel_25b_original_top_b",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TopPanelSlabBlock> SLAB_25B_ORIGINAL_TOP = register("slab_25b_original_top",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Train25GLadderBlock> LADDER_25B_ORIGIINAL = register("ladder_25b_original",
            () -> new Train25GLadderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25B_ORIGINAL_UPPER = register("end_face_25b_original_upper",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25B_ORIGINAL_UPPER_2 = register("end_face_25b_original_upper_2",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CarPortBlockBGKZ.Train25BGEndFaceBlock> END_FACE_25G_ORIGINAL = register("end_face_25g_original",
            () -> new CarPortBlockBGKZ.Train25BGEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

//direct_drainage_toilet_original_25b

    public static final DeferredHolder<Block, TopPanelSlabBlock> DIRECT_DRAINAGE_TOILET_ORIGINAL_25B = register("direct_drainage_toilet_original_25b",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    //25G
    public static final DeferredHolder<Block, TrainDoorBlock> PANEL_25G_ORIGINAL_DOOR = register("original_25g_door",
            () -> new TrainDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //panel_25g_original_bottom
    //猪

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25G_ORIGINAL_BOTTOM = register("panel_25g_original_bottom",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));


    //panel_25g_original_mid
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25G_ORIGINAL_MID = register("panel_25g_original_mid",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //panel_25g_original_window
    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_25G_ORIGINAL_WINDOW = register("panel_25g_original_window",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //original_color_window_25g_small
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25G_SMALL = register("original_color_window_25g_small",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25G_SMALL_2 = register("original_color_window_25g_small_2",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> ORIGINAL_COLOR_WINDOW_25G_SEALED_SMALL = register("original_color_window_25g_sealed_small",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25G_TOILET = register("original_color_window_25g_toilet",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //original_color_window_25g_wide
public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25G_WIDE = register("original_color_window_25g_wide",
        () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),1),
        object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TrainSealedWindowBlock> ORIGINAL_COLOR_WINDOW_25G_WIDE_SEALED = register("original_color_window_25g_wide_sealed",
            () -> new TrainSealedWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //panel_25g_original_top
    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25G_ORIGINAL_TOP = register("panel_25g_original_top",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TopPanelSlabBlock> SLAB_25G_ORIGINAL_TOP = register("slab_25g_original_top",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Train25GLadderBlock> LADDER_25G_ORIGIINAL = register("ladder_25g_original",
            () -> new Train25GLadderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainIsolationDoorBlock> ISOLATION_DOOR_25BG = register2("isolation_door_25bg",
            () -> new TrainIsolationDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25G_ORIGINAL_UPPER = register("end_face_25g_original_upper",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25G_ORIGINAL_UPPER_2 = register("end_face_25g_original_upper_2",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TopPanelSlabBlock> DIRECT_DRAINAGE_TOILET_ORIGINAL_25G = register("direct_drainage_toilet_original_25g",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //25z
    public static final DeferredHolder<Block, TopPanelSlabBlock> SLAB_25Z_ORIGINAL_TOP = register("slab_25z_original_top",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    /*
    public static final DeferredHolder<Block, TrainDoorBlock> PANEL_25Z_ORIGINAL_DOOR = register("original_25z_door",
            () -> new TrainDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

     */

    public static final DeferredHolder<Block, DF11GMirrorCarportBlock> CARPORT_25Z_AC_SIDE = register("carport_25z_ac_side",
            () -> new DF11GMirrorCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CarPortBlockBGKZ> CARPORT_25Z_AC = register("carport_25z_ac",
            () -> new CarPortBlockBGKZ(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25Z_SMALL2 = register("original_color_window_25z_small2",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25Z_TOILET = register("original_color_window_25z_toilet",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TopPanelSlabBlock> DIRECT_DRAINAGE_TOILET_ORIGINAL_25Z = register("direct_drainage_toilet_original_25z",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Train25GLadderBlock> LADDER_25Z_ORIGIINAL = register("ladder_25z_original",
            () -> new Train25GLadderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CarPortBlockBGKZ> CARPORT_25Z_SIDE = register("carport_25z",
            () -> new CarPortBlockBGKZ(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, HXD3DPanelBlocks> CARPORT_CENTRE_25Z = register("carport_centre_25z",
            () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 2),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, Train25KTEndFaceBlock> END_FACE_25Z_ORIGINAL = register("end_face_25z_original",
            () -> new Train25KTEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25Z_ORIGINAL_UPPER = register("end_face_25z_original_upper",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25Z_ORIGINAL_UPPER_AC = register("end_face_25z_original_upper_ac",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    //猪
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25Z_ORIGINAL_BOTTOM_SIDE = register("panel_25z_original_bottom_side",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25Z_ORIGINAL_BOTTOM_LINE = register("panel_25z_original_bottom_line",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25Z_ORIGINAL_BOTTOM_SYMBOL = register("panel_25z_original_bottom_symbol",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, MultiType25ZMidBlock> PANEL_25Z_ORIGINAL_MID = register("panel_25z_original_mid",
            () -> new MultiType25ZMidBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25Z_ORIGINAL_MID_SIDE = register("panel_25z_original_mid_side",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25Z_ORIGINAL_TOP = register("panel_25z_original_top",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25Z_WIDE = register("original_color_window_25z_wide",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25Z = register("original_color_window_25z",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Window25ZLevel> ORIGINAL_COLOR_WINDOW_25Z_LEVEL = register("original_color_window_25z_level",
            () -> new Window25ZLevel(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //25K

    public static final DeferredHolder<Block, TrainDoorBlock> PANEL_25K_ORIGINAL_DOOR = register("original_25k_door",
            () -> new TrainDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //panel_25k_original_bottom
    //猪
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25K_ORIGINAL_BOTTOM = register("panel_25k_original_bottom",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //猪
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25K_ORIGINAL_LINE = register("panel_25k_original_bottom_line",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //panel_25k_original_mid
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25K_ORIGINAL_MID = register("panel_25k_original_mid",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //panel_25k_original_window
    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_25K_ORIGINAL_WINDOW = register("panel_25k_original_window",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25K_WIDE = register("original_color_window_25k_wide",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25K_SMALL = register("original_color_window_25k_small",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> ORIGINAL_COLOR_WINDOW_25K_SEALED_SMALL = register("original_color_window_25k_sealed_small",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25K_TOILET = register("original_color_window_25k_toilet",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainSealedWindowBlock> ORIGINAL_COLOR_WINDOW_25K_WIDE_SEALED = register("original_color_window_25k_wide_sealed",
            () -> new TrainSealedWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    //panel_25k_original_top
    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25K_ORIGINAL_TOP = register("panel_25k_original_top",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25K_ORIGINAL_SYMBOL = register("panel_25k_original_symbol",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CarPortBlockBGKZ> CARPORT_25BGZK = register("carport_25bgzk",
            () -> new CarPortBlockBGKZ(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, HXD3DPanelBlocks>VENTIDUCT_25B = register("ventiduct_25b",
            () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 2),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));


    //carport_25bgzk_air_condition_all
    public static final DeferredHolder<Block, HXD3DPanelBlocks> CARPORT_25BGZKT_CENTRE = register("carport_25bgzkt_centre",
            () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 2),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, HXD3DPanelBlocks> CARPORT_BSP25T_CENTRE = register("carport_bsp25t_centre",
            () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 2),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

//carport_25bgzkt_water_tank
public static final DeferredHolder<Block, HXD3DPanelBlocks> CARPORT_25BGZKT_WATER_TANK = register("carport_25bgzkt_water_tank",
        () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 2),
        object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CarPortBlockBGKZ> CARPORT_25BGZK_AIR_CONDITION_ALL = register("carport_25bgzk_air_condition_all",
            () -> new CarPortBlockBGKZ(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));




    public static final DeferredHolder<Block, Train25GLadderBlock> LADDER_25K_ORIGIINAL = register("ladder_25k_original_sliding",
            () -> new Train25GLadderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TopPanelSlabBlock> DIRECT_DRAINAGE_TOILET_ORIGINAL_25K = register("direct_drainage_toilet_original_25k",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //carport_25bgzk_air_condition_side

    public static final DeferredHolder<Block, DF11GMirrorCarportBlock> CARPORT_25BGZK_AIR_CONDITION_SIDE = register("carport_25bgzk_air_condition_side",
            () -> new DF11GMirrorCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TopPanelSlabBlock> SLAB_25K_ORIGINAL_TOP = register("slab_25k_original_top",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //end_face_25k_original
    public static final DeferredHolder<Block, Train25KTEndFaceBlock> END_FACE_25K_ORIGINAL = register("end_face_25k_original",
            () -> new Train25KTEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25K_ORIGINAL_UPPER = register("end_face_25k_original_upper",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25K_ORIGINAL_UPPER_2 = register("end_face_25k_original_upper_2",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, CarPortBlockBGKZ.Train25BGEndFaceBlock> END_FACE_25K_ORIGINAL_RUBBER = register("end_face_25k_original_rubber",
            () -> new CarPortBlockBGKZ.Train25BGEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25K_ORIGINAL_RUBBER_UPPER = register("end_face_25k_original_rubber_upper",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25K_ORIGINAL_RUBBER_UPPER_2 = register("end_face_25k_original_rubber_upper_2",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //25T
    public static final DeferredHolder<Block, TrainDoorBlock> PANEL_25T_ORIGINAL_DOOR = register("original_25t_door",
            () -> new TrainDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //panel_25t_original_bottom
    //猪
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25T_ORIGINAL_BOTTOM = register("panel_25t_original_bottom",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //panel_25t_original_mid
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25T_ORIGINAL_MID = register("panel_25t_original_mid",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, PanelBlock25Side> PANEL_25T_ORIGINAL_MID_B = register("panel_25t_original_mid_b",
            () -> new PanelBlock25Side(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25T_ORIGINAL_TOP_B = register("panel_25t_original_top_b",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //2猪
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25T_ORIGINAL_BOTTOM_B = register("panel_25t_original_bottom_b",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Train25GLadderBlock> LADDER_25T_ORIGIINAL = register("ladder_25t_original_sliding",
            () -> new Train25GLadderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));


    //panel_25t_original_window
    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_25T_ORIGINAL_WINDOW = register("panel_25t_original_window",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_BLUE = register("original_color_window_25t_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_SEALED_BLUE = register("original_color_window_25t_sealed_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_SMALL = register("original_color_window_25t_small",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_SMALL_BLUE = register("original_color_window_25t_small_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_BSP25T_SMALL_BLUE = register("original_color_window_bsp25t_small_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_SEALED_SMALL = register("original_color_window_25t_sealed_small",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_SEALED_SMALL_BLUE = register("original_color_window_25t_sealed_small_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_BSP25T_SEALED_SMALL_BLUE = register("original_color_window_bsp25t_sealed_small_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_TOILET = register("original_color_window_25t_toilet",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_TOILET_BLUE = register("original_color_window_25t_toilet_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_BSP25T_TOILET_BLUE = register("original_color_window_bsp25t_toilet_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_WIDE = register("original_color_window_25t_wide",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_25T_WIDE_BLUE = register("original_color_window_25t_wide_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainOpenableWindowBlock> ORIGINAL_COLOR_WINDOW_BSP25T_WIDE_BLUE = register("original_color_window_bsp25t_wide_blue",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainSealedWindowBlock> ORIGINAL_COLOR_WINDOW_25T_WIDE_SEALED = register("original_color_window_25t_wide_sealed",
            () -> new TrainSealedWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainSealedWindowBlock> ORIGINAL_COLOR_WINDOW_25T_WIDE_SEALED_BLUE = register("original_color_window_25t_wide_sealed_blue",
            () -> new TrainSealedWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TrainSealedWindowBlock> ORIGINAL_COLOR_WINDOW_BSP25T_WIDE_SEALED_BLUE = register("original_color_window_bsp25t_wide_sealed_blue",
            () -> new TrainSealedWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //panel_25t_original_top
    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25T_ORIGINAL_TOP = register("panel_25t_original_top",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_XL25T_ORIGINAL_TOP = register("panel_xl25t_original_top",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_25T_ORIGINAL_SKIRT = register("panel_25t_original_skirt",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, PanelBlock25Side> PANEL_25T_ORIGINAL_SKIRT_SIDE = register("panel_25t_original_skirt_side",
            () -> new PanelBlock25Side(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CarPortBlockBGKZ> CARPORT_25T = register("carport_25t",
            () -> new CarPortBlockBGKZ(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, CarPortBlockBGKZ> CARPORT_25T_TOILET = register("carport_25t_toilet",
            () -> new CarPortBlockBGKZ(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));



    public static final DeferredHolder<Block, BSP25TAirConditionBlock> AIR_CODITION_BSP25T = register("air_condition_bsp25t",
            () -> new BSP25TAirConditionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.POLISHED_DEEPSLATE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TopPanelSlabBlock> SLAB_25T_ORIGINAL_TOP = register("slab_25t_original_top",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //widepanel_bsp25t_original_mid
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> WIDEPANEL_BSP25T_ORIGINAL_MID = register("widepanel_bsp25t_original_mid",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25T_ORIGINAL_UPPER = register("end_face_25t_original_upper",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25T_ORIGINAL_UPPER_2 = register("end_face_25t_original_upper_2",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CarPortBlockBGKZ.Train25BGEndFaceBlock> END_FACE_25T_ORIGINAL_RUBBER = register("end_face_25t_original_rubber",
            () -> new CarPortBlockBGKZ.Train25BGEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25T_ORIGINAL_RUBBER_UPPER = register("end_face_25t_original_rubber_upper",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> END_FACE_25T_ORIGINAL_RUBBER_UPPER_2 = register("end_face_25t_original_rubber_upper_2",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, TopPanelSlabBlock> DIRECT_DRAINAGE_TOILET_ORIGINAL_25T = register("direct_drainage_toilet_original_25t",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //刷绿shualv
    public static final DeferredHolder<Block, TrainDoorBlock> PANEL_25_MARSHALLED_DOOR = register("marshalled_25_door",
            () -> new TrainDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //猪
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25_MARSHALLED_BOTTOM = register("panel_25_marshalled_bottom",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //2猪
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25_MARSHALLED_BOTTOM_LINE = register("panel_25_marshalled_bottom_line",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25_MARSHALLED_MID = register("panel_25_marshalled_mid",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25_MARSHALLED_TOP = register("panel_25_marshalled_top",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_25_MARSHALLED_TOP_B = register("panel_25_marshalled_top_b",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25B_MARSHALLED_SYMBOL = register("panel_25b_marshalled_symbol",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25G_MARSHALLED_SYMBOL = register("panel_25g_marshalled_symbol",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_25K_MARSHALLED_SYMBOL = register("panel_25k_marshalled_symbol",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    @Deprecated
    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_25B_MARSHALLED_WINDOW = register("panel_25b_marshalled_window",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));  // 

    public static final DeferredHolder<Block, TopPanelSlabBlock> SLAB_25_MARSHALLED_TOP = register("slab_25_marshalled_top",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    @Deprecated
    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_25_MARSHALLED_WINDOW = register("panel_25_marshalled_window",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));  // 

    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_25T_MARSHALLED_SKIRT = register("panel_25t_marshalled_skirt",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlock25Side> PANEL_25T_MARSHALLED_SKIRT_SIDE = register("panel_25t_marshalled_skirt_side",
            () -> new PanelBlock25Side(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Train25KTEndFaceBlock> END_FACE_25T_ORIGINAL = register("end_face_25t_original",
            () -> new Train25KTEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    //受电弓
    public static final DeferredHolder<Block, PantographBlock> PANTOGRAPH = register("pantograph",
            () -> new PantographBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));


    /*
    public static final DeferredHolder<Block, CarriageTypeSignBlock> CARRIAGE_TYPE_SIGN_BLOCK = register("carriage_type_sign",
            () -> new CarriageTypeSignBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

     */

    public static final DeferredHolder<Block, SS8PantographBlock> SS8_PANTOGRAPH = register("ss8_pantograph",
            () -> new SS8PantographBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlockHalf> SMOOTH_QUARTZ_PANEL_HALF = register("smooth_quartz_panel_half",
            () -> new PanelBlockHalf(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlock4> SMOOTH_QUARTZ_PANEL_4 = register("smooth_quartz_panel_4",
            () -> new PanelBlock4(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlock2> SMOOTH_QUARTZ_PANEL_2 = register("smooth_quartz_panel_2",
            () -> new PanelBlock2(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, AngleBlock22> SMOOTH_QUARTZ_ANGLE_22_UP = register("smooth_quartz_angle_22_up",
            () -> new AngleBlock22(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //粘土板
    public static final DeferredHolder<Block, Block> CLAY_SLAB = register("clay_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 3.0f)
                    .sound(SoundType.POLISHED_DEEPSLATE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //clay_stairs
    public static final DeferredHolder<Block, StairBlock> CLAY_STAIRS = register("clay_stairs",
            () -> new StairBlock(Blocks.CLAY.defaultBlockState(), BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, GreenInsulationFenceBlock> GREEN_FENCE = register("green_fence",
            () -> new GreenInsulationFenceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 3.0f)
                    .sound(SoundType.POLISHED_DEEPSLATE),0),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, GreenInsulationFenceBlock> GREEN_FENCE_45 = register("green_fence_45",
            () -> new GreenInsulationFenceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 3.0f)
                    .sound(SoundType.POLISHED_DEEPSLATE),1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //车棚

    //接触网
    //Catenary_Pole

    public static final DeferredHolder<Block, PlatformLineBlock> Platform_Line_Block = register("platform_line_block",
            () -> new PlatformLineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, SeatedSignalBlock> Station_Entrance_Signal = register("station_entrance_signal",
            () -> new SeatedSignalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, SeatedSignalBlock> Shunting_Signal = register("shunting_signal",
            () -> new SeatedSignalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));
    public static final DeferredHolder<Block, TrainSignBlock> No_Double_Pantograph = register("no_double_pantograph",
            () -> new TrainSignBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));
    public static final DeferredHolder<Block, TrainSignBlock> Switch_Off_Sign = register("switch_off_sign",
            () -> new TrainSignBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, TrainSignBlock> Switch_On_Sign = register("switch_on_sign",
            () -> new TrainSignBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, SignalPoleBlock> Signal_Pole = register("signal_pole",
            () -> new SignalPoleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, SignalPoleLightBlock> Signal_Pole_Light = register("signal_pole_light",
            () -> new SignalPoleLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, TactilePavingBlock> TactilePavingStraight = register("tactile_paving_straight",
            () -> new TactilePavingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, TactilePavingBlock> TactilePavingPin = register("tactile_paving_pin",
            () -> new TactilePavingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, FluorescentLightBlock> FLUORESCENT_LIGHT = register("flourescent_light",
            () -> new FluorescentLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 15)),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    public static final DeferredHolder<Block, MegaphoneBlock> MEGAPHONE = register("megaphone",
            () -> new MegaphoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()),object -> () -> new BlockItem(object.get(),
                    new Item.Properties()));

    //cr200j

    public static final DeferredHolder<Block, CR200jPanelBlocks> PANEL_CR200J_MARSHALLED_TOP_2 = register("panel_cr200j_marshalled_top_2",
            () -> new CR200jPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_CR200J_MARSHALLED_TOP_3 = register("panel_cr200j_marshalled_top_3",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CR200jPanelBlocks> WIDEPANEL_MARSHALLED_CR200J_LOGO_CR = register("widepanel_marshalled_cr200j_logo_cr",
            () -> new CR200jPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CR200jPanelBlocks> WIDEPANEL_MARSHALLED_CR200J_FU_XING = register("widepanel_marshalled_cr200j_fu_xing",
            () -> new CR200jPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 3),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CR200jPanelBlocks> WIDEPANEL_MARSHALLED_CR200J = register("widepanel_marshalled_cr200j",
            () -> new CR200jPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlock25Side> PANEL_CR200J_MARSHALLED_MIDDLE_LOGO_CR = register("panel_cr200j_marshalled_middle_logo_cr",
            () -> new PanelBlock25Side(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainGlassPanelBlock> PANEL_MARSHALLED_CR200J_SKIRT = register("panel_marshalled_cr200j_skirt",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, PanelBlock25Side> PANEL_MARSHALLED_CR200J_SKIRT_SIDE = register("panel_marshalled_cr200j_skirt_side",
            () -> new PanelBlock25Side(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainUnOpenableWindowBlock> MARSHALLED_WINDOW_CR200J_WIDE_SEALED = register("marshalled_window_cr200j_wide_sealed",
            () -> new TrainUnOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops(), 1),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainGlassPanelBlock> MARSHALLED_WINDOW_CR200J_SEALED = register("marshalled_window_cr200j_sealed",
            () -> new TrainGlassPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> MARSHALLED_WINDOW_CR200J_WIDE = register("marshalled_window_cr200j_wide",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainOpenableWindowBlock> MARSHALLED_WINDOW_CR200J_TOILET = register("marshalled_window_cr200j_toilet",
            () -> new TrainOpenableWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion(),0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TopPanelSlabBlock> DIRECT_DRAINAGE_TOILET_MARSHALLED_CR200J = register("direct_drainage_toilet_marshalled_cr200j",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, OrdinaryTrainUpperPanelBlock> PANEL_CR200J_MARSHALLED_TOP = register("panel_cr200j_marshalled_top",
            () -> new OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    //猪
    public static final DeferredHolder<Block, OrdinaryTrainPanelBlock> PANEL_CR200J_MARSHALLED_BOTTOM = register("panel_cr200j_marshalled_bottom",
            () -> new OrdinaryTrainPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CR200jPanelBlocks> PANEL_CR200J_MARSHALLED_MIDDLE = register("panel_cr200j_marshalled_middle",
            () -> new CR200jPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 0),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TopPanelSlabBlock> SLAB_CR200J_MARSHALLED_TOP = register("slab_cr200j_marshalled_top",
            () -> new TopPanelSlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Train25GLadderBlock> LADDER_MARSHALLED_CR200J = register("ladder_marshalled_cr200j",
            () -> new Train25GLadderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));


    public static final DeferredHolder<Block, CarPortBlockBGKZ> CARPORT_MARSHALLED_CR200J = register("carport_cr200j_marshalled",
            () -> new CarPortBlockBGKZ(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, HXD3DPanelBlocks> CARPORT_MARSHALLED_CR200J_CENTER = register("carport_cr200j_marshalled_center",
            () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 2),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));



public static final DeferredHolder<Block, DF11GCowcatcherBlock> DF11G_COWCATCHER = register("df11g_cowcatcher",
        () -> new DF11GCowcatcherBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
        object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DF11GMirrorCarportBlock> DF11G_CARPORT_1 = register("df11g_carport_1",
            () -> new DF11GMirrorCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DF11GMirrorCarportBlock> DF11G_CARPORT_2 = register("df11g_carport_2",
            () -> new DF11GMirrorCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DF11GCarportBlock> DF11G_CARPORT_GENERAL = register("df11g_carport_general",
            () -> new DF11GCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DF11GCarportBlock> DF11G_CARPORT_LOUVER = register("df11g_carport_louver",
            () -> new DF11GCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, DF11GCarportBlock> DF11G_CARPORT_LOUVER_2 = register("df11g_carport_louver_2",
            () -> new DF11GCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, DF11GCarportBlock> DF11G_CARPORT_LOUVER_3 = register("df11g_carport_louver_3",
            () -> new DF11GCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DF11GCarportBlock> DF11G_CARPORT_HEXIE = register("df11g_carport_hexie",
            () -> new DF11GCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),3),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DF11GPanel3Wide> DF11G_PANEL_HEXIE = register("df11g_panel_hexie",
            () -> new DF11GPanel3Wide(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),3),
            object -> () -> new ToolTipsItemHelper(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlock2> DF11G_PANEL_GENERAL = register("df11g_panel_general",
            () -> new PanelBlock2(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlock2> DF11G_PANEL_MID_GENERAL = register("df11g_panel_mid_general",
            () -> new PanelBlock2(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlock2> DF11G_PANEL_SHADES = register("df11g_panel_shades",
            () -> new PanelBlock2(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, PanelBlock2> DF11G_PANEL_MID_WINDOW= register("df11g_panel_mid_window",
            () -> new PanelBlock2(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, PanelBlock2> DF11G_PANEL_MID_SHADES = register("df11g_panel_mid_shades",
            () -> new PanelBlock2(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
//df11g_panel_cr
public static final DeferredHolder<Block, DF11GPanel3Wide> DF11G_PANEL_CR = register("df11g_panel_cr",
        () -> new DF11GPanel3Wide(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),3),
        object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DF11GMirrorCarportBlock> DF11G_PANEL_MID_FRONT_2= register("df11g_panel_mid_front_2",
            () -> new DF11GMirrorCarportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(),2),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, PanelBlock25Side> DF11G_PANEL_MID_FRONT = register("df11g_panel_mid_front",
            () -> new PanelBlock25Side(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DF11GEndFaceBlock> DF11G_END_FACE = register("df11g_end_face",
            () -> new DF11GEndFaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, TrainDoorBlock> DF11G_CAB_DOOR = register("df11g_cab_door",
            () -> new TrainDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, HXD3DCabDoorBlock> DF11G_EQUIP_ROOM_DOOR = register("df11g_equip_room_door",
            () -> new HXD3DCabDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, HXD3DPanelBlocks> HXD3D_PANEL_RED = register("hxd3d_panel_red",
            () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 0),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, HXD3DPanelBlocks> HXD3D_PANEL_HEXIE = register("hxd3d_panel_hexie",
            () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 1),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    public static final DeferredHolder<Block, HXD3DPanelBlocks> HXD3D_CARPORT_GENERAL = register("hxd3d_carport_general",
            () -> new HXD3DPanelBlocks(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion(), 2),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> HXD3D_SLAB = register("hxd3d_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 3.0f)
                    .sound(SoundType.POLISHED_DEEPSLATE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, HXD3DCabDoorBlock> HXD3D_CAB_DOOR = register("hxd3d_cab_door",
            () -> new HXD3DCabDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

//qj

    public static final DeferredHolder<Block, DF11GFrontBlock> QJ_POT_CARCASS = register("qj_pot_carcass",
            () -> new DF11GFrontBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f, 3.0f)
                    .sound(SoundType.POLISHED_DEEPSLATE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));  // 


    public static final DeferredHolder<Block, PanelBlockHalf> BOILING_WATER_PLACE = register("boiling_water_place",
            () -> new PanelBlockHalf(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static final DeferredHolder<Block, DirectionalCarpetBlock> ORIGINAL_25Z_FLOOR_CARPET = register("original_25z_floor_carpet",
            () -> new DirectionalCarpetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(.1f, .1f)
                    .sound(SoundType.WOOL)), obj -> () -> new BlockItem(obj.get(), new Item.Properties()));

    static <T extends Block> DeferredHolder<Block, T> registerBlock(final String name,
                                                                     final Supplier<? extends T> block) {
        return BLOCKS.register(name, block);

    }


    static <T extends Block> DeferredHolder<Block, T> register(final String name, final Supplier<? extends T> block,
                                                                Function<DeferredHolder<Block, T>, Supplier<? extends Item>> item) {
        DeferredHolder<Block, T> obj = registerBlock(name, block);
        ITEMS.register(name, item.apply(obj));
        // generateDrops(name);
        return obj;
    }
    static <T extends Block> DeferredHolder<Block, T> register2(final String name, final Supplier<? extends T> block,
                                                                Function<DeferredHolder<Block, T>, Supplier<? extends Item>> item) {
        DeferredHolder<Block, T> obj = registerBlock(name, block);
        //ITEMS.register(name, item.apply(obj));
        return obj;
    }
    static <T extends Block> DeferredHolder<Block, T> registerBlockWithoutBlockItem(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    public static void registerAllBlocks(IEventBus bus){
        CustomRenderedDoorBlockInit.invoke();
        WeatheringResistantSteelBlockInit.invoke();
        BLOCKS.register(bus);
    }

    private static void generateDrops(String name) {
        String basic = "{\n" +
                "  \"type\": \"minecraft:block\",\n" +
                "  \"pools\": [\n" +
                "    {\n" +
                "      \"bonus_rolls\": 1.0,\n" +
                "      \"conditions\": [\n" +
                "        {\n" +
                "          \"condition\": \"minecraft:survives_explosion\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"entries\": [\n" +
                "        {\n" +
                "          \"type\": \"minecraft:item\",\n" +
                "          \"name\": \"hexie:" + name + "\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"rolls\": 1.0\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        File file = new File("../src/generated/resources/data/" + Main.MOD_ID + "/loot_table/blocks/" + name + ".json");
        try {
            file.createNewFile();
            FileWriter writer = new FileWriter(file);
            writer.write(basic);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

