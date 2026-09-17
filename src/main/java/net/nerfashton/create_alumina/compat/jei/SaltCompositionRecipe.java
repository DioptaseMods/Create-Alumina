package net.nerfashton.create_alumina.compat.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.SoluteData;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record SaltCompositionRecipe(ItemStack saltItem, SoluteData soluteData, List<IonConstituent> constituents) {

    public SaltCompositionRecipe(ItemStack saltItem, SoluteData soluteData) {
        this(saltItem, soluteData, createConstituents(soluteData));
    }

    private static List<IonConstituent> createConstituents(SoluteData data) {
        List<IonConstituent> list = new ArrayList<>();
        if (data != null && data.ions() != null) {
            for (Map.Entry<ResourceLocation, Float> entry : data.ions().entrySet()) {
                ResourceLocation id = entry.getKey();
                Float amount = entry.getValue();
                ChemicalSpecies species = ModChemicals.REGISTRY.get(id);
                if (species != null) {
                    list.add(new IonConstituent(species, id, amount != null ? amount : 1.0f));
                }
            }
        }
        return List.copyOf(list);
    }

    public record IonConstituent(ChemicalSpecies species, ResourceLocation id, float amount) {
        public String formatAmount() {
            if (amount == (long) amount) {
                return String.format("%d", (long) amount);
            }
            return String.format("%.1f", amount);
        }
    }
}
