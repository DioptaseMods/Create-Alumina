package net.nerfashton.create_alumina.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;

import java.util.ArrayList;
import java.util.List;

public class ChemicalSpeciesIngredientRenderer implements IIngredientRenderer<ChemicalSpecies> {
    private static final ResourceLocation ION_BASE =
            ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "textures/item/ion_base.png");

    @Override
    public void render(GuiGraphics guiGraphics, ChemicalSpecies ingredient) {
        if (ingredient == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Render tinted ion base sprite (16x16)
        int color = ingredient.colorHex();
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a <= 0.01f) a = 1.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        guiGraphics.setColor(r, g, b, a);
        guiGraphics.blit(ION_BASE, 0, 0, 0f, 0f, 16, 16, 16, 16);
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 2. Render charge indicator using normal-sized white superscript font (e.g. ⁺, ⁻, ²⁻, ³⁺)
        String chargeStr = formatChargeSuperscript(ingredient.charge());
        if (!chargeStr.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(0, 0, 200);

            int textWidth = font.width(chargeStr);
            int x = 16 - textWidth;
            int y = 0;

            guiGraphics.drawString(font, chargeStr, x, y, 0xFFFFFFFF, true);
            pose.popPose();
        }
    }

    public static String formatChargeSuperscript(int charge) {
        if (charge == 0) return "";
        int abs = Math.abs(charge);
        String sign = charge > 0 ? "⁺" : "⁻";
        if (abs == 1) {
            return sign;
        }
        String num = switch (abs) {
            case 2 -> "²";
            case 3 -> "³";
            case 4 -> "⁴";
            case 5 -> "⁵";
            case 6 -> "⁶";
            case 7 -> "⁷";
            default -> String.valueOf(abs);
        };
        return num + sign;
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, ChemicalSpecies ingredient, TooltipFlag tooltipFlag) {
        tooltip.addAll(getTooltip(ingredient, tooltipFlag));
    }

    @Override
    public List<Component> getTooltip(ChemicalSpecies ingredient, TooltipFlag tooltipFlag) {
        List<Component> tooltip = new ArrayList<>();
        if (ingredient == null) return tooltip;

        ResourceLocation key = ModChemicals.REGISTRY.getKey(ingredient);
        String humanName = key != null ? humanizePath(key.getPath()) : ingredient.symbol();
        Component nameComponent = key != null
                ? Component.translatableWithFallback("chemical_species." + key.getNamespace() + "." + key.getPath(), humanName)
                : Component.literal(humanName);

        // Title: Ion Name (Symbol)
        tooltip.add(Component.empty()
                .append(nameComponent)
                .append(Component.literal(" (" + ingredient.symbol() + ")").withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.WHITE));

        // Charge
        int charge = ingredient.charge();
        String chargeDisplay = formatChargeSuperscript(charge);
        String chargeSigned = (charge > 0 ? "+" : "") + charge;
        String chargeFull = chargeDisplay.isEmpty() ? "0" : chargeDisplay + " (" + chargeSigned + ")";
        ChatFormatting chargeColor = charge > 0 ? ChatFormatting.RED : (charge < 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY);
        tooltip.add(Component.translatable("jei.create_alumina.chemical_species.charge")
                .append(": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(chargeFull).withStyle(chargeColor)));

        // Standard Reduction Potential E°
        tooltip.add(Component.translatable("jei.create_alumina.chemical_species.potential")
                .append(": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%+.2f V", ingredient.standardPotential())).withStyle(ChatFormatting.YELLOW)));

        // Molar Conductivity
        tooltip.add(Component.translatable("jei.create_alumina.chemical_species.conductivity")
                .append(": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.1f mS·m²/mol", ingredient.molarConductivity())).withStyle(ChatFormatting.GREEN)));

        // Registry ID on advanced tooltips (F3 + H)
        if (tooltipFlag.isAdvanced() && key != null) {
            tooltip.add(Component.literal(key.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }

        return tooltip;
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
