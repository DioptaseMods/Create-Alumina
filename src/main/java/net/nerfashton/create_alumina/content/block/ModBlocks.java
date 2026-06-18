package net.nerfashton.create_alumina.content.block;

import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.MapColor;
import net.nerfashton.create_alumina.content.ModPartialModels;
import net.nerfashton.create_alumina.content.processing.gas_burner.GasBurnerBlock;

import static com.simibubi.create.foundation.data.CreateRegistrate.casingConnectivity;
import static com.simibubi.create.foundation.data.CreateRegistrate.connectedTextures;
import static com.simibubi.create.foundation.data.ModelGen.customItemModel;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class ModBlocks {

    public static final BlockEntry<GasBurnerBlock> GAS_BURNER =
            REGISTRATE.block("gas_burner", GasBurnerBlock::new)
                    .properties(p -> p.mapColor(MapColor.COLOR_BLACK)
                            .lightLevel(GasBurnerBlock::getLight))
                    .addLayer(() -> RenderType::cutoutMipped)
                    .loot((lt, block) -> lt.add(block, GasBurnerBlock.buildLootTable()))
                    //.blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry(), AssetLookup.partialBaseModel(ctx, prov)))
                    .item().transform(customItemModel())
                    .register();
    public static final BlockEntry<CasingBlock> STAINLESS_STEEL_CASING = REGISTRATE.block("stainless_steel_casing", CasingBlock::new)
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry()))
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .onRegister(connectedTextures(() -> new EncasedCTBehaviour(ModPartialModels.STAINLESS_STEEL_CASING)))
            .onRegister(casingConnectivity((block, cc) -> cc.makeCasing(block, ModPartialModels.STAINLESS_STEEL_CASING)))
            .simpleItem()
            .register();
    public static final BlockEntry<CasingBlock> HASTELLOY_CASING = REGISTRATE.block("hastelloy_casing", CasingBlock::new)
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry()))
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .onRegister(connectedTextures(() -> new EncasedCTBehaviour(ModPartialModels.HASTELLOY_CASING)))
            .onRegister(casingConnectivity((block, cc) -> cc.makeCasing(block, ModPartialModels.HASTELLOY_CASING)))
            .simpleItem()
            .register();

    public static void init() {

    }
}
