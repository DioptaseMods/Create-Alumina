package net.nerfashton.create_alumina.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.registry.CAItems;

import java.util.List;

public class SaltCompositionRecipeCategory implements IRecipeCategory<SaltCompositionRecipe> {
    public static final RecipeType<SaltCompositionRecipe> RECIPE_TYPE =
            RecipeType.create(CreateAlumina.MOD_ID, "salt_composition", SaltCompositionRecipe.class);

    private final IGuiHelper guiHelper;
    private final IDrawable icon;

    public static final int WIDTH = 162;
    public static final int HEIGHT = 68;

    public SaltCompositionRecipeCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(CAItems.SODIUM_CHLORIDE.get()));
    }

    @Override
    public RecipeType<SaltCompositionRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.create_alumina.salt_composition");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    private int getSlotSpacing(int count) {
        return count <= 3 ? 24 : Math.max(18, (WIDTH - 56 - 18 - 8) / Math.max(1, count - 1));
    }

    private int getSaltBoxX(int count) {
        int spacing = getSlotSpacing(count);
        int ionsWidth = 18 + Math.max(0, count - 1) * spacing;
        int totalWidth = 56 + ionsWidth;
        return Math.max(2, (WIDTH - totalWidth) / 2);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SaltCompositionRecipe recipe, IFocusGroup focuses) {
        List<SaltCompositionRecipe.IonConstituent> constituents = recipe.constituents();
        int count = constituents.size();
        int saltBoxX = getSaltBoxX(count);
        int slotBoxY = 10;

        // Centered input salt item slot
        builder.addSlot(RecipeIngredientRole.INPUT, saltBoxX + 1, slotBoxY + 1)
                .addItemStack(recipe.saltItem())
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addRichTooltipCallback((recipeSlotView, tooltip) -> {
                    tooltip.add(Component.translatable("jei.create_alumina.salt_composition.salt_input")
                            .withStyle(ChatFormatting.GRAY));
                });

        // Centered output ion ingredients
        int slotSpacing = getSlotSpacing(count);
        int firstIonBoxX = saltBoxX + 56;

        for (int i = 0; i < count; i++) {
            SaltCompositionRecipe.IonConstituent constituent = constituents.get(i);
            int ionBoxX = firstIonBoxX + i * slotSpacing;

            builder.addSlot(RecipeIngredientRole.OUTPUT, ionBoxX + 1, slotBoxY + 1)
                    .addIngredient(ChemicalSpeciesIngredientType.CHEMICAL_SPECIES, constituent.species())
                    .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                    .addRichTooltipCallback((recipeSlotView, tooltip) -> {
                        tooltip.add(Component.literal("Stoichiometry: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(constituent.formatAmount() + " mol / mol salt")
                                        .withStyle(ChatFormatting.GOLD)));
                    });
        }
    }

    @Override
    public void draw(SaltCompositionRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        List<SaltCompositionRecipe.IonConstituent> constituents = recipe.constituents();
        int count = constituents.size();
        int saltBoxX = getSaltBoxX(count);
        int slotSpacing = getSlotSpacing(count);
        int firstIonBoxX = saltBoxX + 56;

        // 1. Centered Reaction Arrow & H2O Solvent Label
        int arrowStartX = saltBoxX + 18 + 9;
        int arrowEndX = arrowStartX + 20;

        // Arrow line
        guiGraphics.fill(arrowStartX, 19, arrowEndX, 20, 0xFF888888);
        // Arrow head
        guiGraphics.fill(arrowEndX - 2, 17, arrowEndX, 19, 0xFF888888);
        guiGraphics.fill(arrowEndX - 2, 20, arrowEndX, 22, 0xFF888888);
        guiGraphics.fill(arrowEndX, 18, arrowEndX + 2, 21, 0xFF888888);

        // "H₂O" solvent label centered above arrow
        int arrowCenterX = arrowStartX + 10;
        int h2oWidth = font.width("H₂O");
        guiGraphics.drawString(font, "H₂O", arrowCenterX - (h2oWidth / 2), 8, 0xFF64B5F6, false);

        // 2. Stoichiometry badges centered below each output ion box
        for (int i = 0; i < count; i++) {
            SaltCompositionRecipe.IonConstituent constituent = constituents.get(i);
            int ionBoxX = firstIonBoxX + i * slotSpacing;
            String text = "×" + constituent.formatAmount();
            int w = font.width(text);
            guiGraphics.drawString(font, text, ionBoxX + 9 - (w / 2), 31, 0xFFFFD54F, true);
        }

        // 3. Centered Ksp info
        double ksp = recipe.soluteData().ksp();
        String kspStr = formatKsp(ksp);
        String kspLabel = "Ksp: ";
        int totalKspWidth = font.width(kspLabel) + font.width(kspStr);
        int kspStartX = (WIDTH - totalKspWidth) / 2;
        guiGraphics.drawString(font, kspLabel, kspStartX, 44, 0xFF888888, false);
        guiGraphics.drawString(font, kspStr, kspStartX + font.width(kspLabel), 44, 0xFF4DD0E1, false);

        // 4. Centered Heat of Dissolution (ΔH)
        float dh = recipe.soluteData().heatOfDissolution();
        if (Math.abs(dh) > 0.001f) {
            boolean endo = dh > 0;
            String dhLabel = "ΔH: ";
            String dhVal = String.format("%+.1f kJ/mol", dh);
            String dhType = endo ? " (Endothermic)" : " (Exothermic)";
            int dhColor = endo ? 0xFFEF5350 : 0xFF42A5F5;
            int totalDhWidth = font.width(dhLabel) + font.width(dhVal) + font.width(dhType);
            int dhStartX = (WIDTH - totalDhWidth) / 2;

            guiGraphics.drawString(font, dhLabel, dhStartX, 54, 0xFF888888, false);
            int valX = dhStartX + font.width(dhLabel);
            guiGraphics.drawString(font, dhVal, valX, 54, dhColor, false);
            guiGraphics.drawString(font, dhType, valX + font.width(dhVal), 54, 0xFF757575, false);
        } else {
            String athermic = "ΔH: Athermic (0.0 kJ/mol)";
            guiGraphics.drawString(font, athermic, (WIDTH - font.width(athermic)) / 2, 54, 0xFF757575, false);
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, SaltCompositionRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= 10 && mouseX <= (WIDTH - 10)) {
            if (mouseY >= 43 && mouseY <= 52) {
                tooltip.add(Component.translatable("jei.create_alumina.salt_composition.ksp_tooltip.title")
                        .withStyle(ChatFormatting.AQUA));
                tooltip.add(Component.translatable("jei.create_alumina.salt_composition.ksp_tooltip.desc")
                        .withStyle(ChatFormatting.GRAY));
            } else if (mouseY >= 53 && mouseY <= 65) {
                tooltip.add(Component.translatable("jei.create_alumina.salt_composition.dh_tooltip.title")
                        .withStyle(ChatFormatting.GOLD));
                boolean endo = recipe.soluteData().heatOfDissolution() > 0;
                tooltip.add(Component.translatable(endo
                        ? "jei.create_alumina.salt_composition.dh_tooltip.endo"
                        : "jei.create_alumina.salt_composition.dh_tooltip.exo")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static String formatKsp(double ksp) {
        if (ksp >= 1000.0) {
            return String.format("%.1e", ksp);
        } else if (ksp >= 0.01) {
            return String.format("%.2f", ksp);
        } else {
            return String.format("%.2e", ksp);
        }
    }
}
