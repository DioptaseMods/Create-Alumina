package net.nerfashton.create_alumina.registry;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class CAItems {
    public static final ItemEntry<Item>
            STAINLESS_STEEL_INGOT = ingotEntry("stainless_steel_ingot"),
            HASTELLOY_INGOT = ingotEntry("hastelloy_ingot");


    private static ItemEntry<Item> ingotEntry(String name) {
        return REGISTRATE.item(name, Item::new)
                .tag()
                .register();
    }

    public static void init() {
    }
}
