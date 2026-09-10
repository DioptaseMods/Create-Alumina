package net.nerfashton.create_alumina.api.chemical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.nerfashton.create_alumina.api.util.ColorMixer;

import java.util.HashMap;
import java.util.Map;

/**
 * Data structure representing a chemical mixture, including solutes (moles/bucket),
 * the dynamically blended color, and temperature.
 * By storing concentrations rather than absolute moles, this data component gracefully
 * handles FluidStack splitting.
 */
public record ChemicalComposition(Map<ResourceLocation, Float> solutes, int color, float temperature) {

    public ChemicalComposition {
        solutes = Map.copyOf(solutes);
    }

    public static final Codec<ChemicalComposition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                            .fieldOf("solutes")
                            .forGetter(ChemicalComposition::solutes),
                    Codec.INT.fieldOf("color").forGetter(ChemicalComposition::color),
                    Codec.FLOAT.fieldOf("temperature").forGetter(ChemicalComposition::temperature)
            ).apply(instance, ChemicalComposition::new)
    );

    public static final StreamCodec<ByteBuf, ChemicalComposition> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.FLOAT),
            ChemicalComposition::solutes,
            ByteBufCodecs.INT,
            ChemicalComposition::color,
            ByteBufCodecs.FLOAT,
            ChemicalComposition::temperature,
            ChemicalComposition::new
    );

    /**
     * Volume-weighted mixing of this composition with another.
     * @param other The other composition
     * @param thisMb The volume of this composition being mixed
     * @param otherMb The volume of the other composition being mixed
     * @return A new blended ChemicalComposition
     */
    public ChemicalComposition mix(ChemicalComposition other, int thisMb, int otherMb) {
        if (thisMb <= 0 && otherMb <= 0) return this;
        if (thisMb <= 0) return other;
        if (otherMb <= 0) return this;

        long totalMb = (long) thisMb + otherMb;

        // 1. Volume-weighted blend of solutes
        Map<ResourceLocation, Float> newSolutes = new HashMap<>();
        
        for (Map.Entry<ResourceLocation, Float> entry : this.solutes.entrySet()) {
            float myMoles = entry.getValue() * thisMb;
            newSolutes.put(entry.getKey(), myMoles);
        }

        for (Map.Entry<ResourceLocation, Float> entry : other.solutes.entrySet()) {
            float otherMoles = entry.getValue() * otherMb;
            newSolutes.merge(entry.getKey(), otherMoles, Float::sum);
        }

        // Convert back to concentration (Moles / Bucket)
        for (Map.Entry<ResourceLocation, Float> entry : newSolutes.entrySet()) {
            newSolutes.put(entry.getKey(), entry.getValue() / totalMb);
        }

        int newColor = ColorMixer.blend(this.color, thisMb, other.color, otherMb);

        float newTemp = ((this.temperature * thisMb) + (other.temperature * otherMb)) / totalMb;

        return new ChemicalComposition(newSolutes, newColor, newTemp);
    }
}
