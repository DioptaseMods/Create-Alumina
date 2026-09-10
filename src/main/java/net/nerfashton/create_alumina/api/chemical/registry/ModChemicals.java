package net.nerfashton.create_alumina.api.chemical.registry;

import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;

public class ModChemicals {

        public static final ResourceKey<Registry<ChemicalSpecies>> CHEMICAL_SPECIES_KEY = ResourceKey
                        .createRegistryKey(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "chemical_species"));

        public static final DeferredRegister<ChemicalSpecies> CHEMICAL_SPECIES = DeferredRegister
                        .create(CHEMICAL_SPECIES_KEY, CreateAlumina.MOD_ID);

        public static final Registry<ChemicalSpecies> REGISTRY = CHEMICAL_SPECIES.makeRegistry(builder -> {
        });

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> HYDROGEN = CreateAlumina.REGISTRATE
                        .chemicalSpecies("hydrogen", "H⁺", 1, 0.0f, 35.0f, 0xFFFFFFFF);

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> HYDROXIDE = CreateAlumina.REGISTRATE
                        .chemicalSpecies("hydroxide", "OH⁻", -1, 0.40f, 20.0f, 0xFFE0F7FA);

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> SODIUM = CreateAlumina.REGISTRATE
                        .chemicalSpecies("sodium", "Na⁺", 1, -2.71f, 5.0f, 0xFFFFF59D);

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> CHLORIDE = CreateAlumina.REGISTRATE
                        .chemicalSpecies("chloride", "Cl⁻", -1, 1.36f, 7.6f, 0xFFABD683);

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> ALUMINUM = CreateAlumina.REGISTRATE
                        .chemicalSpecies("aluminum", "Al³⁺", 3, -1.66f, 18.9f, 0xFFE8F5E9);

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> OXIDE = CreateAlumina.REGISTRATE
                        .chemicalSpecies("oxide", "O²⁻", -2, 1.23f, 12.0f, 0xFF91C3CF);

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> FLUORIDE = CreateAlumina.REGISTRATE
                        .chemicalSpecies("fluoride", "F⁻", -1, 2.87f, 7.6f, 0xFFFFFFCC); //Fix molar conductivity value!!

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> COPPER = CreateAlumina.REGISTRATE
                        .chemicalSpecies("copper", "Cu²⁺", 2, 0.34f, 10.7f, 0xFF21A3FF);

        public static final RegistryEntry<ChemicalSpecies, ChemicalSpecies> NITRATE = CreateAlumina.REGISTRATE
                .chemicalSpecies("nitrate", "NO₃⁻", -1, 0.96f, 7.1f, 0xFFEB7CE3);

        public static void register(IEventBus eventBus) {
                CHEMICAL_SPECIES.register(eventBus);
        }
}
