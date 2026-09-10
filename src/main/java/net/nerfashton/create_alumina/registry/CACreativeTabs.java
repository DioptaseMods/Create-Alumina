package net.nerfashton.create_alumina.registry;

import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nerfashton.create_alumina.CreateAlumina;

import java.util.function.Supplier;

import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class CACreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateAlumina.MOD_ID);

    public static final Supplier<CreativeModeTab> ITEMS_TAB = CREATIVE_MODE_TAB.register("items_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> CAItems.CHEMICAL_FLASK.asItem().getDefaultInstance())
                    .title(Component.translatable("creativetab.create_alumina.items_tab"))
                    .build());

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ITEMS_TAB.get()) {
            for (RegistryEntry<Item, ?> item : REGISTRATE.getAll(Registries.ITEM)) {
                event.accept(item.get(), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
            }
        }
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
