package net.nerfashton.create_alumina.registry;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.nerfashton.create_alumina.content.block.VatWindowBlock;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatBlock;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatGenerator;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatModel;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatItem;

import static com.simibubi.create.foundation.data.CreateRegistrate.casingConnectivity;
import static com.simibubi.create.foundation.data.CreateRegistrate.connectedTextures;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class CABlocks {

    public static final BlockEntry<Block> STAINLESS_STEEL_BLOCK = REGISTRATE.block("stainless_steel_block", Block::new)
            .properties(properties -> BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK))
            .simpleItem()
            .register();

    public static final BlockEntry<ChemicalVatBlock> CHEMICAL_VAT_BLOCK = REGISTRATE.block("chemical_vat", ChemicalVatBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor((p1, p2, p3) -> true))
            .transform(pickaxeOnly())
            .blockstate(new ChemicalVatGenerator()::generate)
            .onRegister(CreateRegistrate.blockModel(() -> ChemicalVatModel::standard))
            .item(ChemicalVatItem::new)
            .model(AssetLookup.customBlockItemModel("_", "block_single"))
            .build()
            .register();

    public static final BlockEntry<VatWindowBlock> VAT_WINDOW_BLOCK = REGISTRATE.block("vat_window", VatWindowBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .transform(pickaxeOnly())
            .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(), AssetLookup.partialBaseModel(ctx, prov)))
            .simpleItem()
            .register();

    public static final BlockEntry<CasingBlock> STAINLESS_STEEL_CASING = REGISTRATE.block("stainless_steel_casing", CasingBlock::new)
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry()))
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .onRegister(connectedTextures(() -> new EncasedCTBehaviour(CAPartialModels.STAINLESS_STEEL_CASING)))
            .onRegister(casingConnectivity((block, cc) -> cc.makeCasing(block, CAPartialModels.STAINLESS_STEEL_CASING)))
            .simpleItem()
            .register();

    public static final BlockEntry<CasingBlock> HASTELLOY_CASING = REGISTRATE.block("hastelloy_casing", CasingBlock::new)
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry()))
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .onRegister(connectedTextures(() -> new EncasedCTBehaviour(CAPartialModels.HASTELLOY_CASING)))
            .onRegister(casingConnectivity((block, cc) -> cc.makeCasing(block, CAPartialModels.HASTELLOY_CASING)))
            .simpleItem()
            .register();

    public static void init() {

    }
}
