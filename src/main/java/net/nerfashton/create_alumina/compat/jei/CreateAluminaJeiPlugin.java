package net.nerfashton.create_alumina.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.SolubleItem;
import net.nerfashton.create_alumina.api.chemical.SoluteData;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.registry.CABlocks;
import net.nerfashton.create_alumina.registry.CAItems;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class CreateAluminaJeiPlugin implements IModPlugin {
    public static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        List<ChemicalSpecies> speciesList = ModChemicals.REGISTRY.stream().toList();
        registration.register(
                ChemicalSpeciesIngredientType.CHEMICAL_SPECIES,
                speciesList,
                new ChemicalSpeciesIngredientHelper(),
                new ChemicalSpeciesIngredientRenderer(),
                ChemicalSpecies.CODEC
        );
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new SaltCompositionRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<SaltCompositionRecipe> recipes = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SolubleItem solubleItem) {
                ItemStack stack = new ItemStack(item);
                SoluteData data = solubleItem.getSoluteData(stack);
                if (data != null && data.ions() != null && !data.ions().isEmpty()) {
                    recipes.add(new SaltCompositionRecipe(stack, data));
                }
            }
        }
        registration.addRecipes(SaltCompositionRecipeCategory.RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(CABlocks.CHEMICAL_VAT_BLOCK.get()),
                SaltCompositionRecipeCategory.RECIPE_TYPE
        );
        registration.addRecipeCatalyst(
                new ItemStack(CAItems.CHEMICAL_FLASK.get()),
                SaltCompositionRecipeCategory.RECIPE_TYPE
        );
    }
}
