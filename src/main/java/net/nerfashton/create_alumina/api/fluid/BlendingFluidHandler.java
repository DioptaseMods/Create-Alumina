package net.nerfashton.create_alumina.api.fluid;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.nerfashton.create_alumina.api.chemical.ChemicalComposition;
import net.nerfashton.create_alumina.api.chemical.ChemistryAPI;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper for IFluidHandler that intercepts insertions of chemical solutions.
 * If the tank already contains a solution, it intercepts the standard rejection
 * and forces the fluids to mix properties (volume-weighted).
 */
public class BlendingFluidHandler implements IFluidHandler {

    private final IFluidHandler delegate;

    public BlendingFluidHandler(IFluidHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        if (ChemistryAPI.isSolution(stack)) {
            FluidStack inTank = delegate.getFluidInTank(tank);
            if (!inTank.isEmpty() && ChemistryAPI.isBaseSolvent(inTank.getFluid())) {
                return true;
            }
        }
        return delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !ChemistryAPI.isSolution(resource)) {
            return delegate.fill(resource, action);
        }

        int standardFill = delegate.fill(resource, FluidAction.SIMULATE);
        if (standardFill > 0 && standardFill == resource.getAmount()) {
            return delegate.fill(resource, action);
        }

        for (int i = 0; i < delegate.getTanks(); i++) {
            FluidStack inTank = delegate.getFluidInTank(i);

            if (!inTank.isEmpty() && ChemistryAPI.isBaseSolvent(inTank.getFluid()) && ChemistryAPI.isBaseSolvent(resource.getFluid()) && (resource.getFluid() == inTank.getFluid())) {
                int capacity = delegate.getTankCapacity(i);
                int space = capacity - inTank.getAmount();
                if (space <= 0) continue;

                int toFill = Math.min(space, resource.getAmount());

                if (action.execute()) {
                    FluidStack drained = delegate.drain(inTank.getAmount(), FluidAction.EXECUTE);

                    if (drained.isEmpty()) return 0;

                    int baseColor = 0xFFFFFFFF;
                    float baseTemp = 300f;
                    if (drained.getFluid().getFluidType() instanceof SolventFluidType solventType) {
                        baseColor = solventType.getTintColor();
                        baseTemp = solventType.getTemperature(drained);
                    }

                    ChemicalComposition currentComp = drained.getOrDefault(ModDataComponents.CHEMICAL_COMPOSITION.get(),
                            new ChemicalComposition(Map.of(), baseColor, baseTemp));
                    ChemicalComposition incomingComp = resource.getOrDefault(ModDataComponents.CHEMICAL_COMPOSITION.get(),
                            new ChemicalComposition(Map.of(), baseColor, baseTemp));

                    ChemicalComposition mixed = currentComp.mix(incomingComp, drained.getAmount(), toFill);

                    Map<ResourceLocation, Float> mixedSolutes = new HashMap<>(mixed.solutes());
                    float tempChange = ChemistryAPI.processReactions(mixedSolutes);
                    int newColor = ChemistryAPI.recalculateColor(mixedSolutes, baseColor);
                    mixed = new ChemicalComposition(mixedSolutes, newColor, mixed.temperature() + tempChange);

                    drained.grow(toFill);
                    drained.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), mixed);

                    delegate.fill(drained, FluidAction.EXECUTE);
                }

                return toFill;
            }
        }

        return delegate.fill(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        return delegate.drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        return delegate.drain(maxDrain, action);
    }
}
