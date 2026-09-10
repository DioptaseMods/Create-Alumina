package net.nerfashton.create_alumina.api.fluid;

import com.tterrag.registrate.builders.FluidBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import org.joml.Vector3f;

import java.util.Map;
import java.util.function.Consumer;

public class SolventFluidType extends FluidType {
    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;
    private final int tintColor;
    private final Vector3f fogColor;
    private final float specificHeat;
    private final float breakdownVoltage;
    private final float baseLineConductivity;
    private final Map<ResourceLocation, Float> breakdownProducts;

    public SolventFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture,
                            int tintColor, Vector3f fogColor, float specificHeat,
                            float breakdownVoltage, float baseLineConductivity, Map<ResourceLocation, Float> breakdownProducts) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.tintColor = tintColor;
        this.fogColor = fogColor;
        this.specificHeat = specificHeat;
        this.breakdownVoltage = breakdownVoltage;
        this.baseLineConductivity = baseLineConductivity;
        this.breakdownProducts = breakdownProducts;
    }

    public SolventFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
        this(properties, stillTexture, flowingTexture, 0xFFFFFFFF, null, 4.184f, 1.23f, 0.055f, Map.of());
    }

    public static FluidBuilder.FluidTypeFactory create(int tintColor) {
        return (properties, still, flow) -> new SolventFluidType(properties, still, flow, tintColor, null, 4.184f, 1.23f, 0.055f, Map.of());
    }

    public static FluidBuilder.FluidTypeFactory create(int tintColor, float specificHeat, float breakdownVoltage, float baseLineConductivity) {
        return (properties, still, flow) -> new SolventFluidType(properties, still, flow, tintColor, null, specificHeat, breakdownVoltage, baseLineConductivity, Map.of());
    }

    public static FluidBuilder.FluidTypeFactory create(int tintColor, Vector3f fogColor, float specificHeat, float breakdownVoltage, float baseLineConductivity, Map<ResourceLocation, Float> breakdownProducts) {
        return (properties, still, flow) -> new SolventFluidType(properties, still, flow, tintColor, fogColor, specificHeat, breakdownVoltage, baseLineConductivity, breakdownProducts);
    }

    public ResourceLocation getStillTexture() {
        return stillTexture;
    }

    public ResourceLocation getFlowingTexture() {
        return flowingTexture;
    }

    public int getTintColor() {
        return tintColor;
    }

    public Vector3f getFogColor() {
        return fogColor;
    }

    public float getBaseLineConductivity() {
        return baseLineConductivity;
    }

    public float getSpecificHeat() {
        return specificHeat;
    }

    public float getBreakdownVoltage() {
        return breakdownVoltage;
    }

    public Map<ResourceLocation, Float> getBreakdownProducts() {
        return breakdownProducts;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return stillTexture;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flowingTexture;
            }

            @Override
            public int getTintColor() {
                return tintColor;
            }

            @Override
            public int getTintColor(FluidStack stack) {
                if (stack.has(ModDataComponents.CHEMICAL_COMPOSITION.get())) {
                    return stack.get(ModDataComponents.CHEMICAL_COMPOSITION.get()).color();
                }
                return tintColor;
            }

            @Override
            public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                return fogColor != null ? fogColor : fluidFogColor;
            }
        });
    }
}
