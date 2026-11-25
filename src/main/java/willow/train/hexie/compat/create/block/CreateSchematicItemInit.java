package willow.train.hexie.compat.create.block;

import com.simibubi.create.content.schematics.SchematicItem;
import java.util.List;
import willow.train.hexie.compat.create.CreateBootstrap;
import willow.train.hexie.core.HexieConstants;

public final class CreateSchematicItemInit {
    private CreateSchematicItemInit() {}

    static {
        var registrate = CreateBootstrap.registrate();
        for (String series : List.of("25b", "25g", "25k", "25z", "25t")) {
            registrate.item("hx_schematic_" + series, SchematicItem::new)
                    .properties(p -> p.stacksTo(1))
                    .model((c, p) -> p.generated(c, HexieConstants.asResource("item/schematic/schematic_" + series)))
                    .register();
        }
    }

    public static void register() {}
}
