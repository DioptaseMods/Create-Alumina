package net.nerfashton.create_alumina.content.fluids.vat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.nerfashton.create_alumina.api.chemical.*;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import net.nerfashton.create_alumina.api.chemistry_item.PassivatingMetalItem;
import net.nerfashton.create_alumina.api.fluid.SolventFluidType;

import java.util.*;

/**
 * Handles autonomous chemical and electrochemical reactions and dissolution
 * occurring exclusively inside the Chemical Vat.
 */
public class VatData {

    // Partial dissolution moles per slot
    private final Map<Integer, Float> slotDissolutionProgress = new HashMap<>();
    // Partial item consumption moles for oxidation/reduction
    private final Map<Item, Float> itemConsumptionProgress = new HashMap<>();
    // Partial item production moles
    private final Map<Item, Float> itemProductionProgress = new HashMap<>();

    private int tickCounter = 0;

    public void tick(ChemicalVatBlockEntity controller) {
        if (controller.getLevel() == null || controller.getLevel().isClientSide) {
            return;
        }

        FluidStack fluid = controller.getTankInventory().getFluid();
        if (fluid.isEmpty()) {
            return;
        }

        // Initialize solution if solvent fluid
        if (!ChemistryAPI.isSolution(fluid)) {
            if (ChemistryAPI.isBaseSolvent(fluid.getFluid())) {
                int color = 0xFFFFFFFF;
                float temp = 300f;
                if (fluid.getFluid().getFluidType() instanceof SolventFluidType solventType) {
                    color = solventType.getTintColor();
                    temp = solventType.getTemperature(fluid);
                }
                fluid.set(ModDataComponents.CHEMICAL_COMPOSITION.get(),
                        new ChemicalComposition(Map.of(), color, temp));
            } else {
                return;
            }
        }

        float volumeLiters = fluid.getAmount() / 1000.0f;
        if (volumeLiters <= 0.001f) {
            return;
        }

        IItemHandler itemHandler = controller.getItemHandler();
        if (itemHandler == null) {
            return;
        }

        boolean changed = false;

        // 1. Dissolution of soluble items based on Ksp and ion composition
        changed |= tickDissolution(controller, fluid, volumeLiters, itemHandler);

        // 2. Autonomous electrochemical redox reactions between half-reactions
        changed |= tickHalfReactions(controller, fluid, volumeLiters, itemHandler);

        // 3. Explicit autonomous reactions defined in JSON
        changed |= tickExplicitAutonomousReactions(controller, fluid, volumeLiters, itemHandler);

        if (changed) {
            controller.setChanged();
            if (++tickCounter % 5 == 0) {
                controller.sendData();
            }
        }
    }

    /**
     * Dissolves soluble items in the inventory into the vat fluid realistically:
     * - Respects Ksp saturation limit (Qsp < Ksp)
     * - Noyes-Whitney saturation deficit dissolution kinetics
     * - Heat of dissolution modifies fluid temperature
     * - Beer-Lambert recalculation of fluid color
     * - Consumes items stack by stack
     */
    private boolean tickDissolution(ChemicalVatBlockEntity controller, FluidStack fluid, float volumeLiters, IItemHandler itemHandler) {
        boolean anyChanged = false;

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                slotDissolutionProgress.remove(slot);
                continue;
            }

            SoluteData soluteData = SolubleItem.getSoluteDataFrom(stack);
            if (soluteData == null) {
                continue;
            }

            // Calculate ion product Qsp = product([ion]^coeff)
            double qsp = 1.0;
            double sumCoeff = 0.0;
            for (Map.Entry<ResourceLocation, Float> entry : soluteData.ions().entrySet()) {
                float conc = ChemistryAPI.getIonConcentration(fluid, entry.getKey());
                float coeff = entry.getValue();
                sumCoeff += coeff;
                qsp *= Math.pow(Math.max(conc, 0.0), coeff);
            }

            // Check saturation: if Qsp >= Ksp, dissolution cannot occur
            if (qsp >= soluteData.ksp()) {
                continue;
            }

            // Noyes-Whitney saturation deficit factor
            double saturationRatio = Math.pow(Math.max(0.0, qsp / soluteData.ksp()), 1.0 / Math.max(1.0, sumCoeff));
            double deficit = Math.max(0.02, 1.0 - saturationRatio);

