package willow.train.hexie.vehicle.bogey;

import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.bogey.BogeyStyleMigration;

/** One model/profile mapping shared by assembly and optional model adapters. */
public enum BuiltinBogeyModel {
    CW2("cw2", "hxstandardbogey", "create:small", BuiltinBogeyProfile.CW2),
    PK209P("pk209p", "hxstandardbogey", "hexie:pk209p", BuiltinBogeyProfile.PK209P),
    PK209P_NO_MOTOR("pk209p_no_motor", "hxstandardbogey", "hexie:pk209p_no_motor", BuiltinBogeyProfile.PK209P),
    PK209P_BACKWARD("pk209p_backward", "hxstandardbogey", "hexie:pk209p_backward", BuiltinBogeyProfile.PK209P),
    SW220K("sw220k", "hxstandardbogey", "hexie:sw220k", BuiltinBogeyProfile.SW220K),
    SW220K_BACKWARD("sw220k_backward", "hxstandardbogey", "hexie:sw220k_backward", BuiltinBogeyProfile.SW220K),
    SW160("sw160", "hxstandardbogey", "hexie:sw160", BuiltinBogeyProfile.SW160),
    DF11G("df11g", "loco", "create:small", BuiltinBogeyProfile.DF11G),
    DF11G_BACKWARD("df11g_backward", "loco", "hexie:backward", BuiltinBogeyProfile.DF11G),
    QJ("qj", "steambogey", "hexie:qj", BuiltinBogeyProfile.QJ),
    QJ_BACKWARD("qj_backward", "steambogey", "hexie:qj_backward", BuiltinBogeyProfile.QJ),
    DT206("dt206", "crhbogey", "hexie:dt206", BuiltinBogeyProfile.DT206),
    SKTB200("sktb200", "crhbogey", "hexie:sktb200", BuiltinBogeyProfile.SKTB200),
    CRH1A_MOTOR("crh1a_motor", "crhbogey", "hexie:crh1a_motor", BuiltinBogeyProfile.CRH1A_MOTOR),
    CRH1A_TRAILER("crh1a_trailer", "crhbogey", "hexie:crh1a_trailer", BuiltinBogeyProfile.CRH1A_TRAILER);

    private final ResourceLocation id, style, size;
    private final BuiltinBogeyProfile profile;

    BuiltinBogeyModel(String id, String style, String size, BuiltinBogeyProfile profile) {
        this.id = ResourceLocation.fromNamespaceAndPath("hexie", id);
        this.style = ResourceLocation.fromNamespaceAndPath("hexie", style);
        this.size = ResourceLocation.parse(size);
        this.profile = profile;
    }

    public ResourceLocation id() { return id; }
    public ResourceLocation style() { return style; }
    public ResourceLocation size() { return size; }
    public BuiltinBogeyProfile profile() { return profile; }

    public static BuiltinBogeyModel find(ResourceLocation style, ResourceLocation size) {
        style = BogeyStyleMigration.normalizeStyle(style, size);
        for (var model : values()) if (model.style.equals(style) && model.size.equals(size)) return model;
        return null;
    }
}
