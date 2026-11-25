package dev.hexie.renderer.iris;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shadows.ShadowRenderer;
import dev.hexie.renderer.mc.ClientRenderState;
import dev.hexie.renderer.mc.ViewPassOwnership.Pass;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

public final class IrisFrames {
    @FunctionalInterface public interface ShadowDraw { void draw(Object view,Pass pass,Camera camera,Matrix4f matrix); }
    public static ShadowDraw geometry;
    public static java.util.function.BiConsumer<Object,Boolean> expectShadow;
    private static String lastKey;
    public static boolean sample() {
        boolean active=IrisApi.getInstance().isShaderPackInUse();
        if (!active) { lastKey=null; ClientRenderState.pack="off"; return false; }
        String name=Iris.getCurrentPackName();
        String world=net.minecraft.client.Minecraft.getInstance().level==null?"world0":
                switch(net.minecraft.client.Minecraft.getInstance().level.dimension().location().getPath()) {
                    case "the_nether" -> "world-1"; case "the_end" -> "world1"; default -> "world0";
                };
        String key=name+"/"+world;
        if (!key.equals(lastKey)) {
            lastKey=key;
            ClientRenderState.pack=describe(Iris.getShaderpacksDirectory(),name,world);
        }
        return true;
    }
    public static void invalidate() { lastKey=null; }
    public static boolean inShadow() { return ShadowRenderer.ACTIVE; }
    public static Matrix4f shadowProjection() { return ShadowRenderer.PROJECTION; }
    public static String describe(Path directory,String name,String world) {
        if (!Set.of("ComplementaryReimagined_r5.9.3","ComplementaryReimagined_r5.9.3.zip").contains(name))
            return "未知光影包 "+name+"，使用標準幾何路徑，點光由光影包處理";
        Path pack=directory.resolve(name);
        List<String> programs=new ArrayList<>();
        try (ZipFile zip=Files.isDirectory(pack)?null:new ZipFile(pack.toFile())) {
            for (String program:List.of("clrwl_gbuffers","clrwl_gbuffers_translucent","clrwl_shadow")) {
                String path="shaders/"+world+"/"+program;
                boolean present=true;
                for (String ext:List.of(".vsh",".fsh"))
                    present &= zip==null?Files.isRegularFile(pack.resolve(path+ext)):zip.getEntry(path+ext)!=null;
                if (present) programs.add(program);
            }
        } catch (IOException e) { return name+" | standard geometry | pack sources unavailable"; }
        return name+" | standard geometry | "+programs;
    }
}
