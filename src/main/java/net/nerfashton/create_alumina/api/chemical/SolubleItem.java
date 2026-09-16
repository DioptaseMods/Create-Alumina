package net.nerfashton.create_alumina.api.chemical;

import net.minecraft.world.item.ItemStack;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import org.jetbrains.annotations.Nullable;

/**
 * Interface implemented by items that can dissolve into chemical solutions.
 */
public interface SolubleItem {

    @Nullable
    SoluteData getSoluteData(ItemStack stack);

    /**
     * Resolves SoluteData from an ItemStack, checking its SolubleItem implementation
     * first, then falling back to the ModDataComponents.SOLUTE_DATA component.
     */
    @Nullable
    static SoluteData getSoluteDataFrom(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.has(ModDataComponents.SOLUTE_DATA.get())) {
            return stack.get(ModDataComponents.SOLUTE_DATA.get());
        }
        if (stack.getItem() instanceof SolubleItem solubleItem) {
            return solubleItem.getSoluteData(stack);
        }
        return null;
    }
}
