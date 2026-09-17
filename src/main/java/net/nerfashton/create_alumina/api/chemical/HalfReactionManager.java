package net.nerfashton.create_alumina.api.chemical;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.nerfashton.create_alumina.CreateAlumina;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads JSON-defined HalfReactions from data packs (path: data/<namespace>/half_reactions/*.json).
 */
public class HalfReactionManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final HalfReactionManager INSTANCE = new HalfReactionManager();
    private static final Map<ResourceLocation, HalfReaction> HALF_REACTIONS = new HashMap<>();

    private HalfReactionManager() {
        super(GSON, "half_reactions");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        HALF_REACTIONS.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();

            try {
                JsonObject normalized = normalizeJson(element.getAsJsonObject());
                HalfReaction reaction = HalfReaction.CODEC.parse(JsonOps.INSTANCE, normalized)
                        .getOrThrow(err -> new IllegalArgumentException("Failed to decode HalfReaction " + id + ": " + err));

                HalfReaction finalReaction = reaction.withId(id);
                HALF_REACTIONS.put(id, finalReaction);
                CreateAlumina.LOGGER.debug("Loaded HalfReaction: {}", id);
            } catch (Exception ex) {
                CreateAlumina.LOGGER.error("Failed to load HalfReaction from {}: {}", id, ex.getMessage());
            }
        }

        CreateAlumina.LOGGER.info("Loaded {} half-reactions.", HALF_REACTIONS.size());
    }

    /**
     * Normalizes alternative JSON layouts (e.g. standard_potential vs e0, nested reactants/products)
     * so datapack creators can write clean and intuitive JSON.
     */
    private JsonObject normalizeJson(JsonObject obj) {
        JsonObject result = obj.deepCopy();

        // Map standard_potential -> e0 if needed
        if (!result.has("e0") && result.has("standard_potential")) {
            result.add("e0", result.get("standard_potential"));
        }

        // Support nested structure: "reactants": { "ions": [...], "items": [...] }
        if (result.has("reactants") && result.get("reactants").isJsonObject()) {
            JsonObject reactants = result.getAsJsonObject("reactants");
            if (reactants.has("ions") && !result.has("reactant_ions")) {
                result.add("reactant_ions", reactants.get("ions"));
            }
            if (reactants.has("items") && !result.has("reactant_items")) {
                result.add("reactant_items", reactants.get("items"));
            }
        }

        // Support nested structure: "products": { "ions": [...], "items": [...], "fluids": [...] }
        if (result.has("products") && result.get("products").isJsonObject()) {
            JsonObject products = result.getAsJsonObject("products");
            if (products.has("ions") && !result.has("product_ions")) {
                result.add("product_ions", products.get("ions"));
            }
            if (products.has("items") && !result.has("product_items")) {
                result.add("product_items", products.get("items"));
            }
            if (products.has("fluids") && !result.has("product_fluids")) {
                result.add("product_fluids", products.get("fluids"));
            }
        }

        return result;
    }

    public static @Nullable HalfReaction get(ResourceLocation id) {
        return HALF_REACTIONS.get(id);
    }

    public static Collection<HalfReaction> getAll() {
        return Collections.unmodifiableCollection(HALF_REACTIONS.values());
    }
}
