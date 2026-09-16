package net.nerfashton.create_alumina.api.chemical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Physical data defining a soluble chemical compound.
 *
 * @param ions               Map of constituent ion ResourceLocations to stoichiometric coefficients (e.g. Na+ -> 1, Cl- -> 1)
 * @param ksp                Solubility product constant (Ksp) at standard temperature
 * @param heatOfDissolution  Enthalpy of dissolution in kJ/mol (positive = endothermic, negative = exothermic)
 * @param molesPerItem       Amount of compound in moles represented by 1 item stack count (default: 1.0 mol)
 */
public record SoluteData(
        Map<ResourceLocation, Float> ions,
        double ksp,
        float heatOfDissolution,
        float molesPerItem
) {
    public SoluteData {
        ions = Map.copyOf(ions);
        if (molesPerItem <= 0.0f) {
            molesPerItem = 1.0f;
        }
    }

    public static final Codec<SoluteData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                            .fieldOf("ions")
                            .forGetter(SoluteData::ions),
                    Codec.DOUBLE.fieldOf("ksp").forGetter(SoluteData::ksp),
                    Codec.FLOAT.optionalFieldOf("heat_of_dissolution", 0.0f).forGetter(SoluteData::heatOfDissolution),
                    Codec.FLOAT.optionalFieldOf("moles_per_item", 1.0f).forGetter(SoluteData::molesPerItem)
            ).apply(instance, SoluteData::new)
    );

    public static final StreamCodec<ByteBuf, SoluteData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.FLOAT),
            SoluteData::ions,
            ByteBufCodecs.DOUBLE,
            SoluteData::ksp,
            ByteBufCodecs.FLOAT,
            SoluteData::heatOfDissolution,
            ByteBufCodecs.FLOAT,
            SoluteData::molesPerItem,
            SoluteData::new
    );
}
