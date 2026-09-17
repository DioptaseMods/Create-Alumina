package net.nerfashton.create_alumina.compat.jei;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class ChemicalSpeciesIngredientHelper implements IIngredientHelper<ChemicalSpecies> {
    @Override
    public IIngredientType<ChemicalSpecies> getIngredientType() {
        return ChemicalSpeciesIngredientType.CHEMICAL_SPECIES;
    }

    @Override
    public String getDisplayName(ChemicalSpecies ingredient) {
        ResourceLocation key = ModChemicals.REGISTRY.getKey(ingredient);
        String fallback = key != null ? humanizePath(key.getPath()) + " (" + ingredient.symbol() + ")" : ingredient.symbol();
        if (key != null) {
            return Component.translatableWithFallback("chemical_species." + key.getNamespace() + "." + key.getPath(), fallback).getString();
        }
        return fallback;
    }

    @Override
    public Object getUid(ChemicalSpecies ingredient, UidContext context) {
        ResourceLocation key = ModChemicals.REGISTRY.getKey(ingredient);
        return key != null ? key : ingredient.symbol();
    }

    @SuppressWarnings("removal")
    @Override
    public String getUniqueId(ChemicalSpecies ingredient, UidContext context) {
        return getUid(ingredient, context).toString();
    }

    @Override
    public Object getGroupingUid(ChemicalSpecies ingredient) {
        return ChemicalSpecies.class;
    }

    @Override
    public ResourceLocation getResourceLocation(ChemicalSpecies ingredient) {
        ResourceLocation key = ModChemicals.REGISTRY.getKey(ingredient);
        return key != null ? key : ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "species_" + ingredient.symbol().toLowerCase());
    }

    @Override
    public ChemicalSpecies copyIngredient(ChemicalSpecies ingredient) {
        return ingredient;
    }

    @Override
    public ChemicalSpecies normalizeIngredient(ChemicalSpecies ingredient) {
        return ingredient;
    }

    @Override
    public Iterable<Integer> getColors(ChemicalSpecies ingredient) {
        return Collections.singletonList(ingredient.colorHex());
    }

    @Override
    public ItemStack getCheatItemStack(ChemicalSpecies ingredient) {
        return ItemStack.EMPTY;
    }

    @Override
    public String getErrorInfo(@Nullable ChemicalSpecies ingredient) {
        if (ingredient == null) {
            return "ChemicalSpecies: null";
        }
        ResourceLocation key = ModChemicals.REGISTRY.getKey(ingredient);
        return "ChemicalSpecies: " + ingredient.symbol() + " (charge=" + ingredient.charge() + ", id=" + key + ")";
    }

    private static String humanizePath(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.equalsIgnoreCase("iii")) {
                sb.append("(III) ");
            } else if (part.equalsIgnoreCase("ii")) {
                sb.append("(II) ");
            } else if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
