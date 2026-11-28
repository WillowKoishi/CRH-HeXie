package dev.hexie.renderer.flywheel;

import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.ForwardLight;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Patches Flywheel 1.0 fragment shaders and wraps Flywheel draws with HeXie's forward raytraced lighting
 * so moving contraptions, assembled trains, sliding doors, and bogeys match fixed shells & terrain identically. */
public final class FlywheelPrograms {
    private static final Logger LOGGER = LoggerFactory.getLogger("HeXie/Renderer/Flywheel");
    private static final float[] IDENTITY_3X3 = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private static boolean loggedPatch;
    private static final Pattern LIGHT_BLOCK = Pattern.compile(
            "vec4\\s+lightColor\\s*=\\s*vec4\\s*\\(\\s*1\\.\\s*\\)\\s*;\\s*"
                    + "if\\s*\\(\\s*flw_material\\.useLight\\s*\\)\\s*\\{\\s*"
                    + "lightColor\\s*=\\s*texture\\s*\\(\\s*flw_lightTex\\s*,\\s*clamp\\s*\\(\\s*flw_fragLight\\s*,\\s*0\\.5\\s*/\\s*16\\.0\\s*,\\s*15\\.5\\s*/\\s*16\\.0\\s*\\)\\s*\\)\\s*;\\s*"
                    + "color\\s*\\*=\\s*lightColor\\s*;\\s*\\}");

    private FlywheelPrograms() {}

    public static CharSequence patchFragment(CharSequence raw) {
        if (raw == null) return null;
        String source = raw.toString();
        String patched = patchFragment(source);
        return patched == source ? raw : patched;
    }

    public static String patchFragment(String source) {
        if (source == null || !source.contains("void _flw_main()") || source.contains("#define _FLW_DEPTH_RANGE")
                || source.contains("hexieDirect")) {
            return source;
        }
        int versionEnd = source.indexOf('\n');
        if (versionEnd < 0) return source;
        Matcher lightMatcher = LIGHT_BLOCK.matcher(source);
        if (!lightMatcher.find()) return source;
        if (!loggedPatch) {
            loggedPatch = true;
            LOGGER.info("Patched Flywheel fragment shader with HeXie per-pixel forward raytraced lighting");
        }

        String replacement = """
            vec4 lightColor = vec4(1.);
            if (flw_material.useLight) {
                if (hexieEnabled != 0) {
                    vec3 hexieAlb = color.rgb / max(diffuseFactor, 0.001);
                    vec2 hexieLight = clamp((flw_fragLight * 16.0 - 0.5) / 15.0, 0.0, 1.0);
                    float hexieNLen2 = dot(flw_vertexNormal, flw_vertexNormal);
                    vec3 hexieNrm = hexieNLen2 > 1e-8
                            ? (gl_FrontFacing ? flw_vertexNormal : -flw_vertexNormal) * inversesqrt(hexieNLen2)
                            : vec3(0.0, 1.0, 0.0);
                    vec3 hexieRelPos = flw_vertexPos.xyz - flw_cameraPos;
                    float hexieLocal = smoothstep(48.0, 36.0, length(hexieRelPos));
                    vec2 hexieUv = (vec2(hexieLight.x * mix(1.0, 0.0, hexieLocal), hexieLight.y) * 15.0 + 0.5) / 16.0;
                    lightColor = texture(flw_lightTex, hexieUv);
                    color.rgb = hexieShadeSurface(hexieRelPos, hexieAlb, hexieNrm, lightColor.rgb, hexieLight.y, 0.26);
                    color.a *= lightColor.a;
                } else {
                    lightColor = texture(flw_lightTex, clamp(flw_fragLight, 0.5 / 16.0, 15.5 / 16.0));
                    color *= lightColor;
                }
            }""";

        String patched = lightMatcher.replaceFirst(Matcher.quoteReplacement(replacement));
        return patched.replace("void _flw_main()", ForwardLight.FRAGMENT + "\nvoid _flw_main()");
    }

    public static void draw(Runnable original) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null
                || !mc.gameRenderer.getMainCamera().isInitialized()) {
            original.run();
            return;
        }
        var cp = mc.gameRenderer.getMainCamera().getPosition();
        ForwardLight.draw(new Vec(cp.x, cp.y, cp.z), IDENTITY_3X3, false, ForwardLight.TARGET_GEOMETRY, original);
    }
}
