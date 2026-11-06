package net.nerfashton.create_alumina.registry;

import cute.ame.celsius.Core.Event.SpeciesReloadEvent;
import cute.ame.celsius.Fluid.Data.FluidNodeStore;
import cute.ame.celsius.Fluid.Data.SpeciesDefinition;
import cute.ame.celsius.Fluid.Data.SpeciesTable;
import cute.ame.celsius.Fluid.Registry.FluidSpecies;
import cute.ame.celsius.Fluid.Registry.SpeciesRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.nerfashton.create_alumina.CreateAlumina;

import java.util.Collections;
import java.util.Map;

@EventBusSubscriber(modid = CreateAlumina.MOD_ID)
public class CAGases {

    // Common gas species keys
    public static final String CHLORINE_KEY = "cl2";
    public static final String HYDROGEN_KEY = "h2";
    public static final String OXYGEN_KEY = "o2";
    public static final String NITRIC_OXIDE_KEY = "no";
    public static final String CARBON_DIOXIDE_KEY = "co2";
    public static final String CARBON_MONOXIDE_KEY = "co";
    public static final String NITROGEN_KEY = "n2";
    public static final String SULFUR_DIOXIDE_KEY = "so2";
    public static final String AMMONIA_KEY = "nh3";

    // Species definitions for gases introduced by Create: Alumina
    public static final SpeciesDefinition CHLORINE = new SpeciesDefinition(
            70.906,     // molar mass (g/mol)
            479.0f,     // specific heat (J/(kg*K))
            239.11f,    // boiling point (K) (-34.04 °C)
            287_800.0f  // latent heat (J/kg)
    );

    public static final SpeciesDefinition NITRIC_OXIDE = new SpeciesDefinition(
            30.006,     // molar mass (g/mol)
            996.0f,     // specific heat (J/(kg*K))
            121.4f,     // boiling point (K) (-151.7 °C)
            460_000.0f  // latent heat (J/kg)
    );

    public static final SpeciesDefinition CARBON_MONOXIDE = new SpeciesDefinition(
            28.01,      // molar mass (g/mol)
            1040.0f,    // specific heat (J/(kg*K))
            81.65f,     // boiling point (K) (-191.5 °C)
            216_000.0f  // latent heat (J/kg)
    );

    public static final Map<String, SpeciesDefinition> CUSTOM_SPECIES = Map.of(
            CHLORINE_KEY, CHLORINE,
            NITRIC_OXIDE_KEY, NITRIC_OXIDE,
            CARBON_MONOXIDE_KEY, CARBON_MONOXIDE
    );

    // Active species indices in Celsius's SpeciesTable
    public static int CL2 = SpeciesTable.UNKNOWN;
    public static int H2 = SpeciesTable.UNKNOWN;
    public static int O2 = SpeciesTable.UNKNOWN;
    public static int NO = SpeciesTable.UNKNOWN;
    public static int CO2 = SpeciesTable.UNKNOWN;
    public static int CO = SpeciesTable.UNKNOWN;
    public static int N2 = SpeciesTable.UNKNOWN;
    public static int SO2 = SpeciesTable.UNKNOWN;
    public static int NH3 = SpeciesTable.UNKNOWN;

    public static void init() {
        // Initial setup hook called from CreateAlumina mod constructor
    }

    public static void resolve(SpeciesTable table) {
        CL2 = table.indexOf(CHLORINE_KEY);
        H2 = table.indexOf(HYDROGEN_KEY);
        O2 = table.indexOf(OXYGEN_KEY);
        NO = table.indexOf(NITRIC_OXIDE_KEY);
        CO2 = table.indexOf(CARBON_DIOXIDE_KEY);
        CO = table.indexOf(CARBON_MONOXIDE_KEY);
        N2 = table.indexOf(NITROGEN_KEY);
        SO2 = table.indexOf(SULFUR_DIOXIDE_KEY);
        NH3 = table.indexOf(AMMONIA_KEY);

        CreateAlumina.LOGGER.info("[Create: Alumina] Resolved gas species indices: cl2={}, h2={}, o2={}, no={}, co2={}, co={}",
                CL2, H2, O2, NO, CO2, CO);
    }

    @SubscribeEvent
    public static void onSpeciesReload(SpeciesReloadEvent event) {
        resolve(event.table());
    }

    public static int getIndex(String key) {
        return FluidSpecies.active().indexOf(SpeciesRegistry.key(key));
    }

    public static float getAmount(FluidNodeStore store, int nodeId, String gasKey) {
        int idx = getIndex(gasKey);
        if (idx == SpeciesTable.UNKNOWN || nodeId == FluidNodeStore.INVALID) return 0.0f;
        return store.amount(nodeId, idx);
    }

    public static void addGas(FluidNodeStore store, int nodeId, String gasKey, float moles) {
        int idx = getIndex(gasKey);
        if (idx != SpeciesTable.UNKNOWN && nodeId != FluidNodeStore.INVALID && moles > 0.0f) {
            store.add(nodeId, idx, moles);
        }
    }

    public static float consumeGas(FluidNodeStore store, int nodeId, String gasKey, float maxMoles) {
        int idx = getIndex(gasKey);
        if (idx == SpeciesTable.UNKNOWN || nodeId == FluidNodeStore.INVALID || maxMoles <= 0.0f) return 0.0f;
        float current = store.amount(nodeId, idx);
        float consumed = Math.min(current, maxMoles);
        store.setAmount(nodeId, idx, current - consumed);
        return consumed;
    }
}
