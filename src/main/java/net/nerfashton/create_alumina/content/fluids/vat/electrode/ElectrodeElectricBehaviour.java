package net.nerfashton.create_alumina.content.fluids.vat.electrode;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ProxyElectricBehaviour;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.function.Supplier;

public class ElectrodeElectricBehaviour extends ProxyElectricBehaviour {
    private final Supplier<Integer> terminalIndexSupplier;

    public <T extends SmartBlockEntity & IElectricEntity> ElectrodeElectricBehaviour(
            T blockEntity,
            Supplier<BlockPos> targetPos,
            Supplier<Integer> terminalIndexSupplier) {
        super(blockEntity, targetPos);
        this.terminalIndexSupplier = terminalIndexSupplier;
    }

    @Override
    public boolean hasTerminal(int terminal) {
        if (terminal != terminalIndexSupplier.get()) {
            return false;
        }
        return super.hasTerminal(terminal);
    }

    @Override
    public OwnedFloatingNode getTerminal(int terminal) {
        if (terminal != terminalIndexSupplier.get()) {
            return null;
        }
        return super.getTerminal(terminal);
    }

    @Override
    public void refreshEndpoints() {
        int term = terminalIndexSupplier.get();
        if (hasTerminal(term)) {
            OwnedFloatingNode node = getTerminal(term);
            if (node != null) {
                BlockWireEndpoint newEp = new BlockWireEndpoint(this.getPos(), term);
                GlobalElectricNetworks.getWorldNetworks(getWorld()).addAndMigrateNode(newEp, node);
            }
        }
    }
}
