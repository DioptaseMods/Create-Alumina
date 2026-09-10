package net.nerfashton.create_alumina.api.chemical;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import net.nerfashton.create_alumina.api.fluid.SolventFluidType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility helper methods for interacting with fluid solution data and ion
 * concentrations.
 */
public class ChemistryAPI {

    public record IonReaction(ResourceLocation ion1, ResourceLocation ion2, float energyPerMol) {
    }

    private static final List<IonReaction> ION_REACTIONS = new ArrayList<>();

    static {
        registerIonReaction(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydrogen"),
                ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "hydroxide"), 57.1f);
    }

    /**
     * Registers a 1:1 annihilation reaction between two ions with a given energy
     * release in kJ/mol.
     */
    public static void registerIonReaction(ResourceLocation ion1, ResourceLocation ion2, float energyPerMol) {
        ION_REACTIONS.add(new IonReaction(ion1, ion2, energyPerMol));
    }

    public static final float EPSILON = 1e-5f;

    /**
     * Processes any registered ion reactions by annihilating 1:1 ratios of
     * specified pairs.
     * Returns the total heat released (temperature change).
     */
    public static float processReactions(Map<ResourceLocation, Float> solutes) {
        float tempChange = 0f;
        for (IonReaction reaction : ION_REACTIONS) {
            float amount1 = solutes.getOrDefault(reaction.ion1(), 0f);
            float amount2 = solutes.getOrDefault(reaction.ion2(), 0f);
            if (amount1 > EPSILON && amount2 > EPSILON) {
                float min = Math.min(amount1, amount2);
                amount1 -= min;
                amount2 -= min;

                tempChange += min * reaction.energyPerMol();

                if (amount1 > EPSILON) {
                    solutes.put(reaction.ion1(), amount1);
                } else {
                    solutes.remove(reaction.ion1());
                }

                if (amount2 > EPSILON) {
                    solutes.put(reaction.ion2(), amount2);
                } else {
                    solutes.remove(reaction.ion2());
                }
            }
        }
        return tempChange;
    }

    /**
     * Checks if the given FluidStack is a solution (has ChemicalComposition
     * component).
     * If it is a base solvent fluid without ChemicalComposition, initializes an
     * empty ChemicalComposition component on it.
     */
    public static boolean isSolution(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.has(ModDataComponents.CHEMICAL_COMPOSITION.get())) {
            return true;
        }
        if (isBaseSolvent(stack.getFluid())) {
            int color = 0xFFFFFFFF;
            float temp = 300f;
            if (stack.getFluid().getFluidType() instanceof SolventFluidType solventType) {
                color = solventType.getTintColor();
                temp = solventType.getTemperature(stack);
            }
            stack.set(ModDataComponents.CHEMICAL_COMPOSITION.get(),
                    new ChemicalComposition(Map.of(), color, temp));
            return true;
        }
        return false;
    }

    /**
     * Recalculates the ARGB color of a solution based on its solutes and default white base.
     */
    public static int recalculateColor(Map<ResourceLocation, Float> solutes) {
        return recalculateColor(solutes, 0xFFFFFFFF);
    }

    /**
     * Recalculates the ARGB color of a solution based on its solutes, concentrations,
     * and the solvent's baseline tint color using the subtractive Beer-Lambert Law.
     */
    public static int recalculateColor(Map<ResourceLocation, Float> solutes, int baseColor) {
        if (solutes.isEmpty()) {
            return baseColor;
        }

        double rTransmitted = ((baseColor >> 16) & 0xFF) / 255.0;
        double gTransmitted = ((baseColor >> 8) & 0xFF) / 255.0;
        double bTransmitted = (baseColor & 0xFF) / 255.0;

        float intensityFactor = 0.2f;

        for (Map.Entry<ResourceLocation, Float> entry : solutes.entrySet()) {
            ChemicalSpecies species = ModChemicals.REGISTRY.get(entry.getKey());

            if (species != null) {
                float concentration = entry.getValue();

                if (concentration > 1e-6f) {
                    int hex = species.colorHex();

                    double r = ((hex >> 16) & 0xFF) / 255.0;
                    double g = ((hex >> 8) & 0xFF) / 255.0;
                    double b = (hex & 0xFF) / 255.0;

                    // Subtractive Beer-Lambert Law
                    double power = concentration * intensityFactor;
                    rTransmitted *= Math.pow(r, power);
                    gTransmitted *= Math.pow(g, power);
                    bTransmitted *= Math.pow(b, power);
                }
            }
        }

        int finalR = (int) Math.clamp(rTransmitted * 255.0, 0, 255);
        int finalG = (int) Math.clamp(gTransmitted * 255.0, 0, 255);
        int finalB = (int) Math.clamp(bTransmitted * 255.0, 0, 255);

        return (0xFF << 24) | (finalR << 16) | (finalG << 8) | finalB;
    }

    /**
     * Adds the specified moles of an ion to the FluidStack.
     * Updates the ChemicalComposition component on the fluid stack using
     * stack.set().
     */
    public static void addIon(FluidStack stack, ResourceLocation ionId, float moles) {
        if (!isSolution(stack)) {
            return;
        }
        int baseColor = 0xFFFFFFFF;
        float baseTemp = 300f;
        if (stack.getFluid().getFluidType() instanceof SolventFluidType solventType) {
            baseColor = solventType.getTintColor();
            baseTemp = solventType.getTemperature(stack);
        }
        ChemicalComposition currentData = stack.getOrDefault(ModDataComponents.CHEMICAL_COMPOSITION.get(),
                new ChemicalComposition(Map.of(), baseColor, baseTemp));
        Map<ResourceLocation, Float> newSolutes = new HashMap<>(currentData.solutes());

        float volumeFraction = stack.getAmount() / 1000.0f;
        float currentConcentration = newSolutes.getOrDefault(ionId, 0.0f);
        float currentMoles = currentConcentration * volumeFraction;

        float newTotalMoles = currentMoles + moles;

        if (newTotalMoles > EPSILON && volumeFraction > EPSILON) {
            newSolutes.put(ionId, newTotalMoles / volumeFraction);
        } else {
            newSolutes.remove(ionId);
        }

        float heatPerBucket = processReactions(newSolutes);

        float specificHeat = getSpecificHeat(stack.getFluid());
        float massPerBucket = 10.0f;
        float tempChange = calculateTemperatureChange(heatPerBucket, massPerBucket, specificHeat);

        int newColor = recalculateColor(newSolutes, baseColor);
        float newTemp = currentData.temperature() + tempChange;
        stack.set(ModDataComponents.CHEMICAL_COMPOSITION.get(), new ChemicalComposition(newSolutes, newColor, newTemp));
    }

    /**
     * Gets the concentration (in moles per bucket) of the specified ion in the
     * FluidStack.
     * Returns 0.0f if not present or if the stack is not a solution.
     */
    public static float getIonConcentration(FluidStack stack, ResourceLocation ionId) {
        if (stack.isEmpty() || !stack.has(ModDataComponents.CHEMICAL_COMPOSITION.get())) {
            return 0.0f;
        }
        ChemicalComposition data = stack.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (data == null) {
            return 0.0f;
        }
        return data.solutes().getOrDefault(ionId, 0.0f);
    }

    /**
     * Dissolves a compound into a solution FluidStack by adding its constituent
     * ions.
     * For example, 1 mole of NaCl adds 1 mole of Na+ and 1 mole of Cl-.
     *
     * @param stack           The target solution FluidStack
     * @param constituentIons Map of ion ResourceLocations to stoichiometric
     *                        coefficients (e.g. Na+ -> 1, Cl- -> 1)
     * @param molesOfCompound Amount of compound being dissolved in moles
     */
    public static void dissolveCompound(FluidStack stack, Map<ResourceLocation, Float> constituentIons,
            float molesOfCompound, float heatOfDissolution) {
        if (!isSolution(stack)) {
            return;
        }
        for (Map.Entry<ResourceLocation, Float> entry : constituentIons.entrySet()) {
            addIon(stack, entry.getKey(), entry.getValue() * molesOfCompound);
        }
        ChemicalComposition data = stack.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        assert data != null;

        float specificHeat = getSpecificHeat(stack.getFluid());
        float mass = getMass(stack);
        float q = heatOfDissolution * molesOfCompound;
        float deltaTemp = calculateTemperatureChange(q, mass, specificHeat);

        float newTemp = data.temperature() + deltaTemp;
        stack.set(ModDataComponents.CHEMICAL_COMPOSITION.get(),
                new ChemicalComposition(data.solutes(), data.color(), newTemp));
    }

    /**
     * Gets the specific heat of the given fluid.
     */
    public static float getSpecificHeat(Fluid fluid) {
        if (fluid.getFluidType() instanceof SolventFluidType baseType) {
            return baseType.getSpecificHeat();
        }
        return 4.184f; // Fallback specific heat (water)
    }

    /**
     * Gets the conductivity of the given fluid.
     */
    public static float getBaseConductivity(Fluid fluid) {
        if (fluid.getFluidType() instanceof SolventFluidType baseType) {
            return baseType.getBaseLineConductivity();
        }
        return 0.0f; // Fallback conductivity
    }

    public static ChemicalSpecies getSpeciesFromRL(ResourceLocation rl) {
        return ModChemicals.REGISTRY.get(rl);
    }

    /**
     * Calculates the temperature change (Delta T) based on Q = mcDeltaT.
     * @param heat The total heat added (Q)
     * @param mass The mass of the substance (m)
     * @param specificHeat The specific heat capacity (c)
     */
    public static float calculateTemperatureChange(float heat, float mass, float specificHeat) {
        return mass > 0 ? (heat / (mass * specificHeat)) : 0.0f;
    }

    /**
     * Gets the mass of a FluidStack in kg, assuming 1 Bucket (1000 mB) = 10 kg.
     */
    public static float getMass(FluidStack stack) {
        return (stack.getAmount() / 1000.0f) * 10.0f;
    }

    public static final double FARADAY_CONSTANT = 96485.0; // Coulombs / mol e-
    public static final double GAS_CONSTANT = 8.314; // J / (mol*K)
    public static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.018015; // L/mol liquid water equivalent

    /**
     * Solves Kw acid-base neutralization equilibrium: H+ + OH- -> H2O (Kw = 1.0e-14)
     * Returns moles of H+ and OH- neutralized.
     */
    public static double calculateNeutralizationMoles(double concH, double concOH, double volumeLiters) {
        double Kw = 1.0e-14;
        if (concH * concOH > Kw) {
            double b = -(concH + concOH);
            double cVal = (concH * concOH) - Kw;
            double disc = Math.max(0.0, b * b - 4 * cVal);
            double x = (-b - Math.sqrt(disc)) / 2.0;

            if (x > EPSILON) {
                return x * volumeLiters;
            }
        }
        return 0.0;
    }

    /**
     * Calculates precipitation moles based on ion concentrations, reaction stoichiometry, and Ksp.
     */
    public static double calculatePrecipitationMoles(double cCat, double cAn, int catR, int anR, double ksp, double volumeLiters) {
        if (cCat <= EPSILON || cAn <= EPSILON) {
            return 0.0;
        }

        double Qsp = Math.pow(cCat, catR) * Math.pow(cAn, anR);
        if (Qsp > ksp) {
            double excessRatio = Math.pow(Qsp / ksp, 1.0 / (catR + anR)) - 1.0;
            return Math.min(
                    cCat * volumeLiters / catR,
                    cAn * volumeLiters / anR
            ) * Math.min(0.8, excessRatio * 0.5);
        }
        return 0.0;
    }

    /**
     * Calculates the solution conductivity (mS/cm) from dissolved ion concentrations and solvent baseline.
     */
    public static double calculateSolutionConductivity(Map<ResourceLocation, Float> dissolvedIons, double volumeLiters, Fluid activeSolvent) {
        double totalConductivity = 0.0; // mS/cm
        for (Map.Entry<ResourceLocation, Float> entry : dissolvedIons.entrySet()) {
            ChemicalSpecies ion = getSpeciesFromRL(entry.getKey());
            double cM = entry.getValue() / volumeLiters;
            totalConductivity += Math.abs(ion.charge()) * ion.molarConductivity() * cM;
        }
        return Math.max(getBaseConductivity(activeSolvent), totalConductivity);
    }

    /**
     * Calculates internal cell resistance (Ohms) from solution conductivity.
     */
    public static double calculateCellResistance(double conductivity) {
        return Math.max(0.02, 50.0 / (conductivity + 0.01));
    }

    /**
     * Calculates half-reaction Nernst potential: E = E0 + (RT / nF) * ln(Q)
     */
    public static double calculateNernstPotential(double standardPotential, int electrons, double tempK, double logTerm) {
        return standardPotential + ((GAS_CONSTANT * tempK) / (electrons * FARADAY_CONSTANT)) * logTerm;
    }

    /**
     * Calculates current in Amperes for spontaneous galvanic reactions.
     */
    public static double calculateGalvanicCurrent(double eCell, double overpotentialThreshold, double cellResistance) {
        double drivingVoltage = eCell - overpotentialThreshold;
        return (drivingVoltage / cellResistance) * 10.0;
    }

    /**
     * Calculates current in Amperes for electrolytic reactions driven by external voltage.
     */
    public static double calculateElectrolysisCurrent(double netAppliedVoltage, double cellResistance) {
        return netAppliedVoltage / cellResistance;
    }

    /**
     * Calculates moles of electron transferred during time step dt at current I.
     */
    public static double calculateMolesElectronsTransferred(double currentAmps, double dt) {
        return (currentAmps * dt * 200.0) / FARADAY_CONSTANT;
    }

    /**
     * Calculates Joule heat generated by electrolysis overpotential and cell resistance (Joules).
     */
    public static double calculateJouleHeat(double appliedVoltage, double eCell, double currentAmps, double cellResistance, double dt) {
        double powerHeatW = Math.max(0.0, (appliedVoltage - Math.abs(eCell)) * currentAmps) + (currentAmps * currentAmps * cellResistance);
        return powerHeatW * dt;
    }

    public static boolean isBaseSolvent(Fluid fluid) {
        return fluid.getFluidType() instanceof SolventFluidType;
    }
}
