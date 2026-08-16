package net.nerfashton.create_alumina.content.fluids.vat;

import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public class ChemicalVatGenerator extends SpecialBlockStateGen {

    private String prefix;

    public ChemicalVatGenerator() {
        this("");
    }

    public ChemicalVatGenerator(String prefix) {
        this.prefix = prefix;
    }

    @Override
    protected int getXRotation(BlockState state) {
        return 0;
    }

    @Override
    protected int getYRotation(BlockState state) {
        return 0;
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov, BlockState state) {
        Boolean top = state.getValue(ChemicalVatBlock.TOP);
        Boolean bottom = state.getValue(ChemicalVatBlock.BOTTOM);

        String shapeName = "middle";
        if (top && bottom)
            shapeName = "single";
        else if (top)
            shapeName = "top";
        else if (bottom)
            shapeName = "bottom";

        String modelName = shapeName;

        if (!prefix.isEmpty())
            return prov.models()
                    .withExistingParent(prefix + modelName, prov.modLoc("block/chemical_vat/block_" + modelName))
                    .texture("0", prov.modLoc("block/" + prefix + "casing"))
                    .texture("1", prov.modLoc("block/" + prefix + "chemical_vat"))
                    .texture("particle", prov.modLoc("block/" + prefix + "chemical_vat"));

        return AssetLookup.partialBaseModel(ctx, prov, modelName);
    }
}
