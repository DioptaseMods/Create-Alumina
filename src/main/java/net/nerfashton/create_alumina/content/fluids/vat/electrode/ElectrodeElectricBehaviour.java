package net.nerfashton.create_alumina.content.fluids.vat.electrode;

import net.minecraft.core.BlockPos;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ProxyElectricBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatBlockEntity;

import java.util.List;
import java.util.function.Supplier;

public class ElectrodeElectricBehaviour extends ProxyElectricBehaviour {
    private final CarbonElectrodeBlockEntity electrodeBE;

    public ElectrodeElectricBehaviour(
            CarbonElectrodeBlockEntity blockEntity,
            Supplier<BlockPos> targetPos) {
        super(blockEntity, targetPos);
        this.electrodeBE = blockEntity;
    }

    private ChemicalVatBlockEntity getVatController() {
        return electrodeBE.getVatControllerBE();
    }

    private int getVatTerminalIndex() {
        return electrodeBE.getVatTerminalIndex();
    }

    @Override
    public boolean hasTerminal(int terminal) {
        if (terminal != 0) {
            return false;
        }
        ChemicalVatBlockEntity vat = getVatController();
        if (vat == null || vat.getElectricBehaviour() == null) {
            return false;
        }
        int vatIndex = getVatTerminalIndex();
        if (vatIndex < 0) {
            return false;
        }
        return vat.getElectricBehaviour().hasTerminal(vatIndex);
    }

    @Override
    public OwnedFloatingNode getTerminal(int terminal) {
        if (terminal != 0) {
            return null;
        }
        ChemicalVatBlockEntity vat = getVatController();
        if (vat == null || vat.getElectricBehaviour() == null) {
            return null;
        }
        int vatIndex = getVatTerminalIndex();
        if (vatIndex < 0) {
            return null;
        }
        return vat.getElectricBehaviour().getTerminal(vatIndex);
    }

    @Override
    public List<OwnedFloatingNode> getExternalNodes() {
        OwnedFloatingNode node = getTerminal(0);
        if (node != null) {
            return List.of(node);
        }
        return List.of();
    }

    @Override
    public void joinNetwork(ElectricalNetwork network, int terminal) {
        if (terminal != 0) {
            return;
        }
        ChemicalVatBlockEntity vat = getVatController();
        if (vat != null && vat.getElectricBehaviour() != null) {
            int vatIndex = getVatTerminalIndex();
            if (vatIndex >= 0) {
                vat.getElectricBehaviour().joinNetwork(network, vatIndex);
            }
        }
    }

    @Override
    public void refreshEndpoints() {
        if (getWorld() == null) return;
        OwnedFloatingNode node = getTerminal(0);
        if (node != null) {
            BlockWireEndpoint newEp = new BlockWireEndpoint(this.getPos(), 0);
            GlobalElectricNetworks.getWorldNetworks(getWorld()).addAndMigrateNode(newEp, node);
        }
    }

    @Override
    public void addConnection(BlockWireEndpoint endpoint, BaseWireEntity wireEntity) {
        super.addConnection(endpoint, wireEntity);
        notifyVat();
    }

    @Override
    public void removeConnection(BlockWireEndpoint endpoint, BaseWireEntity wireEntity) {
        super.removeConnection(endpoint, wireEntity);
        notifyVat();
    }

    private void notifyVat() {
        ChemicalVatBlockEntity vat = getVatController();
        if (vat != null) {
            vat.onElectrodeChanged();
        }
    }
}
