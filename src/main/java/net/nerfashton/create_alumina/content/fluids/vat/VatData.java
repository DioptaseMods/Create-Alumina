package net.nerfashton.create_alumina.content.fluids.vat;

import com.tterrag.registrate.util.entry.ItemEntry;
import cute.ame.celsius.Fluid.Data.FluidConstants;
import cute.ame.celsius.Fluid.Data.FluidNodeStore;
import cute.ame.celsius.Fluid.Data.SpeciesTable;
import cute.ame.celsius.Fluid.Level.FluidLevelData;
import cute.ame.celsius.Fluid.Registry.FluidSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.nerfashton.create_alumina.api.chemical.*;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import net.nerfashton.create_alumina.api.chemistry_item.PassivatingMetalItem;
import net.nerfashton.create_alumina.api.chemistry_item.SolubleSaltItem;
import net.nerfashton.create_alumina.api.fluid.SolventFluidType;
import net.nerfashton.create_alumina.content.fluids.vat.electrode.ElectrodeType;
import net.nerfashton.create_alumina.registry.CAFluids;
import net.nerfashton.create_alumina.registry.CAItems;

import java.util.*;

/**
 * Handles autonomous chemical and electrochemical reactions, dissolution, and precipitation
 * occurring inside the Chemical Vat.
 */
public class VatData {

    // Active buffer of partially consumed items currently inside the vat chamber.
    // When an item begins dissolving/reacting, it is transferred from the inventory
    // into this buffer, allowing the solution to continuously receive product ions
    // with exact stoichiometric charge balance every tick, while preventing duplication.
    private final Map<Item, Float> partialItemBuffer = new HashMap<>();

    // Partial item production moles (for electrodeposition / precipitation crystals)
    private final Map<Item, Float> itemProductionProgress = new HashMap<>();

    // Partial fluid production millibuckets (for electrolysis output fluids e.g. molten aluminum)
    private final Map<ResourceLocation, Float> fluidProductionProgress = new HashMap<>();

    private int tickCounter = 0;

    /**
     * Calculates a surface area multiplier based on the total number of items in the vat.
     * Higher surface area significantly accelerates dissolution and reaction kinetics.
     */
    public static double calculateSurfaceAreaFactor(int itemCount) {
        if (itemCount <= 0) return 0.0;
        return Math.max(1.0, Math.pow(itemCount, 0.75));
    }

    /**
     * Consumes up to neededMoles of an item from the internal partial item buffer.
     * If the buffer does not have enough, draws whole items from the inventory into the buffer.
     * Returns the actual moles consumed.
     */
    public float consumeFromItemBuffer(IItemHandler itemHandler, Item item, float neededMoles) {
        if (neededMoles <= 1e-7f) return 0.0f;
        float currentBuffer = partialItemBuffer.getOrDefault(item, 0.0f);
        if (currentBuffer < neededMoles) {
            float deficit = neededMoles - currentBuffer;
            int itemsNeeded = (int) Math.ceil(deficit - 1e-5f);
            int itemsTaken = shrinkItems(itemHandler, item, itemsNeeded);
            currentBuffer += itemsTaken;
        }
        float actualConsumed = Math.min(neededMoles, currentBuffer);
        currentBuffer -= actualConsumed;
        if (currentBuffer > 1e-5f) {
            partialItemBuffer.put(item, currentBuffer);
        } else {
            partialItemBuffer.remove(item);
        }
        return actualConsumed;
    }

    private int shrinkItems(IItemHandler itemHandler, Item item, int maxCount) {
        int remainingToShrink = maxCount;
        for (int i = 0; i < itemHandler.getSlots() && remainingToShrink > 0; i++) {
            ItemStack s = itemHandler.getStackInSlot(i);
            if (s.is(item) && !s.isEmpty()) {
                ItemStack extracted = itemHandler.extractItem(i, remainingToShrink, false);
                remainingToShrink -= extracted.getCount();
            }
        }
        return maxCount - remainingToShrink;
    }

    /**
     * Total available item moles in the vat (inventory + internal partial buffer).
     */
    public float getTotalAvailableItemMoles(IItemHandler itemHandler, Item item) {
        float inBuffer = partialItemBuffer.getOrDefault(item, 0.0f);
        int inInventory = countItem(itemHandler, item);
        return inBuffer + inInventory;
    }

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

        ServerLevel serverLevel = (ServerLevel) controller.getLevel();
        FluidLevelData fluidLevelData = FluidLevelData.get(serverLevel);
        int gasNodeId = controller.getOrCreateGasNode(serverLevel);
        FluidNodeStore gasStore = fluidLevelData.store();
        SpeciesTable speciesTable = FluidSpecies.active();

        boolean changed = false;

        // 1. Spontaneous acid-base neutralization (H+ + OH- -> H2O)
        changed |= tickNeutralization(fluid);

        // 2. Dissolution of soluble items based on Ksp, surface area, and ion composition
        changed |= tickDissolution(controller, fluid, volumeLiters, itemHandler);

        // 3. Precipitation of supersaturated salts (Qsp > Ksp) into crystals/items
        changed |= tickPrecipitation(controller, fluid, volumeLiters, itemHandler);

        // 4. Autonomous electrochemical redox reactions between half-reactions (galvanic & driven electrolysis)
        changed |= tickHalfReactions(controller, fluid, volumeLiters, itemHandler, fluidLevelData, gasStore, gasNodeId, speciesTable);

        // 5. Explicit non-redox autonomous reactions (e.g. Bayer alumina digestion)
        changed |= tickExplicitAutonomousReactions(controller, fluid, volumeLiters, itemHandler, fluidLevelData, gasStore, gasNodeId, speciesTable);

