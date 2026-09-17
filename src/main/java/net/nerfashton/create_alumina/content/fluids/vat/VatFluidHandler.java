package net.nerfashton.create_alumina.content.fluids.vat;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.nerfashton.create_alumina.api.fluid.BlendingFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Combined dual-tank fluid handler for ChemicalVatBlockEntity:
 * - Tank 0: Primary electrolyte/solvent bath (accepts external insertions and extractions, blends solutions).
 * - Tank 1: Electrolysis output tank (CANNOT accept external insertions; can only be drained by pipes/buckets).
 *
 * Bottom extraction (Direction.DOWN) prioritizes draining the dense electrolysis product from the output tank.
 */
public class VatFluidHandler implements IFluidHandler {

    private final BlendingFluidHandler mainHandler;
    private final SmartFluidTank outputTank;
    @Nullable
    private final Direction side;

    public VatFluidHandler(BlendingFluidHandler mainHandler, SmartFluidTank outputTank, @Nullable Direction side) {
        this.mainHandler = mainHandler;
        this.outputTank = outputTank;
        this.side = side;
    }

    @Override
    public int getTanks() {
        return 2;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        if (tank == 0) return mainHandler.getFluidInTank(0);
        if (tank == 1) return outputTank.getFluid();
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank == 0) return mainHandler.getTankCapacity(0);
        if (tank == 1) return outputTank.getCapacity();
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        if (tank == 0) return mainHandler.isFluidValid(0, stack);
        // The output tank can ONLY be filled internally by electrolysis
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        // External fluid pipes and buckets can only fill the main electrolyte tank
        return mainHandler.fill(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;

        // If requested fluid matches the output tank, drain from output tank
        FluidStack outFluid = outputTank.getFluid();
        if (!outFluid.isEmpty() && FluidStack.isSameFluidSameComponents(outFluid, resource)) {
            return outputTank.drain(resource.getAmount(), action);
        }

        // Otherwise drain from main tank
        return mainHandler.drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;

        // When draining from the bottom (e.g. tap hole), prioritize the dense electrolysis output
        if (side == Direction.DOWN && !outputTank.isEmpty()) {
            return outputTank.drain(maxDrain, action);
        }

        // If main tank has fluid, drain from main tank
        if (!mainHandler.getFluidInTank(0).isEmpty()) {
            return mainHandler.drain(maxDrain, action);
        }

        // Fallback to output tank if main tank is empty
        return outputTank.drain(maxDrain, action);
    }
}
