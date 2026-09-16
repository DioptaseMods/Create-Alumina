package net.nerfashton.create_alumina.api.chemistry_item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.SolubleItem;
import net.nerfashton.create_alumina.api.chemical.SoluteData;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * An item representing a crystalline chemical salt powder.
 * Tinted dynamically using "salt_powder.png" based on its ion composition.
 */
public class SolubleSaltItem extends Item implements SolubleItem {

    private final SoluteData defaultData;

    public SolubleSaltItem(Properties properties, SoluteData defaultData) {
        super(properties);
        this.defaultData = defaultData;
    }

    @Override
    public @Nullable SoluteData getSoluteData(ItemStack stack) {
        if (stack.has(ModDataComponents.SOLUTE_DATA.get())) {
            return stack.get(ModDataComponents.SOLUTE_DATA.get());
        }
        return defaultData;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        SoluteData data = getSoluteData(stack);
        if (data != null) {
            tooltipComponents.add(Component.literal("Soluble Salt").withStyle(ChatFormatting.GOLD));

            tooltipComponents.add(Component.literal("Ksp: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(formatKsp(data.ksp())).withStyle(ChatFormatting.AQUA)));

            if (Math.abs(data.heatOfDissolution()) > 0.01f) {
                boolean endo = data.heatOfDissolution() > 0;
                tooltipComponents.add(Component.literal("ΔH sol: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%+.1f kJ/mol", data.heatOfDissolution()))
                                .withStyle(endo ? ChatFormatting.RED : ChatFormatting.BLUE))
                        .append(Component.literal(endo ? " (Endothermic)" : " (Exothermic)").withStyle(ChatFormatting.DARK_GRAY)));
            }

            tooltipComponents.add(Component.literal("Ion Composition:").withStyle(ChatFormatting.GRAY));
            for (Map.Entry<ResourceLocation, Float> entry : data.ions().entrySet()) {
                ChemicalSpecies species = ModChemicals.REGISTRY.get(entry.getKey());
                String symbol = species != null ? species.symbol() : entry.getKey().getPath();
                tooltipComponents.add(Component.literal(" • ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(symbol).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" × " + (entry.getValue() % 1 == 0 ? String.valueOf(entry.getValue().intValue()) : entry.getValue()))
                                .withStyle(ChatFormatting.YELLOW)));
            }
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    private static String formatKsp(double ksp) {
        if (ksp >= 0.01 && ksp <= 1000.0) {
            return String.format("%.2f", ksp);
        }
        return String.format("%.2e", ksp);
    }

    /**
     * Blends the base color of constituent ions weighted by their stoichiometry.
     * Produces the dynamic ARGB color tint for "salt_powder.png".
     */
    public static int getColorForComposition(Map<ResourceLocation, Float> ions) {
        if (ions == null || ions.isEmpty()) {
            return 0xFFFFFFFF;
        }

        float totalWeight = 0.0f;
        float totalR = 0.0f;
        float totalG = 0.0f;
        float totalB = 0.0f;

        for (Map.Entry<ResourceLocation, Float> entry : ions.entrySet()) {
            ChemicalSpecies species = ModChemicals.REGISTRY.get(entry.getKey());
            if (species != null) {
                float weight = entry.getValue();
                int hex = species.colorHex();
                float r = (hex >> 16) & 0xFF;
                float g = (hex >> 8) & 0xFF;
                float b = hex & 0xFF;

                totalR += r * weight;
                totalG += g * weight;
                totalB += b * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight <= 0.0f) {
            return 0xFFFFFFFF;
        }

        int finalR = Math.clamp(Math.round(totalR / totalWeight), 0, 255);
        int finalG = Math.clamp(Math.round(totalG / totalWeight), 0, 255);
        int finalB = Math.clamp(Math.round(totalB / totalWeight), 0, 255);

        return (0xFF << 24) | (finalR << 16) | (finalG << 8) | finalB;
    }
}
