package net.nerfashton.create_alumina.content.mixin;

import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.nerfashton.alumina.core.fluid.BlendingFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidTankBlockEntity.class, remap = false)
public abstract class CreateFluidTankMixin {

    @Inject(method = "handlerForCapability", at = @At("RETURN"), cancellable = true, remap = false)
    private void alumina$blendChemicals(CallbackInfoReturnable<IFluidHandler> cir) {
        if ((Object) this instanceof CreativeFluidTankBlockEntity) {
            return;
        }
        IFluidHandler handler = cir.getReturnValue();
        if (handler != null && !(handler instanceof BlendingFluidHandler)) {
            cir.setReturnValue(new BlendingFluidHandler(handler));
        }
    }
}
