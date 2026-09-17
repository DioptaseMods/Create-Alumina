package net.nerfashton.create_alumina.registry;

import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemistry_item.ChemicalFlaskItem;

import java.util.Map;
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

                // Add pre-filled testing flasks right after the empty Chemical Flask
                if (item.get() == CAItems.CHEMICAL_FLASK.get()) {
                    // Distilled Water
                    event.accept(ChemicalFlaskItem.createFlask(
                            CAFluids.DISTILLED_WATER.get(),
                            Map.of(),
                            298.15f
                    ), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

                    // 1.0 M Hydrochloric Acid (HCl)
                    event.accept(ChemicalFlaskItem.createFlask(
                            CAFluids.DISTILLED_WATER.get(),
                            Map.of(
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydrogen"), 1.0f,
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "chloride"), 1.0f
                            ),
                            298.15f
                    ), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

                    // 1.0 M Nitric Acid (HNO3)
                    event.accept(ChemicalFlaskItem.createFlask(
                            CAFluids.DISTILLED_WATER.get(),
                            Map.of(
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydrogen"), 1.0f,
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "nitrate"), 1.0f
                            ),
                            298.15f
                    ), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

                    // 6.0 M Nitric Acid (HNO3)
                    event.accept(ChemicalFlaskItem.createFlask(
                            CAFluids.DISTILLED_WATER.get(),
                            Map.of(
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydrogen"), 6.0f,
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "nitrate"), 6.0f
                            ),
                            298.15f
                    ), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

                    // 1.0 M Sodium Hydroxide (NaOH)
                    event.accept(ChemicalFlaskItem.createFlask(
                            CAFluids.DISTILLED_WATER.get(),
                            Map.of(
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "sodium"), 1.0f,
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydroxide"), 1.0f
                            ),
                            298.15f
                    ), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

                    // 1.0 M Sulfuric Acid (H2SO4)
                    event.accept(ChemicalFlaskItem.createFlask(
                            CAFluids.DISTILLED_WATER.get(),
                            Map.of(
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydrogen"), 2.0f,
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "sulfate"), 1.0f
                            ),
                            298.15f
                    ), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

                    // 1.0 M Copper(II) Sulfate (CuSO4)
                    event.accept(ChemicalFlaskItem.createFlask(
                            CAFluids.DISTILLED_WATER.get(),
                            Map.of(
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "copper"), 1.0f,
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "sulfate"), 1.0f
                            ),
                            298.15f
                    ), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

                    // 1.0 M Iron(III) Chloride (FeCl3)
                    event.accept(ChemicalFlaskItem.createFlask(
                            CAFluids.DISTILLED_WATER.get(),
                            Map.of(
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "iron_iii"), 1.0f,
                                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "chloride"), 3.0f
                            ),
                            298.15f
                    ), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
                }
            }
        }
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
