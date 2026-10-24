package willow.train.hexie.client.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import willow.train.hexie.init.bogeys.BogeyStylesInit;
import willow.train.hexie.client.util.GuiImageCache;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class OverlayManager {
    private static final List<HexieOverlay> OVERLAYS = new ArrayList<>();

    private OverlayManager() {}

    private static void append(HexieOverlay overlay) {
        OVERLAYS.removeIf(existing -> existing.getDescriptionName().equals(overlay.getDescriptionName()));
        OVERLAYS.add(overlay);
    }

    private static boolean contains(String descriptionName) {
        for (HexieOverlay overlay : OVERLAYS) {
            if (overlay.getDescriptionName().equals(descriptionName)) return true;
        }
        return false;
    }

    private static void render(GuiGraphics pose, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        for (HexieOverlay overlay : OVERLAYS) {
            overlay.render(mc, pose, partialTick);
        }
    }

    private static void clear() {
        OVERLAYS.clear();
    }

    public static class OverlayRenderEvent{
        @SubscribeEvent
        public void onRenderOverlay(RenderGuiEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                OverlayManager.clear();
                return;
            }

            // Resources are available once the client is rendering an in-world GUI.
            // Load the bogey preview images before the first overlay looks them up.
            GuiImageCache.loadPendingImages();

            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            showBogeyOverlayIfTargeted(mc, partialTick);
            OverlayManager.render(event.getGuiGraphics(), partialTick);
            OVERLAYS.removeIf(o -> !o.canSurvive());
        }

        private void showBogeyOverlayIfTargeted(Minecraft mc, float partialTick) {
            // The existing overlay checks its own target and close animation below.
            // Avoid another raycast and a throwaway overlay on every rendered frame.
            if (OverlayManager.contains(BogeyCheckOverlay.DESCRIPTION_NAME)) return;

            HitResult hit = mc.player.pick(10.0D, partialTick, false);
            if (hit.getType() != HitResult.Type.BLOCK) return;

            BlockEntity blockEntity = mc.level.getBlockEntity(((BlockHitResult) hit).getBlockPos());
            if (!(blockEntity instanceof AbstractBogeyBlockEntity bogey)) return;
            if (!bogey.getStyle().equals(BogeyStylesInit.HXSTANDARDBOGEY)
                    && !bogey.getStyle().equals(BogeyStylesInit.DF11GBOGEY)) return;

            BlockPos position = bogey.getBlockPos();
            if (mc.player.getEyePosition().distanceToSqr(
                    position.getX(), position.getY(), position.getZ()) >= 100) return;

            OverlayManager.append(new BogeyCheckOverlay(bogey));
        }
    }
}
