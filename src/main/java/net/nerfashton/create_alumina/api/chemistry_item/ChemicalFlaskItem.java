package net.nerfashton.create_alumina.api.chemistry_item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.nerfashton.create_alumina.api.chemical.ChemicalComposition;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;

import java.util.List;
import java.util.Map;

public class ChemicalFlaskItem extends Item {
    public ChemicalFlaskItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        boolean success = FluidUtil.interactWithFluidHandler(player, context.getHand(), context.getLevel(), context.getClickedPos(), context.getClickedFace());
        if (success) return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (stack.has(ModDataComponents.FLUID_CONTENT)) {
            SimpleFluidContent fluidContent = stack.get(ModDataComponents.FLUID_CONTENT);
            if  (fluidContent != null && !fluidContent.isEmpty()) {
                FluidStack fluid = fluidContent.copy();
                ChemicalComposition data = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
                if (data != null) {
                    tooltipComponents.add(Component.literal("Dissolved Ions:").withStyle(ChatFormatting.GRAY));

                    for (Map.Entry<ResourceLocation, Float> entry : data.solutes().entrySet()) {
                        ResourceLocation ionId = entry.getKey();
                        Float moles =  entry.getValue();

                        ChemicalSpecies ionData = ModChemicals.REGISTRY.get(ionId);
                        String displayName = ionData != null ? ionData.symbol() + " (" + ionId.getPath() + ")" : ionId.toString();

                        tooltipComponents.add(Component.literal(" • ")
                                .withStyle(ChatFormatting.DARK_GRAY)
                                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(String.format("%.2f M/B", moles))
                                        .withStyle(ChatFormatting.GREEN)));
                    }
                }
            }
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
