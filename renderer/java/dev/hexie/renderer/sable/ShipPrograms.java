package dev.hexie.renderer.sable;

import dev.hexie.renderer.mc.ForwardLight;
import java.util.Set;
import java.util.regex.*;

public final class ShipPrograms {
    private static final Set<String> LAYERS=Set.of("rendertype_solid","rendertype_cutout","rendertype_cutout_mipped","rendertype_translucent");
    public static String patch(String source,String name,boolean vertex) {
        if (!LAYERS.contains(name.replaceFirst("^minecraft:","")) || source.contains("hexieTint")) return source;
        source=source.replaceFirst("void\\s+main\\s*\\(\\s*(?:void)?\\s*\\)","void main()");
        if (vertex) return source.replace("void main()","out vec3 hexiePos, hexieTint;\nvoid main()")
                .replaceFirst("gl_Position\\s*=","hexiePos = Position + ChunkOffset; hexieTint = Color.rgb;\n    gl_Position =");
        var fog=Pattern.compile("fragColor\\s*=\\s*linear_fog\\s*\\(").matcher(source);
        if (!fog.find()) return source;
        return ForwardLight.fragment(source,fog.group(),"texture(Sampler0,texCoord0).rgb*hexieTint*ColorModulator.rgb",
                "hexieRotation*cross(dFdx(hexiePos),dFdy(hexiePos))");
    }
}
