package net.nerfashton.create_alumina.api.chemical.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemical.ChemicalComposition;

import java.util.function.Supplier;

/**
 * Registry for NeoForge Data Components used by Alumina.
 */
public class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateAlumina.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChemicalComposition>> CHEMICAL_COMPOSITION =
            DATA_COMPONENTS.registerComponentType(
                    "chemical_composition",
                    builder -> builder
                            .persistent(ChemicalComposition.CODEC)
                            .networkSynchronized(ChemicalComposition.STREAM_CODEC)
            );

    public static final Supplier<DataComponentType<SimpleFluidContent>> FLUID_CONTENT =
            DATA_COMPONENTS.register("flask_fluid", () ->
                    DataComponentType.<SimpleFluidContent>builder()
                            .persistent(SimpleFluidContent.CODEC)
                            .networkSynchronized(SimpleFluidContent.STREAM_CODEC)
                            .build()
            );

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
