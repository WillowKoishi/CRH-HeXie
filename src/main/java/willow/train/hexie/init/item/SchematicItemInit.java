package willow.train.hexie.init.item;

import com.simibubi.create.content.schematics.SchematicItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import willow.train.hexie.Main;

public class SchematicItemInit {

    public static final ItemEntry<SchematicItem> SCHEMATIC_25B = Main.registrate().item("hx_schematic_25b",SchematicItem::new)
            .properties(p -> p.stacksTo(1))
            .model((c, p) -> p.generated(c, Main.asResource("item/schematic/schematic_25b")))
            .register();

    public static final ItemEntry<SchematicItem> SCHEMATIC_25G = Main.registrate().item("hx_schematic_25g", SchematicItem::new)
            .properties(p -> p.stacksTo(1))
            .model((c, p) -> p.generated(c, Main.asResource("item/schematic/schematic_25g")))
            .register();

    public static final ItemEntry<SchematicItem> SCHEMATIC_25K = Main.registrate().item("hx_schematic_25k", SchematicItem::new)
            .properties(p -> p.stacksTo(1))
            .model((c, p) -> p.generated(c, Main.asResource("item/schematic/schematic_25k")))
            .register();

    public static final ItemEntry<SchematicItem> SCHEMATIC_25Z = Main.registrate().item("hx_schematic_25z", SchematicItem::new)
            .properties(p -> p.stacksTo(1))
            .model((c, p) -> p.generated(c, Main.asResource("item/schematic/schematic_25z")))
            .register();

    public static final ItemEntry<SchematicItem> SCHEMATIC_25T = Main.registrate().item("hx_schematic_25t", SchematicItem::new)
            .properties(p -> p.stacksTo(1))
            .model((c, p) -> p.generated(c, Main.asResource("item/schematic/schematic_25t")))
            .register();
}
