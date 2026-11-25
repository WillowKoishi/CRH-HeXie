package willow.train.hexie.world.sticker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Content-addressed PNG storage for uploaded custom decals.
 * Server persists under <world>/data/hexie_stickers/<sha256>.png; client maintains an in-memory cache.
 */
public final class StickerImageStore {
    public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024; // 2 MiB safety cap

    private static final Map<String, byte[]> SERVER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> CLIENT_CACHE = new ConcurrentHashMap<>();

    private StickerImageStore() {}

    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            var sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(Character.forDigit((b >>> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean validHash(String hash) {
        return hash != null && hash.length() == 64 && hash.matches("[0-9a-f]{64}");
    }

    private static Path dir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("hexie_stickers");
    }

    public static boolean hasServer(ServerLevel level, String hash) {
        if (!validHash(hash)) return false;
        if (SERVER_CACHE.containsKey(hash)) return true;
        return Files.isRegularFile(dir(level).resolve(hash + ".png"));
    }

    public static Optional<byte[]> loadServer(ServerLevel level, String hash) {
        if (!validHash(hash)) return Optional.empty();
        byte[] cached = SERVER_CACHE.get(hash);
        if (cached != null) return Optional.of(cached);
        Path file = dir(level).resolve(hash + ".png");
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > 0 && bytes.length <= MAX_IMAGE_BYTES) {
                SERVER_CACHE.put(hash, bytes);
                return Optional.of(bytes);
            }
        } catch (IOException ignored) {}
        return Optional.empty();
    }

    public static String saveServer(ServerLevel level, byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0 || pngBytes.length > MAX_IMAGE_BYTES)
            throw new IllegalArgumentException("Invalid decal image size");
        String hash = sha256Hex(pngBytes);
        byte[] copy = pngBytes.clone();
        SERVER_CACHE.put(hash, copy);
        try {
            Path folder = dir(level);
            Files.createDirectories(folder);
            Path file = folder.resolve(hash + ".png");
            if (!Files.exists(file)) Files.write(file, copy);
        } catch (IOException ignored) {}
        return hash;
    }

    public static Optional<byte[]> getClient(String hash) {
        if (!validHash(hash)) return Optional.empty();
        return Optional.ofNullable(CLIENT_CACHE.get(hash));
    }

    public static void putClient(String hash, byte[] pngBytes) {
        if (!validHash(hash) || pngBytes == null || pngBytes.length == 0 || pngBytes.length > MAX_IMAGE_BYTES) return;
        CLIENT_CACHE.put(hash, pngBytes.clone());
    }

    public static void clearClientCache() {
        CLIENT_CACHE.clear();
    }
}
