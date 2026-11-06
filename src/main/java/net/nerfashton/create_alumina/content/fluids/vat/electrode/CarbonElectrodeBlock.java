package net.nerfashton.create_alumina.content.fluids.vat.electrode;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatBlock;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatBlockEntity;
import net.nerfashton.create_alumina.registry.CABlockEntities;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

import javax.annotation.Nullable;

public class CarbonElectrodeBlock extends HorizontalElectricBlock implements IBE<CarbonElectrodeBlockEntity>, IWrenchable {
    public static final EnumProperty<ElectrodeType> TYPE = EnumProperty.create("type", ElectrodeType.class);

    private static final TerminalBoundingBox NORTH_ANODE_TERMINAL = new TerminalBoundingBox(
            IDecoratedTerminal.POSITIVE, 6.5, 0.5, 1.0, 9.5, 3.5, 4.0
    ).withColor(0xFF3B5B);

    private static final TerminalBoundingBox NORTH_CATHODE_TERMINAL = new TerminalBoundingBox(
            IDecoratedTerminal.NEGATIVE, 6.5, 0.5, 1.0, 9.5, 3.5, 4.0
    ).withColor(0x3B81FF);

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 4, 11),
            Block.box(7, 1, 4, 9, 3, 5),
            Block.box(6.5, 0.5, 1, 9.5, 3.5, 4)
    );
    private static final VoxelShaper SHAPER = VoxelShaper.forHorizontal(NORTH_SHAPE, Direction.NORTH);

    public CarbonElectrodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TYPE, ElectrodeType.ANODE));

        BlockStateTerminalCollection.Builder builder = BlockStateTerminalCollection.builder(this);
        builder.forAllStates(state -> {
            Direction facing = state.getValue(HORIZONTAL_FACING);
            ElectrodeType type = state.getValue(TYPE);
            TerminalBoundingBox base = type == ElectrodeType.ANODE ? NORTH_ANODE_TERMINAL : NORTH_CATHODE_TERMINAL;
            TerminalBoundingBox rotated = rotateToFacing(base, facing);
            return new TerminalBoundingBox[]{rotated};
        });
        builder.withShapeMapper(state -> SHAPER.get(state.getValue(HORIZONTAL_FACING)));
        setTerminalCollection(builder.build());
    }

    private static TerminalBoundingBox rotateToFacing(TerminalBoundingBox box, Direction facing) {
        return switch (facing) {
            case SOUTH -> box.rotateAroundY(180);
            case EAST -> box.rotateAroundY(90);
            case WEST -> box.rotateAroundY(-90);
            default -> box;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TYPE);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (!(belowState.getBlock() instanceof ChemicalVatBlock)) {
            return false;
        }
        if (belowState.hasProperty(ChemicalVatBlock.TOP) && !belowState.getValue(ChemicalVatBlock.TOP)) {
            return false;
        }
        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (!(belowState.getBlock() instanceof ChemicalVatBlock)) {
            return null;
        }
        if (belowState.hasProperty(ChemicalVatBlock.TOP) && !belowState.getValue(ChemicalVatBlock.TOP)) {
            return null;
        }

        Direction facing = context.getHorizontalDirection().getOpposite();
        ElectrodeType type = ElectrodeType.ANODE;

        if (level.getBlockEntity(belowPos) instanceof ChemicalVatBlockEntity vatBE) {
            ChemicalVatBlockEntity controller = vatBE.getControllerBE();
            if (controller != null && controller.hasElectrode(ElectrodeType.ANODE)) {
                type = ElectrodeType.CATHODE;
            }
        }

        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(TYPE, type);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            toggleElectrodeType(state, level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                toggleElectrodeType(state, level, pos, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.onWrenched(state, context);
    }

    public void toggleElectrodeType(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        ElectrodeType newType = state.getValue(TYPE).opposite();
        BlockState newState = state.setValue(TYPE, newType);
        level.setBlock(pos, newState, 3);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, newType == ElectrodeType.ANODE ? 1.2f : 0.8f);

        if (player != null) {
            Component msg = Component.literal("Electrode set to: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(newType == ElectrodeType.ANODE
                            ? Component.literal("Anode (+)").withStyle(ChatFormatting.RED)
                            : Component.literal("Cathode (-)").withStyle(ChatFormatting.BLUE));
            player.displayClientMessage(msg, true);
        }

        if (level.getBlockEntity(pos) instanceof CarbonElectrodeBlockEntity electrodeBE) {
            electrodeBE.onTypeChanged();
        }

        notifyVat(level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        notifyVat(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof CarbonElectrodeBlockEntity electrodeBE) {
                if (electrodeBE.getElectricBehaviour() != null) {
                    electrodeBE.getElectricBehaviour().breakConnections();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
        if (state.getBlock() != newState.getBlock()) {
            notifyVat(level, pos);
        }
    }

    private void notifyVat(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            BlockPos below = pos.below();
            if (level.getBlockEntity(below) instanceof ChemicalVatBlockEntity vatBE) {
                vatBE.onElectrodeChanged();
            }
        }
    }

    @Override
    public Class<CarbonElectrodeBlockEntity> getBlockEntityClass() {
        return CarbonElectrodeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CarbonElectrodeBlockEntity> getBlockEntityType() {
        return CABlockEntities.CARBON_ELECTRODE_BE.get();
    }
}
