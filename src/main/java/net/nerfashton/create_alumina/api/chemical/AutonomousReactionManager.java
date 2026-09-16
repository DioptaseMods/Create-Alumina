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
 * Loads JSON-defined Autonomous Reactions from data packs (path: data/<namespace>/chemical_reactions/*.json).
 */
public class AutonomousReactionManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final AutonomousReactionManager INSTANCE = new AutonomousReactionManager();
    private static final Map<ResourceLocation, AutonomousReaction> REACTIONS = new HashMap<>();

    private AutonomousReactionManager() {
        super(GSON, "chemical_reactions");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        REACTIONS.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();

            try {
                JsonObject normalized = normalizeJson(element.getAsJsonObject());
                AutonomousReaction reaction = AutonomousReaction.CODEC.parse(JsonOps.INSTANCE, normalized)
                        .getOrThrow(err -> new IllegalArgumentException("Failed to decode AutonomousReaction " + id + ": " + err));

                AutonomousReaction finalReaction = reaction.withId(id);
                REACTIONS.put(id, finalReaction);
                CreateAlumina.LOGGER.debug("Loaded AutonomousReaction: {}", id);
            } catch (Exception ex) {
                CreateAlumina.LOGGER.error("Failed to load AutonomousReaction from {}: {}", id, ex.getMessage());
            }
        }

        CreateAlumina.LOGGER.info("Loaded {} autonomous reactions.", REACTIONS.size());
    }

    private JsonObject normalizeJson(JsonObject obj) {
        JsonObject result = obj.deepCopy();

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

        // Support nested structure: "products": { "ions": [...], "items": [...] }
        if (result.has("products") && result.get("products").isJsonObject()) {
            JsonObject products = result.getAsJsonObject("products");
            if (products.has("ions") && !result.has("product_ions")) {
                result.add("product_ions", products.get("ions"));
            }
            if (products.has("items") && !result.has("product_items")) {
                result.add("product_items", products.get("items"));
            }
        }

        return result;
    }

    public static @Nullable AutonomousReaction get(ResourceLocation id) {
        return REACTIONS.get(id);
    }

    public static Collection<AutonomousReaction> getAll() {
        return Collections.unmodifiableCollection(REACTIONS.values());
    }
}
