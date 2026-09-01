package net.nerfashton.create_alumina.content.fluids.vat;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.ICapabilityProvider;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryWrapper;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.mixin.accessor.ItemStackHandlerAccessor;
import com.simibubi.create.foundation.utility.SameSizeCombinedInvWrapper;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
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
import net.nerfashton.create_alumina.registry.CABlockEntities;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;

import javax.annotation.Nullable;
import java.util.List;

public class ChemicalVatBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid, IMultiBlockEntityContainer.Inventory, Clearable {

    private static final int MAX_SIZE = 5;
    private static final int DEFAULT_CAPACITY_MULTIPLIER = 8000;
    private static final int SLOTS_PER_BLOCK = 6;

    protected IFluidHandler fluidCapability;
    protected ICapabilityProvider<IItemHandler> itemCapability;
    protected SmartFluidTank tankInventory;
    protected ItemStackHandler inventory;

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

    public ChemicalVatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tankInventory = createInventory();
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

    protected SmartFluidTank createInventory() {
        return new SmartFluidTank(getCapacityMultiplier(), this::onFluidStackChanged);
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
        int overflow = tankInventory.getFluidAmount() - tankInventory.getCapacity();
        if (overflow > 0)
            tankInventory.drain(overflow, FluidAction.EXECUTE);
        forceFluidLevelUpdate = true;
    }

    @Override
    public void removeController(boolean keepContents) {
        if (!hasLevel() || level.isClientSide)
            return;
        updateConnectivity = true;
        if (!keepContents)
            applyFluidTankSize(1);
        controller = null;
        width = 1;
        height = 1;
        onFluidStackChanged(tankInventory.getFluid());

        BlockState state = getBlockState();
        if (ChemicalVatBlock.isVat(state)) {
            state = state.setValue(ChemicalVatBlock.BOTTOM, true);
            state = state.setValue(ChemicalVatBlock.TOP, true);
            getLevel().setBlock(worldPosition, state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
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
        queuedSync = false;
        syncCooldown = SYNC_RATE;
    }

    @Override
    public void setController(BlockPos pos) {
        if ((!hasLevel() || level.isClientSide) && !isVirtual())
            return;
        if (pos.equals(this.controller))
            return;
        this.controller = pos;
        refreshCapabilities();
        setChanged();
        sendData();
    }

    public void refreshCapabilities() {
        fluidCapability = handlerForFluidCapability();
        itemCapability = null;
        invalidateCapabilities();
    }

    private IFluidHandler handlerForFluidCapability() {
        return isController() ? tankInventory : ((getControllerBE() != null) ? getControllerBE().handlerForFluidCapability() : new FluidTank(0));
    }

    private void initItemCapability() {
        if (!hasLevel())
            return;
        if (itemCapability != null && itemCapability.getCapability() != null)
            return;

        if (!isController()) {
            ChemicalVatBlockEntity controllerBE = getControllerBE();
            if (controllerBE == null)
                return;
            controllerBE.initItemCapability();
            itemCapability = ICapabilityProvider.of(() -> {
                if (controllerBE.isRemoved())
                    return null;
                if (controllerBE.itemCapability == null)
                    return null;
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

    public IFluidHandler getFluidHandler() {
        if (fluidCapability == null)
            refreshCapabilities();
        return fluidCapability;
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
    public boolean isController() {
        return controller == null || worldPosition.getX() == controller.getX()
                && worldPosition.getY() == controller.getY() && worldPosition.getZ() == controller.getZ();
    }

    @Override
    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        if (isController())
            return super.createRenderBoundingBox().expandTowards(width - 1, height - 1, width - 1);
        else
            return super.createRenderBoundingBox();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        ChemicalVatBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null)
            return false;
        return containedFluidTooltip(tooltip, isPlayerSneaking,
                level.getCapability(Capabilities.FluidHandler.BLOCK, controllerBE.getBlockPos(), null));
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

        if (isController()) {
            width = compound.getInt("Size");
            height = compound.getInt("Height");
            tankInventory.setCapacity(getTotalTankSize() * getCapacityMultiplier());

            tankInventory.readFromNBT(registries, compound.getCompound("TankContent"));
            if (tankInventory.getSpace() < 0)
                tankInventory.drain(-tankInventory.getSpace(), FluidAction.EXECUTE);
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
            if (isController())
                tankInventory.setCapacity(getCapacityMultiplier() * getTotalTankSize());
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
            compound.putInt("Size", width);
            compound.putInt("Height", height);
        }

        compound.put("Inventory", inventory.serializeNBT(registries));

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
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

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

    @Override
    public void buildCircuit(CircuitBuilder circuitBuilder) {

    }
}