        if (changed) {
            controller.setChanged();
            if (++tickCounter % 5 == 0) {
                controller.sendData();
            }
        }
    }

    /**
     * Spontaneously neutralizes acid and base ions (H+ + OH- -> H2O) releasing 57.1 kJ/mol neutralization heat.
     */
    private boolean tickNeutralization(FluidStack fluid) {
        ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (comp == null || comp.solutes().isEmpty()) return false;

        Map<ResourceLocation, Float> solutes = new HashMap<>(comp.solutes());
        float neutHeat = ChemistryAPI.processReactions(solutes);
        if (neutHeat > 0.0001f) {
            float deltaTemp = ChemistryAPI.calculateTemperatureChange(neutHeat, ChemistryAPI.getMass(fluid), ChemistryAPI.getSpecificHeat(fluid.getFluid()));
            int baseColor = getBaseColor(fluid);
            int newColor = ChemistryAPI.recalculateColor(solutes, baseColor);
            fluid.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), new ChemicalComposition(solutes, newColor, comp.temperature() + deltaTemp));
            return true;
        }
        return false;
    }

    /**
     * Dissolves soluble items in the inventory into the vat fluid realistically.
     */
    private boolean tickDissolution(ChemicalVatBlockEntity controller, FluidStack fluid, float volumeLiters, IItemHandler itemHandler) {
        boolean anyChanged = false;

        // Restore any buffered insoluble items back to inventory if they cannot dissolve
        for (Iterator<Map.Entry<Item, Float>> it = partialItemBuffer.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Item, Float> entry = it.next();
            ItemStack sample = new ItemStack(entry.getKey());
            SoluteData sd = SolubleItem.getSoluteDataFrom(sample);
            if (sd != null && sd.ksp() <= 1e-4 && !(entry.getKey() == CAItems.ALUMINA.get() && fluid.is(CAFluids.MOLTEN_CRYOLITE.get()))) {
                int whole = (int) Math.floor(entry.getValue() + 0.5f);
                if (whole > 0) {
                    ItemStack toReturn = new ItemStack(entry.getKey(), whole);
                    ItemStack left = ItemHandlerHelper.insertItem(itemHandler, toReturn, false);
                    if (!left.isEmpty() && controller.getLevel() != null) {
                        ItemEntity entity = new ItemEntity(
                                controller.getLevel(),
                                controller.getBlockPos().getX() + 0.5,
                                controller.getBlockPos().getY() + 1.0,
                                controller.getBlockPos().getZ() + 0.5,
                                left);
                        controller.getLevel().addFreshEntity(entity);
                    }
                }
                it.remove();
                anyChanged = true;
            }
        }

        // Check itemProductionProgress: if a precipitate reached >= 0.95 items due to floating point near-integer, produce it
        for (Iterator<Map.Entry<Item, Float>> it = itemProductionProgress.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Item, Float> entry = it.next();
            if (entry.getValue() >= 0.95f) {
                int whole = (int) Math.floor(entry.getValue() + 0.05f);
                if (whole > 0) {
                    ItemStack toInsert = new ItemStack(entry.getKey(), whole);
                    ItemStack left = ItemHandlerHelper.insertItem(itemHandler, toInsert, false);
                    if (!left.isEmpty() && controller.getLevel() != null) {
                        ItemEntity entity = new ItemEntity(
                                controller.getLevel(),
                                controller.getBlockPos().getX() + 0.5,
                                controller.getBlockPos().getY() + 1.0,
                                controller.getBlockPos().getZ() + 0.5,
                                left);
                        controller.getLevel().addFreshEntity(entity);
                    }
                    float rem = entry.getValue() - whole;
                    if (rem > 1e-4f) {
                        entry.setValue(rem);
                    } else {
                        it.remove();
                    }
                    anyChanged = true;
                }
            }
        }

        Set<Item> checkedItems = new HashSet<>();
        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                checkedItems.add(stack.getItem());
            }
        }
        checkedItems.addAll(partialItemBuffer.keySet());

        ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        float tempK = comp != null ? comp.temperature() : 298.15f;
        double tempFactor = Math.max(0.5, tempK / 298.15);

        for (Item item : checkedItems) {
            ItemStack sampleStack = new ItemStack(item);
            SoluteData soluteData = SolubleItem.getSoluteDataFrom(sampleStack);
            if (soluteData == null) {
                continue;
            }

            float totalAvailableMoles = getTotalAvailableItemMoles(itemHandler, item) * soluteData.molesPerItem();
            if (totalAvailableMoles <= 1e-5f) {
                continue;
            }

            // Determine effective Ksp depending on solvent and temperature
            double effectiveKsp = soluteData.ksp();
            if (item == CAItems.ALUMINA.get()) {
                if (fluid.is(CAFluids.MOLTEN_CRYOLITE.get())) {
                    // Alumina dissolves in molten cryolite bath at operating temperatures (> 1100 K)
                    if (tempK >= 1100.0f) {
                        effectiveKsp = 150.0;
                    } else {
                        effectiveKsp = 1e-12;
                    }
                } else {
                    effectiveKsp = 1e-12; // Insoluble in water and other ambient solvents
                }
            }

            // Insoluble and sparingly soluble precipitates (effectiveKsp <= 1e-4) do not dissolve in water/ambient solvents
            if (effectiveKsp <= 1e-4) {
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

            // Check saturation: if Qsp >= effectiveKsp, dissolution cannot occur
            if (qsp >= effectiveKsp) {
                continue;
            }

            // Noyes-Whitney saturation deficit factor
            double saturationRatio = Math.pow(Math.max(0.0, qsp / effectiveKsp), 1.0 / Math.max(1.0, sumCoeff));
            double deficit = Math.max(0.05, 1.0 - saturationRatio);

            // Rate scaling based on total items of this type in vat (surface area) and temperature
            int totalItemCount = (int) Math.ceil(totalAvailableMoles / soluteData.molesPerItem());
            double surfaceFactor = calculateSurfaceAreaFactor(totalItemCount);

            // Fast base rate: ~0.5 mol/tick scaled by surface area and temperature
            double rate = 0.5 * deficit * surfaceFactor * tempFactor;

            // Maximum moles before hitting saturation estimated from saturation deficit
            double maxMolesToSat = Math.max(0.05, (1.0 - saturationRatio) * Math.pow(effectiveKsp, 1.0 / Math.max(1.0, sumCoeff)) * volumeLiters);
            double deltaMoles = Math.min(rate, Math.min(maxMolesToSat, totalAvailableMoles));

            if (deltaMoles > 1e-6) {
                float actualConsumedItems = consumeFromItemBuffer(itemHandler, item, (float) (deltaMoles / soluteData.molesPerItem()));
                float dissolvedMoles = actualConsumedItems * soluteData.molesPerItem();
                if (dissolvedMoles > 1e-6f) {
                    ChemistryAPI.dissolveCompound(fluid, soluteData.ions(), dissolvedMoles, soluteData.heatOfDissolution());
                    anyChanged = true;
                }
            }
        }

        return anyChanged;
    }

    /**
     * Precipitates sparingly soluble salts when ion concentrations exceed Ksp (Qsp > Ksp).
     */
    private boolean tickPrecipitation(ChemicalVatBlockEntity controller, FluidStack fluid, float volumeLiters, IItemHandler itemHandler) {
        ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (comp == null || comp.solutes().isEmpty()) return false;

        Map<ResourceLocation, Float> solutes = new HashMap<>(comp.solutes());
        float tempK = comp.temperature();
        boolean anyChanged = false;

        for (ItemEntry<SolubleSaltItem> saltEntry : CAItems.ALL_SALTS) {
            Item saltItem = saltEntry.get();
            ItemStack sampleStack = new ItemStack(saltItem);
            SoluteData soluteData = SolubleItem.getSoluteDataFrom(sampleStack);
            if (soluteData == null || soluteData.ksp() <= 0) continue;

            // In molten cryolite above 1100 K, alumina stays dissolved
            if (saltItem == CAItems.ALUMINA.get() && fluid.is(CAFluids.MOLTEN_CRYOLITE.get()) && tempK >= 1100.0f) {
                continue;
            }

            double effectiveKsp = soluteData.ksp();
            double qsp = 1.0;
            double sumCoeff = 0.0;
            boolean hasAllIons = true;
            for (Map.Entry<ResourceLocation, Float> entry : soluteData.ions().entrySet()) {
                float conc = solutes.getOrDefault(entry.getKey(), 0.0f);
                if (conc <= 1e-5f) {
                    hasAllIons = false;
                    break;
                }
                float coeff = entry.getValue();
                sumCoeff += coeff;
                qsp *= Math.pow(conc, coeff);
            }

            if (!hasAllIons || qsp <= effectiveKsp) {
                continue;
            }

            // Saturation excess: precipitate excess ions
            double excessRatio = Math.pow(qsp / effectiveKsp, 1.0 / Math.max(1.0, sumCoeff)) - 1.0;
            double precipMoles = Math.min(64.0, Math.max(1.0, excessRatio * 0.5 * volumeLiters));

            for (Map.Entry<ResourceLocation, Float> entry : soluteData.ions().entrySet()) {
                float conc = solutes.getOrDefault(entry.getKey(), 0.0f);
                double maxMoles = (conc * volumeLiters) / entry.getValue();
                precipMoles = Math.min(precipMoles, maxMoles);
            }

            if (Math.abs(precipMoles - Math.round(precipMoles)) < 0.01) {
                precipMoles = Math.round(precipMoles);
            }

            if (precipMoles > 1e-5) {
                for (Map.Entry<ResourceLocation, Float> entry : soluteData.ions().entrySet()) {
                    float current = solutes.getOrDefault(entry.getKey(), 0.0f);
                    float consumed = (float) ((precipMoles * entry.getValue()) / volumeLiters);
                    float remaining = Math.max(0.0f, current - consumed);
                    if (remaining > 1e-5f) {
                        solutes.put(entry.getKey(), remaining);
                    } else {
                        solutes.remove(entry.getKey());
                    }
                }

                produceItemMoles(itemHandler, saltItem, (float) (precipMoles / soluteData.molesPerItem()), controller);

                float q = (float) (-soluteData.heatOfDissolution() * precipMoles);
                float deltaTemp = ChemistryAPI.calculateTemperatureChange(q, ChemistryAPI.getMass(fluid), ChemistryAPI.getSpecificHeat(fluid.getFluid()));

                int baseColor = getBaseColor(fluid);
                int newColor = ChemistryAPI.recalculateColor(solutes, baseColor);
                comp = new ChemicalComposition(solutes, newColor, comp.temperature() + deltaTemp);
                fluid.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), comp);

                anyChanged = true;
            }
        }

        return anyChanged;
    }

    /**
     * Executes autonomous spontaneous galvanic redox reactions and driven electrolysis
     * between pairs of HalfReactions with full Celsius gas integration.
     */
    private boolean tickHalfReactions(ChemicalVatBlockEntity controller, FluidStack fluid, float volumeLiters, IItemHandler itemHandler, FluidLevelData fluidLevelData, FluidNodeStore gasStore, int gasNodeId, SpeciesTable speciesTable) {
        ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (comp == null) return false;

        Map<ResourceLocation, Float> solutes = new HashMap<>(comp.solutes());
        Collection<HalfReaction> allHalfReactions = HalfReactionManager.getAll();
        if (allHalfReactions.isEmpty()) return false;

        HalfReaction bestReduction = null;
        HalfReaction bestOxidation = null;
        double maxDrivingPotential = 0.05; // 50 mV threshold for spontaneous or driven reaction

        boolean hasElectrodes = controller.hasElectrode(ElectrodeType.ANODE) && controller.hasElectrode(ElectrodeType.CATHODE);
        float appliedVoltage = hasElectrodes ? Math.max(0.0f, controller.getVoltage()) : 0.0f;

        for (HalfReaction hr1 : allHalfReactions) {
            if (!canUndergoReduction(hr1, solutes, itemHandler, gasStore, gasNodeId, speciesTable)) continue;

            double eRed = calculateHalfReactionPotential(hr1, solutes, comp.temperature(), gasStore, gasNodeId, speciesTable);

            for (HalfReaction hr2 : allHalfReactions) {
                if (hr1.id().equals(hr2.id())) continue;
                if (!canUndergoOxidation(hr2, solutes, itemHandler, gasStore, gasNodeId, speciesTable)) continue;

                double eOx = calculateHalfReactionPotential(hr2, solutes, comp.temperature(), gasStore, gasNodeId, speciesTable);
                double eCell = eRed - eOx;
                double drivingPotential = eCell + appliedVoltage;

                if (drivingPotential > maxDrivingPotential) {
                    maxDrivingPotential = drivingPotential;
                    bestReduction = hr1;
                    bestOxidation = hr2;
                }
            }
        }

        if (bestReduction == null || bestOxidation == null) {
            return false;
        }

        // Calculate galvanic or driven electrolysis current & reaction rate
        double conductivity = ChemistryAPI.calculateSolutionConductivity(solutes, volumeLiters, fluid.getFluid());
        double resistance = ChemistryAPI.calculateCellResistance(conductivity);
        double current;
        if (appliedVoltage > 0.05f && controller.getCurrentAmps() > 0.001f) {
            current = controller.getCurrentAmps();
        } else {
            current = ChemistryAPI.calculateGalvanicCurrent(maxDrivingPotential, 0.05, resistance);
        }
        double molesElectrons = ChemistryAPI.calculateMolesElectronsTransferred(current, 0.05);

        // Passivation throttling
        float passThrottle = getPassivationThrottle(bestReduction, bestOxidation, itemHandler, solutes, comp.temperature());
        molesElectrons *= passThrottle;

        // Surface area scaling
        double surfaceFactor = getReactionSurfaceFactor(bestReduction, bestOxidation, itemHandler);
        molesElectrons *= surfaceFactor;

        double maxCap = Math.max(5.0, 5.0 * surfaceFactor);
        molesElectrons = Math.min(molesElectrons, maxCap);

        // Limit moles of electrons by available reactants (ions, items, and gases) and output tank capacity
        molesElectrons = limitElectronsByReduction(bestReduction, molesElectrons, solutes, volumeLiters, itemHandler, controller, gasStore, gasNodeId, speciesTable);
        molesElectrons = limitElectronsByOxidation(bestOxidation, molesElectrons, solutes, volumeLiters, itemHandler, gasStore, gasNodeId, speciesTable);

        if (molesElectrons <= 1e-6) {
            return false;
        }

        // Execute reduction: Reactants + n e- -> Products (deposits fluids in output tank and evolves product gases)
        double molesReduction = molesElectrons / bestReduction.electrons();
        applyReduction(bestReduction, molesReduction, solutes, volumeLiters, itemHandler, controller, fluidLevelData, gasStore, gasNodeId, speciesTable);

        // Execute oxidation (reverse of reduction): Products -> Reactants + n e-
        double molesOxidation = molesElectrons / bestOxidation.electrons();
        applyOxidation(bestOxidation, molesOxidation, solutes, volumeLiters, itemHandler, controller, fluidLevelData, gasStore, gasNodeId, speciesTable);

        // Heat of reaction: Q = n * F * maxDrivingPotential (Joules)
        double joules = molesElectrons * ChemistryAPI.FARADAY_CONSTANT * maxDrivingPotential;
        float kj = (float) (joules / 1000.0);
        float deltaTemp = ChemistryAPI.calculateTemperatureChange(kj, ChemistryAPI.getMass(fluid), ChemistryAPI.getSpecificHeat(fluid.getFluid()));

        // Check if any acid-base neutralization occurs in the updated solutes
        float neutHeat = ChemistryAPI.processReactions(solutes);
        if (neutHeat > 0.0001f) {
            deltaTemp += ChemistryAPI.calculateTemperatureChange(neutHeat, ChemistryAPI.getMass(fluid), ChemistryAPI.getSpecificHeat(fluid.getFluid()));
        }

        // Equilibrate gas temperature towards fluid temperature
        if (gasNodeId != FluidNodeStore.INVALID) {
            gasStore.setTemperature(gasNodeId, comp.temperature() + deltaTemp);
            fluidLevelData.touch(gasNodeId);
        }

        // Recalculate color and update composition
        int baseColor = getBaseColor(fluid);
        int newColor = ChemistryAPI.recalculateColor(solutes, baseColor);
        fluid.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), new ChemicalComposition(solutes, newColor, comp.temperature() + deltaTemp));

        return true;
    }

    private double getReactionSurfaceFactor(HalfReaction red, HalfReaction ox, IItemHandler itemHandler) {
        int maxItems = 0;
        for (HalfReaction.ItemParticipant p : ox.productItems()) {
            maxItems = Math.max(maxItems, (int) Math.ceil(getTotalAvailableItemMoles(itemHandler, p.item())));
        }
        for (HalfReaction.ItemParticipant p : red.reactantItems()) {
            maxItems = Math.max(maxItems, (int) Math.ceil(getTotalAvailableItemMoles(itemHandler, p.item())));
        }
        return maxItems > 0 ? calculateSurfaceAreaFactor(maxItems) : 1.0;
    }

    /**
     * Calculates the dynamic reduction potential of a half-reaction using the Nernst equation:
     * E = E0 + (RT / nF) * ln(Q_red)
     * Incorporates partial pressures for gas participants.
     */
    private double calculateHalfReactionPotential(HalfReaction hr, Map<ResourceLocation, Float> solutes, float tempK, FluidNodeStore store, int gasNodeId, SpeciesTable table) {
        double logTerm = 0.0;
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            double conc = Math.max(solutes.getOrDefault(ion.ion(), 0.0f), 1e-6);
            logTerm += ion.amount() * Math.log(conc);
        }
        for (HalfReaction.GasParticipant gas : hr.reactantGases()) {
            double pAtm = calculateGasPartialPressureAtm(store, gasNodeId, gas.gas(), tempK, table);
            logTerm += gas.amount() * Math.log(pAtm);
        }
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            double conc = Math.max(solutes.getOrDefault(ion.ion(), 0.0f), 1e-6);
            logTerm -= ion.amount() * Math.log(conc);
        }
        for (HalfReaction.GasParticipant gas : hr.productGases()) {
            double pAtm = calculateGasPartialPressureAtm(store, gasNodeId, gas.gas(), tempK, table);
            logTerm -= gas.amount() * Math.log(pAtm);
        }
        return ChemistryAPI.calculateNernstPotential(hr.standardPotential(), hr.electrons(), tempK, logTerm);
    }

    private double calculateGasPartialPressureAtm(FluidNodeStore store, int gasNodeId, String gasKey, float tempK, SpeciesTable table) {
        if (gasNodeId == FluidNodeStore.INVALID) return 1.0;
        int s = table.indexOf(gasKey);
        if (s == SpeciesTable.UNKNOWN) return 1.0;
        float moles = store.amount(gasNodeId, s);
        float vol = store.volume(gasNodeId);
        if (vol <= 0.0f || moles <= 1e-6f) return 1e-4;
        double pAtm = moles * FluidConstants.R * tempK / vol;
        return Math.max(1e-4, pAtm);
    }

    /**
     * Checks if a HalfReaction can undergo Reduction (oxidized reactants present in the vat).
     */
    private boolean canUndergoReduction(HalfReaction hr, Map<ResourceLocation, Float> solutes, IItemHandler itemHandler, FluidNodeStore store, int gasNodeId, SpeciesTable table) {
        if (hr.reactantIons().isEmpty() && hr.reactantItems().isEmpty() && hr.reactantGases().isEmpty()) {
            return false;
        }
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            if (solutes.getOrDefault(ion.ion(), 0.0f) < 1e-4f) return false;
        }
        for (HalfReaction.ItemParticipant item : hr.reactantItems()) {
            if (getTotalAvailableItemMoles(itemHandler, item.item()) < 1e-4f) return false;
        }
        if (!hr.reactantGases().isEmpty()) {
            if (gasNodeId == FluidNodeStore.INVALID) return false;
            for (HalfReaction.GasParticipant gas : hr.reactantGases()) {
                int s = table.indexOf(gas.gas());
                if (s == SpeciesTable.UNKNOWN || store.amount(gasNodeId, s) < 1e-4f) return false;
            }
        }
        return true;
    }

    /**
     * Checks if a HalfReaction can undergo Oxidation (reduced products present, running in reverse).
     */
    private boolean canUndergoOxidation(HalfReaction hr, Map<ResourceLocation, Float> solutes, IItemHandler itemHandler, FluidNodeStore store, int gasNodeId, SpeciesTable table) {
        if (hr.productIons().isEmpty() && hr.productItems().isEmpty() && hr.productGases().isEmpty()) {
            return false;
        }
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            if (solutes.getOrDefault(ion.ion(), 0.0f) < 1e-4f) return false;
        }
        for (HalfReaction.ItemParticipant item : hr.productItems()) {
            if (getTotalAvailableItemMoles(itemHandler, item.item()) < 1e-4f) return false;
        }
        if (!hr.productGases().isEmpty()) {
            if (gasNodeId == FluidNodeStore.INVALID) return false;
            for (HalfReaction.GasParticipant gas : hr.productGases()) {
                int s = table.indexOf(gas.gas());
                if (s == SpeciesTable.UNKNOWN || store.amount(gasNodeId, s) < 1e-4f) return false;
            }
        }
        return true;
    }

    private double limitElectronsByReduction(HalfReaction hr, double molesE, Map<ResourceLocation, Float> solutes, float volumeLiters, IItemHandler itemHandler, ChemicalVatBlockEntity controller, FluidNodeStore store, int gasNodeId, SpeciesTable table) {
        double maxE = molesE;
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            float conc = solutes.getOrDefault(ion.ion(), 0.0f);
            double totalMoles = conc * volumeLiters;
            double eAvailable = (totalMoles / ion.amount()) * hr.electrons();
            maxE = Math.min(maxE, eAvailable);
        }
        for (HalfReaction.ItemParticipant item : hr.reactantItems()) {
            float available = getTotalAvailableItemMoles(itemHandler, item.item());
            double eAvailable = ((double) available / item.count()) * hr.electrons();
            maxE = Math.min(maxE, eAvailable);
        }
        if (gasNodeId != FluidNodeStore.INVALID) {
            for (HalfReaction.GasParticipant gas : hr.reactantGases()) {
                int s = table.indexOf(gas.gas());
                float available = (s != SpeciesTable.UNKNOWN) ? store.amount(gasNodeId, s) : 0.0f;
                double eAvailable = ((double) available / gas.amount()) * hr.electrons();
                maxE = Math.min(maxE, eAvailable);
            }
        }
        if (!hr.productFluids().isEmpty()) {
            int space = controller.getOutputTank().getSpace();
            for (HalfReaction.FluidParticipant fp : hr.productFluids()) {
                if (fp.amount() > 0) {
                    double maxMolesCanProduce = (double) space / fp.amount();
                    double eAvailable = maxMolesCanProduce * hr.electrons();
                    maxE = Math.min(maxE, eAvailable);
                }
            }
        }
        return Math.max(0.0, maxE);
    }

    private double limitElectronsByOxidation(HalfReaction hr, double molesE, Map<ResourceLocation, Float> solutes, float volumeLiters, IItemHandler itemHandler, FluidNodeStore store, int gasNodeId, SpeciesTable table) {
        double maxE = molesE;
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            float conc = solutes.getOrDefault(ion.ion(), 0.0f);
            double totalMoles = conc * volumeLiters;
            double eAvailable = (totalMoles / ion.amount()) * hr.electrons();
            maxE = Math.min(maxE, eAvailable);
        }
        for (HalfReaction.ItemParticipant item : hr.productItems()) {
            float available = getTotalAvailableItemMoles(itemHandler, item.item());
            double eAvailable = ((double) available / item.count()) * hr.electrons();
            maxE = Math.min(maxE, eAvailable);
        }
        if (gasNodeId != FluidNodeStore.INVALID) {
            for (HalfReaction.GasParticipant gas : hr.productGases()) {
                int s = table.indexOf(gas.gas());
                float available = (s != SpeciesTable.UNKNOWN) ? store.amount(gasNodeId, s) : 0.0f;
                double eAvailable = ((double) available / gas.amount()) * hr.electrons();
                maxE = Math.min(maxE, eAvailable);
            }
        }
        return Math.max(0.0, maxE);
    }

    private void applyReduction(HalfReaction hr, double molesReaction, Map<ResourceLocation, Float> solutes, float volumeLiters, IItemHandler itemHandler, ChemicalVatBlockEntity controller, FluidLevelData fluidLevelData, FluidNodeStore store, int gasNodeId, SpeciesTable table) {
        // Consume reactant ions
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            float current = solutes.getOrDefault(ion.ion(), 0.0f);
            float consumed = (float) ((molesReaction * ion.amount()) / volumeLiters);
            float remaining = Math.max(0.0f, current - consumed);
            if (remaining > 1e-5f) {
                solutes.put(ion.ion(), remaining);
            } else {
                solutes.remove(ion.ion());
            }
        }

        // Consume reactant items from buffer (if any solid item is being reduced)
        for (HalfReaction.ItemParticipant item : hr.reactantItems()) {
            consumeFromItemBuffer(itemHandler, item.item(), (float) (molesReaction * item.count()));
        }

        // Consume reactant gases
        if (gasNodeId != FluidNodeStore.INVALID) {
            for (HalfReaction.GasParticipant gas : hr.reactantGases()) {
                int s = table.indexOf(gas.gas());
                if (s != SpeciesTable.UNKNOWN) {
                    float consumed = (float) (molesReaction * gas.amount());
                    float current = store.amount(gasNodeId, s);
                    store.setAmount(gasNodeId, s, Math.max(0.0f, current - consumed));
                    fluidLevelData.touch(gasNodeId);
                }
            }
        }

        // Produce product ions
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            float current = solutes.getOrDefault(ion.ion(), 0.0f);
            float produced = (float) ((molesReaction * ion.amount()) / volumeLiters);
            solutes.put(ion.ion(), current + produced);
        }

        // Produce product items (e.g. electrodeposition)
        for (HalfReaction.ItemParticipant item : hr.productItems()) {
            produceItemMoles(itemHandler, item.item(), (float) (molesReaction * item.count()), controller);
        }

        // Produce product fluids (e.g. molten aluminum into electrolysis output tank)
        for (HalfReaction.FluidParticipant fluidPart : hr.productFluids()) {
            produceFluidMoles(fluidPart.fluid(), (float) (molesReaction * fluidPart.amount()), controller);
        }

        // Produce product gases (e.g. H2, O2, Cl2, NO)
        if (gasNodeId != FluidNodeStore.INVALID) {
            for (HalfReaction.GasParticipant gas : hr.productGases()) {
                int s = table.indexOf(gas.gas());
                if (s != SpeciesTable.UNKNOWN) {
                    float produced = (float) (molesReaction * gas.amount());
                    store.add(gasNodeId, s, produced);
                    fluidLevelData.touch(gasNodeId);
                }
            }
        }
    }

    private void applyOxidation(HalfReaction hr, double molesReaction, Map<ResourceLocation, Float> solutes, float volumeLiters, IItemHandler itemHandler, ChemicalVatBlockEntity controller, FluidLevelData fluidLevelData, FluidNodeStore store, int gasNodeId, SpeciesTable table) {
        // Reverse of reduction: consumes product side, produces reactant side
        for (HalfReaction.IonParticipant ion : hr.productIons()) {
            float current = solutes.getOrDefault(ion.ion(), 0.0f);
            float consumed = (float) ((molesReaction * ion.amount()) / volumeLiters);
            float remaining = Math.max(0.0f, current - consumed);
            if (remaining > 1e-5f) {
                solutes.put(ion.ion(), remaining);
            } else {
                solutes.remove(ion.ion());
            }
        }

        // Consume product items from buffer (e.g. solid metal ingots dissolving)
        for (HalfReaction.ItemParticipant item : hr.productItems()) {
            consumeFromItemBuffer(itemHandler, item.item(), (float) (molesReaction * item.count()));
        }

        // Consume product gases
        if (gasNodeId != FluidNodeStore.INVALID) {
            for (HalfReaction.GasParticipant gas : hr.productGases()) {
                int s = table.indexOf(gas.gas());
                if (s != SpeciesTable.UNKNOWN) {
                    float consumed = (float) (molesReaction * gas.amount());
                    float current = store.amount(gasNodeId, s);
                    store.setAmount(gasNodeId, s, Math.max(0.0f, current - consumed));
                    fluidLevelData.touch(gasNodeId);
                }
            }
        }

        // Produce reactant ions (metal cations entering the fluid with continuous charge balance)
        for (HalfReaction.IonParticipant ion : hr.reactantIons()) {
            float current = solutes.getOrDefault(ion.ion(), 0.0f);
            float produced = (float) ((molesReaction * ion.amount()) / volumeLiters);
            solutes.put(ion.ion(), current + produced);
        }

        for (HalfReaction.ItemParticipant item : hr.reactantItems()) {
            produceItemMoles(itemHandler, item.item(), (float) (molesReaction * item.count()), controller);
        }

        // Produce reactant gases (e.g. Cl2 from 2Cl- -> Cl2 + 2e-)
        if (gasNodeId != FluidNodeStore.INVALID) {
            for (HalfReaction.GasParticipant gas : hr.reactantGases()) {
                int s = table.indexOf(gas.gas());
                if (s != SpeciesTable.UNKNOWN) {
                    float produced = (float) (molesReaction * gas.amount());
                    store.add(gasNodeId, s, produced);
                    fluidLevelData.touch(gasNodeId);
                }
            }
        }
    }

    /**
     * Executes explicit autonomous reactions defined in JSON (e.g. non-redox Bayer alumina digestion)
     * with full Celsius gas integration.
     */
    private boolean tickExplicitAutonomousReactions(ChemicalVatBlockEntity controller, FluidStack fluid, float volumeLiters, IItemHandler itemHandler, FluidLevelData fluidLevelData, FluidNodeStore gasStore, int gasNodeId, SpeciesTable speciesTable) {
        ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (comp == null) return false;

        Collection<AutonomousReaction> reactions = AutonomousReactionManager.getAll();
        if (reactions.isEmpty()) return false;

        Map<ResourceLocation, Float> solutes = new HashMap<>(comp.solutes());
        boolean anyChanged = false;

        for (AutonomousReaction ar : reactions) {
            if (ar.reactantIons().isEmpty() && ar.reactantItems().isEmpty() && ar.reactantGases().isEmpty()) {
                continue;
            }

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
                if (getTotalAvailableItemMoles(itemHandler, item.item()) < 1e-4f) {
                    canRun = false;
                    break;
                }
            }
            if (!canRun) continue;

            if (!ar.reactantGases().isEmpty()) {
                if (gasNodeId == FluidNodeStore.INVALID) continue;
                for (HalfReaction.GasParticipant gas : ar.reactantGases()) {
                    int s = speciesTable.indexOf(gas.gas());
                    if (s == SpeciesTable.UNKNOWN || gasStore.amount(gasNodeId, s) < 1e-4f) {
                        canRun = false;
                        break;
                    }
                }
                if (!canRun) continue;
            }

            // Surface area scaling based on reactant items in the vat (including buffer)
            int solidItems = 0;
            for (HalfReaction.ItemParticipant p : ar.reactantItems()) {
                solidItems += (int) Math.ceil(getTotalAvailableItemMoles(itemHandler, p.item()));
            }
            double surfaceFactor = solidItems > 0 ? calculateSurfaceAreaFactor(solidItems) : 1.0;

            // Passivation throttling for explicit reactions
            float passThrottle = getExplicitReactionPassivationThrottle(ar, itemHandler, solutes, comp.temperature());
            double rate = Math.min(ar.rate() * 10.0 * surfaceFactor, 5.0 * surfaceFactor) * passThrottle;

            // Limit rate by available ions, items, and gases
            for (HalfReaction.IonParticipant ion : ar.reactantIons()) {
                float conc = solutes.getOrDefault(ion.ion(), 0.0f);
                double maxMoles = (conc * volumeLiters) / ion.amount();
                rate = Math.min(rate, maxMoles);
            }
            for (HalfReaction.ItemParticipant item : ar.reactantItems()) {
                float available = getTotalAvailableItemMoles(itemHandler, item.item());
                double maxMoles = available / item.count();
                rate = Math.min(rate, maxMoles);
            }
            if (gasNodeId != FluidNodeStore.INVALID) {
                for (HalfReaction.GasParticipant gas : ar.reactantGases()) {
                    int s = speciesTable.indexOf(gas.gas());
                    float available = (s != SpeciesTable.UNKNOWN) ? gasStore.amount(gasNodeId, s) : 0.0f;
                    double maxMoles = available / gas.amount();
                    rate = Math.min(rate, maxMoles);
                }
            }

            if (rate <= 1e-6) continue;

            for (HalfReaction.IonParticipant ion : ar.reactantIons()) {
                float current = solutes.getOrDefault(ion.ion(), 0.0f);
                float consumed = (float) ((rate * ion.amount()) / volumeLiters);
                float remaining = Math.max(0.0f, current - consumed);
                if (remaining > 1e-5f) {
                    solutes.put(ion.ion(), remaining);
                } else {
                    solutes.remove(ion.ion());
                }
            }

            for (HalfReaction.ItemParticipant item : ar.reactantItems()) {
                consumeFromItemBuffer(itemHandler, item.item(), (float) (rate * item.count()));
            }

            if (gasNodeId != FluidNodeStore.INVALID) {
                for (HalfReaction.GasParticipant gas : ar.reactantGases()) {
                    int s = speciesTable.indexOf(gas.gas());
                    if (s != SpeciesTable.UNKNOWN) {
                        float consumed = (float) (rate * gas.amount());
                        float cur = gasStore.amount(gasNodeId, s);
                        gasStore.setAmount(gasNodeId, s, Math.max(0.0f, cur - consumed));
                        fluidLevelData.touch(gasNodeId);
                    }
                }
            }

            for (HalfReaction.IonParticipant ion : ar.productIons()) {
                float current = solutes.getOrDefault(ion.ion(), 0.0f);
                float produced = (float) ((rate * ion.amount()) / volumeLiters);
                solutes.put(ion.ion(), current + produced);
            }

            for (HalfReaction.ItemParticipant item : ar.productItems()) {
                produceItemMoles(itemHandler, item.item(), (float) (rate * item.count()), controller);
            }

            if (gasNodeId != FluidNodeStore.INVALID) {
                for (HalfReaction.GasParticipant gas : ar.productGases()) {
                    int s = speciesTable.indexOf(gas.gas());
                    if (s != SpeciesTable.UNKNOWN) {
                        float produced = (float) (rate * gas.amount());
                        gasStore.add(gasNodeId, s, produced);
                        fluidLevelData.touch(gasNodeId);
                    }
                }
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

    private void produceItemMoles(IItemHandler itemHandler, Item item, float moles, ChemicalVatBlockEntity controller) {
        float progress = itemProductionProgress.getOrDefault(item, 0.0f) + moles;
        int wholeItems = (int) Math.floor(progress + 0.01f);
        if (wholeItems > 0) {
            progress = Math.max(0.0f, progress - wholeItems);
            while (wholeItems > 0) {
                int toInsert = Math.min(wholeItems, item.getDefaultInstance().getMaxStackSize());
                ItemStack stack = new ItemStack(item, toInsert);
                ItemStack remaining = ItemHandlerHelper.insertItem(itemHandler, stack, false);
                if (!remaining.isEmpty() && controller.getLevel() != null) {
                    // Inventory is completely full: drop remaining consolidated stack gently at vat top
                    ItemEntity entity = new ItemEntity(
                            controller.getLevel(),
                            controller.getBlockPos().getX() + 0.5,
                            controller.getBlockPos().getY() + 1.0,
                            controller.getBlockPos().getZ() + 0.5,
                            remaining);
                    entity.setDeltaMovement(0, 0.05, 0);
                    controller.getLevel().addFreshEntity(entity);
                }
                wholeItems -= toInsert;
            }
        }
        if (progress > 1e-4f) {
            itemProductionProgress.put(item, progress);
        } else {
            itemProductionProgress.remove(item);
        }
    }

    private void produceFluidMoles(ResourceLocation fluidId, float millibuckets, ChemicalVatBlockEntity controller) {
        float progress = fluidProductionProgress.getOrDefault(fluidId, 0.0f) + millibuckets;
        int wholeMb = (int) Math.floor(progress + 1e-4f);
        if (wholeMb > 0) {
            Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
            if (fluid != null && fluid != Fluids.EMPTY) {
                FluidStack stack = new FluidStack(fluid, wholeMb);
                int filled = controller.fillOutputTank(stack, IFluidHandler.FluidAction.EXECUTE);
                progress -= filled;
            }
        }
        if (progress > 1e-4f) {
            fluidProductionProgress.put(fluidId, progress);
        } else {
            fluidProductionProgress.remove(fluidId);
        }
    }

    /**
     * Drops any whole/rounded remaining buffered items when the vat is broken.
     */
    public void dropPartialItems(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) return;
        for (Map.Entry<Item, Float> entry : partialItemBuffer.entrySet()) {
            int toDrop = (int) Math.floor(entry.getValue() + 0.5f);
            if (toDrop > 0) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(entry.getKey(), toDrop));
            }
        }
        partialItemBuffer.clear();

        for (Map.Entry<Item, Float> entry : itemProductionProgress.entrySet()) {
            int toDrop = (int) Math.floor(entry.getValue() + 0.5f);
            if (toDrop > 0) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(entry.getKey(), toDrop));
            }
        }
        itemProductionProgress.clear();
    }

    public CompoundTag write(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag partialTag = new CompoundTag();
        partialItemBuffer.forEach((item, v) -> {
            if (v > 1e-5f) {
                partialTag.putFloat(BuiltInRegistries.ITEM.getKey(item).toString(), v);
            }
        });
        tag.put("PartialItems", partialTag);

        CompoundTag prodTag = new CompoundTag();
        itemProductionProgress.forEach((item, v) -> {
            if (v > 1e-5f) {
                prodTag.putFloat(BuiltInRegistries.ITEM.getKey(item).toString(), v);
            }
        });
        tag.put("ItemProduction", prodTag);

        CompoundTag fluidProdTag = new CompoundTag();
        fluidProductionProgress.forEach((fluidId, v) -> {
            if (v > 1e-5f) {
                fluidProdTag.putFloat(fluidId.toString(), v);
            }
        });
        tag.put("FluidProduction", fluidProdTag);

        return tag;
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        partialItemBuffer.clear();
        if (tag.contains("PartialItems")) {
            CompoundTag partialTag = tag.getCompound("PartialItems");
            for (String key : partialTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    partialItemBuffer.put(item, partialTag.getFloat(key));
                }
            }
        }

        itemProductionProgress.clear();
        if (tag.contains("ItemProduction")) {
            CompoundTag prodTag = tag.getCompound("ItemProduction");
            for (String key : prodTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    itemProductionProgress.put(item, prodTag.getFloat(key));
                }
            }
        }

        fluidProductionProgress.clear();
        if (tag.contains("FluidProduction")) {
            CompoundTag fluidProdTag = tag.getCompound("FluidProduction");
            for (String key : fluidProdTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    fluidProductionProgress.put(id, fluidProdTag.getFloat(key));
                }
            }
        }
    }
}
