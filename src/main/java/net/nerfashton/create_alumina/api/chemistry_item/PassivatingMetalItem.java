package net.nerfashton.create_alumina.api.chemistry_item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;

import java.util.List;
import java.util.Map;

/**
 * An item representing a passivating metal (such as Aluminum or Stainless Steel).
 * In ambient air and neutral environments, forms a protective passive oxide film
 * that strongly inhibits electrochemical reactions.
 *
 * The oxide layer is attacked and stripped (depassivated) by:
 * - Chloride ions (Cl⁻) via pitting corrosion
 * - Hydroxide ions (OH⁻) via caustic alkaline dissolution
 * - Strong acid (H⁺) via oxide dissolution
 * - Fluoride ions (F⁻) via complexation
 *
 * Strongly oxidizing acids (such as Nitrate / Nitric Acid) enhance and heal passivation.
 * Once depassivated, the metal reacts at its full spontaneous thermodynamic rate.
 */
public class PassivatingMetalItem extends Item {

    public static final TagKey<Item> PASSIVATING_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "passivating_metals"));

    public PassivatingMetalItem(Properties properties) {
        super(properties);
    }

    public static float getPassivation(ItemStack stack) {
        if (!isPassivating(stack)) return 0.0f;
        Float val = stack.get(ModDataComponents.PASSIVATION_LEVEL.get());
        return val != null ? val : 1.0f;
    }

    public static void setPassivation(ItemStack stack, float level) {
        stack.set(ModDataComponents.PASSIVATION_LEVEL.get(), Math.clamp(level, 0.0f, 1.0f));
    }

    public static boolean isPassivating(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof PassivatingMetalItem) return true;
        if (stack.has(ModDataComponents.PASSIVATION_LEVEL.get())) return true;
        if (stack.is(PASSIVATING_TAG)) return true;
        return false;
    }

    public static float updatePassivation(ItemStack stack, Map<ResourceLocation, Float> solutes, float tempK, float dt) {
        float current = getPassivation(stack);

        float cl = solutes.getOrDefault(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "chloride"), 0.0f);
        float f = solutes.getOrDefault(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "fluoride"), 0.0f);
        float oh = solutes.getOrDefault(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydroxide"), 0.0f);
        float h = solutes.getOrDefault(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydrogen"), 0.0f);
        float no3 = solutes.getOrDefault(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "nitrate"), 0.0f);

        float tempFactor = Math.max(0.5f, tempK / 298.15f);
        float attackRate = 0.0f;

        if (cl > 0.01f) {
            attackRate += cl * 0.25f;
        }
        if (f > 0.01f) {
            attackRate += f * 0.20f;
        }
        if (oh > 0.01f) {
            attackRate += oh * 0.50f;
        }
        if (h > 0.1f) {
            attackRate += (h - 0.1f) * 0.15f;
        }

        if (attackRate > 1e-4f) {
            float delta = attackRate * tempFactor * dt;
            current = Math.max(0.0f, current - delta);
        } else {
            float healRate = 0.02f * tempFactor * dt;
            if (no3 > 0.01f) {
                healRate += no3 * 0.1f * tempFactor * dt;
            }
            current = Math.min(1.0f, current + healRate);
        }

        setPassivation(stack, current);
        return current;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        float level = getPassivation(stack);
        int percent = Math.round(level * 100);

        if (level >= 0.8f) {
            tooltipComponents.add(Component.literal("Oxide Film: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Passivated (" + percent + "%)")
                            .withStyle(ChatFormatting.AQUA)));
        } else if (level >= 0.2f) {
            tooltipComponents.add(Component.literal("Oxide Film: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Etched / Pitted (" + percent + "%)")
                            .withStyle(ChatFormatting.YELLOW)));
        } else {
            tooltipComponents.add(Component.literal("Oxide Film: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Stripped / Active (" + percent + "%)")
                            .withStyle(ChatFormatting.RED)));
        }

        if (Screen.hasShiftDown()) {
            tooltipComponents.add(Component.literal("Self-passivating metal.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.literal("Oxide layer suppresses reactions.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.literal("Stripped by Cl⁻, OH⁻, F⁻, or strong acids.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.literal("Passivated by oxidizing acids (e.g. HNO₃).")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltipComponents.add(Component.literal("Hold Shift for chemical details")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
