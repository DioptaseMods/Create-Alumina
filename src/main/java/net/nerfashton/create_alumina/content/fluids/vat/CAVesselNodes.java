package net.nerfashton.create_alumina.content.fluids.vat;

import cute.ame.celsius.Fluid.Block.IFluidVesselBlock;
import cute.ame.celsius.Fluid.BlockEntity.IFluidVesselBlockEntity;
import cute.ame.celsius.Fluid.Data.FluidNodeStore;
import cute.ame.celsius.Fluid.Graph.FluidGraph;
import cute.ame.celsius.Fluid.Helper.VesselRelocation;
import cute.ame.celsius.Fluid.Level.FluidLevelData;
import cute.ame.celsius.Fluid.Level.RoomLevelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CAVesselNodes {

    private CAVesselNodes() {}

    public static void onRemoved(Level world, BlockPos pos, BlockState state) {
        if (!(world instanceof ServerLevel serverLevel))
            return;

        FluidLevelData data = FluidLevelData.getIfPresent(serverLevel);
        if (data == null)
            return;

        if (VesselRelocation.depart(serverLevel, pos)) {
            data.graph().forget(pos);
            return;
        }

        FluidNodeStore store = data.store();
        FluidGraph graph = data.graph();
        graph.forget(pos);

        // Detach direct room mouth if present
        if (state.getBlock() instanceof IFluidVesselBlock vesselBlock) {
            BlockPos mouth = vesselBlock.roomMouth(pos, state);
            if (mouth != null) {
                detachRoom(serverLevel, data, pos, mouth);
            }
        }

        // Also check adjacent room nodes in case of bridging connections
        RoomLevelData roomData = RoomLevelData.getIfPresent(serverLevel);
        if (roomData != null) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (Direction dir : Direction.values()) {
                cursor.setWithOffset(pos, dir);
                int roomNode = roomData.nodeAt(cursor);
                if (roomNode != -1) {
                    detachRoom(serverLevel, data, pos, cursor);
                }
            }
        }

        // Destroy or scale the vessel's node in FluidNodeStore
        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (be instanceof IFluidVesselBlockEntity vesselBE) {
            long handle = vesselBE.getNodeHandle();
            int node = store.resolve(handle);
            if (node != FluidNodeStore.INVALID) {
                float vol = store.volume(node);
                float blockVol = (state.getBlock() instanceof IFluidVesselBlock vb)
                        ? vb.getVolumeLitres()
                        : 1000.0f;
                float removeVol = Math.min(vol, blockVol);

                scaleMatter(store, node, (vol - removeVol) / vol);
                if (vol - removeVol <= 1.0f) {
                    store.destroy(node);
                    vesselBE.setNodeHandle(-1L);
                } else {
                    store.setVolume(node, vol - removeVol);
                }
            }
        }

        graph.invalidate();
        data.setDirty();
    }

    public static void detachRoom(ServerLevel serverLevel, FluidLevelData data, BlockPos pos, BlockPos mouth) {
        RoomLevelData roomData = RoomLevelData.getIfPresent(serverLevel);
        if (roomData == null)
            return;

        int roomNode = roomData.nodeAt(mouth);
        if (roomNode == -1)
            return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction dir : Direction.values()) {
            cursor.setWithOffset(mouth, dir);
            if (cursor.equals(pos))
                continue;

            BlockState neighborState = serverLevel.getBlockState(cursor);
            if (neighborState.getBlock() instanceof IFluidVesselBlock vessel) {
                BlockPos otherMouth = vessel.roomMouth(cursor, neighborState);
                if (otherMouth != null && roomData.nodeAt(otherMouth) == roomNode) {
                    return;
                }
            }
        }

        roomData.remove(serverLevel, roomNode);
        data.graph().invalidate();
    }

    private static void scaleMatter(FluidNodeStore store, int node, float factor) {
        if (factor >= 1.0f)
            return;
        int stride = store.getStride();
        for (int i = 0; i < stride; i++) {
            float amt = store.amount(node, i);
            if (amt > 0.0f) {
                store.setAmount(node, i, factor <= 0.0f ? 0.0f : amt * factor);
            }
        }
    }
}
