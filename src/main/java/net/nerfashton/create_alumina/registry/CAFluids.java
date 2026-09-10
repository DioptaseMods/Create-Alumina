package net.nerfashton.create_alumina.registry;

import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.Tags;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.fluid.SolventFluid;
import net.nerfashton.create_alumina.api.fluid.SolventFluidType;
import org.joml.Vector3f;

import java.util.Map;

import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class CAFluids {
    public static final FluidEntry<SolventFluid> DISTILLED_WATER =
            REGISTRATE.solventFluid("distilled_water")
                    .lang("Distilled Water")
                    .bucket()
                    .lang("Distilled Water Bucket")
                    .tag(Tags.Items.BUCKETS)
                    .build()
                    .register();

    public static final FluidEntry<SolventFluid> MOLTEN_CRYOLITE =
            REGISTRATE.solventFluid("molten_cryolite",
                            ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "block/molten_metal_still"),
                            SolventFluidType.create(0xFFF88B41, new Vector3f(0.97f, 0.55f, 0.25f), 1.8f, 2.2f, 2.8f, Map.of()))
                    .lang("Molten Cryolite")
                    .properties(b -> b.temperature(1273).density(2100).viscosity(2500).lightLevel(15))
                    .register();

    public static void init() {
    }
}
