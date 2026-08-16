package net.nerfashton.create_alumina.registry;

import net.neoforged.bus.api.IEventBus;

public class CACreativeTabs {
    /*public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateAlumina.MOD_ID);

    /*public static final Supplier<CreativeModeTab> ITEMS_TAB = CREATIVE_MODE_TAB.register("items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.REGISTERED_ELEMENTS.getFirst().get()))
                    .title(Component.translatable("creativetab.alumina.items_tab"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.RADON_GAS_TANK);
                        output.accept(ModItems.GAS_TANK);
                    }).build());

    public static final Supplier<CreativeModeTab> BLOCKS_TAB = CREATIVE_MODE_TAB.register("blocks_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.STAINLESS_STEEL_CASING.asItem()))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "items_tab"))
                    .title(Component.translatable("creativetab.alumina.blocks_tab"))
                    .displayItems((itemDisplayParameters, output) -> {
                        //output.accept(ModBlocks.HASTELLOY_CASING);
                        output.accept(ModBlocks.STAINLESS_STEEL_CASING.get());
                        output.accept(ModBlocks.GAS_BURNER.get());
                    }).build());*/

    public static void register(IEventBus eventBus) {
        //CREATIVE_MODE_TAB.register(eventBus);
    }
    public static void init() {

    }
}
