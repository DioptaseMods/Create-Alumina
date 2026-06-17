package net.nerfashton.create_alumina.core;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.AbstractRegistrate;
import net.minecraft.world.item.Item;
import net.nerfashton.create_alumina.CreateAlumina;
import org.patryk3211.powergrid.AbstractPowerGridRegistrate;

import javax.annotation.Nullable;
import java.util.function.Function;

public class CreateAluminaRegistrate extends CreateRegistrate {
    protected CreateAluminaRegistrate(String modid) {
        super(modid);
    }

    public CreateAluminaRegistrate setTooltipModifierFactory(@Nullable Function<Item, TooltipModifier> factory) {
        currentTooltipModifierFactory = factory;
        return this;
    }

    public static CreateAluminaRegistrate create(String modid) {
        return new CreateAluminaRegistrate(modid);
    }
}
