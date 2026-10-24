package willow.train.hexie.util.enums;

import net.minecraft.world.level.block.Block;
import java.util.function.Supplier;

import willow.train.hexie.blocks.carriage.OrdinaryTrainPanelBlock;
import willow.train.hexie.blocks.carriage.OrdinaryTrainUpperPanelBlock;
import willow.train.hexie.blocks.carriage.TopPanelSlabBlock;
import willow.train.hexie.init.block.BlockInit;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class PanelTypesUtil {

    private final Map<Supplier<OrdinaryTrainPanelBlock>, PanelTypes> panelMap = new HashMap<>();
    private final Map<Supplier<OrdinaryTrainUpperPanelBlock>, UpperPanelTypes> upperPanelMap =
            new HashMap<>();
    private final Map<Supplier<TopPanelSlabBlock>, SlabTypes> slabMap = new HashMap<>();
    private final Map<PanelTypes, Supplier<OrdinaryTrainPanelBlock>> panelsByType = new EnumMap<>(PanelTypes.class);
    private final Map<UpperPanelTypes, Supplier<OrdinaryTrainUpperPanelBlock>> upperPanelsByType =
            new EnumMap<>(UpperPanelTypes.class);
    private final Map<SlabTypes, Supplier<TopPanelSlabBlock>> slabsByType = new EnumMap<>(SlabTypes.class);
    private Map<Block, PanelTypes> panelsByBlock;
    private Map<Block, UpperPanelTypes> upperPanelsByBlock;
    private Map<Block, SlabTypes> slabsByBlock;

    public PanelTypesUtil() {
        registerPanel();
        registerUpperPanel();
        registerSlabPanel();
        panelMap.forEach((block, type) -> panelsByType.putIfAbsent(type, block));
        upperPanelMap.forEach((block, type) -> upperPanelsByType.putIfAbsent(type, block));
        slabMap.forEach((block, type) -> slabsByType.putIfAbsent(type, block));
    }

    private void registerPanel() {
        panelMap.put(BlockInit.PANEL_25B_ORIGINAL_BOTTOM, PanelTypes.P25B);
        panelMap.put(BlockInit.PANEL_25G_ORIGINAL_BOTTOM, PanelTypes.P25G);
        panelMap.put(BlockInit.PANEL_25Z_ORIGINAL_BOTTOM_SIDE, PanelTypes.P25ZA);
        panelMap.put(BlockInit.PANEL_25Z_ORIGINAL_BOTTOM_LINE, PanelTypes.P25ZB);
        panelMap.put(BlockInit.PANEL_25Z_ORIGINAL_BOTTOM_SYMBOL, PanelTypes.P25ZC);
        panelMap.put(BlockInit.PANEL_25K_ORIGINAL_BOTTOM, PanelTypes.P25KA);
        panelMap.put(BlockInit.PANEL_25K_ORIGINAL_LINE, PanelTypes.P25KB);
        panelMap.put(BlockInit.PANEL_25K_ORIGINAL_SYMBOL, PanelTypes.P25KC);
        panelMap.put(BlockInit.PANEL_25T_ORIGINAL_BOTTOM, PanelTypes.P25TA);
        panelMap.put(BlockInit.PANEL_25T_ORIGINAL_BOTTOM_B, PanelTypes.P25TB);
        panelMap.put(BlockInit.PANEL_25B_MARSHALLED_SYMBOL, PanelTypes.M25B);
        panelMap.put(BlockInit.PANEL_25G_MARSHALLED_SYMBOL, PanelTypes.M25G);
        panelMap.put(BlockInit.PANEL_25K_MARSHALLED_SYMBOL, PanelTypes.M25KA);
        panelMap.put(BlockInit.PANEL_25_MARSHALLED_BOTTOM, PanelTypes.M25Z);
        panelMap.put(BlockInit.PANEL_25_MARSHALLED_BOTTOM_LINE, PanelTypes.M25T);
        panelMap.put(BlockInit.PANEL_CR200J_MARSHALLED_BOTTOM, PanelTypes.M25KB);
    }

    private void registerUpperPanel() {
        upperPanelMap.put(BlockInit.PANEL_25B_ORIGINAL_TOP, UpperPanelTypes.P25BA);
        upperPanelMap.put(BlockInit.PANEL_25B_ORIGINAL_TOP_B, UpperPanelTypes.P25BB);
        upperPanelMap.put(BlockInit.PANEL_25G_ORIGINAL_TOP, UpperPanelTypes.P25G);
        upperPanelMap.put(BlockInit.PANEL_25Z_ORIGINAL_TOP, UpperPanelTypes.P25Z);
        upperPanelMap.put(BlockInit.PANEL_25K_ORIGINAL_TOP, UpperPanelTypes.P25K);
        upperPanelMap.put(BlockInit.PANEL_25T_ORIGINAL_TOP, UpperPanelTypes.P25TA);
        upperPanelMap.put(BlockInit.PANEL_25T_ORIGINAL_TOP_B, UpperPanelTypes.P25TB);
        upperPanelMap.put(BlockInit.PANEL_XL25T_ORIGINAL_TOP, UpperPanelTypes.P25TC);
        upperPanelMap.put(BlockInit.PANEL_25_MARSHALLED_TOP, UpperPanelTypes.M25A);
        upperPanelMap.put(BlockInit.PANEL_25_MARSHALLED_TOP_B, UpperPanelTypes.M25B);
        upperPanelMap.put(BlockInit.PANEL_CR200J_MARSHALLED_TOP, UpperPanelTypes.CR200J);
    }

    private void registerSlabPanel() {
        slabMap.put(BlockInit.SLAB_25_MARSHALLED_TOP, SlabTypes.S25M);
        slabMap.put(BlockInit.SLAB_25B_ORIGINAL_TOP, SlabTypes.S25B);
        slabMap.put(BlockInit.SLAB_25Z_ORIGINAL_TOP, SlabTypes.S25Z);
        slabMap.put(BlockInit.SLAB_25G_ORIGINAL_TOP, SlabTypes.S25G);
        slabMap.put(BlockInit.SLAB_25T_ORIGINAL_TOP, SlabTypes.S25T);
        slabMap.put(BlockInit.SLAB_25K_ORIGINAL_TOP, SlabTypes.S25K);
        slabMap.put(BlockInit.SLAB_CR200J_MARSHALLED_TOP, SlabTypes.CR200J);
        slabMap.put(BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25B, SlabTypes.D25B);
        slabMap.put(BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25G, SlabTypes.D25G);
        slabMap.put(BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25K, SlabTypes.D25K);
        slabMap.put(BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25T, SlabTypes.D25T);
        slabMap.put(BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25Z, SlabTypes.D25Z);
    }

    private Map<Block, PanelTypes> panelsByBlock() {
        if (panelsByBlock == null) {
            Map<Block, PanelTypes> index = new IdentityHashMap<>();
            panelMap.forEach((supplier, type) -> index.put(supplier.get(), type));
            panelsByBlock = index;
        }
        return panelsByBlock;
    }

    private Map<Block, UpperPanelTypes> upperPanelsByBlock() {
        if (upperPanelsByBlock == null) {
            Map<Block, UpperPanelTypes> index = new IdentityHashMap<>();
            upperPanelMap.forEach((supplier, type) -> index.put(supplier.get(), type));
            upperPanelsByBlock = index;
        }
        return upperPanelsByBlock;
    }

    private Map<Block, SlabTypes> slabsByBlock() {
        if (slabsByBlock == null) {
            Map<Block, SlabTypes> index = new IdentityHashMap<>();
            slabMap.forEach((supplier, type) -> index.put(supplier.get(), type));
            slabsByBlock = index;
        }
        return slabsByBlock;
    }

    public PanelTypes matchBottomBlock2Panel(Block block) {
        return panelsByBlock().getOrDefault(block, PanelTypes.P25B);
    }

    public Block matchBottomPanel2Block(PanelTypes panel) {
        return panelsByType.getOrDefault(panel, BlockInit.PANEL_25B_ORIGINAL_BOTTOM).get();
    }

    public boolean canTransformPanel(Block block) {
        return panelsByBlock().containsKey(block);
    }

    public boolean canTransformUpperPanel(Block block) {
        return upperPanelsByBlock().containsKey(block);
    }

    public boolean canTransformSlab(Block block) {
        return slabsByBlock().containsKey(block);
    }

    public UpperPanelTypes matchUpperBlock2Panel(Block block) {
        return upperPanelsByBlock().getOrDefault(block, UpperPanelTypes.P25BA);
    }

    public Block matchUpperPanel2Block(UpperPanelTypes panel) {
        return upperPanelsByType.getOrDefault(panel, BlockInit.PANEL_25B_ORIGINAL_TOP).get();
    }

    public SlabTypes matchSlabBlock2Panel(Block block) {
        return slabsByBlock().getOrDefault(block, SlabTypes.S25M);
    }

    public Block matchSlabPanel2Block(SlabTypes panel) {
        return slabsByType.getOrDefault(panel, BlockInit.SLAB_25_MARSHALLED_TOP).get();
    }
}
