package net.nerfashton.create_alumina.api.chemical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.nerfashton.create_alumina.CreateAlumina;

import java.util.List;
import java.util.Optional;

/**
 * An autonomous reaction that executes inside of the Chemical Vat only.
 * Can be defined either by referencing a pair of HalfReactions (reduction & oxidation)
 * or by directly defining reactant and product species.
 */
public record AutonomousReaction(
        ResourceLocation id,
        String description,
        Optional<ResourceLocation> reduction,
        Optional<ResourceLocation> oxidation,
        List<HalfReaction.IonParticipant> reactantIons,
        List<HalfReaction.ItemParticipant> reactantItems,
        List<HalfReaction.IonParticipant> productIons,
        List<HalfReaction.ItemParticipant> productItems,
        float heatPerMol,
        float rate
) {
    public AutonomousReaction {
        reactantIons = List.copyOf(reactantIons);
        reactantItems = List.copyOf(reactantItems);
        productIons = List.copyOf(productIons);
        productItems = List.copyOf(productItems);
        if (rate <= 0.0f) {
            rate = 0.05f;
        }
    }

    public static final Codec<AutonomousReaction> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("id", ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "unnamed"))
                            .forGetter(AutonomousReaction::id),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(AutonomousReaction::description),
                    ResourceLocation.CODEC.optionalFieldOf("reduction").forGetter(AutonomousReaction::reduction),
                    ResourceLocation.CODEC.optionalFieldOf("oxidation").forGetter(AutonomousReaction::oxidation),
                    HalfReaction.IonParticipant.CODEC.listOf().optionalFieldOf("reactant_ions", List.of()).forGetter(AutonomousReaction::reactantIons),
                    HalfReaction.ItemParticipant.CODEC.listOf().optionalFieldOf("reactant_items", List.of()).forGetter(AutonomousReaction::reactantItems),
                    HalfReaction.IonParticipant.CODEC.listOf().optionalFieldOf("product_ions", List.of()).forGetter(AutonomousReaction::productIons),
                    HalfReaction.ItemParticipant.CODEC.listOf().optionalFieldOf("product_items", List.of()).forGetter(AutonomousReaction::productItems),
                    Codec.FLOAT.optionalFieldOf("heat_per_mol", 0.0f).forGetter(AutonomousReaction::heatPerMol),
                    Codec.FLOAT.optionalFieldOf("rate", 0.05f).forGetter(AutonomousReaction::rate)
            ).apply(instance, AutonomousReaction::new)
    );

    public AutonomousReaction withId(ResourceLocation newId) {
        return new AutonomousReaction(newId, description, reduction, oxidation, reactantIons, reactantItems, productIons, productItems, heatPerMol, rate);
    }
}
