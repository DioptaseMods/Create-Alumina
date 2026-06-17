package net.nerfashton.create_alumina.content.processing.gas_burner;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nerfashton.create_alumina.content.block.ModBlocks;
import net.nerfashton.create_alumina.content.block.entity.ModBlockEntityTypes;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GasBurnerBlock extends HorizontalDirectionalBlock implements IBE<GasBurnerBlockEntity>, IWrenchable, SpecialBlockItemRequirement {
    public static final MapCodec<GasBurnerBlock> CODEC = simpleCodec(GasBurnerBlock::new);
    public static final VoxelShape SHAPE = Block.box(0,0,0,16,13,16);

    public GasBurnerBlock(Properties properties) {
        super(properties);
    }

    public static LootTable.Builder buildLootTable() {
        LootItemCondition.Builder survivesExplosion = ExplosionCondition.survivesExplosion();
        //GasBurnerBlock block = ModBlocks.GAS_BURNER.get();
        LootTable.Builder builder = LootTable.lootTable();
        LootPool.Builder poolBuilder = LootPool.lootPool();

        return builder;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public static int getLight(BlockState state) {
        BlazeBurnerBlock.HeatLevel level = state.getValue(HEAT_LEVEL);
        return switch (level) {
            case NONE -> 0;
            case SMOULDERING -> 8;
            default -> 15;
        };
    }

    @Override
    public Class<GasBurnerBlockEntity> getBlockEntityClass() {
        return GasBurnerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GasBurnerBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.GAS_BURNER.get();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.NONE)
            return null;
        return IBE.super.newBlockEntity(pos, state);
        //return new GasBurnerBlockEntity(this, pos, state);
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
        return null;
    }
}
