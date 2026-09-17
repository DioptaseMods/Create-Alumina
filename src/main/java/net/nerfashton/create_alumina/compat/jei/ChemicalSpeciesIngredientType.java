package net.nerfashton.create_alumina.compat.jei;

import mezz.jei.api.ingredients.IIngredientType;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;

public final class ChemicalSpeciesIngredientType implements IIngredientType<ChemicalSpecies> {
    public static final ChemicalSpeciesIngredientType CHEMICAL_SPECIES = new ChemicalSpeciesIngredientType();

    private ChemicalSpeciesIngredientType() {}

    @Override
    public Class<? extends ChemicalSpecies> getIngredientClass() {
        return ChemicalSpecies.class;
    }

    @Override
    public String getUid() {
        return "create_alumina:chemical_species";
    }
}