            // Rate scaling based on stack surface area and solution temperature
            ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
            float tempK = comp != null ? comp.temperature() : 298.15f;
            double tempFactor = Math.max(0.5, tempK / 298.15);
            double surfaceFactor = Math.min(Math.sqrt(stack.getCount()), 4.0);

            // Base rate: ~0.02 mol/tick at standard conditions
            double rate = 0.02 * deficit * surfaceFactor * tempFactor;

            // Maximum moles before hitting saturation
            double maxMolesToSat = Math.max(0.0001, (soluteData.ksp() - qsp) * volumeLiters / Math.max(1.0, sumCoeff));
            double deltaMoles = Math.min(rate, maxMolesToSat);

            float currentProgress = slotDissolutionProgress.getOrDefault(slot, 0.0f);
            float molesRemainingInItem = soluteData.molesPerItem() - currentProgress;
            deltaMoles = Math.min(deltaMoles, molesRemainingInItem);

            if (deltaMoles > 1e-6) {
                ChemistryAPI.dissolveCompound(fluid, soluteData.ions(), (float) deltaMoles, soluteData.heatOfDissolution());

                currentProgress += (float) deltaMoles;
                if (currentProgress >= soluteData.molesPerItem() - 1e-4f) {
                    stack.shrink(1);
                    currentProgress = 0.0f;
                }
                slotDissolutionProgress.put(slot, currentProgress);
                anyChanged = true;
            }
        }

        return anyChanged;
    }

    /**
     * Executes autonomous spontaneous galvanic redox reactions formed by pairs of HalfReactions:
     * Evaluates cell potential E_cell = E_cathode - E_anode.
     * When E_cell > 0, electrons flow autonomously, consuming reactants and producing products.
     */
    private boolean tickHalfReactions(ChemicalVatBlockEntity controller, FluidStack fluid, float volumeLiters, IItemHandler itemHandler) {
        ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (comp == null) return false;

        Map<ResourceLocation, Float> solutes = new HashMap<>(comp.solutes());
        Collection<HalfReaction> allHalfReactions = HalfReactionManager.getAll();
        if (allHalfReactions.isEmpty()) return false;

        HalfReaction bestReduction = null;
        HalfReaction bestOxidation = null;
        double maxCellPotential = 0.05; // 50 mV threshold for spontaneous reaction

        for (HalfReaction hr1 : allHalfReactions) {
            if (!canUndergoReduction(hr1, solutes, itemHandler)) continue;

            for (HalfReaction hr2 : allHalfReactions) {
                if (hr1.id().equals(hr2.id())) continue;
                if (!canUndergoOxidation(hr2, solutes, itemHandler)) continue;

                double eCell = hr1.standardPotential() - hr2.standardPotential();
                if (eCell > maxCellPotential) {
                    maxCellPotential = eCell;
                    bestReduction = hr1;
                    bestOxidation = hr2;
                }
            }
        }

        if (bestReduction == null || bestOxidation == null) {
            return false;
        }

        // Calculate galvanic current & reaction rate
        double conductivity = ChemistryAPI.calculateSolutionConductivity(solutes, volumeLiters, fluid.getFluid());
        double resistance = ChemistryAPI.calculateCellResistance(conductivity);
        double current = ChemistryAPI.calculateGalvanicCurrent(maxCellPotential, 0.05, resistance);
        double molesElectrons = ChemistryAPI.calculateMolesElectronsTransferred(current, 0.05);

        // Passivation throttling: Check if any participating item has a passive oxide layer
        float passThrottle = getPassivationThrottle(bestReduction, bestOxidation, itemHandler, solutes, comp.temperature());
        molesElectrons *= passThrottle;

        // Limit moles of electrons by available reactants
        molesElectrons = Math.min(molesElectrons, 0.05); // per-tick cap
        molesElectrons = limitElectronsByReduction(bestReduction, molesElectrons, solutes, volumeLiters, itemHandler);
        molesElectrons = limitElectronsByOxidation(bestOxidation, molesElectrons, solutes, volumeLiters, itemHandler);

        if (molesElectrons <= 1e-6) {
            return false;
        }

        // Execute reduction: Reactants + n e- -> Products
        double molesReduction = molesElectrons / bestReduction.electrons();
        applyReduction(bestReduction, molesReduction, solutes, volumeLiters, itemHandler, controller);

        // Execute oxidation (reverse of reduction): Products -> Reactants + n e-
        double molesOxidation = molesElectrons / bestOxidation.electrons();
        applyOxidation(bestOxidation, molesOxidation, solutes, volumeLiters, itemHandler, controller);

        // Heat of reaction: Q = n * F * E_cell (Joules)
        double joules = molesElectrons * 96485.332 * maxCellPotential;
        float kj = (float) (joules / 1000.0);
        float deltaTemp = ChemistryAPI.calculateTemperatureChange(kj, ChemistryAPI.getMass(fluid), ChemistryAPI.getSpecificHeat(fluid.getFluid()));

        // Recalculate color and update composition
        int baseColor = getBaseColor(fluid);
        int newColor = ChemistryAPI.recalculateColor(solutes, baseColor);
        fluid.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), new ChemicalComposition(solutes, newColor, comp.temperature() + deltaTemp));

        return true;
    }

    /**
     * Checks if a HalfReaction can undergo Reduction (oxidized reactants present).
     */
    private boolean canUndergoReduction(HalfReaction hr, Map<ResourceLocation, Float> solutes, IItemHandler itemHandler) {
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            if (solutes.getOrDefault(ion.ion(), 0.0f) < 1e-4f) return false;
        }
        for (HalfReaction.ItemParticipant item : hr.reactantItems()) {
            if (countItem(itemHandler, item.item()) < item.count()) return false;
        }
        return true;
    }

    /**
     * Checks if a HalfReaction can undergo Oxidation (reduced products present, runs in reverse).
     */
    private boolean canUndergoOxidation(HalfReaction hr, Map<ResourceLocation, Float> solutes, IItemHandler itemHandler) {
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            if (solutes.getOrDefault(ion.ion(), 0.0f) < 1e-4f) return false;
        }
        for (HalfReaction.ItemParticipant item : hr.productItems()) {
            if (countItem(itemHandler, item.item()) < item.count()) return false;
        }
        return true;
    }

    private double limitElectronsByReduction(HalfReaction hr, double molesE, Map<ResourceLocation, Float> solutes, float volumeLiters, IItemHandler itemHandler) {
        double maxE = molesE;
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            float conc = solutes.getOrDefault(ion.ion(), 0.0f);
            double totalMoles = conc * volumeLiters;
            double eAvailable = (totalMoles / ion.amount()) * hr.electrons();
            maxE = Math.min(maxE, eAvailable);
        }
        for (HalfReaction.ItemParticipant item : hr.reactantItems()) {
            int count = countItem(itemHandler, item.item());
            double eAvailable = ((double) count / item.count()) * hr.electrons();
            maxE = Math.min(maxE, eAvailable);
        }
        return Math.max(0.0, maxE);
    }

    private double limitElectronsByOxidation(HalfReaction hr, double molesE, Map<ResourceLocation, Float> solutes, float volumeLiters, IItemHandler itemHandler) {
        double maxE = molesE;
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            float conc = solutes.getOrDefault(ion.ion(), 0.0f);
            double totalMoles = conc * volumeLiters;
            double eAvailable = (totalMoles / ion.amount()) * hr.electrons();
            maxE = Math.min(maxE, eAvailable);
        }
        for (HalfReaction.ItemParticipant item : hr.productItems()) {
            int count = countItem(itemHandler, item.item());
            double eAvailable = ((double) count / item.count()) * hr.electrons();
            maxE = Math.min(maxE, eAvailable);
        }
        return Math.max(0.0, maxE);
    }

    private void applyReduction(HalfReaction hr, double molesReaction, Map<ResourceLocation, Float> solutes, float volumeLiters, IItemHandler itemHandler, ChemicalVatBlockEntity controller) {
        // Consume reactant ions
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            float current = solutes.getOrDefault(ion.ion(), 0.0f);
            float consumed = (float) ((molesReaction * ion.amount()) / volumeLiters);
            solutes.put(ion.ion(), Math.max(0.0f, current - consumed));
        }
        // Consume reactant items
        for (HalfReaction.ItemParticipant item : hr.reactantItems()) {
            consumeItemMoles(itemHandler, item.item(), (float) (molesReaction * item.count()));
        }
        // Produce product ions
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            float current = solutes.getOrDefault(ion.ion(), 0.0f);
            float produced = (float) ((molesReaction * ion.amount()) / volumeLiters);
            solutes.put(ion.ion(), current + produced);
        }
        // Produce product items
        for (HalfReaction.ItemParticipant item : hr.productItems()) {
            produceItemMoles(itemHandler, item.item(), (float) (molesReaction * item.count()), controller);
        }
    }

    private void applyOxidation(HalfReaction hr, double molesReaction, Map<ResourceLocation, Float> solutes, float volumeLiters, IItemHandler itemHandler, ChemicalVatBlockEntity controller) {
        // Reverse of reduction: consumes product side, produces reactant side
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            float current = solutes.getOrDefault(ion.ion(), 0.0f);
            float consumed = (float) ((molesReaction * ion.amount()) / volumeLiters);
            solutes.put(ion.ion(), Math.max(0.0f, current - consumed));
        }
        for (HalfReaction.ItemParticipant item : hr.productItems()) {
            consumeItemMoles(itemHandler, item.item(), (float) (molesReaction * item.count()));
        }
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            float current = solutes.getOrDefault(ion.ion(), 0.0f);
            float produced = (float) ((molesReaction * ion.amount()) / volumeLiters);
            solutes.put(ion.ion(), current + produced);
        }
        for (HalfReaction.ItemParticipant item : hr.reactantItems()) {
            produceItemMoles(itemHandler, item.item(), (float) (molesReaction * item.count()), controller);
        }
    }

    /**
     * Executes explicit autonomous reactions defined in JSON.
     */
    private boolean tickExplicitAutonomousReactions(ChemicalVatBlockEntity controller, FluidStack fluid, float volumeLiters, IItemHandler itemHandler) {
        ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (comp == null) return false;

        Collection<AutonomousReaction> reactions = AutonomousReactionManager.getAll();
        if (reactions.isEmpty()) return false;

        Map<ResourceLocation, Float> solutes = new HashMap<>(comp.solutes());
        boolean anyChanged = false;

        for (AutonomousReaction ar : reactions) {
            // Check if reactants available
            boolean canRun = true;
            for (HalfReaction.IonParticipant ion : ar.reactantIons()) {
                if (solutes.getOrDefault(ion.ion(), 0.0f) < 1e-4f) {
                    canRun = false;
                    break;
                }
            }
            if (!canRun) continue;

            for (HalfReaction.ItemParticipant item : ar.reactantItems()) {
                if (countItem(itemHandler, item.item()) < item.count()) {
                    canRun = false;
                    break;
                }
            }
            if (!canRun) continue;

            // Passivation throttling for explicit reactions
            float passThrottle = getExplicitReactionPassivationThrottle(ar, itemHandler, solutes, comp.temperature());
            double rate = Math.min(ar.rate(), 0.05) * passThrottle;

            for (HalfReaction.IonParticipant ion : ar.reactantIons()) {
                float current = solutes.getOrDefault(ion.ion(), 0.0f);
                float consumed = (float) ((rate * ion.amount()) / volumeLiters);
                solutes.put(ion.ion(), Math.max(0.0f, current - consumed));
            }
            for (HalfReaction.ItemParticipant item : ar.reactantItems()) {
                consumeItemMoles(itemHandler, item.item(), (float) (rate * item.count()));
            }

            for (HalfReaction.IonParticipant ion : ar.productIons()) {
                float current = solutes.getOrDefault(ion.ion(), 0.0f);
                float produced = (float) ((rate * ion.amount()) / volumeLiters);
                solutes.put(ion.ion(), current + produced);
            }
            for (HalfReaction.ItemParticipant item : ar.productItems()) {
                produceItemMoles(itemHandler, item.item(), (float) (rate * item.count()), controller);
            }

            if (Math.abs(ar.heatPerMol()) > 0.01f) {
                float q = (float) (rate * ar.heatPerMol());
                float deltaTemp = ChemistryAPI.calculateTemperatureChange(q, ChemistryAPI.getMass(fluid), ChemistryAPI.getSpecificHeat(fluid.getFluid()));
                comp = new ChemicalComposition(solutes, comp.color(), comp.temperature() + deltaTemp);
            }

            int baseColor = getBaseColor(fluid);
            int newColor = ChemistryAPI.recalculateColor(solutes, baseColor);
            fluid.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), new ChemicalComposition(solutes, newColor, comp.temperature()));
            anyChanged = true;
        }

        return anyChanged;
    }

    private float getPassivationThrottle(HalfReaction red, HalfReaction ox, IItemHandler itemHandler, Map<ResourceLocation, Float> solutes, float tempK) {
        float minThrottle = 1.0f;
        for (HalfReaction.ItemParticipant p : red.reactantItems()) {
            minThrottle = Math.min(minThrottle, checkAndUpdatePassivation(itemHandler, p.item(), solutes, tempK));
        }
        for (HalfReaction.ItemParticipant p : ox.productItems()) {
            minThrottle = Math.min(minThrottle, checkAndUpdatePassivation(itemHandler, p.item(), solutes, tempK));
        }
        return minThrottle;
    }

    private float getExplicitReactionPassivationThrottle(AutonomousReaction ar, IItemHandler itemHandler, Map<ResourceLocation, Float> solutes, float tempK) {
        float minThrottle = 1.0f;
        for (HalfReaction.ItemParticipant p : ar.reactantItems()) {
            minThrottle = Math.min(minThrottle, checkAndUpdatePassivation(itemHandler, p.item(), solutes, tempK));
        }
        return minThrottle;
    }

    private float checkAndUpdatePassivation(IItemHandler itemHandler, Item item, Map<ResourceLocation, Float> solutes, float tempK) {
        float factor = 1.0f;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.is(item)) {
                if (PassivatingMetalItem.isPassivating(stack)) {
                    float passLevel = PassivatingMetalItem.updatePassivation(stack, solutes, tempK, 0.05f);
                    float currentFactor = 1.0f - 0.96f * passLevel;
                    factor = Math.min(factor, currentFactor);
                }
            }
        }
        return factor;
    }

    private int getBaseColor(FluidStack fluid) {
        if (fluid.getFluid().getFluidType() instanceof SolventFluidType solventType) {
            return solventType.getTintColor();
        }
        return 0xFFFFFFFF;
    }

    private int countItem(IItemHandler itemHandler, Item item) {
        int count = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack s = itemHandler.getStackInSlot(i);
            if (s.is(item)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private void consumeItemMoles(IItemHandler itemHandler, Item item, float moles) {
        float progress = itemConsumptionProgress.getOrDefault(item, 0.0f) + moles;
        while (progress >= 1.0f) {
            // Shrink 1 item from handler
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                ItemStack s = itemHandler.getStackInSlot(i);
                if (s.is(item) && !s.isEmpty()) {
                    s.shrink(1);
                    break;
                }
            }
            progress -= 1.0f;
        }
        itemConsumptionProgress.put(item, progress);
    }

    private void produceItemMoles(IItemHandler itemHandler, Item item, float moles, ChemicalVatBlockEntity controller) {
        float progress = itemProductionProgress.getOrDefault(item, 0.0f) + moles;
        while (progress >= 1.0f) {
            ItemStack stack = new ItemStack(item, 1);
            ItemStack remaining = ItemHandlerHelper.insertItem(itemHandler, stack, false);
            if (!remaining.isEmpty() && controller.getLevel() != null) {
                Containers.dropItemStack(controller.getLevel(),
                        controller.getBlockPos().getX() + 0.5,
                        controller.getBlockPos().getY() + 1.0,
                        controller.getBlockPos().getZ() + 0.5,
                        remaining);
            }
            progress -= 1.0f;
        }
        itemProductionProgress.put(item, progress);
    }

    public CompoundTag write(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag slotTag = new CompoundTag();
        slotDissolutionProgress.forEach((k, v) -> slotTag.putFloat(String.valueOf(k), v));
        tag.put("SlotDissolution", slotTag);
        return tag;
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        slotDissolutionProgress.clear();
        if (tag.contains("SlotDissolution")) {
            CompoundTag slotTag = tag.getCompound("SlotDissolution");
            for (String key : slotTag.getAllKeys()) {
                try {
                    slotDissolutionProgress.put(Integer.parseInt(key), slotTag.getFloat(key));
                } catch (NumberFormatException ignored) {}
            }
        }
    }
}
