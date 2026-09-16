package net.nerfashton.create_alumina.content.fluids.vat.electrode;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;

import javax.annotation.Nullable;
import java.util.List;

public class CarbonElectrodeBlockEntity extends SmartBlockEntity implements IElectricEntity, IHaveGoggleInformation {
    protected ElectrodeElectricBehaviour electricBehaviour;

    public CarbonElectrodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public BlockPos getVatControllerPos() {
        if (level == null) return null;
        BlockPos below = worldPosition.below();
        if (level.getBlockEntity(below) instanceof ChemicalVatBlockEntity vatBE) {
            ChemicalVatBlockEntity controller = vatBE.getControllerBE();
            if (controller != null) {
                return controller.getBlockPos();
            }
        }
        return null;
    }

    @Nullable
    public ChemicalVatBlockEntity getVatControllerBE() {
        if (level == null) return null;
        BlockPos below = worldPosition.below();
        if (level.getBlockEntity(below) instanceof ChemicalVatBlockEntity vatBE) {
            return vatBE.getControllerBE();
        }
        return null;
    }

    public int getTerminalIndex() {
        BlockState state = getBlockState();
        if (state.hasProperty(CarbonElectrodeBlock.TYPE)) {
            return state.getValue(CarbonElectrodeBlock.TYPE).getTerminalIndex();
        }
        return 0;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        electricBehaviour = new ElectrodeElectricBehaviour(this, this::getVatControllerPos, this::getTerminalIndex);
        behaviours.add(electricBehaviour);
    }

    public ElectrodeElectricBehaviour getElectricBehaviour() {
        return electricBehaviour;
    }

    public void onTypeChanged() {
        if (electricBehaviour != null) {
            electricBehaviour.breakConnections();
            electricBehaviour.refreshEndpoints();
            notifyUpdate();
        }
    }

    @Override
    public void buildCircuit(CircuitBuilder circuitBuilder) {
        // Internal electrolysis circuit is handled by ChemicalVatBlockEntity
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();
        ElectrodeType type = state.hasProperty(CarbonElectrodeBlock.TYPE) ? state.getValue(CarbonElectrodeBlock.TYPE) : ElectrodeType.ANODE;

        tooltip.add(Component.literal("Carbon Electrode").withStyle(ChatFormatting.GOLD));
        if (type == ElectrodeType.ANODE) {
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Type: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Anode (+)").withStyle(ChatFormatting.RED)));
        } else {
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Type: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Cathode (-)").withStyle(ChatFormatting.BLUE)));
        }

        ChemicalVatBlockEntity controller = getVatControllerBE();
        if (controller != null) {
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Connected to: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Chemical Vat").withStyle(ChatFormatting.GREEN)));
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Cell Voltage: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.2f V", controller.getVoltage())).withStyle(ChatFormatting.YELLOW)));
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Cell Current: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.2f A", controller.getCurrentAmps())).withStyle(ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Status: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("No Chemical Vat below!").withStyle(ChatFormatting.RED)));
        }

        return true;
    }
}
