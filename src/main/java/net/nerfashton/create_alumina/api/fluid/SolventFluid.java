package net.nerfashton.create_alumina.api.fluid;

import com.simibubi.create.content.fluids.VirtualFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.nerfashton.create_alumina.api.chemical.ChemicalComposition;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import org.joml.Vector3f;

import java.util.Map;
import java.util.function.Supplier;

public class SolventFluid extends VirtualFluid {

    public static SolventFluid createSource(Properties properties) {
        return new SolventFluid(properties, true);
    }

    public static SolventFluid createFlowing(Properties properties) {
        return new SolventFluid(properties, false);
    }

    public SolventFluid(Properties properties, boolean source) {
        super(properties, source);
    }

    @Override
    public Item getBucket() {
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(this);
        if (key != null) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getPath().replace("flowing_", "") + "_bucket"));
            if (item != Items.AIR) {
                return item;
            }
        }
        return super.getBucket();
    }

    public static FluidStack of(Fluid fluid, int amount) {
        FluidStack stack = new FluidStack(fluid, amount);
        int color = 0xFFFFFFFF;
        float temp = 300f;
        if (fluid.getFluidType() instanceof SolventFluidType solventType) {
            color = solventType.getTintColor();
            temp = solventType.getTemperature(stack);
        }
        stack.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), new ChemicalComposition(Map.of(), color, temp));
        return stack;
    }

    public static FluidStack of(Fluid fluid, int amount, ChemicalComposition composition) {
        FluidStack stack = new FluidStack(fluid, amount);
        setComposition(stack, composition);
        return stack;
    }

    public static FluidStack of(Supplier<? extends Fluid> fluid, int amount) {
        return of(fluid.get(), amount);
    }

    public static FluidStack of(Supplier<? extends Fluid> fluid, int amount, ChemicalComposition composition) {
        return of(fluid.get(), amount, composition);
    }

    public FluidStack of(int amount) {
        return of(getSource(), amount);
    }

    public FluidStack of(int amount, ChemicalComposition composition) {
        return of(getSource(), amount, composition);
    }

    public static FluidStack setComposition(FluidStack stack, ChemicalComposition composition) {
        if (composition == null) {
            stack.remove(ModDataComponents.CHEMICAL_COMPOSITION.get());
            return stack;
        }
        stack.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), composition);
        return stack;
    }

    public static ChemicalComposition getComposition(FluidStack stack) {
        int color = 0xFFFFFFFF;
        float temp = 300f;
        if (stack.getFluid().getFluidType() instanceof SolventFluidType solventType) {
            color = solventType.getTintColor();
            temp = solventType.getTemperature(stack);
        }
        return stack.getOrDefault(ModDataComponents.CHEMICAL_COMPOSITION.get(),
                new ChemicalComposition(Map.of(), color, temp));
    }

    public static class SolventFluidType extends net.nerfashton.create_alumina.api.fluid.SolventFluidType {
        public SolventFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
            super(properties, stillTexture, flowingTexture);
        }

        public SolventFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture,
                                int tintColor, Vector3f fogColor, float specificHeat,
                                float breakdownVoltage, float baseLineConductivity, Map<ResourceLocation, Float> breakdownProducts) {
            super(properties, stillTexture, flowingTexture, tintColor, fogColor, specificHeat, breakdownVoltage, baseLineConductivity, breakdownProducts);
        }
    }
}
