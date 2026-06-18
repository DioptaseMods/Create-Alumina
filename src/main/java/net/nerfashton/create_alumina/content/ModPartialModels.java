package net.nerfashton.create_alumina.content;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import net.nerfashton.create_alumina.CreateAlumina;

import static com.simibubi.create.foundation.block.connected.CTSpriteShifter.getCT;

public class ModPartialModels {
    public static final CTSpriteShiftEntry STAINLESS_STEEL_CASING = omni("stainless_steel_casing");
    public static final CTSpriteShiftEntry HASTELLOY_CASING = omni("hastelloy_casing");

    public static final PartialModel GAS_BURNER = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "block/gas_burner"));

    public static CTSpriteShiftEntry omni(String name) {
        return getCT(AllCTTypes.OMNIDIRECTIONAL,
                ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "block/" + name),
                ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "block/" + name + "_connected"));
    }
}
