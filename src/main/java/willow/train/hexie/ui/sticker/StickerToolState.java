package willow.train.hexie.ui.sticker;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.sticker.*;

/** Client-side state for the unified custom-sticker tool and active free-camera edit session. */
public final class StickerToolState {
    private static final int MAX_DIMENSION = 1024;
    // Safely below NeoForge 1.21.1's 1 MiB C2S custom-payload packet limit
    private static final int MAX_BYTES = 768 * 1024;

    public enum GizmoAxis { NONE, UP, DOWN, LEFT, RIGHT }

    private static StickerMode mode = StickerMode.WINDOW;
    private static WindowStickerParams windowParams = WindowStickerParams.DEFAULT;
    private static DecalStickerParams decalParams = DecalStickerParams.DEFAULT;

    // Locally loaded image (kept client-only until user confirms placement with right-click!)
    private static byte[] localImageBytes;
    private static String localImageHash = "";
    private static String localImageName = "";
    private static float localImageAspect = 1.0f;

    // Free-camera window editing session
    private static boolean freeCamActive;
    private static StickerEntry editingEntry;
    private static Vec3 camPos = Vec3.ZERO;
    private static float camYaw;
    private static float camPitch;
    private static boolean cursorUnlocked = true;
    private static GizmoAxis hoveredAxis = GizmoAxis.NONE;
    private static GizmoAxis draggingAxis = GizmoAxis.NONE;

    private StickerToolState() {}

    public static StickerMode mode() { return mode; }
    public static void setMode(StickerMode next) { mode = next == null ? StickerMode.WINDOW : next; }

    public static WindowStickerParams windowParams() { return windowParams; }
    public static void setWindowParams(WindowStickerParams next) {
        if (next != null) windowParams = next;
    }

    public static DecalStickerParams decalParams() { return decalParams; }
    public static void setDecalParams(DecalStickerParams next) {
        if (next != null) decalParams = next;
    }

    public static byte[] localImageBytes() { return localImageBytes; }
    public static String localImageHash() { return localImageHash; }
    public static String localImageName() { return localImageName; }
    public static boolean hasLocalImage() { return localImageBytes != null && localImageBytes.length > 0 && !localImageHash.isEmpty(); }

    /** Loads a local image file strictly client-side (no server upload until right-click confirm). */
    public static String loadLocalImage(Path file) {
        try {
            BufferedImage src = ImageIO.read(file.toFile());
            if (src == null) return "無法解析圖片格式";
            int srcW = src.getWidth(), srcH = src.getHeight();
            if (srcW <= 0 || srcH <= 0) return "圖片尺寸無效";
            // Always normalize to TYPE_INT_ARGB and iteratively downscale if needed to stay under the C2S packet limit.
            int[] caps = {MAX_DIMENSION, 768, 512, 384, 256, 192};
            byte[] bytes = null;
            int w = srcW, h = srcH;
            for (int cap : caps) {
                double s = (srcW > cap || srcH > cap)
                        ? Math.min((double) cap / srcW, (double) cap / srcH)
                        : 1.0;
                int nw = Math.max(1, (int) Math.round(srcW * s));
                int nh = Math.max(1, (int) Math.round(srcH * s));
                BufferedImage argb = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = argb.createGraphics();
                g.drawImage(nw == srcW && nh == srcH ? src : src.getScaledInstance(nw, nh, Image.SCALE_SMOOTH), 0, 0, null);
                g.dispose();
                var baos = new ByteArrayOutputStream();
                ImageIO.write(argb, "png", baos);
                byte[] encoded = baos.toByteArray();
                if (encoded.length <= MAX_BYTES) {
                    bytes = encoded;
                    w = nw;
                    h = nh;
                    break;
                }
            }
            if (bytes == null) return "圖片壓縮後仍超過 768KB 上限";
            String hash = sha256(bytes);
            localImageBytes = bytes;
            localImageHash = hash;
            localImageName = file.getFileName().toString() + " (" + w + "x" + h + ")";
            localImageAspect = (float) w / (float) h;
            float baseH = 1.0f;
            float baseW = Math.max(DecalStickerParams.MIN_SIZE, Math.min(DecalStickerParams.MAX_SIZE, baseH * localImageAspect));
            decalParams = new DecalStickerParams(hash, baseW, baseH, decalParams.rotationDegrees(), 0f, 0f);
            HexieBridges.sticker().putClientImage(hash, bytes);
            return null;
        } catch (Exception e) {
            return "讀取失敗: " + e.getMessage();
        }
    }

