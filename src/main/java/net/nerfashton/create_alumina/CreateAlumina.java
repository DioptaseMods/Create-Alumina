package net.nerfashton.create_alumina;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.nerfashton.create_alumina.content.block.ModBlocks;
import net.nerfashton.create_alumina.content.block.entity.ModBlockEntities;
import net.nerfashton.create_alumina.content.block.entity.ModBlockEntityTypes;
import net.nerfashton.create_alumina.content.item.ModCreativeTabs;
import net.nerfashton.create_alumina.content.item.ModItems;
import net.nerfashton.create_alumina.core.CreateAluminaRegistrate;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(CreateAlumina.MOD_ID)
public class CreateAlumina {
    public static final String MOD_ID = "create_alumina";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static CreateAluminaRegistrate REGISTRATE = CreateAluminaRegistrate.create(MOD_ID)
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
            );

    public CreateAlumina(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        //ModCreativeTabs.register(modEventBus);

        REGISTRATE.registerEventListeners(modEventBus);

        ModBlocks.init();
        ModBlockEntityTypes.init();
        ModBlockEntities.init();
        ModItems.init();

        //Config Registration
        //modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
    }

    public static CreateAluminaRegistrate registrate() {
        return REGISTRATE;
    }
}
