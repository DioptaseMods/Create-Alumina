package net.nerfashton.create_alumina;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.nerfashton.create_alumina.registry.CABlockEntities;
import net.nerfashton.create_alumina.registry.CABlocks;
import net.nerfashton.create_alumina.registry.CAItems;
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
        // Do not add this line if there are no @SubscribeEvent-annotated functions in
        // this class, like onServerStarting() below.
        // NeoForge.EVENT_BUS.register(this);

        // ModCreativeTabs.register(modEventBus);

        REGISTRATE.registerEventListeners(modEventBus);

        CABlocks.init();
        CABlockEntities.init();
        CAItems.init();

        // Config Registration
        // modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
