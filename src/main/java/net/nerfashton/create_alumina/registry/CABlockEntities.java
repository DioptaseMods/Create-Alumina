package net.nerfashton.create_alumina.registry;

import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatBlockEntity;

import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatRenderer;

import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class CABlockEntities {
    public static final BlockEntityEntry<ChemicalVatBlockEntity> CHEMICAL_VAT_BE = REGISTRATE
            .blockEntity("chemical_vat", ChemicalVatBlockEntity::new)
            .validBlock(CABlocks.CHEMICAL_VAT_BLOCK)
            .renderer(() -> ChemicalVatRenderer::new)
            .register();

    public static void init() {}
}
