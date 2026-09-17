package net.nerfashton.create_alumina;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.nerfashton.create_alumina.api.chemical.ChemicalComposition;
import net.nerfashton.create_alumina.api.chemical.SolubleItem;
import net.nerfashton.create_alumina.api.chemical.SoluteData;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import net.nerfashton.create_alumina.api.chemistry_item.ChemicalFlaskItem;
import net.nerfashton.create_alumina.api.chemistry_item.SolubleSaltItem;
import net.nerfashton.create_alumina.api.fluid.SolventFluidType;
import net.nerfashton.create_alumina.registry.CAItems;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CreateAlumina.MOD_ID, dist = Dist.CLIENT)
public class CreateAluminaClient {

    public CreateAluminaClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.getEventBus().addListener(CreateAluminaClient::clientSetup);
        container.getEventBus().addListener(CreateAluminaClient::registerItemColors);
    }

    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    CAItems.CHEMICAL_FLASK.get(),
                    ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "filled"),
                    (stack, level, entity, seed) -> ChemicalFlaskItem.getContainedFluid(stack).isEmpty() ? 0.0f : 1.0f
            );
        });
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColor saltColorHandler = (stack, tintIndex) -> {
            if (tintIndex == 0) {
                SoluteData data = SolubleItem.getSoluteDataFrom(stack);
                if (data != null) {
                    return SolubleSaltItem.getColorForComposition(data.ions());
                }
            }
            return 0xFFFFFFFF;
        };

        for (ItemEntry<SolubleSaltItem> entry : CAItems.ALL_SALTS) {
            event.register(saltColorHandler, entry.get());
        }

        event.register((stack, tintIndex) -> {
            FluidStack fluid = ChemicalFlaskItem.getContainedFluid(stack);
            if (!fluid.isEmpty()) {
                // In chemical_flask_filled model: layer0 is fluid mask
                if (tintIndex == 0) {
                    ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
                    if (comp != null) {
                        return comp.color();
                    }
                    if (fluid.getFluid().getFluidType() instanceof SolventFluidType solventType) {
                        return solventType.getTintColor();
                    }
                    return 0xFF3F76E4;
                }
                // layer1 is the un-tinted glass flask outline/reflections
                return 0xFFFFFFFF;
            }
            // In empty model: layer0 is the un-tinted glass flask
            return 0xFFFFFFFF;
        }, CAItems.CHEMICAL_FLASK.get());
    }
}
