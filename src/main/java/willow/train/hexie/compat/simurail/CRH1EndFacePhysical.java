package willow.train.hexie.compat.simurail;

import com.crystaelix.simurail.api.coupler.CouplerType;
import net.minecraft.resources.ResourceLocation;

/** The physical backend uses the same distinct mating type as the carriage runtime. */
final class CRH1EndFacePhysical {
    static final CouplerType TYPE = new CouplerType(
            ResourceLocation.fromNamespaceAndPath("hexie", "crh1a_semipermanent"),
            ResourceLocation.fromNamespaceAndPath("hexie", "block/crh1a/endface/coupler"));
    private CRH1EndFacePhysical() {}
}
