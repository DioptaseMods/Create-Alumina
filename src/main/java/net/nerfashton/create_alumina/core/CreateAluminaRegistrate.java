package net.nerfashton.create_alumina.core;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.VirtualFluidBuilder;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.builders.FluidBuilder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.api.fluid.SolventFluid;
import net.nerfashton.create_alumina.api.fluid.SolventFluidType;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.function.Supplier;

public class CreateAluminaRegistrate extends CreateRegistrate {
    public static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    public static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    protected CreateAluminaRegistrate(String modid) {
        super(modid);
    }

    public CreateAluminaRegistrate setTooltipModifierFactory(@Nullable Function<Item, TooltipModifier> factory) {
        currentTooltipModifierFactory = factory;
        return this;
    }

    public static CreateAluminaRegistrate create(String modid) {
        return new CreateAluminaRegistrate(modid);
    }

    /* Chemical Species */

    public RegistryEntry<ChemicalSpecies, ChemicalSpecies> chemicalSpecies(String name, String symbol, int charge, float standardPotential, float molarConductivity, int colorHex) {
        return this.simple(name, ModChemicals.CHEMICAL_SPECIES_KEY, () -> new ChemicalSpecies(symbol, charge, standardPotential, colorHex, molarConductivity));
    }

    public RegistryEntry<ChemicalSpecies, ChemicalSpecies> chemicalSpecies(String name, Supplier<ChemicalSpecies> speciesSupplier) {
        return this.simple(name, ModChemicals.CHEMICAL_SPECIES_KEY, speciesSupplier::get);
    }

    /* Solvent Fluids */

    public FluidBuilder<SolventFluid, CreateAluminaRegistrate> solventFluid(String name) {
        return solventFluid(name, WATER_STILL, WATER_FLOW, SolventFluidType::new);
    }

    public FluidBuilder<SolventFluid, CreateAluminaRegistrate> solventFluid(String name, FluidBuilder.FluidTypeFactory typeFactory) {
        return solventFluid(name, WATER_STILL, WATER_FLOW, typeFactory);
    }

    public FluidBuilder<SolventFluid, CreateAluminaRegistrate> solventFluid(String name, ResourceLocation texture) {
        return solventFluid(name, texture, texture, SolventFluidType::new);
    }

    public FluidBuilder<SolventFluid, CreateAluminaRegistrate> solventFluid(String name, ResourceLocation texture, FluidBuilder.FluidTypeFactory typeFactory) {
        return solventFluid(name, texture, texture, typeFactory);
    }

    public FluidBuilder<SolventFluid, CreateAluminaRegistrate> solventFluid(String name, ResourceLocation still, ResourceLocation flow) {
        return solventFluid(name, still, flow, SolventFluidType::new);
    }

    public FluidBuilder<SolventFluid, CreateAluminaRegistrate> solventFluid(String name, ResourceLocation still, ResourceLocation flow,
                                                                           FluidBuilder.FluidTypeFactory typeFactory) {
        return entry(name, c -> new VirtualFluidBuilder<>(this, this, name, c, still, flow, typeFactory,
                SolventFluid::createSource, SolventFluid::createFlowing));
    }
}
