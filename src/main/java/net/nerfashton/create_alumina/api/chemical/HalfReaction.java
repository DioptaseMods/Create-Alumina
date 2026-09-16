package net.nerfashton.create_alumina.api.chemical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.nerfashton.create_alumina.CreateAlumina;

import java.util.List;

/**
 * Represents an electrochemical half-reaction written as a reduction:
 * Reactants + n e⁻ ⇌ Products
 * with standard reduction potential E° (in Volts).
 */
public record HalfReaction(
        ResourceLocation id,
        double standardPotential,
        int electrons,
        List<IonParticipant> reactantIons,
        List<ItemParticipant> reactantItems,
        List<IonParticipant> productIons,
        List<ItemParticipant> productItems
) {

    public static final ResourceKey<net.minecraft.core.Registry<HalfReaction>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "half_reaction"));

    public HalfReaction {
        reactantIons = List.copyOf(reactantIons);
        reactantItems = List.copyOf(reactantItems);
        productIons = List.copyOf(productIons);
        productItems = List.copyOf(productItems);
        if (electrons <= 0) {
            electrons = 1;
        }
    }

    public record IonParticipant(ResourceLocation ion, float amount) {
        public static final Codec<IonParticipant> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("ion").forGetter(IonParticipant::ion),
                        Codec.FLOAT.optionalFieldOf("amount", 1.0f).forGetter(IonParticipant::amount)
                ).apply(instance, IonParticipant::new)
        );
    }

    public record ItemParticipant(Item item, int count) {
        public static final Codec<ItemParticipant> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemParticipant::item),
                        Codec.INT.optionalFieldOf("count", 1).forGetter(ItemParticipant::count)
                ).apply(instance, ItemParticipant::new)
        );

        public ItemStack toStack() {
            return new ItemStack(item, count);
        }
    }

    public static final Codec<HalfReaction> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("id", ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "unnamed"))
                            .forGetter(HalfReaction::id),
                    Codec.DOUBLE.fieldOf("e0").forGetter(HalfReaction::standardPotential),
                    Codec.INT.optionalFieldOf("electrons", 1).forGetter(HalfReaction::electrons),
                    IonParticipant.CODEC.listOf().optionalFieldOf("reactant_ions", List.of()).forGetter(HalfReaction::reactantIons),
                    ItemParticipant.CODEC.listOf().optionalFieldOf("reactant_items", List.of()).forGetter(HalfReaction::reactantItems),
                    IonParticipant.CODEC.listOf().optionalFieldOf("product_ions", List.of()).forGetter(HalfReaction::productIons),
                    ItemParticipant.CODEC.listOf().optionalFieldOf("product_items", List.of()).forGetter(HalfReaction::productItems)
            ).apply(instance, HalfReaction::new)
    );

    public HalfReaction withId(ResourceLocation newId) {
        return new HalfReaction(newId, standardPotential, electrons, reactantIons, reactantItems, productIons, productItems);
    }
}
