package net.nerfashton.create_alumina.content.block;

import com.mojang.serialization.MapCodec;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nerfashton.create_alumina.content.fluids.vat.ChemicalVatBlock;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public class VatWindowBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<VatWindowBlock> CODEC = simpleCodec(VatWindowBlock::new);
    protected static final VoxelShaper SHAPE = VoxelShaper.forDirectional(box(0, 0, 0, 16, 16, 3), Direction.SOUTH);

    public VatWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));

    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        return SHAPE.get(direction);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder.add(FACING, WATERLOGGED));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        return canAttach(pLevel, pPos, pState.getValue(FACING).getOpposite());
    }

    public static boolean canAttach(LevelReader pReader, BlockPos pPos, Direction pDirection) {
        BlockPos blockpos = pPos.relative(pDirection);
        return pReader.getBlockState(blockpos)
                .getBlock() instanceof ChemicalVatBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        Direction clickedFace = context.getClickedFace();

        if (clickedFace.getAxis().isHorizontal()) {
            state = state.setValue(FACING, clickedFace);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
            }
        }
        return null;
    }
}
