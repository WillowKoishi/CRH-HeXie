package willow.train.hexie.ui.sticker;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sticker.StickerClientBridge;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.sticker.*;

/**
 * Client-side controller wiring the custom-sticker tool interactions, scroll scale/rotate,
 * Ctrl 16x16 precision placement, free-camera viewport override, and white-brush removal.
 */
public final class StickerClientController implements StickerClientBridge {
    private static final StickerClientController INSTANCE = new StickerClientController();

    private StickerClientController() {}

    public static void install() {
        HexieBridges.Client.registerStickerClient(INSTANCE);
        NeoForge.EVENT_BUS.addListener(StickerClientController::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(StickerClientController::onComputeCameraAngles);
    }

    public static boolean isHoldingCustomSticker(Player player) {
        if (player == null) return false;
        return isCustomSticker(player.getMainHandItem()) || isCustomSticker(player.getOffhandItem());
    }

    public static boolean isCustomSticker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return HexieConstants.MOD_ID.equals(key.getNamespace()) && "custom_sticker".equals(key.getPath());
    }

    @Override
    public void openModeScreen() {
        Minecraft.getInstance().setScreen(new StickerModeScreen());
    }

    @Override
    public boolean handleRightClickBlock(BlockPos pos, Direction facing, Vec3 hitLocation) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        if (StickerToolState.mode() == StickerMode.WINDOW) {
            // If this block face already has saved window data -> enter Free-Camera Edit Mode!
            var existing = HexieBridges.sticker().findExact(mc.level, pos, facing);
            if (existing.isPresent() && existing.get().mode() == StickerMode.WINDOW) {
                StickerToolState.startFreeCam(existing.get());
                mc.setScreen(new StickerEditorScreen());
                return true;
            }
            // Otherwise confirm placement: centered by default, or 16x16 snapped when holding Ctrl!
            WindowStickerParams base = StickerToolState.windowParams();
            WindowStickerParams placed;
            if (Screen.hasControlDown()) {
                var snap = StickerPlaneMath.snap16(pos, facing, hitLocation, base.width(), base.height());
                placed = base.withOffset(snap.snappedOffsetU(), snap.snappedOffsetV());
            } else {
                placed = base.withOffset(0f, 0f);
            }
            StickerEntry entry = new StickerEntry(
                    UUID.randomUUID(), pos, facing, StickerMode.WINDOW,
                    placed, DecalStickerParams.DEFAULT, 1L);
            HexieBridges.sync().sendStickerUpsert(entry);
            return true;
        } else {
            // DECAL mode: right-click confirms upload & placement!
            if (!StickerToolState.hasLocalImage()) {
                mc.player.displayClientMessage(Component.literal("請先對空氣右鍵選擇本地圖片"), true);
                return true;
            }
            DecalStickerParams base = StickerToolState.decalParams();
            DecalStickerParams placed;
            if (Screen.hasControlDown()) {
                var snap = StickerPlaneMath.snap16(pos, facing, hitLocation, base.width(), base.height());
                placed = base.withOffset(snap.snappedOffsetU(), snap.snappedOffsetV());
            } else {
                placed = base.withOffset(0f, 0f);
            }
            // Upload PNG bytes ONLY on right-click confirm, then upsert decal entry
            HexieBridges.sync().sendStickerImageUpload(StickerToolState.localImageBytes());
            StickerEntry entry = new StickerEntry(
                    UUID.randomUUID(), pos, facing, StickerMode.DECAL,
                    WindowStickerParams.DEFAULT, placed, 1L);
            HexieBridges.sync().sendStickerUpsert(entry);
            return true;
        }
    }

    @Override
    public boolean handleBrushRemove(BlockPos pos, Direction facing) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        boolean hasAny = HexieBridges.sticker().findExact(mc.level, pos, facing).isPresent()
                || HexieBridges.sticker().entriesAffecting(mc.level, pos).stream().anyMatch(e -> e.facing() == facing);
        if (!hasAny) return false;
        HexieBridges.sync().sendStickerRemove(pos, facing);
        return true;
    }

    @Override
    public boolean isFreeCamActive() {
        return StickerToolState.isFreeCamActive();
    }

    @Override
    public StickerEntry activePreviewOrEditing() {
        if (StickerToolState.isFreeCamActive()) return StickerToolState.editingEntry();
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !isHoldingCustomSticker(mc.player)) return null;
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)
                || bhr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        BlockPos pos = bhr.getBlockPos();
        Direction face = bhr.getDirection();
        if (StickerToolState.mode() == StickerMode.WINDOW) {
            WindowStickerParams base = StickerToolState.windowParams();
            WindowStickerParams p = Screen.hasControlDown()
                    ? base.withOffset(
                            StickerPlaneMath.snap16(pos, face, bhr.getLocation(), base.width(), base.height()).snappedOffsetU(),
                            StickerPlaneMath.snap16(pos, face, bhr.getLocation(), base.width(), base.height()).snappedOffsetV())
                    : base.withOffset(0f, 0f);
            return new StickerEntry(new UUID(0, 0), pos, face, StickerMode.WINDOW, p, DecalStickerParams.DEFAULT, 0L);
        } else {
            if (!StickerToolState.hasLocalImage()) return null;
            DecalStickerParams base = StickerToolState.decalParams();
            DecalStickerParams p = Screen.hasControlDown()
                    ? base.withOffset(
                            StickerPlaneMath.snap16(pos, face, bhr.getLocation(), base.width(), base.height()).snappedOffsetU(),
                            StickerPlaneMath.snap16(pos, face, bhr.getLocation(), base.width(), base.height()).snappedOffsetV())
                    : base.withOffset(0f, 0f);
            return new StickerEntry(new UUID(0, 0), pos, face, StickerMode.DECAL, WindowStickerParams.DEFAULT, p, 0L);
        }
    }

    @Override
    public StickerPlaneMath.PrecisionSnap activePrecisionSnap() {
        if (StickerToolState.isFreeCamActive() || !Screen.hasControlDown()) return null;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !isHoldingCustomSticker(mc.player)) return null;
        if (StickerToolState.mode() != StickerMode.WINDOW) return null;
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)
                || bhr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        var w = StickerToolState.windowParams();
        return StickerPlaneMath.snap16(bhr.getBlockPos(), bhr.getDirection(), bhr.getLocation(), w.width(), w.height());
    }

    @Override
    public int activeGizmoAxis() {
        var axis = StickerToolState.draggingAxis() != StickerToolState.GizmoAxis.NONE
                ? StickerToolState.draggingAxis() : StickerToolState.hoveredAxis();
        return axis.ordinal();
    }

    /** Decal mode: plain scroll adjusts scale; Ctrl+scroll adjusts rotation angle. */
    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (!isHoldingCustomSticker(mc.player) || StickerToolState.mode() != StickerMode.DECAL) return;
        double dy = event.getScrollDeltaY();
        if (dy == 0) return;
        if (Screen.hasControlDown()) {
            StickerToolState.rotateDecal(dy);
        } else {
            StickerToolState.scaleDecal(dy);
        }
        event.setCanceled(true);
    }

    /** Overrides camera position and orientation smoothly during Free-Camera Edit Mode. */
    private static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!StickerToolState.isFreeCamActive()) return;
        event.setYaw(StickerToolState.camYaw());
        event.setPitch(StickerToolState.camPitch());
        event.setRoll(0f);
        event.getCamera().setPosition(StickerToolState.camPos());
    }
}
