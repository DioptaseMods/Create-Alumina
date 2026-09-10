package net.nerfashton.create_alumina.api.chemistry_item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.nerfashton.create_alumina.api.chemical.ChemicalComposition;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.ChemistryAPI;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;

import java.util.Map;

/**
 * Debug item that analyzes solution fluid stacks in blocks, fluid handlers, or
 * world fluids
 * and prints dissolved ion concentrations to chat.
 */
public class SolutionAnalyzerItem extends Item {

    public SolutionAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos,
                context.getClickedFace());

        boolean analyzed = false;

        // 1. Check if the block has a FluidHandler capability (e.g. Tanks, Machines)
        if (fluidHandler != null) {
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                FluidStack stack = fluidHandler.getFluidInTank(i);
                if (!stack.isEmpty()) {
                    displaySolutionData(player, stack, "Tank #" + (i + 1));
                    analyzed = true;
                }
            }
        }

        if (!analyzed) {
            FluidState fluidState = level.getFluidState(pos);
            if (!fluidState.isEmpty()) {
                FluidStack worldFluidStack = new FluidStack(fluidState.getType(), 1000);
                if (ChemistryAPI.isSolution(worldFluidStack)) {
                    displaySolutionData(player, worldFluidStack, "World Fluid");
                    analyzed = true;
                }
            }
        }

        if (!analyzed) {
            player.sendSystemMessage(
                    Component.literal("No chemical solution detected at target.").withStyle(ChatFormatting.RED));
        }

        return InteractionResult.SUCCESS;
    }

    private void displaySolutionData(Player player, FluidStack stack, String sourceName) {
        player.sendSystemMessage(Component.literal("=== Solution Analysis [" + sourceName + "] ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        player.sendSystemMessage(Component.literal("Fluid: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stack.getHoverName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + stack.getAmount() + " mB)").withStyle(ChatFormatting.DARK_GRAY)));

        if (!stack.has(ModDataComponents.CHEMICAL_COMPOSITION.get())) {
            return;
        }

        ChemicalComposition data = stack.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (data == null || data.solutes().isEmpty()) {
            player.sendSystemMessage(
                    Component.literal("Solutes: Pure Solvent (0 dissolved ions)").withStyle(ChatFormatting.GREEN));
            return;
        }

        player.sendSystemMessage(Component.literal("Dissolved Ions:").withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Temperature: " + String.format("%.1f K", data.temperature())).withStyle(ChatFormatting.RED));
        for (Map.Entry<ResourceLocation, Float> entry : data.solutes().entrySet()) {
            ResourceLocation ionId = entry.getKey();
            float concentration = entry.getValue();

            ChemicalSpecies species = ModChemicals.REGISTRY.get(ionId);
            String displayName = species != null ? species.symbol() + " (" + ionId.getPath() + ")" : ionId.toString();

            player.sendSystemMessage(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.2f M/B", concentration))
                            .withStyle(ChatFormatting.GREEN)));
        }
    }
}
