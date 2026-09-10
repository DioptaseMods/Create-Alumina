package net.nerfashton.create_alumina;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.providers.ProviderType;
import net.createmod.catnip.lang.FontHelper;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import net.nerfashton.create_alumina.registry.*;
import net.nerfashton.create_alumina.core.CreateAluminaRegistrate;
import net.nerfashton.create_alumina.core.ModCapabilities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CreateAlumina.MOD_ID)
public class CreateAlumina {
    public static final String MOD_ID = "create_alumina";

    public static CreateAluminaRegistrate REGISTRATE = CreateAluminaRegistrate.create(MOD_ID)
            .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public CreateAlumina(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ModCapabilities::register);
        REGISTRATE.registerEventListeners(modEventBus);

        // NeoForge.EVENT_BUS.register(this);

        CACreativeTabs.register(modEventBus);
        modEventBus.addListener(CACreativeTabs::addCreative);
        ModDataComponents.register(modEventBus);

        CABlocks.init();
        CABlockEntities.init();
        CAItems.init();
        CAFluids.init();

        REGISTRATE.addDataGenerator(ProviderType.LANG, prov -> {
            // UI & Tooltips
            prov.add("creativetab.create_alumina.items_tab", "Create Alumina");
        });

        // modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
