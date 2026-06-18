package net.nerfashton.create_alumina.content.item;

import com.simibubi.create.foundation.data.recipe.CommonMetal;
import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class ModItems {
    public static final ItemEntry<Item> STAINLESS_STEEL_INGOT = ingotEntry("stainless_steel_ingot");


    private static ItemEntry<Item> ingotEntry(String name) {
        return REGISTRATE.item(name, Item::new)
                .tag()
                .register();
    }

    public static void init() {
    }
}
