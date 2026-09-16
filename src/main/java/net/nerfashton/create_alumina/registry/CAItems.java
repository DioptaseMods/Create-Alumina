package net.nerfashton.create_alumina.registry;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.loaders.SeparateTransformsModelBuilder;
import net.nerfashton.create_alumina.CreateAlumina;
import net.nerfashton.create_alumina.api.chemical.SoluteData;
import net.nerfashton.create_alumina.api.chemistry_item.AlkaliMetalItem;
import net.nerfashton.create_alumina.api.chemistry_item.ChemicalFlaskItem;
import net.nerfashton.create_alumina.api.chemistry_item.PassivatingMetalItem;
import net.nerfashton.create_alumina.api.chemistry_item.RespiratorItem;
import net.nerfashton.create_alumina.api.chemistry_item.SolubleSaltItem;
import net.nerfashton.create_alumina.api.chemistry_item.SolutionAnalyzerItem;

import java.util.List;
import java.util.Map;

import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class CAItems {
    public static final ItemEntry<PassivatingMetalItem>
            ALUMINUM_INGOT = REGISTRATE.item("aluminum_ingot", PassivatingMetalItem::new)
                    .tag()
                    .register(),
            STAINLESS_STEEL_INGOT = REGISTRATE.item("stainless_steel_ingot", PassivatingMetalItem::new)
                    .tag()
                    .register();

    public static final ItemEntry<Item>
            HASTELLOY_INGOT = ingotEntry("hastelloy_ingot"),
            STAINLESS_STEEL_SHEET = REGISTRATE.item("stainless_steel_sheet", Item::new)
                    .register();

    public static final ItemEntry<AlkaliMetalItem>
            SODIUM_INGOT = REGISTRATE.item("sodium_ingot", AlkaliMetalItem::new)
                    .tag()
                    .lang("Sodium Ingot")
                    .register();

    private static ItemEntry<Item> ingotEntry(String name) {
        return REGISTRATE.item(name, Item::new)
                .tag()
                .register();
    }

    private static ResourceLocation modLoc(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, path);
    }

    private static ItemEntry<SolubleSaltItem> saltEntry(String name, String lang, SoluteData soluteData) {
        return REGISTRATE.item(name, p -> new SolubleSaltItem(p, soluteData))
                .lang(lang)
                .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), "minecraft:item/generated")
                        .texture("layer0", prov.modLoc("item/salt_powder")))
                .register();
    }

    // Soluble Salt Items with Ksp and Ion Compositions
    public static final ItemEntry<SolubleSaltItem>
            SALT_POWDER = saltEntry("salt_powder", "Salt Powder", new SoluteData(
                    Map.of(modLoc("sodium"), 1.0f, modLoc("chloride"), 1.0f),
                    36.0, 3.88f, 1.0f)),
            SODIUM_CHLORIDE = saltEntry("sodium_chloride", "Sodium Chloride", new SoluteData(
                    Map.of(modLoc("sodium"), 1.0f, modLoc("chloride"), 1.0f),
                    36.0, 3.88f, 1.0f)),
            COPPER_CHLORIDE = saltEntry("copper_chloride", "Copper(II) Chloride", new SoluteData(
                    Map.of(modLoc("copper"), 1.0f, modLoc("chloride"), 2.0f),
                    5.0, -51.4f, 1.0f)),
            COPPER_NITRATE = saltEntry("copper_nitrate", "Copper(II) Nitrate", new SoluteData(
                    Map.of(modLoc("copper"), 1.0f, modLoc("nitrate"), 2.0f),
                    10.0, -45.0f, 1.0f)),
            SODIUM_HYDROXIDE = saltEntry("sodium_hydroxide", "Sodium Hydroxide", new SoluteData(
                    Map.of(modLoc("sodium"), 1.0f, modLoc("hydroxide"), 1.0f),
                    50.0, -44.5f, 1.0f)),
            ALUMINUM_FLUORIDE = saltEntry("aluminum_fluoride", "Aluminum Fluoride", new SoluteData(
                    Map.of(modLoc("aluminum"), 1.0f, modLoc("fluoride"), 3.0f),
                    0.001, -15.0f, 1.0f)),
            COPPER_SULFATE = saltEntry("copper_sulfate", "Copper(II) Sulfate", new SoluteData(
                    Map.of(modLoc("copper"), 1.0f, modLoc("sulfate"), 1.0f),
                    1.4, -66.5f, 1.0f)),
            IRON_SULFATE = saltEntry("iron_sulfate", "Iron(II) Sulfate", new SoluteData(
                    Map.of(modLoc("iron"), 1.0f, modLoc("sulfate"), 1.0f),
                    1.9, -16.8f, 1.0f)),
            IRON_CHLORIDE = saltEntry("iron_chloride", "Iron(III) Chloride", new SoluteData(
                    Map.of(modLoc("iron_iii"), 1.0f, modLoc("chloride"), 3.0f),
                    5.6, -95.0f, 1.0f)),
            POTASSIUM_CHLORIDE = saltEntry("potassium_chloride", "Potassium Chloride", new SoluteData(
                    Map.of(modLoc("potassium"), 1.0f, modLoc("chloride"), 1.0f),
                    4.8, 17.2f, 1.0f)),
            ALUMINUM_SULFATE = saltEntry("aluminum_sulfate", "Aluminum Sulfate", new SoluteData(
                    Map.of(modLoc("aluminum"), 2.0f, modLoc("sulfate"), 3.0f),
                    1.1, -120.0f, 1.0f)),
            ALUMINA = saltEntry("alumina", "Alumina Powder", new SoluteData(
                    Map.of(modLoc("aluminum"), 2.0f, modLoc("oxide"), 3.0f),
                    1e-12, -1675.0f, 1.0f)),
            CRYOLITE = saltEntry("cryolite", "Synthetic Cryolite", new SoluteData(
                    Map.of(modLoc("sodium"), 3.0f, modLoc("aluminum"), 1.0f, modLoc("fluoride"), 6.0f),
                    0.04, 25.0f, 1.0f));

    public static final List<ItemEntry<SolubleSaltItem>> ALL_SALTS = List.of(
            SALT_POWDER,
            SODIUM_CHLORIDE,
            COPPER_CHLORIDE,
            COPPER_NITRATE,
            SODIUM_HYDROXIDE,
            ALUMINUM_FLUORIDE,
            COPPER_SULFATE,
            IRON_SULFATE,
            IRON_CHLORIDE,
            POTASSIUM_CHLORIDE,
            ALUMINUM_SULFATE,
            ALUMINA,
            CRYOLITE
    );

    public static final ItemEntry<SolutionAnalyzerItem> SOLUTION_ANALYZER = REGISTRATE.item("solution_analyzer", SolutionAnalyzerItem::new)
            .properties(p -> p.stacksTo(1))
            .lang("Solution Analyzer")
            .register();

    public static final ItemEntry<ChemicalFlaskItem> CHEMICAL_FLASK = REGISTRATE.item("chemical_flask", ChemicalFlaskItem::new)
            .register();

    public static final ItemEntry<RespiratorItem> RESPIRATOR = REGISTRATE.item("respirator", RespiratorItem::new)
            .properties(p -> p.stacksTo(1))
            .model((ctx, prov) -> {
                ItemModelBuilder baseBuilder = prov.withExistingParent(ctx.getName() + "_base", "minecraft:item/generated")
                        .texture("layer0", prov.modLoc("item/respirator"));
                ItemModelBuilder headBuilder = prov.getBuilder(ctx.getName() + "_head")
                        .parent(prov.getExistingFile(prov.modLoc("block/respirator")))
                        .transforms()
                        .transform(ItemDisplayContext.HEAD)
                        .rotation(0, 270, 0)
                        .scale(1.7F, 1.7F, 1.7F)
                        .end()
                        .end();
                prov.getBuilder(ctx.getName())
                        .guiLight(BlockModel.GuiLight.FRONT)
                        .customLoader(SeparateTransformsModelBuilder::begin)
                        .base(baseBuilder)
                        .perspective(ItemDisplayContext.HEAD, headBuilder);
            })
            .register();

    public static void init() {
    }
}