    public static void scaleDecal(double scrollDelta) {
        float factor = scrollDelta > 0 ? 1.1f : (1f / 1.1f);
        float newH = Math.max(DecalStickerParams.MIN_SIZE, Math.min(DecalStickerParams.MAX_SIZE, decalParams.height() * factor));
        float newW = Math.max(DecalStickerParams.MIN_SIZE, Math.min(DecalStickerParams.MAX_SIZE, newH * localImageAspect));
        decalParams = new DecalStickerParams(decalParams.imageHash(), newW, newH,
                decalParams.rotationDegrees(), decalParams.offsetU(), decalParams.offsetV());
    }

    public static void rotateDecal(double scrollDelta) {
        float step = scrollDelta > 0 ? 5.0f : -5.0f;
        decalParams = decalParams.withRotation(decalParams.rotationDegrees() + step);
    }

    // --- Free-Camera Edit Session ---
    public static boolean isFreeCamActive() { return freeCamActive && editingEntry != null; }
    public static StickerEntry editingEntry() { return editingEntry; }
    public static Vec3 camPos() { return camPos; }
    public static float camYaw() { return camYaw; }
    public static float camPitch() { return camPitch; }
    public static boolean isCursorUnlocked() { return cursorUnlocked; }
    public static GizmoAxis hoveredAxis() { return hoveredAxis; }
    public static GizmoAxis draggingAxis() { return draggingAxis; }

    public static void startFreeCam(StickerEntry entry) {
        editingEntry = entry;
        freeCamActive = true;
        cursorUnlocked = true;
        hoveredAxis = GizmoAxis.NONE;
        draggingAxis = GizmoAxis.NONE;
        BlockPos pos = entry.anchorPos();
        Direction face = entry.facing();
        Vec3 target = StickerPlaneMath.unproject(pos, face, entry.window().offsetU(), entry.window().offsetV(), 0.0);
        Vec3 out = StickerPlaneMath.outward(face);
        camPos = target.add(out.scale(2.5));
        Vec3 look = target.subtract(camPos).normalize();
        camYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        camPitch = (float) Math.toDegrees(Math.asin(-Math.max(-1.0, Math.min(1.0, look.y))));
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null) cutter.setLiveOverride(editingEntry);
    }

    public static void updateEditingEntry(WindowStickerParams next) {
        if (editingEntry != null) {
            editingEntry = editingEntry.withWindow(next);
            windowParams = next;
            var cutter = HexieBridges.Client.stickerMeshCutter();
            if (cutter != null) cutter.setLiveOverride(editingEntry);
        }
    }

    public static void setCamPos(Vec3 pos) { camPos = pos; }
    public static void setCamAngles(float yaw, float pitch) {
        camYaw = yaw;
        camPitch = Math.max(-89f, Math.min(89f, pitch));
    }
    public static void setCursorUnlocked(boolean unlocked) { cursorUnlocked = unlocked; }
    public static void setHoveredAxis(GizmoAxis axis) { hoveredAxis = axis; }
    public static void setDraggingAxis(GizmoAxis axis) { draggingAxis = axis; }

    public static StickerEntry stopFreeCam() {
        StickerEntry finalEntry = editingEntry;
        freeCamActive = false;
        editingEntry = null;
        hoveredAxis = GizmoAxis.NONE;
        draggingAxis = GizmoAxis.NONE;
        var cutter = HexieBridges.Client.stickerMeshCutter();
        if (cutter != null) cutter.setLiveOverride(null);
        return finalEntry;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        var sb = new StringBuilder(64);
        for (byte b : d) sb.append(Character.forDigit((b >>> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }
}
