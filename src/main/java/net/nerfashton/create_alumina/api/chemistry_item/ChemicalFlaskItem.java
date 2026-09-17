package net.nerfashton.create_alumina.api.chemistry_item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStackSimple;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.nerfashton.create_alumina.api.chemical.ChemicalComposition;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.ChemistryAPI;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import net.nerfashton.create_alumina.api.fluid.SolventFluidType;
import net.nerfashton.create_alumina.registry.CAItems;

import java.util.List;
import java.util.Map;

public class ChemicalFlaskItem extends Item {
    public static final int CAPACITY = 1000;

    public ChemicalFlaskItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static FluidStack getContainedFluid(ItemStack stack) {
        SimpleFluidContent fluidContent = stack.get(ModDataComponents.FLUID_CONTENT);
        return fluidContent != null ? fluidContent.copy() : FluidStack.EMPTY;
    }

    public static void setContainedFluid(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) {
            stack.remove(ModDataComponents.FLUID_CONTENT);
        } else {
            stack.set(ModDataComponents.FLUID_CONTENT, SimpleFluidContent.copyOf(fluid));
        }
    }

    /**
     * Helper factory to create a pre-filled Chemical Flask ItemStack with defined solutes.
     */
    public static ItemStack createFlask(Fluid fluid, Map<ResourceLocation, Float> solutes, float tempK) {
        ItemStack stack = new ItemStack(CAItems.CHEMICAL_FLASK.get());
        FluidStack fluidStack = new FluidStack(fluid, CAPACITY);
        int baseColor = 0xFFFFFFFF;
        if (fluid.getFluidType() instanceof SolventFluidType solventType) {
            baseColor = solventType.getTintColor();
        }
        int color = ChemistryAPI.recalculateColor(solutes, baseColor);
        fluidStack.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), new ChemicalComposition(solutes, color, tempK));
        setContainedFluid(stack, fluidStack);
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Try interacting with a block entity fluid handler (Chemical Vat, Tank, Basin, etc.)
        boolean success = FluidUtil.interactWithFluidHandler(
                player,
                context.getHand(),
                context.getLevel(),
                context.getClickedPos(),
                context.getClickedFace()
        );
        if (success) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        FluidStack contained = getContainedFluid(held);

        if (contained.isEmpty()) {
            // Empty flask: raytrace for fluid source block to scoop up (like an empty bucket)
            BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hitResult.getType() == HitResult.Type.MISS || hitResult.getType() != HitResult.Type.BLOCK) {
                return InteractionResultHolder.pass(held);
            }

            BlockPos pos = hitResult.getBlockPos();
            Direction direction = hitResult.getDirection();
            if (level.mayInteract(player, pos) && player.mayUseItemAt(pos, direction, held)) {
                FluidActionResult pickupResult = FluidUtil.tryPickUpFluid(held, player, level, pos, direction);
                if (pickupResult.isSuccess()) {
                    ItemStack filledStack = pickupResult.getResult();
                    FluidStack fluid = getContainedFluid(filledStack);
                    // If fluid is a base solvent and lacks chemical composition, initialize it
                    if (!fluid.isEmpty() && ChemistryAPI.isBaseSolvent(fluid.getFluid()) && !fluid.has(ModDataComponents.CHEMICAL_COMPOSITION.get())) {
                        ChemistryAPI.isSolution(fluid);
                        setContainedFluid(filledStack, fluid);
                    }

                    if (!player.getAbilities().instabuild) {
                        player.setItemInHand(hand, filledStack);
                    } else if (!player.getInventory().contains(filledStack)) {
                        ItemHandlerHelper.giveItemToPlayer(player, filledStack);
                    }
                    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                }
            }
            return InteractionResultHolder.pass(held);
        } else {
            // Filled flask: raytrace for block to place fluid (like a full bucket)
            BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (hitResult.getType() == HitResult.Type.MISS || hitResult.getType() != HitResult.Type.BLOCK) {
                return InteractionResultHolder.pass(held);
            }

            BlockPos pos = hitResult.getBlockPos();
            Direction direction = hitResult.getDirection();
            BlockState state = level.getBlockState(pos);
            BlockPos targetPos = pos;
            boolean canPlaceDirectly = state.canBeReplaced() || (state.getBlock() instanceof LiquidBlockContainer);
            if (!canPlaceDirectly) {
                targetPos = pos.relative(direction);
            }

            if (level.mayInteract(player, targetPos) && player.mayUseItemAt(targetPos, direction, held)) {
                FluidActionResult placeResult = FluidUtil.tryPlaceFluid(player, level, hand, targetPos, held, contained);
                if (placeResult.isSuccess()) {
                    if (!player.getAbilities().instabuild) {
                        player.setItemInHand(hand, placeResult.getResult());
                    }
                    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                }
            }
            return InteractionResultHolder.pass(held);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            return super.getName(stack);
        }

        FluidStack fluid = getContainedFluid(stack);
        if (!fluid.isEmpty()) {
            ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
            if (comp != null && !comp.solutes().isEmpty()) {
                String solutionName = getCommonSolutionName(comp.solutes());
                if (solutionName != null) {
                    return Component.translatable(this.getDescriptionId(stack))
                            .append(" (" + solutionName + ")");
                }
            }
            return Component.translatable(this.getDescriptionId(stack))
                    .append(" (")
                    .append(fluid.getHoverName())
                    .append(")");
        }
        return super.getName(stack);
    }

    private static String getCommonSolutionName(Map<ResourceLocation, Float> solutes) {
        boolean hasH = solutes.containsKey(ModChemicals.HYDROGEN.getId());
        boolean hasOH = solutes.containsKey(ModChemicals.HYDROXIDE.getId());
        boolean hasCl = solutes.containsKey(ModChemicals.CHLORIDE.getId());
        boolean hasNa = solutes.containsKey(ModChemicals.SODIUM.getId());
        boolean hasCu = solutes.containsKey(ModChemicals.COPPER.getId());
        boolean hasSO4 = solutes.containsKey(ModChemicals.SULFATE.getId());
        boolean hasFe3 = solutes.containsKey(ModChemicals.IRON_III.getId());
        boolean hasFe2 = solutes.containsKey(ModChemicals.IRON.getId());
        boolean hasAl = solutes.containsKey(ModChemicals.ALUMINUM.getId());
        boolean hasAluminate = solutes.containsKey(ModChemicals.ALUMINATE.getId());
        boolean hasZn = solutes.containsKey(ModChemicals.ZINC.getId());
        boolean hasNO3 = solutes.containsKey(ModChemicals.NITRATE.getId());

        if (solutes.size() == 2) {
            if (hasH && hasCl) return "Hydrochloric Acid";
            if (hasNa && hasOH) return "Sodium Hydroxide";
            if (hasH && hasSO4) return "Sulfuric Acid";
            if (hasH && hasNO3) return "Nitric Acid";
            if (hasCu && hasSO4) return "Copper(II) Sulfate";
            if (hasCu && hasCl) return "Copper(II) Chloride";
            if (hasCu && hasNO3) return "Copper(II) Nitrate";
            if (hasFe3 && hasCl) return "Iron(III) Chloride";
            if (hasFe2 && hasSO4) return "Iron(II) Sulfate";
            if (hasNa && hasCl) return "Sodium Chloride";
            if (hasZn && hasSO4) return "Zinc Sulfate";
            if (hasAl && hasSO4) return "Aluminum Sulfate";
        }
        if (hasAluminate) return "Sodium Aluminate";
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        FluidStack fluid = getContainedFluid(stack);
        if (!fluid.isEmpty()) {
            tooltipComponents.add(Component.literal(fluid.getAmount() + " / " + CAPACITY + " mB")
                    .withStyle(ChatFormatting.GRAY));

            ChemicalComposition data = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
            if (data != null) {
                tooltipComponents.add(Component.literal("Temperature: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.1f °C (%.1f K)", data.temperature() - 273.15f, data.temperature()))
                                .withStyle(ChatFormatting.YELLOW)));

                if (!data.solutes().isEmpty()) {
                    tooltipComponents.add(Component.literal("Dissolved Solutes:").withStyle(ChatFormatting.GOLD));

                    for (Map.Entry<ResourceLocation, Float> entry : data.solutes().entrySet()) {
                        ResourceLocation ionId = entry.getKey();
                        Float moles = entry.getValue();

                        ChemicalSpecies ionData = ModChemicals.REGISTRY.get(ionId);
                        String displayName = ionData != null ? ionData.symbol() + " (" + ionId.getPath() + ")" : ionId.toString();

                        tooltipComponents.add(Component.literal(" • ")
                                .withStyle(ChatFormatting.DARK_GRAY)
                                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(String.format("%.3f mol/L", moles))
                                        .withStyle(ChatFormatting.AQUA)));
                    }
                }
            }
        } else {
            tooltipComponents.add(Component.literal("Empty").withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    /**
     * Item fluid handler capability implementation for ChemicalFlaskItem.
     */
    public static class FlaskFluidHandler extends FluidHandlerItemStackSimple {
        public FlaskFluidHandler(ItemStack container) {
            super(ModDataComponents.FLUID_CONTENT, container, CAPACITY);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !canFillFluidType(resource)) return 0;

            FluidStack toFill = resource;
            if (ChemistryAPI.isBaseSolvent(resource.getFluid()) && !resource.has(ModDataComponents.CHEMICAL_COMPOSITION.get())) {
                toFill = resource.copy();
                ChemistryAPI.isSolution(toFill);
            }
            return super.fill(toFill, action);
        }
    }
}
