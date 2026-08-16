package net.nerfashton.create_alumina.core;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatBlockEntity;
import net.nerfashton.create_alumina.registry.CABlockEntities;

public class ModCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CABlockEntities.CHEMICAL_VAT_BE.get(),
                (be, context) -> be.getFluidHandler()
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CABlockEntities.CHEMICAL_VAT_BE.get(),
                (be, context) -> be.getItemHandler()
        );
    }
}
