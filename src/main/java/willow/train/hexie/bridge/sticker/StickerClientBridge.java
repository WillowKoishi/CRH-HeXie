package willow.train.hexie.bridge.sticker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.core.sticker.StickerEntry;
import willow.train.hexie.core.sticker.StickerPlaneMath;

/** Client-side tool interactions, mode selector UI, and free-camera window editor hooks. */
public interface StickerClientBridge {
    void openModeScreen();

    boolean handleRightClickBlock(BlockPos pos, Direction facing, Vec3 hitLocation);

    boolean handleBrushRemove(BlockPos pos, Direction facing);

    boolean isFreeCamActive();

    StickerEntry activePreviewOrEditing();

    StickerPlaneMath.PrecisionSnap activePrecisionSnap();

    /** 0=NONE, 1=UP, 2=DOWN, 3=LEFT, 4=RIGHT */
    int activeGizmoAxis();
}
