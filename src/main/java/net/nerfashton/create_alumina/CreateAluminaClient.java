package net.nerfashton.create_alumina;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.client.color.item.ItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.nerfashton.create_alumina.api.chemical.SolubleItem;
import net.nerfashton.create_alumina.api.chemical.SoluteData;
import net.nerfashton.create_alumina.api.chemistry_item.SolubleSaltItem;
import net.nerfashton.create_alumina.registry.CAItems;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CreateAlumina.MOD_ID, dist = Dist.CLIENT)
public class CreateAluminaClient {

    public CreateAluminaClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.getEventBus().addListener(CreateAluminaClient::registerItemColors);
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
    }
}
