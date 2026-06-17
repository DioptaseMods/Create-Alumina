package net.nerfashton.create_alumina.content.block.entity;

import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.content.block.ModBlocks;
import net.nerfashton.create_alumina.content.processing.gas_burner.GasBurnerBlockEntity;
import net.nerfashton.create_alumina.core.CreateAluminaRegistrate;

import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class ModBlockEntityTypes {

    public static final BlockEntityEntry<GasBurnerBlockEntity> GAS_BURNER = REGISTRATE
            .blockEntity("gas_burner", GasBurnerBlockEntity::new)
            //.validBlocks(ModBlocks.GAS_BURNER)
            .register();

    public static void init() {
    }
}
