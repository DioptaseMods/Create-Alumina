package net.nerfashton.create_alumina.api.chemical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Defines a specific chemical species / ion with physical and electrochemical properties.
 *
 * @param symbol            Chemical notation (e.g., "Na+", "SO4 2-")
 * @param charge            Oxidation state / charge (e.g., +1, -2)
 * @param standardPotential Standard reduction potential in Volts
 * @param colorHex          Baseline ARGB / RGB tint of the ion
 * @param molarConductivity
 */
public record ChemicalSpecies(String symbol, int charge, float standardPotential, int colorHex, float molarConductivity ) {

    public static final Codec<ChemicalSpecies> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("symbol").forGetter(ChemicalSpecies::symbol),
                    Codec.INT.fieldOf("charge").forGetter(ChemicalSpecies::charge),
                    Codec.FLOAT.fieldOf("standardPotential").forGetter(ChemicalSpecies::standardPotential),
                    Codec.INT.fieldOf("colorHex").forGetter(ChemicalSpecies::colorHex),
                    Codec.FLOAT.fieldOf("molarConductivity").forGetter(ChemicalSpecies::molarConductivity)
            ).apply(instance, ChemicalSpecies::new)
    );

    public static final StreamCodec<ByteBuf, ChemicalSpecies> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ChemicalSpecies::symbol,
            ByteBufCodecs.VAR_INT, ChemicalSpecies::charge,
            ByteBufCodecs.FLOAT, ChemicalSpecies::standardPotential,
            ByteBufCodecs.VAR_INT, ChemicalSpecies::colorHex,
            ByteBufCodecs.FLOAT, ChemicalSpecies::molarConductivity,
            ChemicalSpecies::new
    );
}
