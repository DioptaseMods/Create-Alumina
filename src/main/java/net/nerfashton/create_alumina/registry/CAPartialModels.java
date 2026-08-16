package net.nerfashton.create_alumina.registry;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTType;
import net.minecraft.resources.ResourceLocation;
import net.nerfashton.create_alumina.CreateAlumina;

import static com.simibubi.create.foundation.block.connected.CTSpriteShifter.getCT;

public class CAPartialModels {
    public static final CTSpriteShiftEntry STAINLESS_STEEL_CASING = omni("stainless_steel_casing");
    public static final CTSpriteShiftEntry HASTELLOY_CASING = omni("hastelloy_casing");

    public static final CTSpriteShiftEntry CHEMICAL_VAT = getCT(AllCTTypes.RECTANGLE, "chemical_vat"),
            CHEMICAL_VAT_TOP = getCT(AllCTTypes.RECTANGLE, "chemical_vat_top"),
            CHEMICAL_VAT_INNER = getCT(AllCTTypes.RECTANGLE, "chemical_vat_inner");

    private static CTSpriteShiftEntry omni(String name) {
        return getCT(AllCTTypes.OMNIDIRECTIONAL, name);
    }

    private static CTSpriteShiftEntry horizontal(String name) {
        return getCT(AllCTTypes.HORIZONTAL, name);
    }

    private static CTSpriteShiftEntry getCT(CTType type, String blockTextureName, String connectedTextureName) {
        return com.simibubi.create.foundation.block.connected.CTSpriteShifter.getCT(type,
                ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "block/" + blockTextureName),
                ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "block/" + connectedTextureName + "_connected"));
    }

    private static CTSpriteShiftEntry getCT(CTType type, String blockTextureName) {
        return getCT(type, blockTextureName, blockTextureName);
    }
}
