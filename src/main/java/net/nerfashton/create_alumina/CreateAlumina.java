package net.nerfashton.create_alumina;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.providers.ProviderType;
import net.createmod.catnip.lang.FontHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.nerfashton.create_alumina.api.chemical.AutonomousReactionManager;
import net.nerfashton.create_alumina.api.chemical.HalfReaction;
import net.nerfashton.create_alumina.api.chemical.HalfReactionManager;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import net.nerfashton.create_alumina.core.CreateAluminaRegistrate;
import net.nerfashton.create_alumina.core.ModCapabilities;
import net.nerfashton.create_alumina.registry.CABlockEntities;
import net.nerfashton.create_alumina.registry.CABlocks;
import net.nerfashton.create_alumina.registry.CACreativeTabs;
import net.nerfashton.create_alumina.registry.CAFluids;
import net.nerfashton.create_alumina.registry.CAGases;
import net.nerfashton.create_alumina.registry.CAItems;
import org.slf4j.Logger;

@Mod(CreateAlumina.MOD_ID)
public class CreateAlumina {
    public static final String MOD_ID = "create_alumina";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static CreateAluminaRegistrate REGISTRATE = CreateAluminaRegistrate.create(MOD_ID)
            .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public CreateAlumina(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ModCapabilities::register);
        REGISTRATE.registerEventListeners(modEventBus);

        CACreativeTabs.register(modEventBus);
        modEventBus.addListener(CACreativeTabs::addCreative);
        ModDataComponents.register(modEventBus);
        ModChemicals.register(modEventBus);

        modEventBus.addListener(CreateAlumina::registerDatapackRegistries);
        NeoForge.EVENT_BUS.addListener(CreateAlumina::onAddReloadListeners);

        CABlocks.init();
        CABlockEntities.init();
        CAItems.init();
        CAFluids.init();
        CAGases.init();

        REGISTRATE.addDataGenerator(ProviderType.LANG, prov -> {
            prov.add("creativetab.create_alumina.items_tab", "Create Alumina");
        });
    }

    public static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(
                HalfReaction.REGISTRY_KEY,
                HalfReaction.CODEC,
                HalfReaction.CODEC
        );
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(HalfReactionManager.INSTANCE);
        event.addListener(AutonomousReactionManager.INSTANCE);
    }
}
