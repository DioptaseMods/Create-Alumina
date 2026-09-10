package net.nerfashton.create_alumina.registry;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.loaders.SeparateTransformsModelBuilder;
import net.nerfashton.create_alumina.api.chemistry_item.ChemicalFlaskItem;
import net.nerfashton.create_alumina.api.chemistry_item.RespiratorItem;
import net.nerfashton.create_alumina.api.chemistry_item.SolutionAnalyzerItem;

import static net.nerfashton.create_alumina.CreateAlumina.REGISTRATE;

public class CAItems {
    public static final ItemEntry<Item>
            STAINLESS_STEEL_INGOT = ingotEntry("stainless_steel_ingot"),
            HASTELLOY_INGOT = ingotEntry("hastelloy_ingot"),
            STAINLESS_STEEL_SHEET = REGISTRATE.item("stainless_steel_sheet", Item::new)
                    .register();


    private static ItemEntry<Item> ingotEntry(String name) {
        return REGISTRATE.item(name, Item::new)
                .tag()
                .register();
    }

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
