package net.nerfashton.create_alumina.content.fluids.vat;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.ICapabilityProvider;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryWrapper;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.mixin.accessor.ItemStackHandlerAccessor;
import com.simibubi.create.foundation.utility.SameSizeCombinedInvWrapper;
import cute.ame.celsius.Fluid.BlockEntity.IFluidVesselBlockEntity;
import cute.ame.celsius.Fluid.Data.FluidNodeStore;
import cute.ame.celsius.Fluid.Data.SpeciesTable;
import cute.ame.celsius.Fluid.Helper.VesselNodes;
import cute.ame.celsius.Fluid.Level.FluidLevelData;
import cute.ame.celsius.Fluid.Registry.FluidSpecies;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.IFluidTank;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.nerfashton.create_alumina.api.chemical.ChemicalComposition;
import net.nerfashton.create_alumina.api.chemical.ChemicalSpecies;
import net.nerfashton.create_alumina.api.chemical.ChemistryAPI;
import net.nerfashton.create_alumina.api.chemical.registry.ModChemicals;
import net.nerfashton.create_alumina.api.chemical.registry.ModDataComponents;
import net.nerfashton.create_alumina.api.fluid.BlendingFluidHandler;
import net.nerfashton.create_alumina.content.fluids.vat.electrode.CarbonElectrodeBlock;
import net.nerfashton.create_alumina.content.fluids.vat.electrode.CarbonElectrodeBlockEntity;
import net.nerfashton.create_alumina.content.fluids.vat.electrode.ElectrodeType;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChemicalVatBlockEntity extends ElectricBlockEntity implements IFluidVesselBlockEntity, IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid, IMultiBlockEntityContainer.Inventory, Clearable {

    private static final int MAX_SIZE = 5;
    private static final int DEFAULT_CAPACITY_MULTIPLIER = 8000;
    private static final int SLOTS_PER_BLOCK = 6;

    protected IFluidHandler fluidCapability;
    protected BlendingFluidHandler blendingFluidHandler;
    protected ICapabilityProvider<IItemHandler> itemCapability;
    protected SmartFluidTank tankInventory;
    protected SmartFluidTank outputTankInventory;
    protected ItemStackHandler inventory;

    protected final VatData vatData = new VatData();

    // Celsius Gas Vessel state
    protected long nodeHandle = -1L;
    protected float burstPressure = 0.0f;

    // Client-side synced gas summary for goggles
    protected float clientGasPressure = 0f;
    protected float clientGasTemperature = 0f;
    protected float clientGasMoles = 0f;
    protected final Map<String, Float> clientGasSpecies = new LinkedHashMap<>();

    protected BlockPos controller;
    protected BlockPos lastKnownPos;
    protected boolean updateConnectivity;
    protected boolean updateCapability;
    protected boolean forceFluidLevelUpdate;

    protected int width;
    protected int height;

    private static final int SYNC_RATE = 8;
    protected int syncCooldown;
    protected boolean queuedSync;

    // For rendering purposes only
    private LerpedFloat fluidLevel;

    // PowerGrid electrolysis circuit state
    protected List<ElectricWire> anodeWires;
    protected List<ElectricWire> cathodeWires;
    protected float voltage = 0f;
    protected float currentAmps = 0f;

    public ChemicalVatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        if (anodeWires == null) anodeWires = new ArrayList<>();
        if (cathodeWires == null) cathodeWires = new ArrayList<>();
        tankInventory = createInventory();
        outputTankInventory = createOutputInventory();
        inventory = new ItemStackHandler(SLOTS_PER_BLOCK) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                setChanged();
            }
        };
        forceFluidLevelUpdate = true;
        updateConnectivity = false;
        updateCapability = false;
        height = 1;
        width = 1;
        refreshCapabilities();
    }

    public List<ElectricWire> getAnodeWires() {
        if (anodeWires == null) anodeWires = new ArrayList<>();
        return anodeWires;
    }

    public List<ElectricWire> getCathodeWires() {
        if (cathodeWires == null) cathodeWires = new ArrayList<>();
        return cathodeWires;
    }

    public VatData getVatData() {
        return vatData;
    }

    public float getVoltage() {
        return voltage;
    }

    public float getCurrentAmps() {
        return currentAmps;
    }

    @Override
    public long getNodeHandle() {
        if (!isController()) {
            ChemicalVatBlockEntity ctrl = getControllerBE();
            if (ctrl != null) {
                return ctrl.getNodeHandle();
            }
        }
        return this.nodeHandle;
    }

    @Override
    public void setNodeHandle(long handle) {
        if (isController()) {
            this.nodeHandle = handle;
            setChanged();
        } else {
            ChemicalVatBlockEntity ctrl = getControllerBE();
            if (ctrl != null) {
                ctrl.setNodeHandle(handle);
            }
        }
    }

    @Override
    public float getBurstPressure() {
        return this.burstPressure;
    }

    @Override
    public void setBurstPressure(float pressure) {
        this.burstPressure = pressure;
        setChanged();
    }

    public int getOrCreateGasNode(ServerLevel serverLevel) {
        if (!isController()) {
            ChemicalVatBlockEntity ctrl = getControllerBE();
            return ctrl != null ? ctrl.getOrCreateGasNode(serverLevel) : FluidNodeStore.INVALID;
        }
        FluidLevelData data = FluidLevelData.get(serverLevel);
        int nodeId = data.store().resolve(this.nodeHandle);
        if (nodeId == FluidNodeStore.INVALID) {
            float totalVolumeLitres = getTotalTankSize() * 1000.0f;
            nodeId = data.store().create(totalVolumeLitres, cute.ame.celsius.Core.Thermal.Ambient.of(serverLevel).temperatureK());
            this.nodeHandle = data.store().handle(nodeId);
            data.graph().invalidate();
            setChanged();
        }
        return nodeId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            rollBurstPressure(serverLevel);
            if (isController()) {
                VesselNodes.onLoaded(serverLevel, this.worldPosition, this);
            } else {
                FluidLevelData.get(serverLevel).graph().track(this.worldPosition);
            }
        }
    }

    @Override
    public void remove() {
        if (this.level instanceof ServerLevel serverLevel) {
            VesselNodes.onUnloaded(serverLevel, this.worldPosition);
            if (isController() && this.nodeHandle != -1L) {
                FluidLevelData data = FluidLevelData.get(serverLevel);
                int nodeId = data.store().resolve(this.nodeHandle);
                if (nodeId != FluidNodeStore.INVALID) {
                    data.store().destroy(nodeId);
                }
            }
        }
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        if (this.level instanceof ServerLevel serverLevel) {
            VesselNodes.onUnloaded(serverLevel, this.worldPosition);
        }
        super.onChunkUnloaded();
    }

    protected SmartFluidTank createInventory() {
        return new SmartFluidTank(getCapacityMultiplier(), this::onFluidStackChanged);
    }

    protected SmartFluidTank createOutputInventory() {
        return new SmartFluidTank(getCapacityMultiplier(), this::onOutputFluidStackChanged);
    }

    protected void onOutputFluidStackChanged(FluidStack newFluidStack) {
        if (!hasLevel())
            return;
        if (!level.isClientSide) {
            setChanged();
            sendData();
        }
    }

    public SmartFluidTank getOutputTank() {
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null && controllerBE != this) {
            return controllerBE.outputTankInventory;
        }
        return outputTankInventory;
    }

    public int fillOutputTank(FluidStack stack, FluidAction action) {
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null && controllerBE != this) {
            return controllerBE.fillOutputTank(stack, action);
        }
        int filled = outputTankInventory.fill(stack, action);
        if (filled > 0 && action.execute()) {
            onOutputFluidStackChanged(outputTankInventory.getFluid());
        }
        return filled;
    }

    protected void updateConnectivity() {
        updateConnectivity = false;
        if (!hasLevel() || level.isClientSide)
            return;
        if (!isController())
            return;
        ConnectivityHandler.formMulti(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (syncCooldown > 0) {
            syncCooldown--;
            if (syncCooldown == 0 && queuedSync)
                sendData();
        }

        if (lastKnownPos == null)
            lastKnownPos = getBlockPos();
        else if (!lastKnownPos.equals(worldPosition) && worldPosition != null) {
            onPositionChanged();
            return;
        }

        if (updateCapability) {
            updateCapability = false;
            refreshCapabilities();
        }
        if (updateConnectivity)
            updateConnectivity();
        if (fluidLevel != null)
            fluidLevel.tickChaser();

        if (level != null && !level.isClientSide && isController()) {
            if (anodeWires == null) anodeWires = new ArrayList<>();
            if (cathodeWires == null) cathodeWires = new ArrayList<>();

            boolean hasAnode = hasElectrode(ElectrodeType.ANODE);
            boolean hasCathode = hasElectrode(ElectrodeType.CATHODE);
            if (hasAnode && hasCathode && (anodeWires.isEmpty() || cathodeWires.isEmpty())) {
                onElectrodeChanged();
            } else if ((!hasAnode || !hasCathode) && (!anodeWires.isEmpty() || !cathodeWires.isEmpty())) {
                onElectrodeChanged();
            }

            float prevVoltage = this.voltage;
            float prevCurrent = this.currentAmps;

            if (!anodeWires.isEmpty() && !cathodeWires.isEmpty()) {
                float targetResistance = calculateElectrolyteResistance();
                float rAnode = Math.max(0.05f, (targetResistance * 0.5f) * anodeWires.size());
                float rCathode = Math.max(0.05f, (targetResistance * 0.5f) * cathodeWires.size());

                for (ElectricWire wire : anodeWires) {
                    if (Math.abs(wire.getResistance() - rAnode) > 0.05f) {
                        wire.setResistance(rAnode);
                    }
                }
                for (ElectricWire wire : cathodeWires) {
                    if (Math.abs(wire.getResistance() - rCathode) > 0.05f) {
                        wire.setResistance(rCathode);
                    }
                }

                double totalCurrentAnode = 0;
                double totalPowerAnode = 0;
                double maxAnodeV = 0;
                for (ElectricWire wire : anodeWires) {
                    double i = Math.abs(wire.current());
                    double v = Math.abs(wire.potentialDifference());
                    totalCurrentAnode += i;
                    totalPowerAnode += v * i;
                    if (v > maxAnodeV) maxAnodeV = v;
                }

                double totalCurrentCathode = 0;
                double totalPowerCathode = 0;
                double maxCathodeV = 0;
                for (ElectricWire wire : cathodeWires) {
                    double i = Math.abs(wire.current());
                    double v = Math.abs(wire.potentialDifference());
                    totalCurrentCathode += i;
                    totalPowerCathode += v * i;
                    if (v > maxCathodeV) maxCathodeV = v;
                }

                this.currentAmps = (float) ((totalCurrentAnode + totalCurrentCathode) * 0.5);
                if (this.currentAmps > 0.001f) {
                    this.voltage = (float) ((totalPowerAnode + totalPowerCathode) / this.currentAmps);
                } else {
                    this.voltage = (float) (maxAnodeV + maxCathodeV);
                }
            } else {
                this.voltage = 0f;
                this.currentAmps = 0f;
            }

            boolean voltageChanged = Math.abs(this.voltage - prevVoltage) > 0.01f || (this.voltage == 0f && prevVoltage != 0f);
            boolean currentChanged = Math.abs(this.currentAmps - prevCurrent) > 0.005f || (this.currentAmps == 0f && prevCurrent != 0f);
            if (voltageChanged || currentChanged) {
                setChanged();
                sendData();
            }

            vatData.tick(this);
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        sendData();
        if (hasLevel() && level.isClientSide)
            invalidateRenderBoundingBox();
    }

    private void onPositionChanged() {
        removeController(true);
        lastKnownPos = worldPosition;
    }

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        if (!hasLevel())
            return;

        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
                    ChemicalVatBlockEntity vatAt = ConnectivityHandler.partAt(getType(), level, pos);
                    if (vatAt == null)
                        continue;
                    level.updateNeighbourForOutputSignal(pos, vatAt.getBlockState().getBlock());
                }
            }
        }

        if (!level.isClientSide) {
            setChanged();
            sendData();
        }

        if (isVirtual()) {
            if (fluidLevel == null)
                fluidLevel = LerpedFloat.linear().startWithValue(getFillState());
            fluidLevel.chase(getFillState(), .5f, LerpedFloat.Chaser.EXP);
        }
    }

    @Override
    public ChemicalVatBlockEntity getControllerBE() {
        if (isController() || !hasLevel())
            return this;
        BlockEntity blockEntity = level.getBlockEntity(controller);
        if (blockEntity instanceof ChemicalVatBlockEntity)
            return (ChemicalVatBlockEntity) blockEntity;
        return null;
    }

    public void applyFluidTankSize(int blocks) {
        tankInventory.setCapacity(blocks * getCapacityMultiplier());
        outputTankInventory.setCapacity(blocks * getCapacityMultiplier());
        int overflow = tankInventory.getFluidAmount() - tankInventory.getCapacity();
        if (overflow > 0)
            tankInventory.drain(overflow, FluidAction.EXECUTE);
        int outputOverflow = outputTankInventory.getFluidAmount() - outputTankInventory.getCapacity();
        if (outputOverflow > 0)
            outputTankInventory.drain(outputOverflow, FluidAction.EXECUTE);
        forceFluidLevelUpdate = true;
    }

    public void removeController(boolean keepContents) {
        if (level.isClientSide)
            return;
        updateConnectivity = true;
        controller = null;
        width = 1;
        height = 1;
        onFluidStackChanged(tankInventory.getFluid());

        BlockState state = getBlockState();
        if (ChemicalVatBlock.isVat(state)) {
            state = state.setValue(ChemicalVatBlock.BOTTOM, true);
            state = state.setValue(ChemicalVatBlock.TOP, true);
            getLevel().setBlock(worldPosition, state, 22);
        }

        if (level instanceof ServerLevel serverLevel) {
            FluidLevelData data = FluidLevelData.get(serverLevel);
            if (!keepContents) {
                if (this.nodeHandle != -1L) {
                    int oldNodeId = data.store().resolve(this.nodeHandle);
                    if (oldNodeId != FluidNodeStore.INVALID) {
                        data.store().destroy(oldNodeId);
                    }
                    this.nodeHandle = -1L;
                }
            }
            if (this.nodeHandle == -1L) {
                VesselNodes.onLoaded(serverLevel, worldPosition, this);
            }
            data.graph().invalidate();
        }

        refreshCapabilities();
        setChanged();
        sendData();
    }

    public void sendDataImmediately() {
        syncCooldown = 0;
        queuedSync = false;
        sendData();
    }

    @Override
    public void sendData() {
        if (syncCooldown > 0) {
            queuedSync = true;
            return;
        }
        super.sendData();
        syncCooldown = SYNC_RATE;
        queuedSync = false;
    }

    public void refreshCapabilities() {
        fluidCapability = handlerForFluidCapability();
        itemCapability = null;
        invalidateCapabilities();
    }

    private IFluidHandler handlerForFluidCapability() {
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null)
            return controllerBE.getOrCreateVatFluidHandler(null);
        return isController() ? getOrCreateVatFluidHandler(null) : new FluidTank(0);
    }

    public BlendingFluidHandler getOrCreateBlendingFluidHandler() {
        if (blendingFluidHandler == null) {
            blendingFluidHandler = new BlendingFluidHandler(tankInventory);
        }
        return blendingFluidHandler;
    }

    public IFluidHandler getOrCreateVatFluidHandler(@Nullable Direction side) {
        BlendingFluidHandler main = getOrCreateBlendingFluidHandler();
        return new VatFluidHandler(main, outputTankInventory, side);
    }

    public void updateCapabilities() {
        refreshCapabilities();
    }

    private void initItemCapability() {
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null)
            return;

        if (controllerBE != this) {
            itemCapability = ICapabilityProvider.of(() -> {
                if (controllerBE.itemCapability == null)
                    controllerBE.initItemCapability();
                return controllerBE.itemCapability.getCapability();
            });
            return;
        }

        IItemHandlerModifiable[] invs = new IItemHandlerModifiable[height * width * width];
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = worldPosition.offset(xOffset, yOffset, zOffset);
                    ChemicalVatBlockEntity vatAt = ConnectivityHandler.partAt(getType(), level, pos);
                    invs[yOffset * width * width + xOffset * width + zOffset] = vatAt != null ? vatAt.inventory : new ItemStackHandler(SLOTS_PER_BLOCK);
                }
            }
        }

        itemCapability = ICapabilityProvider.of(new VersionedInventoryWrapper(SameSizeCombinedInvWrapper.create(invs)));
    }

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null)
            return controllerBE.getOrCreateVatFluidHandler(side);
        if (isController())
            return getOrCreateVatFluidHandler(side);
        if (fluidCapability == null)
            refreshCapabilities();
        return fluidCapability;
    }

    public IFluidHandler getFluidHandler() {
        return getFluidHandler(null);
    }

    public IItemHandler getItemHandler() {
        if (itemCapability == null || itemCapability.getCapability() == null)
            initItemCapability();
        return itemCapability != null ? itemCapability.getCapability() : null;
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        if (isController())
            return new AABB(worldPosition).expandTowards(width - 1, height - 1, width - 1);
        return super.createRenderBoundingBox();
    }

    @Override
    public boolean isController() {
        return controller == null || worldPosition.equals(controller);
    }

    public void setController(BlockPos controller) {
        if (level.isClientSide && !isVirtual())
            return;
        if (controller.equals(this.controller))
            return;

        // When becoming a slave, transfer any remaining gas to controller and destroy local node
        if (level instanceof ServerLevel serverLevel && this.nodeHandle != -1L) {
            FluidLevelData data = FluidLevelData.get(serverLevel);
            int oldNodeId = data.store().resolve(this.nodeHandle);
            if (oldNodeId != FluidNodeStore.INVALID) {
                BlockEntity ctrlEntity = level.getBlockEntity(controller);
                if (ctrlEntity instanceof ChemicalVatBlockEntity ctrlBE) {
                    int ctrlNodeId = data.store().resolve(ctrlBE.getNodeHandle());
                    if (ctrlNodeId != FluidNodeStore.INVALID && ctrlNodeId != oldNodeId) {
                        SpeciesTable table = FluidSpecies.active();
                        for (int s = 0; s < table.size(); s++) {
                            float amt = data.store().amount(oldNodeId, s);
                            if (amt > 0) {
                                data.store().add(ctrlNodeId, s, amt);
                            }
                        }
                        data.touch(ctrlNodeId);
                    }
                }
                data.store().destroy(oldNodeId);
            }
            this.nodeHandle = -1L;
        }

        this.controller = controller;
        refreshCapabilities();
        setChanged();
        sendData();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        BlockPos controllerBefore = controller;
        int prevSize = width;
        int prevHeight = height;

        updateConnectivity = compound.contains("Uninitialized");

        lastKnownPos = null;
        if (compound.contains("LastKnownPos"))
            lastKnownPos = NBTHelper.readBlockPos(compound, "LastKnownPos");

        controller = null;
        if (compound.contains("Controller"))
            controller = NBTHelper.readBlockPos(compound, "Controller");

        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));

        if (!clientPacket) {
            if (compound.contains("node")) {
                this.nodeHandle = compound.getLong("node");
            } else {
                this.nodeHandle = -1L;
            }
            this.burstPressure = compound.getFloat("burst");
        } else {
            if (compound.contains("ClientGasData")) {
                CompoundTag gasTag = compound.getCompound("ClientGasData");
                this.clientGasPressure = gasTag.getFloat("Pressure");
                this.clientGasTemperature = gasTag.getFloat("Temperature");
                this.clientGasMoles = gasTag.getFloat("Moles");
                this.clientGasSpecies.clear();
                if (gasTag.contains("Species")) {
                    CompoundTag speciesTag = gasTag.getCompound("Species");
                    for (String key : speciesTag.getAllKeys()) {
                        clientGasSpecies.put(key, speciesTag.getFloat(key));
                    }
                }
            } else {
                this.clientGasPressure = 0f;
                this.clientGasTemperature = 0f;
                this.clientGasMoles = 0f;
                this.clientGasSpecies.clear();
            }
        }

        if (isController()) {
            width = compound.getInt("Size");
            height = compound.getInt("Height");
            int totalCap = getTotalTankSize() * getCapacityMultiplier();
            tankInventory.setCapacity(totalCap);
            outputTankInventory.setCapacity(totalCap);

            tankInventory.readFromNBT(registries, compound.getCompound("TankContent"));
            if (tankInventory.getSpace() < 0)
                tankInventory.drain(-tankInventory.getSpace(), FluidAction.EXECUTE);

            if (compound.contains("OutputTankContent")) {
                outputTankInventory.readFromNBT(registries, compound.getCompound("OutputTankContent"));
                if (outputTankInventory.getSpace() < 0)
                    outputTankInventory.drain(-outputTankInventory.getSpace(), FluidAction.EXECUTE);
            }

            if (compound.contains("VatData"))
                vatData.read(compound.getCompound("VatData"), registries);
            if (compound.contains("Voltage"))
                voltage = compound.getFloat("Voltage");
            if (compound.contains("CurrentAmps"))
                currentAmps = compound.getFloat("CurrentAmps");
        }

        if (compound.contains("ForceFluidLevel") || fluidLevel == null)
            fluidLevel = LerpedFloat.linear().startWithValue(getFillState());

        updateCapability = true;

        if (!clientPacket)
            return;

        boolean changeOfController = controllerBefore == null ? controller != null : !controllerBefore.equals(controller);
        if (changeOfController || prevSize != width || prevHeight != height) {
            if (hasLevel())
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
            if (isController()) {
                int totalCap = getCapacityMultiplier() * getTotalTankSize();
                tankInventory.setCapacity(totalCap);
                outputTankInventory.setCapacity(totalCap);
            }
            invalidateRenderBoundingBox();
        }

        if (isController()) {
            float fillState = getFillState();
            if (compound.contains("ForceFluidLevel") || fluidLevel == null)
                fluidLevel = LerpedFloat.linear().startWithValue(fillState);
            fluidLevel.chase(fillState, 0.5f, LerpedFloat.Chaser.EXP);
        }
    }

    public float getFillState() {
        return (float) tankInventory.getFluidAmount() / tankInventory.getCapacity();
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (updateConnectivity)
            compound.putBoolean("Uninitialized", true);
        if (lastKnownPos != null)
            compound.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        if (!isController())
            compound.put("Controller", NbtUtils.writeBlockPos(controller));
        if (isController()) {
            compound.put("TankContent", tankInventory.writeToNBT(registries, new CompoundTag()));
            compound.put("OutputTankContent", outputTankInventory.writeToNBT(registries, new CompoundTag()));
            compound.putInt("Size", width);
            compound.putInt("Height", height);
            compound.put("VatData", vatData.write(new CompoundTag(), registries));
            compound.putFloat("Voltage", voltage);
            compound.putFloat("CurrentAmps", currentAmps);
        }

        compound.put("Inventory", inventory.serializeNBT(registries));

        if (!clientPacket) {
            compound.putLong("node", this.nodeHandle);
            compound.putFloat("burst", this.burstPressure);
        } else if (isController() && level instanceof ServerLevel serverLevel) {
            FluidLevelData data = FluidLevelData.getIfPresent(serverLevel);
            if (data != null && nodeHandle != -1L) {
                int nodeId = data.store().resolve(nodeHandle);
                if (nodeId != FluidNodeStore.INVALID) {
                    CompoundTag gasTag = new CompoundTag();
                    gasTag.putFloat("Pressure", (float) data.store().pressure(nodeId));
                    gasTag.putFloat("Temperature", data.store().temperature(nodeId));
                    gasTag.putFloat("Moles", data.store().moles(nodeId));
                    SpeciesTable table = FluidSpecies.active();
                    CompoundTag speciesTag = new CompoundTag();
                    for (int s = 0; s < table.size(); s++) {
                        float amt = data.store().amount(nodeId, s);
                        if (amt > 0.001f) {
                            speciesTag.putFloat(table.key(s), amt);
                        }
                    }
                    gasTag.put("Species", speciesTag);
                    compound.put("ClientGasData", gasTag);
                }
            }
        }

        super.write(compound, registries, clientPacket);

        if (!clientPacket)
            return;
        if (forceFluidLevelUpdate)
            compound.putBoolean("ForceFluidLevel", true);
        if (queuedSync)
            compound.putBoolean("LazySync", true);
        forceFluidLevelUpdate = false;
    }

    @Override
    public void writeSafe(CompoundTag compound, HolderLookup.Provider registries) {
        if (isController()) {
            compound.putInt("Size", width);
            compound.putInt("Height", height);
        }
    }

    @Override
    public void clearContent() {
        ((ItemStackHandlerAccessor) inventory).create$getStacks().clear();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public SmartFluidTank getTankInventory() {
        return tankInventory;
    }

    public ItemStackHandler getInventoryOfBlock() {
        return inventory;
    }

    public int getTotalTankSize() {
        return width * width * height;
    }

    public static int getMaxSize() {
        return MAX_SIZE;
    }

    public static int getCapacityMultiplier() {
        return DEFAULT_CAPACITY_MULTIPLIER;
    }

    public int getMaxHeight() {
        if (this.width == 5 || this.width == 4) return 4;
        return 16;
    }

    public LerpedFloat getFluidLevel() {
        return fluidLevel;
    }

    public void setFluidLevel(LerpedFloat fluidLevel) {
        this.fluidLevel = fluidLevel;
    }

    @Override
    public void preventConnectivityUpdate() {
        updateConnectivity = false;
    }

    @Override
    public void notifyMultiUpdated() {
        BlockState state = this.getBlockState();
        if (ChemicalVatBlock.isVat(state)) {
            state = state.setValue(ChemicalVatBlock.BOTTOM, getController().getY() == getBlockPos().getY());
            state = state.setValue(ChemicalVatBlock.TOP, getController().getY() + height - 1 == getBlockPos().getY());
            level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }

        if (level instanceof ServerLevel serverLevel) {
            FluidLevelData data = FluidLevelData.get(serverLevel);

            if (isController()) {
                int totalBlocks = getTotalTankSize();
                float totalVolumeLitres = totalBlocks * 1000.0f;

                int nodeId = data.store().resolve(this.nodeHandle);
                if (nodeId == FluidNodeStore.INVALID) {
                    nodeId = data.store().create(totalVolumeLitres, cute.ame.celsius.Core.Thermal.Ambient.of(serverLevel).temperatureK());
                    this.nodeHandle = data.store().handle(nodeId);
                } else {
                    data.store().setVolume(nodeId, totalVolumeLitres);
                    data.touch(nodeId);
                }
                data.graph().invalidate();
            } else {
                if (this.nodeHandle != -1L) {
                    int oldNodeId = data.store().resolve(this.nodeHandle);
                    if (oldNodeId != FluidNodeStore.INVALID) {
                        data.store().destroy(oldNodeId);
                    }
                    this.nodeHandle = -1L;
                }
            }
        }

        onFluidStackChanged(tankInventory.getFluid());
        refreshCapabilities();
        setChanged();
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        if (longAxis == Direction.Axis.Y)
            return getMaxHeight();
        return getMaxWidth();
    }

    @Override
    public int getMaxWidth() {
        return MAX_SIZE;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public boolean hasTank() {
        return true;
    }

    @Override
    public int getTankSize(int tank) {
        return getCapacityMultiplier();
    }

    @Override
    public void setTankSize(int tank, int blocks) {
        applyFluidTankSize(blocks);
    }

    @Override
    public IFluidTank getTank(int tank) {
        return tankInventory;
    }

    @Override
    public FluidStack getFluid(int tank) {
        return tankInventory.getFluid().copy();
    }

    @Override
    public boolean hasInventory() {
        return true;
    }

    @Override
    public void setExtraData(@Nullable Object data) {}

    @Override
    @Nullable
    public Object getExtraData() {
        return null;
    }

    @Override
    public Object modifyExtraData(Object data) {
        return data;
    }

    public List<BlockPos> getElectrodePositions() {
        List<BlockPos> positions = new ArrayList<>();
        if (level == null) return positions;
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null) return positions;

        for (int x = 0; x < controllerBE.width; x++) {
            for (int z = 0; z < controllerBE.width; z++) {
                BlockPos abovePos = controllerBE.worldPosition.offset(x, controllerBE.height, z);
                if (level.getBlockState(abovePos).getBlock() instanceof CarbonElectrodeBlock) {
                    positions.add(abovePos);
                }
            }
        }
        return positions;
    }

    public int getElectrodeTerminalIndex(BlockPos electrodePos) {
        List<BlockPos> positions = getElectrodePositions();
        return positions.indexOf(electrodePos);
    }

    public List<CarbonElectrodeBlockEntity> getAttachedElectrodes() {
        List<CarbonElectrodeBlockEntity> electrodes = new ArrayList<>();
        if (level == null) return electrodes;
        for (BlockPos pos : getElectrodePositions()) {
            if (level.getBlockEntity(pos) instanceof CarbonElectrodeBlockEntity electrode) {
                electrodes.add(electrode);
            }
        }
        return electrodes;
    }

    public boolean hasElectrode(ElectrodeType type) {
        for (CarbonElectrodeBlockEntity electrode : getAttachedElectrodes()) {
            BlockState state = electrode.getBlockState();
            if (state.hasProperty(CarbonElectrodeBlock.TYPE) && state.getValue(CarbonElectrodeBlock.TYPE) == type) {
                return true;
            }
        }
        return false;
    }

    public float calculateElectrolyteResistance() {
        FluidStack fluid = getTankInventory().getFluid();
        if (fluid.isEmpty() || !ChemistryAPI.isSolution(fluid)) {
            return 1000.0f;
        }
        ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
        if (comp == null || comp.solutes().isEmpty()) {
            return 250.0f;
        }
        float totalIonicConcentration = 0f;
        for (Map.Entry<ResourceLocation, Float> entry : comp.solutes().entrySet()) {
            ChemicalSpecies species = ModChemicals.REGISTRY.get(entry.getKey());
            if (species != null && species.charge() != 0) {
                totalIonicConcentration += entry.getValue() * Math.abs(species.charge());
            }
        }
        return Math.max(0.1f, 20.0f / (totalIonicConcentration * 2.0f + 0.1f));
    }

    public void onElectrodeChanged() {
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null) {
            if (controllerBE.getElectricBehaviour() != null) {
                controllerBE.getElectricBehaviour().rebuildCircuit(true);
            }
            for (CarbonElectrodeBlockEntity electrode : controllerBE.getAttachedElectrodes()) {
                if (electrode.getElectricBehaviour() != null) {
                    electrode.getElectricBehaviour().refreshEndpoints();
                }
            }
            controllerBE.setChanged();
            controllerBE.sendData();
        }
    }

    @Override
    public void buildCircuit(CircuitBuilder circuitBuilder) {
        if (!isController()) {
            return;
        }
        if (anodeWires == null) {
            anodeWires = new ArrayList<>();
        } else {
            anodeWires.clear();
        }
        if (cathodeWires == null) {
            cathodeWires = new ArrayList<>();
        } else {
            cathodeWires.clear();
        }

        List<BlockPos> electrodePositions = getElectrodePositions();
        circuitBuilder.setTerminalCount(electrodePositions.size());

        List<Integer> anodeIndices = new ArrayList<>();
        List<Integer> cathodeIndices = new ArrayList<>();
        if (level != null) {
            for (int i = 0; i < electrodePositions.size(); i++) {
                BlockState state = level.getBlockState(electrodePositions.get(i));
                if (state.hasProperty(CarbonElectrodeBlock.TYPE)) {
                    if (state.getValue(CarbonElectrodeBlock.TYPE) == ElectrodeType.ANODE) {
                        anodeIndices.add(i);
                    } else {
                        cathodeIndices.add(i);
                    }
                }
            }
        }

        if (!anodeIndices.isEmpty() && !cathodeIndices.isEmpty()) {
            FloatingNode electrolyteNode = circuitBuilder.addInternalNode();
            float baseResistance = calculateElectrolyteResistance();
            float rAnode = Math.max(0.05f, (baseResistance * 0.5f) * anodeIndices.size());
            float rCathode = Math.max(0.05f, (baseResistance * 0.5f) * cathodeIndices.size());

            for (int anodeIdx : anodeIndices) {
                ElectricWire wire = circuitBuilder.connect(rAnode, circuitBuilder.terminalNode(anodeIdx), electrolyteNode);
                anodeWires.add(wire);
            }
            for (int cathodeIdx : cathodeIndices) {
                ElectricWire wire = circuitBuilder.connect(rCathode, electrolyteNode, circuitBuilder.terminalNode(cathodeIdx));
                cathodeWires.add(wire);
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null)
            return false;

        boolean added = containedFluidTooltip(tooltip, isPlayerSneaking,
                level.getCapability(Capabilities.FluidHandler.BLOCK, controllerBE.getBlockPos(), Direction.UP));

        FluidStack outputFluid = controllerBE.outputTankInventory.getFluid();
        if (!outputFluid.isEmpty()) {
            tooltip.add(Component.literal("Electrolysis Output Tank:").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(outputFluid.getDescriptionId()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format(": %d / %d mB", outputFluid.getAmount(), controllerBE.outputTankInventory.getCapacity()))
                            .withStyle(ChatFormatting.AQUA)));
            added = true;
        }

        FluidStack fluid = controllerBE.getTankInventory().getFluid();
        if (ChemistryAPI.isSolution(fluid)) {
            ChemicalComposition comp = fluid.get(ModDataComponents.CHEMICAL_COMPOSITION.get());
            if (comp != null && !comp.solutes().isEmpty()) {
                tooltip.add(Component.literal("Dissolved Solutes:").withStyle(ChatFormatting.GOLD));
                for (Map.Entry<ResourceLocation, Float> entry : comp.solutes().entrySet()) {
                    ChemicalSpecies species = ModChemicals.REGISTRY.get(entry.getKey());
                    String name = species != null ? species.symbol() : entry.getKey().getPath();
                    tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format(": %.3f mol/L", entry.getValue())).withStyle(ChatFormatting.AQUA)));
                }
                tooltip.add(Component.literal("Temperature: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.1f °C (%.1f K)", comp.temperature() - 273.15f, comp.temperature()))
                                .withStyle(ChatFormatting.YELLOW)));
                added = true;
            }
        }

        if (hasElectrode(ElectrodeType.ANODE) || hasElectrode(ElectrodeType.CATHODE) || controllerBE.currentAmps > 0.001f || controllerBE.voltage > 0.001f) {
            tooltip.add(Component.literal("Electrolysis Cell:").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Voltage: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.2f V", controllerBE.voltage)).withStyle(ChatFormatting.YELLOW)));
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Current: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.2f A", controllerBE.currentAmps)).withStyle(ChatFormatting.AQUA)));
            float power = controllerBE.voltage * controllerBE.currentAmps;
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Power: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.2f W", power)).withStyle(ChatFormatting.GREEN)));
            added = true;
        }

        if (controllerBE.clientGasMoles > 0.001f || controllerBE.clientGasPressure > 0.001f) {
            tooltip.add(Component.literal("Gas Chamber:").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Pressure: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.2f kPa (%.3f atm)", controllerBE.clientGasPressure * 101.325f, controllerBE.clientGasPressure))
                            .withStyle(ChatFormatting.AQUA)));
            tooltip.add(Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Total Gas: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.3f mol", controllerBE.clientGasMoles))
                            .withStyle(ChatFormatting.YELLOW)));
            for (Map.Entry<String, Float> entry : controllerBE.clientGasSpecies.entrySet()) {
                tooltip.add(Component.literal("   - ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(entry.getKey().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(String.format(": %.3f mol", entry.getValue())).withStyle(ChatFormatting.GRAY)));
            }
            added = true;
        }

        return added;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (isController() && level != null && !level.isClientSide) {
            vatData.dropPartialItems(level, worldPosition);
        }
    }
}
