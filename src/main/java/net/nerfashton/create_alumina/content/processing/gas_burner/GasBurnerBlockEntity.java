package net.nerfashton.create_alumina.content.processing.gas_burner;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.patryk3211.powergrid.electricity.basinheater.BasinHeaterBlock;

import java.util.List;

public class GasBurnerBlockEntity extends SmartBlockEntity {
    protected BlazeBurnerBlock.HeatLevel state;
    protected FluidTank fluidTank;
    protected IFluidHandler fluidHandler;

    public GasBurnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        fluidTank = new FluidTank(1000);
        setLazyTickRate(60);
    }

    public void setState(BlazeBurnerBlock.HeatLevel newState) {
        assert this.level != null;

        if (this.state != newState) {
            this.state = newState;
            this.level.setBlockAndUpdate(this.worldPosition, (BlockState)this.getBlockState().setValue(BasinHeaterBlock.HEAT_LEVEL, this.state));
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide || this.isVirtual()) {
            this.setState(BlazeBurnerBlock.HeatLevel.SEETHING);
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }
}
